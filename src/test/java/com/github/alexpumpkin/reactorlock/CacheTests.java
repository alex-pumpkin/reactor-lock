package com.github.alexpumpkin.reactorlock;

import com.github.alexpumpkin.reactorlock.concurrency.Lock;
import com.github.alexpumpkin.reactorlock.concurrency.LockCommand;
import com.github.alexpumpkin.reactorlock.concurrency.exceptions.LockIsNotAvailableException;
import com.github.alexpumpkin.reactorlock.concurrency.impl.InMemoryMapLockCommand;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import reactor.cache.CacheMono;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.core.scheduler.Schedulers;
import reactor.retry.Retry;

import java.time.Duration;
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
        LockCommand lockCommand = new InMemoryMapLockCommand(Duration.ofSeconds(60));
        AtomicInteger counter = new AtomicInteger();
        Mono<String> mono4Test = getMono4Test(counter);

        Flux<String> flux = Flux.merge(
                getCachedLockedMono("cacheKey", mono4Test, lockCommand),
                getCachedLockedMono("cacheKey", mono4Test, lockCommand),
                getCachedLockedMono("cacheKey", mono4Test, lockCommand),
                getCachedLockedMono("cacheKey", mono4Test, lockCommand),
                getCachedLockedMono("cacheKey", mono4Test, lockCommand));

        flux.blockLast();
        Assert.assertEquals(1, counter.get());

        CACHE.set(null);
        flux.blockLast();
        Assert.assertEquals(2, counter.get());
    }

    private Mono<String> getMono4Test(AtomicInteger counter) {
        return Mono.fromCallable(() -> {
            Thread.sleep(50);
            return "hello " + counter.incrementAndGet();
        }).subscribeOn(Schedulers.parallel());
    }

    private static final BiFunction<String, Signal<? extends String>, Mono<Void>> CACHE_WRITER =
            (k, signal) -> Mono.fromRunnable(() -> CACHE.set(signal.get()));
    private static final Function<String, Mono<Signal<? extends String>>> CACHE_READER =
            k -> Mono.justOrEmpty(CACHE.get()).map(Signal::next);

    private Mono<String> getCachedMono(String cacheKey, Mono<String> source) {
        return CacheMono.lookup(CACHE_READER, cacheKey)
                .onCacheMissResume(() -> source)
                .andWriteWith(CACHE_WRITER);
    }

    private Mono<String> getCachedLockedMono(String cacheKey, Mono<String> source, LockCommand lockCommand) {
        Lock lock = new Lock(lockCommand, cacheKey);
        return CacheMono.lookup(CACHE_READER, cacheKey)
                // Lock and double check
                .onCacheMissResume(() -> lock.tryLock(Mono.justOrEmpty(CACHE.get()).switchIfEmpty(source)))
                .andWriteWith(lock.unlockAfterCacheWriter(CACHE_WRITER))
                // Retry if lock is not available
                .retryWhen(Retry.anyOf(LockIsNotAvailableException.class)
                        .fixedBackoff(Duration.ofMillis(100))
                        .retryMax(100));
    }
}
