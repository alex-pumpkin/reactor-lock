package com.github.alexpumpkin.reactorlock.concurrency.impl;

import com.github.alexpumpkin.reactorlock.concurrency.LockData;
import com.github.alexpumpkin.reactorlock.concurrency.ReactorLock;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

//todo: documentation and license
public class InMemoryMapReactorLock implements ReactorLock {
    private static final AtomicReference<Map<String, LockData>> REGISTRY = new AtomicReference<>();

    private final Duration maxDuration;

    public InMemoryMapReactorLock(Duration maxDuration) {
        this.maxDuration = maxDuration;
    }

    @Override
    public Mono<Tuple2<Boolean, LockData>> tryLock(LockData lockData) {
        return Mono.fromCallable(() -> {
            LockData newRegistryLockData = REGISTRY.updateAndGet(map -> Option.of(map)
                    .getOrElse(HashMap::empty)
                    .computeIfPresent(lockData.getKey(), (s, registryLockData) -> {
                        if (registryLockData.getAcquiredDateTime()
                                .isBefore(OffsetDateTime.now(ZoneOffset.UTC).minus(maxDuration))) {
                            return lockData.toBuilder()
                                    .acquiredDateTime(OffsetDateTime.now(ZoneOffset.UTC))
                                    .build();
                        }

                        return registryLockData;
                    })
                    ._2
                    .computeIfAbsent(lockData.getKey(), s -> lockData.toBuilder()
                            .acquiredDateTime(OffsetDateTime.now(ZoneOffset.UTC))
                            .build())
                    ._2)
                    .get(lockData.getKey())
                    .getOrNull();

            return Tuples.of(Objects.equals(newRegistryLockData, lockData), newRegistryLockData);
        });
    }

    @Override
    public Mono<Void> unlock(LockData lockData) {
        return Mono.fromRunnable(() -> REGISTRY.updateAndGet(map -> Option.of(map)
                .map(map1 -> map1.remove(lockData.getKey()))
                .getOrElse(map)));
    }

    @Override
    public Duration getMaxLockDuration() {
        return maxDuration;
    }

}
