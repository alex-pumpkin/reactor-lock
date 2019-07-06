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
import reactor.util.function.Tuple2;

import java.time.Duration;

/**
 * {@link ReactorLock} implementations provide the way to acquire and release lock in the asynchronous manner.
 */
public interface ReactorLock<K> {
    /**
     * Try acquire given lock with max lock duration.
     *
     * @param lockData        lock data.
     * @param maxLockDuration max lock duration.
     * @return Mono with pair (is lock acquired, actual acquired {@link LockData}).
     */
    Mono<Tuple2<Boolean, LockData<K>>> tryLock(LockData<K> lockData, Duration maxLockDuration);

    /**
     * Unlock.
     *
     * @param lockData lock data.
     * @return empty Mono.
     */
    Mono<Void> unlock(LockData<K> lockData);
}
