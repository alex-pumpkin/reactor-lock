package com.github.alexpumpkin.reactorlock.concurrency;

import com.github.alexpumpkin.reactorlock.concurrency.exceptions.LockIsNotAvailableException;
import reactor.core.Exceptions;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/**
 * Lock helper for Mono. If lock is not available generates error.
 */
public final class Lock {
    private final LockCommand lockCommand;
    private final LockData lockData;
    private final UnlockEventsRegistry unlockEventsRegistry;
    private final EmitterProcessor<Integer> unlockEvents;
    private final FluxSink<Integer> unlockEventSink;

    public Lock(LockCommand lockCommand, String key, UnlockEventsRegistry unlockEventsRegistry) {
        this.lockCommand = lockCommand;
        this.lockData = LockData.builder()
                .key(key)
                .uuid(UUID.randomUUID().toString())
                .build();
        this.unlockEventsRegistry = unlockEventsRegistry;
        this.unlockEvents = EmitterProcessor.create(false);
        this.unlockEventSink = unlockEvents.sink();
    }

    /**
     * Wrap source Mono to immediate lock and release on error.
     *
     * @param source source Mono.
     * @param <T>    Mono parameter.
     * @return Mono wrapped by lock.
     */
    public final <T> Mono<T> tryLock(Mono<T> source) {
        return Mono.fromCallable(() -> lockCommand.tryLock(lockData))
                .flatMap(isLocked -> {
                    if (isLocked.getT1()) {
                        return unlockEventsRegistry.add(lockData)
                                .then(source
                                        .switchIfEmpty(unlock().then(Mono.empty()))
                                        .onErrorResume(throwable -> unlock().then(Mono.error(throwable))));
                    } else {
                        return Mono.error(new LockIsNotAvailableException(isLocked.getT2()));
                    }
                });
    }

    /**
     * Create Mono to release lock.
     *
     * @return empty Mono completed after releasing lock.
     */
    public Mono<Void> unlock() {
        return Mono.<Void>fromRunnable(() -> lockCommand.unlock(lockData))
                .then(unlockEventsRegistry.remove(lockData));
    }

    /**
     * Extend CacheMono writer to unlock after write.
     *
     * @param cacheWriter CacheMono writer BiFunction.
     * @return BiFunction with unlock after write.
     */
    public <KEY, VALUE> BiFunction<KEY, Signal<? extends VALUE>, Mono<Void>> unlockAfterCacheWriter(
            BiFunction<KEY, Signal<? extends VALUE>, Mono<Void>> cacheWriter) {
        Objects.requireNonNull(cacheWriter);
        return cacheWriter.andThen(voidMono -> voidMono.then(unlock())
                .onErrorResume(throwable -> unlock()));
    }

    /**
     * Retry function to react on the unlock events.
     *
     * @param <T> Mono parameter.
     * @return retry function.
     */
    public final <T> UnaryOperator<Mono<T>> retryTransformer() {
        return mono -> mono
                .doOnError(LockIsNotAvailableException.class,
                        error -> unlockEventsRegistry.register(error.getLockData(), unlockEventSink::next)
                                .doOnNext(registered -> {
                                    if (!registered) unlockEventSink.next(0);
                                })
                                .then(Mono.just(2).map(unlockEventSink::next)
                                        .delaySubscription(lockCommand.getMaxLockDuration()))
                                .subscribe())
                .doOnError(throwable -> !(throwable instanceof LockIsNotAvailableException),
                        ignored -> unlockEventSink.next(0))
                .retryWhen(errorFlux -> errorFlux.zipWith(unlockEvents, (error, integer) -> {
                    if (error instanceof LockIsNotAvailableException) return integer;
                    else throw Exceptions.propagate(error);
                }));
    }

}
