package com.github.alexpumpkin.reactorlock.concurrency.impl;

import com.github.alexpumpkin.reactorlock.concurrency.LockData;
import com.github.alexpumpkin.reactorlock.concurrency.UnlockEventsRegistry;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

//todo: documentation and license
public enum InMemoryUnlockEventsRegistry implements UnlockEventsRegistry {
    INSTANCE;

    private static final AtomicReference<Map<LockData, Set<Consumer<Integer>>>> REGISTRY = new AtomicReference<>();


    @Override
    public Mono<Void> add(LockData lockData) {
        return Mono.fromRunnable(() -> REGISTRY.updateAndGet(map -> Option.of(map)
                .getOrElse(() -> HashMap.of(lockData, HashSet.empty()))
                .computeIfAbsent(lockData, lockData1 -> HashSet.empty())
                ._2));
    }

    @Override
    public Mono<Boolean> register(LockData lockData, Consumer<Integer> unlockEventListener) {
        return Mono.fromSupplier(() -> Option.of(REGISTRY.updateAndGet(map -> Option.of(map)
                .map(map1 -> map1.computeIfPresent(lockData, (lockData1, consumers) ->
                        consumers.add(unlockEventListener))._2)
                .getOrNull()))
                .flatMap(map -> map.get(lockData)
                        .map(consumers -> consumers.contains(unlockEventListener)))
                .getOrElse(false));
    }

    @Override
    public Mono<Void> remove(LockData lockData) {
        return Mono.fromRunnable(() -> Option.of(REGISTRY.getAndUpdate(map -> Option.of(map)
                .map(map1 -> map1.remove(lockData))
                .getOrElse(map)))
                .forEach(map -> map.get(lockData)
                        .forEach(consumers -> consumers
                                .forEach(integerConsumer -> integerConsumer.accept(1)))));
    }
}
