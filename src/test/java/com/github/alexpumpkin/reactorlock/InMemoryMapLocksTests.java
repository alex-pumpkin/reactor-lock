package com.github.alexpumpkin.reactorlock;

import com.github.alexpumpkin.reactorlock.concurrency.LockMono;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.equalTo;

public class InMemoryMapLocksTests {

    @Test
    public void testConcurrentInvocationsNoLock() {
        AtomicInteger maxConcurrentInvocations = new AtomicInteger();
        AtomicInteger innerCounter = new AtomicInteger();
        AtomicInteger resultCounter = new AtomicInteger();
        AtomicBoolean delayFlag = new AtomicBoolean(false);
        Mono<String> helloMono = Mono.defer(() ->
                getMono4Test(innerCounter, maxConcurrentInvocations, delayFlag));

        Flux.range(0, 1000)
                .parallel(100)
                .runOn(Schedulers.elastic())
                .flatMap(integer -> helloMono)
                .subscribe(s -> resultCounter.incrementAndGet());

        Mono.fromRunnable(() -> delayFlag.set(true))
                .delaySubscription(Duration.ofMillis(10))
                .subscribe();

        Awaitility.await().atMost(10, TimeUnit.SECONDS)
                .untilAtomic(resultCounter, equalTo(1000));

        Assert.assertTrue(maxConcurrentInvocations.get() > 1);
    }

    @Test
    public void testConcurrentInvocationsWithLock() {
        AtomicInteger maxConcurrentInvocations = new AtomicInteger();
        AtomicInteger innerCounter = new AtomicInteger();
        AtomicInteger resultCounter = new AtomicInteger();
        Duration maxDuration = Duration.ofSeconds(5);
        Mono<String> helloMono = Mono.defer(() ->
                getLockedMono(getMono4Test(innerCounter, maxConcurrentInvocations, null), maxDuration));

        Flux.range(0, 1000)
                .parallel(100)
                .runOn(Schedulers.elastic())
                .flatMap(integer -> helloMono)
                .subscribe(s -> resultCounter.incrementAndGet());

        Awaitility.await().atMost(10, TimeUnit.SECONDS)
                .untilAtomic(resultCounter, equalTo(1000));

        Assert.assertEquals(1, maxConcurrentInvocations.get());
    }

    @Test
    public void testLockMaxDuration() {
        AtomicInteger maxConcurrentInvocations = new AtomicInteger();
        AtomicInteger innerCounter = new AtomicInteger();
        AtomicInteger resultCounter = new AtomicInteger();
        AtomicBoolean delayFlag = new AtomicBoolean(false);
        Duration maxDuration = Duration.ofMillis(10);
        Mono<String> helloMono = Mono.defer(() ->
                getLockedMono(getMono4Test(innerCounter, maxConcurrentInvocations, delayFlag),
                        maxDuration));

        Flux.range(0, 2)
                .parallel(2)
                .runOn(Schedulers.elastic())
                .flatMap(integer -> helloMono)
                .subscribe(s -> resultCounter.incrementAndGet());

        Mono.fromRunnable(() -> delayFlag.set(true))
                .delaySubscription(Duration.ofMillis(100))
                .subscribe();

        Awaitility.await().atMost(10, TimeUnit.SECONDS)
                .untilAtomic(resultCounter, equalTo(2));

        Assert.assertTrue(maxConcurrentInvocations.get() > 1);
    }

    @Test
    public void testUnlockWhenError() {
        AtomicInteger counter = new AtomicInteger();
        Duration maxDuration = Duration.ofSeconds(5);
        Mono<String> helloMono = Mono.defer(() ->
                getLockedMono(Mono.error(new TestException()), maxDuration)
                        .onErrorResume(TestException.class, ignored -> Mono.just("hello")));

        Flux.range(0, 1000)
                .parallel(100)
                .runOn(Schedulers.elastic())
                .flatMap(integer -> helloMono)
                .subscribe(s -> counter.incrementAndGet());

        Awaitility.await().atMost(10, TimeUnit.SECONDS)
                .untilAtomic(counter, equalTo(1000));
    }

    private Mono<String> getMono4Test(
            AtomicInteger counter, AtomicInteger maxCounterHolder, AtomicBoolean delayFlag) {
        return Mono.fromCallable(() -> {
            int currentConcurrentInvocations = counter.incrementAndGet();
            if (currentConcurrentInvocations > maxCounterHolder.get())
                maxCounterHolder.set(currentConcurrentInvocations);
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
