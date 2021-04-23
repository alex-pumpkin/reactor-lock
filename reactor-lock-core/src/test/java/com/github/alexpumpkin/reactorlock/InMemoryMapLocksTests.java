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
package com.github.alexpumpkin.reactorlock;

import com.github.alexpumpkin.reactorlock.concurrency.LockMono;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryMapLocksTests {

    @Test
    public void testConcurrentInvocationsNoLock() {
        AtomicInteger maxConcurrentInvocations = new AtomicInteger();
        AtomicInteger innerCounter = new AtomicInteger();
        AtomicBoolean delayFlag = new AtomicBoolean(false);
        Mono<String> helloMono = Mono.defer(() ->
                getMono4Test(innerCounter, maxConcurrentInvocations, delayFlag));

        Mono.fromRunnable(() -> delayFlag.set(true))
                .delaySubscription(Duration.ofMillis(100))
                .subscribe();

        Flux.range(0, 2)
                .parallel(2)
                .runOn(Schedulers.boundedElastic())
                .flatMap(integer -> helloMono)
                .sequential()
                .blockLast(Duration.ofSeconds(10));

        Assert.assertTrue(maxConcurrentInvocations.get() > 1);
    }

    @Test
    public void testConcurrentInvocationsWithLock() {
        AtomicInteger maxConcurrentInvocations = new AtomicInteger();
        AtomicInteger innerCounter = new AtomicInteger();
        Duration maxDuration = Duration.ofSeconds(5);
        Mono<String> helloMono = Mono.defer(() ->
                getLockedMono(getMono4Test(innerCounter, maxConcurrentInvocations, null), maxDuration));

        Flux.range(0, 1000)
                .parallel(100)
                .runOn(Schedulers.boundedElastic())
                .flatMap(integer -> helloMono)
                .sequential()
                .blockLast(Duration.ofSeconds(10));

        Assert.assertEquals(1, maxConcurrentInvocations.get());
    }

    @Test
    public void testLockMaxDuration() {
        AtomicInteger maxConcurrentInvocations = new AtomicInteger();
        AtomicInteger innerCounter = new AtomicInteger();
        AtomicBoolean delayFlag = new AtomicBoolean(false);
        Duration maxDuration = Duration.ofMillis(10);
        Mono<String> helloMono = Mono.defer(() ->
                getLockedMono(getMono4Test(innerCounter, maxConcurrentInvocations, delayFlag).log(),
                        maxDuration));

        Mono.fromRunnable(() -> delayFlag.set(true))
                .delaySubscription(Duration.ofMillis(100))
                .subscribe();

        Flux.range(0, 2)
                .parallel(2)
                .runOn(Schedulers.boundedElastic())
                .flatMap(integer -> helloMono)
                .sequential()
                .blockLast(Duration.ofSeconds(10));

        Assert.assertTrue(maxConcurrentInvocations.get() > 1);
    }

    @Test
    public void testUnlockWhenError() {
        Duration maxDuration = Duration.ofSeconds(5);
        Mono<String> helloMono = Mono.defer(() ->
                getLockedMono(Mono.error(new TestException()), maxDuration)
                        .onErrorResume(TestException.class, ignored -> Mono.just("hello")));

        Flux.range(0, 1000)
                .parallel(100)
                .runOn(Schedulers.boundedElastic())
                .flatMap(integer -> helloMono)
                .sequential()
                .blockLast(Duration.ofSeconds(10));
    }

    private Mono<String> getMono4Test(
            AtomicInteger counter, AtomicInteger maxCounterHolder, AtomicBoolean delayFlag) {
        return Mono.fromCallable(() -> {
            maxCounterHolder.updateAndGet(operand -> {
                int currentConcurrentInvocations = counter.incrementAndGet();
                if (currentConcurrentInvocations > operand) {
                    return currentConcurrentInvocations;
                } else {
                    return operand;
                }
            });
            if (delayFlag != null) {
                Awaitility.await().untilTrue(delayFlag);
            }
            counter.decrementAndGet();
            return "hello";
        });
    }

    private Mono<String> getLockedMono(Mono<String> source, Duration duration) {
        return LockMono.key("hello")
                .maxLockDuration(duration)
                .lock(source);
    }

    private class TestException extends Exception {
    }
}
