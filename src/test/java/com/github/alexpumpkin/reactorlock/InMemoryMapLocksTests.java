package com.github.alexpumpkin.reactorlock;

import com.github.alexpumpkin.reactorlock.concurrency.Lock;
import com.github.alexpumpkin.reactorlock.concurrency.LockCommand;
import com.github.alexpumpkin.reactorlock.concurrency.impl.InMemoryMapLockCommand;
import com.github.alexpumpkin.reactorlock.concurrency.impl.InMemoryUnlockEventsRegistry;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryMapLocksTests {

    @Test
    public void testConcurrentInvocationsNoLock() {
        AtomicInteger maxConcurrentInvocations = new AtomicInteger();
        Mono<String> helloMono = getMono4Test(50L, new AtomicInteger(), maxConcurrentInvocations);
        Flux.merge(helloMono,
                helloMono,
                helloMono,
                helloMono,
                helloMono)
                .blockLast();

        Assert.assertTrue(maxConcurrentInvocations.get() > 1);
    }

    @Test
    public void testConcurrentInvocationsWithLock() {
        AtomicInteger maxConcurrentInvocations = new AtomicInteger();
        Mono<String> helloMono = getMono4Test(50L, new AtomicInteger(), maxConcurrentInvocations);

        LockCommand lockCommand = new InMemoryMapLockCommand(Duration.ofSeconds(60));
        Flux.merge(getLockedMono(helloMono, lockCommand),
                getLockedMono(helloMono, lockCommand),
                getLockedMono(helloMono, lockCommand),
                getLockedMono(helloMono, lockCommand),
                getLockedMono(helloMono, lockCommand))
                .blockLast();

        Assert.assertEquals(1, maxConcurrentInvocations.get());
    }

    @Test
    public void testLockMaxDuration() {
        AtomicInteger maxConcurrentInvocations = new AtomicInteger();
        Mono<String> helloMono = getMono4Test(300L, new AtomicInteger(), maxConcurrentInvocations);

        LockCommand lockCommand = new InMemoryMapLockCommand(Duration.ofMillis(100));
        Flux.merge(getLockedMono(helloMono, lockCommand),
                getLockedMono(helloMono, lockCommand),
                getLockedMono(helloMono, lockCommand),
                getLockedMono(helloMono, lockCommand),
                getLockedMono(helloMono, lockCommand))
                .blockLast();

        Assert.assertTrue(maxConcurrentInvocations.get() > 1);
    }

    @Test
    public void testUnlockWhenError() {
        LockCommand lockCommand = new InMemoryMapLockCommand(Duration.ofSeconds(60));
        getLockedMono(Mono.error(new TestException()), lockCommand)
                .onErrorResume(TestException.class, ignored -> Mono.empty()).block();
        getLockedMono(Mono.error(new TestException()), lockCommand)
                .onErrorResume(TestException.class, ignored -> Mono.empty()).block();
    }

    private Mono<String> getMono4Test(Long sleepDurationMillis, AtomicInteger counter, AtomicInteger maxCounterHolder) {
        return Mono.fromCallable(() -> {
            int currentConcurrentInvocations = counter.incrementAndGet();
            System.out.println("Number of concurrent invocations = " + currentConcurrentInvocations);
            if (currentConcurrentInvocations > maxCounterHolder.get())
                maxCounterHolder.set(currentConcurrentInvocations);
            Thread.sleep(sleepDurationMillis);
            counter.decrementAndGet();
            return "hello";
        }).subscribeOn(Schedulers.elastic());
    }

    private Mono<String> getLockedMono(Mono<String> source, LockCommand lockCommand) {
        Lock lock = new Lock(lockCommand, "hello", new InMemoryUnlockEventsRegistry());
        return lock.tryLock(source)
                .flatMap(s -> lock.unlock().then(Mono.just(s)))
                .transform(lock.retryTransformer());
    }

    private class TestException extends Exception {
    }
}
