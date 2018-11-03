package com.github.alexpumpkin.reactorlock.concurrency.impl;

import com.github.alexpumpkin.reactorlock.concurrency.LockData;
import com.github.alexpumpkin.reactorlock.concurrency.UnlockEventsRegistry;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class InMemoryUnlockEventsRegistry implements UnlockEventsRegistry {
    private static final ConcurrentHashMap<LockData, List<Consumer<Integer>>> REGISTRY = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> add(LockData lockData) {
        return Mono.fromRunnable(() -> REGISTRY.computeIfAbsent(lockData, ignored -> new ArrayList<>()));
    }

    @Override
    public Mono<Boolean> register(LockData lockData, Consumer<Integer> unlockEventListener) {
        return Mono.fromCallable(() -> Objects.nonNull(REGISTRY.computeIfPresent(lockData, (ld, consumers) -> {
            consumers.add(unlockEventListener);
            return consumers;
        })));
    }

    @Override
    public Mono<Void> remove(LockData lockData) {
        return Mono.fromRunnable(() -> REGISTRY.computeIfPresent(lockData, (ld, consumers) -> {
            consumers.forEach(integerConsumer -> integerConsumer.accept(1));
            return null;
        }));
    }
}
