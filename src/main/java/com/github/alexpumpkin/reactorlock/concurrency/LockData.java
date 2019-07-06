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

@Builder(toBuilder = true)
@EqualsAndHashCode
@ToString
public final class LockData {
    // Lock key to identify same operation (same cache key, for example).
    @Getter
    @NonNull
    private final String key;
    // Unique identifier for equals and hashCode.
    @NonNull
    private final String uuid;
    @Getter
    @EqualsAndHashCode.Exclude
    // Date and time of the acquiring for lock duration limiting.
    private final OffsetDateTime acquiredDateTime;
}
