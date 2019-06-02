package com.github.alexpumpkin.reactorlock.concurrency.impl;

import com.github.alexpumpkin.reactorlock.concurrency.LockData;
import com.github.alexpumpkin.reactorlock.concurrency.UnlockEventsRegistry;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;

//todo: documentation and license
public enum InMemoryUnlockEventsRegistry implements UnlockEventsRegistry {
    INSTANCE;

    private static final Map<LockData, Set<Consumer<Integer>>> REGISTRY = new ConcurrentHashMap<>();
    private static final Map<LockData, StampedLock> LOCKS_MAP = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> add(LockData lockData) {
        return Mono.fromRunnable(() -> {
            LOCKS_MAP.computeIfAbsent(lockData, lockData1 -> new StampedLock());
            REGISTRY.computeIfAbsent(lockData, lockData1 -> new HashSet<>());

        });
    }

    @Override
    public Mono<Boolean> register(LockData lockData, Consumer<Integer> unlockEventListener) {
        return Mono.fromSupplier(() -> {
            StampedLock lock = LOCKS_MAP.get(lockData);
            if (lock != null) {
                long writeLock = lock.writeLock();
                try {
                    return Objects.nonNull(REGISTRY.computeIfPresent(lockData, (lockData1, consumers) -> {
                        consumers.add(unlockEventListener);
                        return consumers;
                    }));
                } finally {
                    lock.unlockWrite(writeLock);
                }
            } else {
                return false;
            }

        });
    }

    @Override
    public Mono<Void> remove(LockData lockData) {
        return Mono.fromRunnable(() -> {
            StampedLock lock = LOCKS_MAP.get(lockData);
            if (lock != null) {
                long writeLock = lock.writeLock();
                try {
                    REGISTRY.getOrDefault(lockData, Collections.emptySet())
                            .forEach(integerConsumer -> integerConsumer.accept(1));
                    REGISTRY.remove(lockData);
                } finally {
                    lock.unlockWrite(writeLock);
                }

                LOCKS_MAP.remove(lockData);
            }
        });
    }
}
