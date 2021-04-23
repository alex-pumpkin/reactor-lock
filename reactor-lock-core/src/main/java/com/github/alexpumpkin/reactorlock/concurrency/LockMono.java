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
package com.github.alexpumpkin.reactorlock.concurrency;

import com.github.alexpumpkin.reactorlock.concurrency.exceptions.LockIsNotAvailableException;
import com.github.alexpumpkin.reactorlock.concurrency.impl.InMemoryMapReactorLock;
import com.github.alexpumpkin.reactorlock.concurrency.impl.InMemoryUnlockEventsRegistry;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;


/**
 * Locking helper that wraps any {@link Mono} to subscribe it in the exclusive manner by given key.
 * <p>
 * Example:
 * <pre><code>
 *     Mono&lt;Integer> lockedMono = LockMono.key("key")
 *             .lock(Mono.fromCallable(() -> {...}));
 * </code></pre>
 * </p>
 */
public final class LockMono<K> {
    private final LockData<K> lockData;
    private final Flux<Integer> unlockEvents;
    private final Consumer<Integer> unlockEventSink;
    private final UnlockEventsRegistry unlockEventsRegistry;
    private final ReactorLock<K> reactorLock;
    private final Duration maxLockDuration;

    private LockMono(K key, ReactorLock<K> reactorLock, UnlockEventsRegistry unlockEventsRegistry,
                     Duration maxLockDuration) {
        this.lockData = LockData.<K>builder()
                .key(key)
                .uuid(UUID.randomUUID().toString())
                .build();
        this.maxLockDuration = maxLockDuration;
        Sinks.Many<Integer> sinksMany = Sinks.many().unicast().onBackpressureBuffer();
        this.unlockEventSink = unlockCode -> sinksMany.emitNext(unlockCode, Sinks.EmitFailureHandler.FAIL_FAST);
        this.unlockEvents = sinksMany.asFlux().log();
        this.reactorLock = reactorLock;
        this.unlockEventsRegistry = unlockEventsRegistry;
    }

    /**
     * Transform given Mono to subscribe it in the exclusive manner.
     *
     * @param source original Mono.
     * @param <T>    type of Mono.
     * @return Mono with locking.
     */
    public final <T> Mono<T> lock(Mono<T> source) {
        return tryLock(source)
                .flatMap(s -> unlock().then(Mono.just(s)))
                .transform(retryTransformer());
    }

    <T> Mono<T> tryLock(Mono<T> source) {
        return reactorLock.tryLock(lockData, maxLockDuration)
                .flatMap(isLocked -> {
                    if (Boolean.TRUE.equals(isLocked.getT1())) {
                        return unlockEventsRegistry.add(lockData)
                                .then(source.switchIfEmpty(unlock().then(Mono.empty()))
                                        .onErrorResume(throwable -> unlock().then(Mono.error(throwable))));
                    } else {
                        return Mono.error(new LockIsNotAvailableException(isLocked.getT2()));
                    }
                });
    }

    Mono<Void> unlock() {
        return reactorLock.unlock(lockData)
                .then(unlockEventsRegistry.remove(lockData));
    }

    <T> UnaryOperator<Mono<T>> retryTransformer() {
        return mono -> mono
                .doOnError(LockIsNotAvailableException.class,
                        error -> unlockEventsRegistry.register(error.getLockData(), unlockEventSink)
                                .doOnNext(registered -> {
                                    if (Boolean.FALSE.equals(registered)) unlockEventSink.accept(-1);
                                })
                                .then(Mono.just(2).doOnNext(unlockEventSink)
                                        .delaySubscription(maxLockDuration))
                                .subscribe())
                .doOnError(throwable -> !(throwable instanceof LockIsNotAvailableException),
                        ignored -> unlockEventSink.accept(0))
                .retryWhen(Retry.from(retrySignalFlux -> retrySignalFlux.zipWith(unlockEvents,
                        (retrySignal, integer) -> {
                            if (retrySignal.failure() instanceof LockIsNotAvailableException) return integer;
                            else throw Exceptions.propagate(retrySignal.failure());
                        })));
    }

    K getKey() {
        return lockData.getKey();
    }

    /**
     * Create {@link LockMonoBuilder} with given key.
     *
     * @param key key that defines monos that should be subscribed in the exclusive manner.
     * @param <K> key type. It is required to override equals and hashCode for K type.
     * @return new {@link LockMonoBuilder} with default params.
     */
    public static <K> LockMonoBuilder<K> key(K key) {
        return new LockMonoBuilder<>(key);
    }

    public static final class LockMonoBuilder<K> {
        private static final Duration DEFAULT_MAX_LOCK_DURATION = Duration.ofSeconds(60);
        private final K key;
        private Duration maxLockDuration = DEFAULT_MAX_LOCK_DURATION;
        private ReactorLock<K> reactorLock = InMemoryMapReactorLock.defaultInstance();
        private UnlockEventsRegistry unlockEventsRegistry = InMemoryUnlockEventsRegistry.DEFAULT_INSTANCE;

        private LockMonoBuilder(K key) {
            this.key = key;
        }

        public LockMonoBuilder<K> maxLockDuration(Duration duration) {
            this.maxLockDuration = duration;
            return this;
        }

        public LockMonoBuilder<K> reactorLock(ReactorLock<K> reactorLock) {
            this.reactorLock = reactorLock;
            return this;
        }

        public LockMonoBuilder<K> unlockEventsRegistry(UnlockEventsRegistry unlockEventsRegistry) {
            this.unlockEventsRegistry = unlockEventsRegistry;
            return this;
        }

        public LockMono<K> build() {
            return new LockMono<>(this.key,
                    this.reactorLock,
                    this.unlockEventsRegistry,
                    maxLockDuration);
        }

        /**
         * Build {@link LockMono} and wrap given Mono to subscribe it in the exclusive manner.
         *
         * @param source original Mono.
         * @param <T>    type of Mono.
         * @return Mono with locking.
         */
        public <T> Mono<T> lock(Mono<T> source) {
            return build().lock(source);
        }
    }

}
