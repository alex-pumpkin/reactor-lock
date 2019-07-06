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

import lombok.*;

import java.time.OffsetDateTime;

/**
 * This class represents lock entities with {@code key} to identify same operations (like same cache key etc),
 * {@code uuid} for differentiation between entities and {@code acquiredDateTime} to control lock duration.
 * <p>
 * K - key type. It is required to override equals and hashCode for K type.
 */
@Builder(toBuilder = true)
@EqualsAndHashCode
@ToString
public final class LockData<K> {
    @Getter
    @NonNull
    private final K key;
    @NonNull
    private final String uuid;
    @Getter
    @EqualsAndHashCode.Exclude
    private final OffsetDateTime acquiredDateTime;
}
