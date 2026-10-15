package uk.ac.man.cs.eventlite.services;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** A bounded stale-while-revalidate cache with one refresh and failure backoff. */
public final class TimelineCache<T> {
    private final long freshNanos;
    private final long staleNanos;
    private final long retryNanos;
    private final LongSupplier ticker;
    private final Executor executor;
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile Snapshot<T> snapshot;
    private volatile long retryAt;
    private volatile boolean failed;

    public TimelineCache(Duration fresh, Duration stale, Duration retry,
                         LongSupplier ticker, Executor executor) {
        if (fresh.isNegative() || fresh.isZero() || fresh.compareTo(Duration.ofMinutes(5)) > 0
                || stale.compareTo(fresh) < 0 || stale.compareTo(Duration.ofHours(1)) > 0
                || retry.isNegative() || retry.isZero() || retry.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("Timeline cache durations are outside their allowed bounds.");
        }
        this.freshNanos = fresh.toNanos();
        this.staleNanos = stale.toNanos();
        this.retryNanos = retry.toNanos();
        this.ticker = ticker;
        this.executor = executor;
    }

    public List<T> get(Supplier<Optional<List<T>>> loader) {
        long now = ticker.getAsLong();
        Snapshot<T> current = snapshot;
        if (current != null && now - current.loadedAt() < freshNanos) {
            return current.items();
        }
        if ((!failed || now - retryAt >= 0) && refreshing.compareAndSet(false, true)) {
            // A competing refresh may have finished between the first read and this CAS.
            now = ticker.getAsLong();
            current = snapshot;
            if (current != null && now - current.loadedAt() < freshNanos) {
                refreshing.set(false);
                return current.items();
            }
            if (failed && now - retryAt < 0) {
                refreshing.set(false);
            } else if (current == null) {
                // One bounded cold request; concurrent callers return immediately.
                refresh(loader);
            } else {
                try {
                    executor.execute(() -> refresh(loader));
                } catch (RuntimeException ex) {
                    markFailed();
                    refreshing.set(false);
                }
            }
        }
        current = snapshot;
        return current != null && ticker.getAsLong() - current.loadedAt() < staleNanos
                ? current.items() : List.of();
    }

    private void refresh(Supplier<Optional<List<T>>> loader) {
        try {
            Optional<List<T>> loaded = loader.get();
            if (loaded.isPresent()) {
                snapshot = new Snapshot<>(List.copyOf(loaded.get()), ticker.getAsLong());
                failed = false;
            } else {
                markFailed();
            }
        } catch (RuntimeException ex) {
            // Never fail the main page because a feed parser or executor failed.
            markFailed();
        } finally {
            refreshing.set(false);
        }
    }

    private void markFailed() {
        retryAt = ticker.getAsLong() + retryNanos;
        failed = true;
    }

    private record Snapshot<T>(List<T> items, long loadedAt) { }
}
