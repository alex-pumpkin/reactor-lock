package com.github.alexpumpkin.reactorlock.concurrency;

import com.github.alexpumpkin.reactorlock.concurrency.exceptions.LockIsNotAvailableException;
import com.github.alexpumpkin.reactorlock.concurrency.impl.InMemoryMapReactorLock;
import com.github.alexpumpkin.reactorlock.concurrency.impl.InMemoryUnlockEventsRegistry;
import reactor.core.Exceptions;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;


//todo: documentation and license
/**
 * Locking helper that wraps any {@link Mono} to access it in the exclusive manner by given string key.
 * <p>
 *
 * </p>
 */
public final class LockMono {
    private final LockData lockData;
    private final UnicastProcessor<Integer> unlockEvents;
    private final FluxSink<Integer> unlockEventSink;
    private final UnlockEventsRegistry unlockEventsRegistry;
    private final ReactorLock reactorLock;

    private LockMono(String key, ReactorLock reactorLock, UnlockEventsRegistry unlockEventsRegistry) {
        this.lockData = LockData.builder()
                .key(key)
                .uuid(UUID.randomUUID().toString())
                .build();
        this.unlockEvents = UnicastProcessor.create();
        this.unlockEventSink = unlockEvents.sink();
        this.reactorLock = reactorLock;
        this.unlockEventsRegistry = unlockEventsRegistry;
    }

    public final <T> Mono<T> lock(Mono<T> source) {
        return tryLock(source)
                .flatMap(s -> unlock().then(Mono.just(s)))
                .transform(retryTransformer());
    }

    <T> Mono<T> tryLock(Mono<T> source) {
        return reactorLock.tryLock(lockData)
                .flatMap(isLocked -> {
                    if (isLocked.getT1()) {
                        return unlockEventsRegistry.add(lockData)
                                .then(source.switchIfEmpty(unlock().then(Mono.empty()))
                                        .onErrorResume(throwable -> unlock().then(Mono.error(throwable))));
                    } else {
                        return Mono.error(new LockIsNotAvailableException(isLocked.getT2()));
                    }
                });
    }

    Mono<Void> unlock() {
        return reactorLock.unlock(lockData)
                .then(unlockEventsRegistry.remove(lockData));
    }

    <T> UnaryOperator<Mono<T>> retryTransformer() {
        return mono -> mono
                .doOnError(LockIsNotAvailableException.class,
                        error -> unlockEventsRegistry.register(error.getLockData(), unlockEventSink::next)
                                .doOnNext(registered -> {
                                    if (!registered) unlockEventSink.next(-1);
                                })
                                .then(Mono.just(2).map(unlockEventSink::next)
                                        .delaySubscription(reactorLock.getMaxLockDuration()))
                                .subscribe())
                .doOnError(throwable -> !(throwable instanceof LockIsNotAvailableException),
                        ignored -> unlockEventSink.next(0))
                .retryWhen(errorFlux -> errorFlux.zipWith(unlockEvents, (error, integer) -> {
                    if (error instanceof LockIsNotAvailableException) return integer;
                    else throw Exceptions.propagate(error);
                }));
    }

    public static LockMonoBuilder key(String key) {
        return new LockMonoBuilder(key);
    }

    public static final class LockMonoBuilder {
        private static final Duration DEFAULT_MAX_LOCK_DURATION = Duration.ofSeconds(30);
        private final String key;
        private Duration maxLockDuration = DEFAULT_MAX_LOCK_DURATION;
        private Function<Duration, ReactorLock> reactorLock;
        private UnlockEventsRegistry unlockEventsRegistry;

        private LockMonoBuilder(String key) {
            this.key = key;
        }

        public LockMonoBuilder maxLockDuration(Duration duration) {
            this.maxLockDuration = duration;
            return this;
        }

        public LockMonoBuilder reactorLock(Function<Duration, ReactorLock> reactorLock) {
            this.reactorLock = reactorLock;
            return this;
        }

        public LockMonoBuilder unlockEventsRegistry(UnlockEventsRegistry unlockEventsRegistry) {
            this.unlockEventsRegistry = unlockEventsRegistry;
            return this;
        }

        public LockMono build() {
            this.reactorLock = Optional.ofNullable(this.reactorLock)
                    .orElseGet(() -> InMemoryMapReactorLock::new);
            this.unlockEventsRegistry = Optional.ofNullable(this.unlockEventsRegistry)
                    .orElse(InMemoryUnlockEventsRegistry.INSTANCE);
            return new LockMono(this.key, this.reactorLock.apply(this.maxLockDuration), this.unlockEventsRegistry);
        }

        public <T> Mono<T> lock(Mono<T> source) {
            return build().lock(source);
        }
    }

}
