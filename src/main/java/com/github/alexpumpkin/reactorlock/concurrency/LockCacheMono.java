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

import reactor.cache.CacheMono;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;

import java.util.Map;
import java.util.function.Function;

/**
 * Caching helper with exclusive subscribing of the original {@link Mono}. It is based on {@link CacheMono}.
 * <p>
 * Example:
 * <pre><code>
 *     Mono&lt;Integer> cachedMono = LockCacheMono.create(LockMono.key("cacheKey").build())
 *             .lookup(k -> {... Mono that reads from the cache ...})
 *             .onCacheMissResume(() -> {... Original Mono ...})
 *             .andWriteWith((k, signal) -> {... Mono that puts value to the cache ...});
 * </code></pre>
 * <p>
 *
 * @see CacheMono
 */
public final class LockCacheMono<K> {
    private final LockMono<K> lock;

    private LockCacheMono(LockMono<K> lock) {
        this.lock = lock;
    }

    /**
     * Create new instance with given {@link LockMono}.
     *
     * @param lock {@link LockMono} instance to prevent concurrent writing operations.
     * @return new instance of the {@link LockCacheMono}.
     * @see LockMono
     */
    public static <K> LockCacheMono<K> create(LockMono<K> lock) {
        return new LockCacheMono<>(lock);
    }

    /**
     * Prepare next {@link CacheMono.MonoCacheBuilderMapMiss builder step} to use to set up the source.
     *
     * @param cacheMap {@link Map} wrapper of a cache.
     * @param <V>      Value Type.
     * @return The next {@link CacheMono.MonoCacheBuilderMapMiss} builder step to use to set up the source.
     * @see CacheMono#lookup(java.util.Map, java.lang.Object)
     */
    public <V> CacheMono.MonoCacheBuilderMapMiss<V> lookup(Map<? super K, Signal<V>> cacheMap) {
        Mono<Signal<V>> cachedSignal = Mono.fromCallable(() -> cacheMap.get(lock.getKey()));
        return otherSupplier -> Mono.defer(() ->
                cachedSignal
                        .switchIfEmpty(lock.tryLock(Mono.defer(() -> cachedSignal)
                                .switchIfEmpty(otherSupplier.get().materialize()))
                                .transform(lock.retryTransformer())
                                .flatMap(signal -> Mono.fromRunnable(() -> cacheMap.put(lock.getKey(), signal))
                                        .then(lock.unlock())
                                        .onErrorResume(throwable -> lock.unlock())
                                        .then(Mono.just(signal))))
                        .dematerialize()
        );
    }

    /**
     * Prepare next {@link CacheMono.MonoCacheBuilderMapMiss builder step} to use to set up the source.
     *
     * @param reader a {@link Function} that looks up {@link Signal} from a cache, returning
     *               them as a {@link Mono Mono&lt;Signal&gt;}.
     * @param <V>    Value Type.
     * @return The next {@link CacheMono.MonoCacheBuilderCacheMiss} builder step to use to set up the source.
     * @see CacheMono#lookup(java.util.function.Function, java.lang.Object)
     */
    public <V> CacheMono.MonoCacheBuilderCacheMiss<K, V> lookup(
            Function<K, Mono<Signal<? extends V>>> reader) {

        return otherSupplier -> writer -> Mono.defer(() ->
                reader.apply(lock.getKey())
                        .switchIfEmpty(lock.tryLock(Mono.defer(() -> reader.apply(lock.getKey()))
                                .switchIfEmpty(otherSupplier.get()
                                        .materialize()))
                                .transform(lock.retryTransformer())
                                .flatMap(signal -> writer.apply(lock.getKey(), signal)
                                        .then(lock.unlock())
                                        .onErrorResume(throwable -> lock.unlock())
                                        .then(Mono.just(signal))
                                )
                        )
                        .dematerialize()
        );
    }
}
