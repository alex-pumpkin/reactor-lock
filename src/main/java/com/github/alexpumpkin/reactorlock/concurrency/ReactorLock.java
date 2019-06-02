package com.github.alexpumpkin.reactorlock.concurrency;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.time.Duration;

//todo: documentation and license
public interface ReactorLock {
    /**
     * Try lock synchronously.
     *
     * @param lockData lock data.
     * @return {@code true} - lock is acquired, {@code false} - lock is busy.
     */
    Mono<Tuple2<Boolean, LockData>> tryLock(LockData lockData);

    /**
     * Unlock.
     *
     * @param lockData lock data.
     */
    Mono<Void> unlock(LockData lockData);

    /**
     * Get max lock duration.
     *
     * @return duration.
     */
    Duration getMaxLockDuration();
}
