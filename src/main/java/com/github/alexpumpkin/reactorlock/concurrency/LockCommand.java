package com.github.alexpumpkin.reactorlock.concurrency;

public interface LockCommand {
    /**
     * Try lock synchronously.
     *
     * @param lockData lock data.
     * @return {@code true} - lock is acquired, {@code false} - lock is busy.
     */
    boolean tryLock(LockData lockData);

    /**
     * Unlock.
     *
     * @param lockData lock data.
     */
    void unlock(LockData lockData);
}
