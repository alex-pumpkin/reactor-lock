package com.github.alexpumpkin.reactorlock.concurrency;

import com.github.alexpumpkin.reactorlock.concurrency.exceptions.LockIsNotAvailableException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Lock helper for Mono. If lock is not available generates error.
 */
public final class Lock {
    private final LockCommand lockCommand;
    private final LockData lockData;

    public Lock(LockCommand lockCommand, String key) {
        this.lockCommand = lockCommand;
        this.lockData = LockData.builder()
                .key(key)
                .uuid(UUID.randomUUID().toString())
                .build();
    }

    /**
     * Wrap source Mono to immediate lock and release on error.
     *
     * @param source source Mono.
     * @param <T>    Mono parameter.
     * @return Mono wrapped by lock.
     */
    public final <T> Mono<T> tryLock(Mono<T> source) {
        return tryLock(source, Schedulers.elastic());
    }

    /**
     * Wrap source Mono to immediate lock and release on error.
     *
     * @param source    source Mono.
     * @param scheduler scheduler to try lock.
     * @param <T>       Mono parameter.
     * @return Mono wrapped by lock.
     */
    public final <T> Mono<T> tryLock(Mono<T> source, Scheduler scheduler) {
        return Mono.fromCallable(() -> lockCommand.tryLock(lockData))
                .subscribeOn(scheduler)
                .flatMap(isLocked -> {
                    if (isLocked) {
                        return source
                                .switchIfEmpty(unlock().then(Mono.empty()))
                                .onErrorResume(throwable -> unlock().then(Mono.error(throwable)));
                    } else {
                        return Mono.error(new LockIsNotAvailableException());
                    }
                });
    }

    /**
     * Create Mono to release lock.
     *
     * @return empty Mono completed after releasing lock.
     */
    public final Mono<Void> unlock() {
        return unlock(Schedulers.elastic());
    }

    /**
     * Create Mono to release lock.
     *
     * @param scheduler scheduler to release lock.
     * @return empty Mono completed after releasing lock.
     */
    public Mono<Void> unlock(Scheduler scheduler) {
        return Mono.<Void>fromRunnable(() -> lockCommand.unlock(lockData))
                .subscribeOn(scheduler);
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
}
