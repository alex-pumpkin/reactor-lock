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
import com.github.alexpumpkin.reactorlock.concurrency.UnlockEventsRegistry;
import io.vavr.collection.*;
import io.vavr.control.Option;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * {@link UnlockEventsRegistry} implementation with in-memory map registry.
 * <p>
 * <b>Important!</b> You should reuse same instance of the {@link InMemoryMapReactorLock} for all concurrent publishers
 * wrapped with {@link com.github.alexpumpkin.reactorlock.concurrency.LockMono}. Default instance is good enough in many
 * cases.
 * </p>
 * <p>
 * Registry couldn't be implemented with {@link java.util.concurrent.ConcurrentHashMap} because of reentrant
 * {@code synchronized} blocks in the {@link java.util.concurrent.ConcurrentHashMap} and work stealing. Same thread
 * could initiate concurrent modification.
 * </p>
 */
public final class InMemoryUnlockEventsRegistry implements UnlockEventsRegistry {
    public static final UnlockEventsRegistry DEFAULT_INSTANCE = new InMemoryUnlockEventsRegistry(1024);

    private final int concurrency;
    private final Array<AtomicReference<Map<LockData, Set<Consumer<Integer>>>>> registry;

    public InMemoryUnlockEventsRegistry(int concurrency) {
        this.concurrency = concurrency;
        this.registry = Array.fill(concurrency, AtomicReference::new);
    }

    @Override
    public Mono<Void> add(LockData lockData) {
        return Mono.fromRunnable(() -> getUnlockConsumersMapReference(lockData).updateAndGet(map -> Option.of(map)
                .getOrElse(() -> HashMap.of(lockData, HashSet.empty()))
                .computeIfAbsent(lockData, lockData1 -> HashSet.empty())
                ._2));
    }

    @Override
    public Mono<Boolean> register(LockData lockData, Consumer<Integer> unlockEventListener) {
        return Mono.fromSupplier(() -> Option.of(getUnlockConsumersMapReference(lockData)
                .updateAndGet(map -> Option.of(map)
                        .map(map1 -> map1.computeIfPresent(lockData, (lockData1, consumers) ->
                                consumers.add(unlockEventListener))._2)
                        .getOrNull()))
                .flatMap(map -> map.get(lockData)
                        .map(consumers -> consumers.contains(unlockEventListener)))
                .getOrElse(false));
    }

    @Override
    public Mono<Void> remove(LockData lockData) {
        return Mono.fromRunnable(() -> Option.of(getUnlockConsumersMapReference(lockData)
                .getAndUpdate(map -> Option.of(map)
                        .map(map1 -> map1.remove(lockData))
                        .getOrElse(map)))
                .forEach(map -> map.get(lockData)
                        .forEach(consumers -> consumers
                                .forEach(integerConsumer -> integerConsumer.accept(1)))));
    }

    private AtomicReference<Map<LockData, Set<Consumer<Integer>>>> getUnlockConsumersMapReference(LockData lockData) {
        return registry.get(Math.abs(lockData.getKey().hashCode() % concurrency));
    }
}
