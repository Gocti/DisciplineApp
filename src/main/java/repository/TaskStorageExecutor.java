
package repository;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Последовательно выполняет операции с хранилищем задач
 * в отдельном рабочем потоке.
 */
public final class TaskStorageExecutor implements AutoCloseable {

    private static final Duration SHUTDOWN_TIMEOUT =
            Duration.ofSeconds(10);

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(task -> {
                var thread = new Thread(task, "discipline-storage");
                thread.setDaemon(false);
                return thread;
            });

    public CompletableFuture<Void> submit(Runnable operation) {
        return CompletableFuture.runAsync(operation, executor);
    }

    @Override
    public void close() {
        executor.shutdown();

        try {
            if (!executor.awaitTermination(
                    SHUTDOWN_TIMEOUT.toSeconds(),
                    TimeUnit.SECONDS
            )) {
                executor.shutdownNow();
            }
        } catch (InterruptedException _) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

