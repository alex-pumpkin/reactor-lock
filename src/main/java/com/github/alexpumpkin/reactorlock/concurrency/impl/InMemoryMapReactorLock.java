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

//todo: documentation
public class InMemoryMapReactorLock implements ReactorLock {
    private static final AtomicReference<Map<String, LockData>> REGISTRY = new AtomicReference<>();

    @Override
    public Mono<Tuple2<Boolean, LockData>> tryLock(LockData lockData, Duration maxLockDuration) {
        return Mono.fromCallable(() -> {
            LockData newRegistryLockData = REGISTRY.updateAndGet(map -> Option.of(map)
                    .getOrElse(HashMap::empty)
                    .computeIfPresent(lockData.getKey(), (s, registryLockData) -> {
                        if (registryLockData.getAcquiredDateTime()
                                .isBefore(OffsetDateTime.now(ZoneOffset.UTC).minus(maxLockDuration))) {
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
}
