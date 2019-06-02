package com.github.alexpumpkin.reactorlock.concurrency;

import reactor.cache.CacheMono;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;

import java.util.Map;
import java.util.function.Function;

//todo: documentation and license
/**
 * Caching helper with exclusive access to writing operations. It is based on {@link CacheMono}.
 *
 * @see CacheMono
 */
public final class LockCacheMono {
    private final LockMono lock;

    private LockCacheMono(LockMono lock) {
        this.lock = lock;
    }

    /**
     * Create new instance with given {@link LockMono}.
     *
     * @param lock {@link LockMono} instance to prevent concurrent writing operations
     * @return new instance of the {@link LockCacheMono}
     * @see LockMono
     */
    public static LockCacheMono create(LockMono lock) {
        return new LockCacheMono(lock);
    }

    /**
     * Prepare next {@link CacheMono.MonoCacheBuilderMapMiss builder step} to use to set up the source.
     *
     * @param cacheMap {@link Map} wrapper of a cache.
     * @param key      mapped key.
     * @param <K>      Key Type.
     * @param <V>      Value Type.
     * @return The next {@link CacheMono.MonoCacheBuilderMapMiss builder step} to use to set up the source.
     * @see CacheMono#lookup(java.util.Map, java.lang.Object)
     */
    public <K, V> CacheMono.MonoCacheBuilderMapMiss<V> lookup(Map<K, Signal<V>> cacheMap, K key) {
        Mono<Signal<V>> cachedSignal = Mono.fromCallable(() -> cacheMap.get(key));
        return otherSupplier -> Mono.defer(() ->
                cachedSignal
                        .switchIfEmpty(lock.tryLock(Mono.defer(() -> cachedSignal)
                                .switchIfEmpty(otherSupplier.get().materialize()))
                                .transform(lock.retryTransformer())
                                .flatMap(signal -> Mono.fromRunnable(() -> cacheMap.put(key, signal))
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
     * @param key    mapped key.
     * @param <K>    Key Type.
     * @param <V>    Value Type.
     * @return The next {@link CacheMono.MonoCacheBuilderCacheMiss builder step} to use to set up the source.
     * @see CacheMono#lookup(java.util.function.Function, java.lang.Object)
     */
    public <K, V> CacheMono.MonoCacheBuilderCacheMiss<K, V> lookup(
            Function<K, Mono<Signal<? extends V>>> reader, K key) {

        return otherSupplier -> writer -> Mono.defer(() ->
                reader.apply(key)
                        .switchIfEmpty(lock.tryLock(Mono.defer(() -> reader.apply(key))
                                .switchIfEmpty(otherSupplier.get()
                                        .materialize()))
                                .transform(lock.retryTransformer())
                                .flatMap(signal -> writer.apply(key, signal)
                                        .then(lock.unlock())
                                        .onErrorResume(throwable -> lock.unlock())
                                        .then(Mono.just(signal))
                                )
                        )
                        .dematerialize()
        );
    }
}
