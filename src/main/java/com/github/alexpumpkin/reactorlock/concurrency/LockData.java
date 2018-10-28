package com.github.alexpumpkin.reactorlock.concurrency;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.OffsetDateTime;

@Builder(toBuilder = true)
@EqualsAndHashCode
public final class LockData {
    // Lock key to identify same operation (same cache key, for example).
    @Getter
    private final String key;
    // Unique identifier for equals and hashCode.
    private final String uuid;
    @Getter
    @EqualsAndHashCode.Exclude
    // Date and time of the acquiring for lock duration limiting.
    private final OffsetDateTime acquiredDateTime;
}
