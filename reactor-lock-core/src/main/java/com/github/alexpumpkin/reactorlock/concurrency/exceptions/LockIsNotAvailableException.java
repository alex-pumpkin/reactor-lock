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
package com.github.alexpumpkin.reactorlock.concurrency.exceptions;

import com.github.alexpumpkin.reactorlock.concurrency.LockData;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Error when lock is already acquired.
 */
@Getter
@AllArgsConstructor
public final class LockIsNotAvailableException extends Exception {
    private final transient LockData lockData;
}
