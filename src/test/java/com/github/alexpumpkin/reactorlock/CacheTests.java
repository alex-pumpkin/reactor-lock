package com.github.alexpumpkin.reactorlock;

import com.github.alexpumpkin.reactorlock.concurrency.Lock;
import com.github.alexpumpkin.reactorlock.concurrency.LockCommand;
import com.github.alexpumpkin.reactorlock.concurrency.impl.InMemoryMapLockCommand;
import com.github.alexpumpkin.reactorlock.concurrency.impl.InMemoryUnlockEventsRegistry;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import reactor.cache.CacheMono;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

public class CacheTests {
    private static final AtomicReference<String> CACHE = new AtomicReference<>();

    @After
    public void after() {
        CACHE.set(null);
    }

    @Test
    public void testNoLock() {
        AtomicInteger counter = new AtomicInteger();
        Mono<String> mono4Test = getMono4Test(counter);

        Flux.merge(
                getCachedMono("cacheKey", mono4Test),
                getCachedMono("cacheKey", mono4Test),
                getCachedMono("cacheKey", mono4Test),
                getCachedMono("cacheKey", mono4Test),
                getCachedMono("cacheKey", mono4Test))
                .blockLast();

        Assert.assertTrue(counter.get() > 1);
    }

    @Test
    public void testWithLock() {
        AtomicInteger counter = new AtomicInteger();
        Mono<String> mono4Test = getMono4Test(counter);

        getFlux4TestWithLock(mono4Test).blockLast();
        Assert.assertEquals(1, counter.get());

        CACHE.set(null);
        getFlux4TestWithLock(mono4Test).blockLast();
        Assert.assertEquals(2, counter.get());
    }

    @Test
    public void testWithLockNoDelay() {
        AtomicInteger counter = new AtomicInteger();
        Mono<String> mono4Test = Mono.fromCallable(() -> "hello " + counter.incrementAndGet())
                .subscribeOn(Schedulers.elastic());

        getFlux4TestWithLock(mono4Test).blockLast();
        Assert.assertEquals(1, counter.get());

        CACHE.set(null);
        getFlux4TestWithLock(mono4Test).blockLast();
        Assert.assertEquals(2, counter.get());
    }

    @Test(expected = IllegalStateException.class)
    public void testWithLockErrorMono() {
        Mono<String> mono4Test = Mono.<String>error(new IllegalStateException())
                .subscribeOn(Schedulers.elastic());

        getFlux4TestWithLock(mono4Test).blockLast();
    }

    private Mono<String> getMono4Test(AtomicInteger counter) {
        return Mono.fromCallable(() -> {
            Thread.sleep(50);
            return "hello " + counter.incrementAndGet();
        }).subscribeOn(Schedulers.elastic());
    }

    private Flux<String> getFlux4TestWithLock(Mono<String> mono4Test) {
        return Flux.merge(
                getCachedLockedMono("cacheKey", mono4Test),
                getCachedLockedMono("cacheKey", mono4Test),
                getCachedLockedMono("cacheKey", mono4Test),
                getCachedLockedMono("cacheKey", mono4Test),
                getCachedLockedMono("cacheKey", mono4Test),
                getCachedLockedMono("cacheKey", mono4Test),
                getCachedLockedMono("cacheKey", mono4Test),
                getCachedLockedMono("cacheKey", mono4Test),
                getCachedLockedMono("cacheKey", mono4Test),
                getCachedLockedMono("cacheKey", mono4Test)
        );
    }

    private static final BiFunction<String, Signal<? extends String>, Mono<Void>> CACHE_WRITER =
            (k, signal) -> Mono.fromRunnable(() -> Optional.ofNullable(signal.get())
                    .ifPresent(o -> CACHE.set(signal.get())));
    private static final Function<String, Mono<Signal<? extends String>>> CACHE_READER =
            k -> Mono.justOrEmpty(CACHE.get()).map(Signal::next);

    private Mono<String> getCachedMono(String cacheKey, Mono<String> source) {
        return CacheMono.lookup(CACHE_READER, cacheKey)
                .onCacheMissResume(() -> source)
                .andWriteWith(CACHE_WRITER);
    }

    private Mono<String> getCachedLockedMono(String cacheKey, Mono<String> source) {
        LockCommand lockCommand = new InMemoryMapLockCommand(Duration.ofSeconds(60));
        Lock lock = new Lock(lockCommand, cacheKey, new InMemoryUnlockEventsRegistry());

        return CacheMono.lookup(CACHE_READER, cacheKey)
                // Lock and double check
                .onCacheMissResume(() -> lock.tryLock(Mono.fromCallable(CACHE::get).switchIfEmpty(source)))
                .andWriteWith(lock.unlockAfterCacheWriter(CACHE_WRITER))
                // Retry if lock is not available
                .transform(lock.retryTransformer());
    }
}
