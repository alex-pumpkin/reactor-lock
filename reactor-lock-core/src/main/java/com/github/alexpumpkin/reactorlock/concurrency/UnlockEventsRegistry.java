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
package com.github.alexpumpkin.reactorlock.concurrency;

import reactor.core.publisher.Mono;

import java.util.function.Consumer;

/**
 * Registry that contains unlock event listeners by {@link LockData}.
 * <p>
 * When lock is acquired we add it to {@link UnlockEventsRegistry}.<br/>
 * If lock is busy, we register unlock event listener.<br/>
 * When lock is released we remove it from {@link UnlockEventsRegistry} and call all registered unlock event listeners.
 */
public interface UnlockEventsRegistry {
    /**
     * Add lock to registry.
     *
     * @param lockData lock data.
     * @return empty Mono.
     */
    Mono<Void> add(LockData lockData);

    /**
     * Remove lock from registry.
     *
     * @param lockData lock data.
     * @return empty Mono.
     */
    Mono<Void> remove(LockData lockData);

    /**
     * Register unlock event listener for lock.
     *
     * @param lockData            lock data.
     * @param unlockEventListener unlock event listener.
     * @return Mono with {@code true} if registered.
     */
    Mono<Boolean> register(LockData lockData, Consumer<Integer> unlockEventListener);
}
