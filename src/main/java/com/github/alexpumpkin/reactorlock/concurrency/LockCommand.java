package com.github.alexpumpkin.reactorlock.concurrency;

import reactor.util.function.Tuple2;

import java.time.Duration;

public interface LockCommand {
    /**
     * Try lock synchronously.
     *
     * @param lockData lock data.
     * @return {@code true} - lock is acquired, {@code false} - lock is busy.
     */
    Tuple2<Boolean, LockData> tryLock(LockData lockData);

    /**
     * Unlock.
     *
     * @param lockData lock data.
     */
    void unlock(LockData lockData);

    /**
     * Get max lock duration.
     *
     * @return duration.
     */
    Duration getMaxLockDuration();
}
