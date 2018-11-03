package com.github.alexpumpkin.reactorlock.concurrency.impl;

import com.github.alexpumpkin.reactorlock.concurrency.LockCommand;
import com.github.alexpumpkin.reactorlock.concurrency.LockData;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMapLockCommand implements LockCommand {
    private static final ConcurrentHashMap<String, LockData> REGISTRY = new ConcurrentHashMap<>();

    private final Duration maxDuration;

    public InMemoryMapLockCommand(Duration maxDuration) {
        this.maxDuration = maxDuration;
    }

    @Override
    public Tuple2<Boolean, LockData> tryLock(LockData lockData) {
        LockData registryLockData = REGISTRY.computeIfAbsent(lockData.getKey(),
                ignored -> lockData.toBuilder()
                        .acquiredDateTime(OffsetDateTime.now(ZoneOffset.UTC))
                        .build());

        if (Objects.equals(registryLockData, lockData)) {
            return Tuples.of(true, lockData);
        } else if (registryLockData.getAcquiredDateTime()
                .isBefore(OffsetDateTime.now(ZoneOffset.UTC).minus(maxDuration))) {
            return tryLock(lockData, registryLockData);
        }

        return Tuples.of(false, registryLockData);
    }

    @Override
    public void unlock(LockData lockData) {
        REGISTRY.remove(lockData.getKey(), lockData);
    }

    @Override
    public Duration getMaxLockDuration() {
        return maxDuration;
    }

    private Tuple2<Boolean, LockData> tryLock(LockData lockData, LockData registryLockData) {
        LockData newRegistryLockData = REGISTRY.compute(lockData.getKey(), (s, lockData1) ->
                Objects.equals(lockData1, registryLockData) ? lockData.toBuilder()
                        .acquiredDateTime(OffsetDateTime.now(ZoneOffset.UTC))
                        .build() : lockData1);
        return Tuples.of(Objects.equals(newRegistryLockData, lockData), Objects.requireNonNull(newRegistryLockData));
    }
}
