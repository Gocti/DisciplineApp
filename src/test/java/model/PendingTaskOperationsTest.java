package model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PendingTaskOperationsTest {

    private PendingTaskOperations operations;
    private LocalDate date;

    @BeforeEach
    void setUp() {
        operations = new PendingTaskOperations();
        date = LocalDate.of(2026, 11, 15);
    }

    @Test
    void shouldAcquireLockForTask() {
        var keys = Set.of("09");

        var acquired = operations.tryAcquire(date, keys);

        assertTrue(acquired);
        assertTrue(operations.isPending(date, "09"));
    }

    @Test
    void shouldRejectSecondLockForSameTask() {
        var keys = Set.of("09");
        operations.tryAcquire(date, keys);

        var acquired = operations.tryAcquire(date, keys);

        assertFalse(acquired);
    }

    @Test
    void shouldAllowLocksForDifferentTasks() {
        operations.tryAcquire(date, Set.of("09"));

        var acquired = operations.tryAcquire(date, Set.of("10"));

        assertTrue(acquired);
        assertTrue(operations.isPending(date, "09"));
        assertTrue(operations.isPending(date, "10"));
    }

    @Test
    void shouldAllowLocksForDifferentDates() {
        var anotherDate = date.plusDays(1);
        operations.tryAcquire(date, Set.of("09"));

        var acquired = operations.tryAcquire(
                anotherDate,
                Set.of("09")
        );

        assertTrue(acquired);
        assertTrue(operations.isPending(date, "09"));
        assertTrue(operations.isPending(anotherDate, "09"));
    }

    @Test
    void shouldRejectEntireBatchWhenOneTaskIsAlreadyLocked() {
        operations.tryAcquire(date, Set.of("10"));

        var acquired = operations.tryAcquire(
                date,
                Set.of("09", "10", "11")
        );

        assertFalse(acquired);
        assertFalse(operations.isPending(date, "09"));
        assertTrue(operations.isPending(date, "10"));
        assertFalse(operations.isPending(date, "11"));
    }

    @Test
    void shouldReleaseTaskLock() {
        var keys = Set.of("09");
        operations.tryAcquire(date, keys);

        operations.release(date, keys);

        assertFalse(operations.isPending(date, "09"));
    }

    @Test
    void shouldAllowLockAfterRelease() {
        var keys = Set.of("09");
        operations.tryAcquire(date, keys);
        operations.release(date, keys);

        var acquired = operations.tryAcquire(date, keys);

        assertTrue(acquired);
    }

    @Test
    void shouldReleaseOnlySpecifiedTasks() {
        operations.tryAcquire(date, Set.of("09", "10"));

        operations.release(date, Set.of("09"));

        assertFalse(operations.isPending(date, "09"));
        assertTrue(operations.isPending(date, "10"));
    }

    @Test
    void shouldRejectNullDate() {
        assertFalse(
                operations.tryAcquire(null, Set.of("09"))
        );
    }

    @Test
    void shouldRejectEmptyKeys() {
        assertFalse(
                operations.tryAcquire(date, Set.of())
        );
    }

    @Test
    void shouldReturnFalseForUnknownTask() {
        assertFalse(operations.isPending(date, "09"));
    }

    // Конкурентные тесты

    @Test
    void shouldAllowOnlyOneThreadToAcquireSameTask()
            throws Exception {

        int threadCount = 10;

        var ready = new CountDownLatch(threadCount);
        var start = new CountDownLatch(1);
        var successfulAcquisitions = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(threadCount)) {
            try {
                List<Future<?>> tasks = new ArrayList<>();

                for (int i = 0; i < threadCount; i++) {
                    tasks.add(executor.submit(() -> {
                        ready.countDown();

                        if (!start.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "Не удалось дождаться старта"
                            );
                        }

                        if (operations.tryAcquire(date, Set.of("09"))) {
                            successfulAcquisitions.incrementAndGet();
                        }

                        return null;
                    }));
                }

                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();

                for (var task : tasks) {
                    task.get(10, TimeUnit.SECONDS);
                }

                assertEquals(1, successfulAcquisitions.get());
                assertTrue(operations.isPending(date, "09"));

            } finally {
                start.countDown();
            }
        }
    }

    @Test
    void shouldAllowConcurrentAcquisitionOfDifferentTasks()
            throws Exception {

        int threadCount = 10;

        var ready = new CountDownLatch(threadCount);
        var start = new CountDownLatch(1);
        var successfulAcquisitions = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(threadCount)) {
            try {
                List<Future<?>> tasks = new ArrayList<>();

                for (int i = 0; i < threadCount; i++) {
                    var key = String.format("%02d", i);

                    tasks.add(executor.submit(() -> {
                        ready.countDown();

                        if (!start.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "Не удалось дождаться старта"
                            );
                        }

                        if (operations.tryAcquire(date, Set.of(key))) {
                            successfulAcquisitions.incrementAndGet();
                        }

                        return null;
                    }));
                }

                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();

                for (var task : tasks) {
                    task.get(10, TimeUnit.SECONDS);
                }

                assertEquals(threadCount, successfulAcquisitions.get());

            } finally {
                start.countDown();
            }
        }
    }

    @Test
    void shouldNotAllowOverlappingBatchAcquisitions()
            throws Exception {

        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                Future<Boolean> first = executor.submit(() -> {
                    ready.countDown();

                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Не удалось дождаться старта"
                        );
                    }

                    return operations.tryAcquire(
                            date,
                            Set.of("09", "10")
                    );
                });

                Future<Boolean> second = executor.submit(() -> {
                    ready.countDown();

                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Не удалось дождаться старта"
                        );
                    }

                    return operations.tryAcquire(
                            date,
                            Set.of("10", "11")
                    );
                });

                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();

                var firstResult = first.get(10, TimeUnit.SECONDS);
                var secondResult = second.get(10, TimeUnit.SECONDS);

                assertNotEquals(firstResult, secondResult);
                assertTrue(operations.isPending(date, "10"));

                assertEquals(
                        firstResult,
                        operations.isPending(date, "09")
                );

                assertEquals(
                        secondResult,
                        operations.isPending(date, "11")
                );

            } finally {
                start.countDown();
            }
        }
    }
}