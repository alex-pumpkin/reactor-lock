/*
 * Copyright 2019 Alexander Pankin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.alexpumpkin.reactorlock;

import com.github.alexpumpkin.reactorlock.concurrency.LockCacheMono;
import com.github.alexpumpkin.reactorlock.concurrency.LockMono;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import reactor.cache.CacheMono;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

public class CacheTests {
    private static final AtomicReference<String> CACHE = new AtomicReference<>();
    private static final Map<String, Signal<String>> MAP_CACHE = new HashMap<>();

    @After
    public void after() {
        CACHE.set(null);
        MAP_CACHE.clear();
    }

    @Test
    public void testNoLock() {
        AtomicInteger innerCounter = new AtomicInteger();
        AtomicBoolean delayFlag = new AtomicBoolean(false);
        Mono<String> helloMono = Mono.defer(() ->
                getCachedMono(getMono4Test(innerCounter, delayFlag)));

        Mono.fromRunnable(() -> delayFlag.set(true))
                .delaySubscription(Duration.ofMillis(10))
                .subscribe();

        Flux.range(0, 1000)
                .parallel(100)
                .runOn(Schedulers.elastic())
                .flatMap(integer -> helloMono)
                .sequential()
                .blockLast(Duration.ofSeconds(10));

        Assert.assertTrue(innerCounter.get() > 1);
    }

    @Test
    public void testWithLock() {
        AtomicInteger innerCounter = new AtomicInteger();
        Mono<String> helloMono = Mono.defer(() ->
                getCachedLockedMono(getMono4Test(innerCounter, null)));

        Flux.range(0, 1000)
                .parallel(100)
                .runOn(Schedulers.elastic())
                .flatMap(integer -> helloMono)
                .sequential()
                .blockLast(Duration.ofSeconds(10));

        Assert.assertEquals(1, innerCounter.get());

        CACHE.set(null);

        Flux.range(0, 1000)
                .parallel(100)
                .runOn(Schedulers.elastic())
                .flatMap(integer -> helloMono)
                .sequential()
                .blockLast(Duration.ofSeconds(10));

        Assert.assertEquals(2, innerCounter.get());
    }

    @Test
    public void testWithLockMapCache() {
        AtomicInteger innerCounter = new AtomicInteger();
        Mono<String> helloMono = Mono.defer(() ->
                getCachedLockedMonoMapCache(getMono4Test(innerCounter, null)));

        Flux.range(0, 1000)
                .parallel(100)
                .runOn(Schedulers.elastic())
                .flatMap(integer -> helloMono)
                .sequential()
                .blockLast(Duration.ofSeconds(10));

        Assert.assertEquals(1, innerCounter.get());

        MAP_CACHE.clear();

        Flux.range(0, 1000)
                .parallel(100)
                .runOn(Schedulers.elastic())
                .flatMap(integer -> helloMono)
                .sequential()
                .blockLast(Duration.ofSeconds(10));

        Assert.assertEquals(2, innerCounter.get());
    }

    @Test
    public void testWithLockErrorMono() {
        Mono<String> mono4Test = Mono.defer(() -> getCachedLockedMono(Mono.error(new IllegalStateException())))
                .onErrorResume(IllegalStateException.class, e -> Mono.just("1"));

        Flux.range(0, 1000)
                .parallel(100)
                .runOn(Schedulers.elastic())
                .flatMap(integer -> mono4Test)
                .sequential()
                .blockLast(Duration.ofSeconds(10));
    }

    private Mono<String> getMono4Test(AtomicInteger counter, AtomicBoolean delayFlag) {
        return Mono.fromCallable(() -> {
            if (delayFlag != null) {
                Awaitility.await().untilTrue(delayFlag);
            }
            return "hello " + counter.incrementAndGet();
        });
    }

    private static final BiFunction<String, Signal<? extends String>, Mono<Void>> CACHE_WRITER =
            (k, signal) -> Mono.fromRunnable(() -> Optional.ofNullable(signal.get())
                    .ifPresent(CACHE::set));
    private static final Function<String, Mono<Signal<? extends String>>> CACHE_READER =
            k -> Mono.justOrEmpty(CACHE.get()).map(Signal::next);

    private Mono<String> getCachedMono(Mono<String> source) {
        return CacheMono.lookup(CACHE_READER, "cacheKey")
                .onCacheMissResume(() -> source)
                .andWriteWith(CACHE_WRITER);
    }

    private Mono<String> getCachedLockedMono(Mono<String> source) {
        return LockCacheMono.create(LockMono.key("cacheKey")
                .maxLockDuration(Duration.ofSeconds(5))
                .build())
                .lookup(CACHE_READER)
                .onCacheMissResume(() -> source)
                .andWriteWith(CACHE_WRITER);
    }

    private Mono<String> getCachedLockedMonoMapCache(Mono<String> source) {
        return LockCacheMono.create(LockMono.key("cacheKeyMap")
                .maxLockDuration(Duration.ofSeconds(5))
                .build())
                .lookup(MAP_CACHE)
                .onCacheMissResume(() -> source);
    }
}
