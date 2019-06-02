package com.github.alexpumpkin.reactorlock.concurrency.impl;

import com.github.alexpumpkin.reactorlock.concurrency.LockData;
import com.github.alexpumpkin.reactorlock.concurrency.ReactorLock;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

//todo: documentation and license
public class InMemoryMapReactorLock implements ReactorLock {
    private static final ConcurrentHashMap<String, LockData> REGISTRY = new ConcurrentHashMap<>();

    private final Duration maxDuration;

    public InMemoryMapReactorLock(Duration maxDuration) {
        this.maxDuration = maxDuration;
    }

    @Override
    public Mono<Tuple2<Boolean, LockData>> tryLock(LockData lockData) {
        return Mono.fromCallable(() -> {
            LockData newRegistryLockData = REGISTRY.compute(lockData.getKey(), (s, registryLockData) -> {
                if (registryLockData == null || registryLockData.getAcquiredDateTime()
                        .isBefore(OffsetDateTime.now(ZoneOffset.UTC).minus(maxDuration))) {
                    return lockData.toBuilder()
                            .acquiredDateTime(OffsetDateTime.now(ZoneOffset.UTC))
                            .build();
                }

                return registryLockData;
            });

            return Tuples.of(Objects.equals(newRegistryLockData, lockData), newRegistryLockData);
        });
    }

    @Override
    public Mono<Void> unlock(LockData lockData) {
        return Mono.fromRunnable(() -> REGISTRY.remove(lockData.getKey(), lockData));
    }

    @Override
    public Duration getMaxLockDuration() {
        return maxDuration;
    }

}
