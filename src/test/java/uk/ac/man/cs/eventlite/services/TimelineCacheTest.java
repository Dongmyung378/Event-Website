package uk.ac.man.cs.eventlite.services;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TimelineCacheTest {
    private final AtomicLong now = new AtomicLong();
    private final List<Runnable> queued = new ArrayList<>();
    private final TimelineCache<String> cache = new TimelineCache<>(Duration.ofSeconds(30),
            Duration.ofMinutes(5), Duration.ofSeconds(10), now::get, queued::add);

    @Test
    void warmHitsReuseOneRequestAndReturnedListIsImmutable() {
        AtomicInteger calls = new AtomicInteger();
        for (int request = 0; request < 100; request++) {
            assertEquals(List.of("public"), cache.get(() -> {
                calls.incrementAndGet();
                return Optional.of(List.of("public"));
            }));
        }
        assertEquals(1, calls.get());
        assertThrows(UnsupportedOperationException.class, () -> cache.get(Optional::empty).add("other"));
    }

    @Test
    void expiredResultReturnsImmediatelyAndQueuesOnlyOneRefresh() {
        cache.get(() -> Optional.of(List.of("old")));
        now.set(Duration.ofSeconds(30).toNanos());
        assertEquals(List.of("old"), cache.get(() -> Optional.of(List.of("new"))));
        assertEquals(List.of("old"), cache.get(() -> fail("Must not stampede")));
        assertEquals(1, queued.size());
        queued.remove(0).run();
        assertEquals(List.of("new"), cache.get(() -> fail("Fresh cache must not reload")));
    }

    @Test
    void failureBackoffRetainsLastGoodResultButNeverBeyondStaleLimit() {
        cache.get(() -> Optional.of(List.of("last good")));
        now.set(Duration.ofSeconds(31).toNanos());
        cache.get(Optional::empty);
        queued.remove(0).run();
        now.set(Duration.ofSeconds(40).toNanos());
        assertEquals(List.of("last good"), cache.get(() -> fail("Backoff must suppress retries")));
        assertTrue(queued.isEmpty());
        now.set(Duration.ofSeconds(41).toNanos());
        cache.get(Optional::empty);
        assertEquals(1, queued.size());
        queued.remove(0).run();
        now.set(Duration.ofMinutes(5).toNanos());
        assertTrue(cache.get(Optional::empty).isEmpty());
    }

    @Test
    void successfulEmptyFeedReplacesOldContent() {
        cache.get(() -> Optional.of(List.of("removed post")));
        now.set(Duration.ofSeconds(31).toNanos());
        cache.get(() -> Optional.of(List.of()));
        queued.remove(0).run();
        assertTrue(cache.get(() -> fail("Empty feed should be cached")).isEmpty());
    }

    @Test
    void failedColdRequestAndUnexpectedParserFailureHaveBackoff() {
        assertTrue(cache.get(() -> { throw new IllegalArgumentException("Malformed upstream"); }).isEmpty());
        assertTrue(cache.get(() -> fail("Failure must be backed off")).isEmpty());
        now.set(Duration.ofSeconds(10).toNanos());
        assertEquals(List.of("recovered"), cache.get(() -> Optional.of(List.of("recovered"))));
    }

    @Test
    void slowColdRefreshDoesNotBlockConcurrentPageRequests() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            var pending = executor.submit(() -> cache.get(() -> {
                started.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); fail(ex); }
                return Optional.of(List.of("loaded"));
            }));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            for (int caller = 0; caller < 20; caller++) {
                assertTrue(cache.get(() -> fail("Only one upstream request is permitted")).isEmpty());
            }
            release.countDown();
            assertEquals(List.of("loaded"), pending.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void simultaneousExpiredRequestsRecheckTheWinnerBeforeStartingAnotherRefresh(boolean successful) throws Exception {
        int callers = 32;
        var together = new CyclicBarrier(callers);
        var concurrent = new AtomicBoolean();
        var entered = ThreadLocal.withInitial(() -> false);
        var clock = new AtomicLong();
        var calls = new AtomicInteger();
        var shared = new TimelineCache<String>(Duration.ofSeconds(30), Duration.ofMinutes(5),
            Duration.ofSeconds(10), () -> {
                if (concurrent.get() && !entered.get()) {
                    entered.set(true);
                    try { together.await(10, TimeUnit.SECONDS); }
                    catch (Exception ex) { throw new IllegalStateException(ex); }
                }
                return clock.get();
            }, Runnable::run);
        shared.get(() -> Optional.of(List.of("old")));
        clock.set(Duration.ofSeconds(31).toNanos());
        concurrent.set(true);
        var pool = Executors.newFixedThreadPool(callers);
        try {
            var pending = new ArrayList<java.util.concurrent.Future<List<String>>>();
            for (int caller = 0; caller < callers; caller++) {
                pending.add(pool.submit(() -> shared.get(() -> {
                    calls.incrementAndGet();
                    return successful ? Optional.of(List.of("new")) : Optional.empty();
                })));
            }
            for (var result : pending) assertFalse(result.get(15, TimeUnit.SECONDS).isEmpty());
            assertEquals(1, calls.get(), "Concurrent expired callers must reuse the completed winner");
        } finally {
            concurrent.set(false);
            pool.shutdownNow();
        }
        assertEquals(List.of(successful ? "new" : "old"),
            shared.get(() -> fail("The winner or failure backoff must be reused")));
    }

    @Test
    void invalidDurationsAndRejectedRefreshRemainBounded() {
        assertThrows(IllegalArgumentException.class, () -> new TimelineCache<String>(Duration.ZERO,
                Duration.ofMinutes(5), Duration.ofSeconds(10), now::get, queued::add));
        var rejected = new TimelineCache<String>(Duration.ofSeconds(1), Duration.ofSeconds(2),
                Duration.ofSeconds(1), now::get, task -> { throw new IllegalStateException("closed"); });
        rejected.get(() -> Optional.of(List.of("old")));
        now.set(Duration.ofSeconds(1).toNanos());
        assertEquals(List.of("old"), rejected.get(Optional::empty));
        now.set(Duration.ofSeconds(2).toNanos());
        assertTrue(rejected.get(Optional::empty).isEmpty());
    }
}
