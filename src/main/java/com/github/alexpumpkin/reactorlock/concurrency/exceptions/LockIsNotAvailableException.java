package com.github.alexpumpkin.reactorlock.concurrency.exceptions;

import com.github.alexpumpkin.reactorlock.concurrency.LockData;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Error when lock is busy.
 */
@Getter
@AllArgsConstructor
public final class LockIsNotAvailableException extends Exception {
    private final transient LockData lockData;
}
