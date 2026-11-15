package model;

import org.junit.jupiter.api.Test;
import repository.TaskStorageExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TaskStorageExecutorTest {

    private static final long TIMEOUT_SECONDS = 5;

    @Test
    void shouldExecuteSubmittedTasksInOrder() throws Exception {
        try (var storage = new TaskStorageExecutor()) {
            List<Integer> executionOrder = new ArrayList<>();

            CompletableFuture<Void> first = storage.submit(
                    () -> executionOrder.add(1)
            );

            CompletableFuture<Void> second = storage.submit(
                    () -> executionOrder.add(2)
            );

            CompletableFuture<Void> third = storage.submit(
                    () -> executionOrder.add(3)
            );

            CompletableFuture.allOf(first, second, third)
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertEquals(List.of(1, 2, 3), executionOrder);
        }
    }

    @Test
    void shouldReturnSameFutureWhenCloseAsyncCalledMultipleTimes()
            throws Exception {
        try (var storage = new TaskStorageExecutor()) {
            CompletableFuture<Void> firstClose = storage.closeAsync();
            CompletableFuture<Void> secondClose = storage.closeAsync();

            assertSame(firstClose, secondClose);

            firstClose.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void shouldRejectNewTasksAfterShutdown() throws Exception {
        try (var storage = new TaskStorageExecutor()) {
            CompletableFuture<Void> closeFuture = storage.closeAsync();

            CompletableFuture<Void> rejectedTask = storage.submit(
                    () -> fail("Задача не должна выполняться после shutdown")
            );

            ExecutionException exception = assertThrows(
                    ExecutionException.class,
                    () -> rejectedTask.get(
                            TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    )
            );

            assertInstanceOf(
                    RejectedExecutionException.class,
                    exception.getCause()
            );

            closeFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void shouldWaitForRunningAndQueuedTasksBeforeClosing()
            throws Exception {
        try (var storage = new TaskStorageExecutor()) {
            CountDownLatch firstTaskStarted = new CountDownLatch(1);
            CountDownLatch releaseFirstTask = new CountDownLatch(1);

            AtomicInteger queuedTaskExecutions = new AtomicInteger();

            try {
                CompletableFuture<Void> firstTask = storage.submit(() -> {
                    firstTaskStarted.countDown();
                    awaitLatch(releaseFirstTask);
                });

                assertTrue(
                        firstTaskStarted.await(
                                TIMEOUT_SECONDS,
                                TimeUnit.SECONDS
                        ),
                        "Первая задача не начала выполняться"
                );

                CompletableFuture<Void> queuedTask = storage.submit(
                        queuedTaskExecutions::incrementAndGet
                );

                CompletableFuture<Void> closeFuture = storage.closeAsync();

                assertFalse(
                        closeFuture.isDone(),
                        "Закрытие не должно завершиться, пока первая задача заблокирована"
                );

                releaseFirstTask.countDown();

                firstTask.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                queuedTask.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                closeFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

                assertEquals(1, queuedTaskExecutions.get());

            } finally {
                releaseFirstTask.countDown();
            }
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Истекло время ожидания CountDownLatch"
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Ожидание задачи было прервано",
                    exception
            );
        }
    }
}

