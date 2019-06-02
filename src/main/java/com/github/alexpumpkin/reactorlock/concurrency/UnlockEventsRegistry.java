package com.github.alexpumpkin.reactorlock.concurrency;

import reactor.core.publisher.Mono;

import java.util.function.Consumer;

//todo: documentation and license
public interface UnlockEventsRegistry {
    /**
     * Add lock to registry.
     *
     * @param lockData lock data.
     * @return empty Mono.
     */
    Mono<Void> add(LockData lockData);

    /**
     * Remove lock from registry.
     *
     * @param lockData lock data.
     * @return empty Mono.
     */
    Mono<Void> remove(LockData lockData);

    /**
     * Register unlock event listener for lock.
     *
     * @param lockData            lock data.
     * @param unlockEventListener unlock event listener.
     * @return Mono with {@code true} if registered.
     */
    Mono<Boolean> register(LockData lockData, Consumer<Integer> unlockEventListener);
}
