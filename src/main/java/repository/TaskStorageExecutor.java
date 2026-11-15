package repository;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Последовательно выполняет операции с хранилищем задач
 * в отдельном рабочем потоке.
 */
public final class TaskStorageExecutor implements AutoCloseable {

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(task -> {
                var thread = new Thread(task, "discipline-storage");
                thread.setDaemon(false);
                return thread;
            });

    private CompletableFuture<Void> closeFuture;

    /**
     * Добавляет операцию в последовательную очередь.
     * <p>
     * После начала закрытия новые операции не принимаются.
     */
    public synchronized CompletableFuture<Void> submit(
            Runnable operation
    ) {
        if (executor.isShutdown()) {
            return CompletableFuture.failedFuture(
                    new RejectedExecutionException(
                            "Хранилище уже закрывается или закрыто."
                    )
            );
        }

        try {
            return CompletableFuture.runAsync(operation, executor);
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    /**
     * Начинает закрытие исполнителя и асинхронно
     * ожидает завершения всех ранее поставленных операций.
     */
    public synchronized CompletableFuture<Void> closeAsync() {
        if (closeFuture != null) {
            return closeFuture;
        }

        executor.shutdown();

        closeFuture = new CompletableFuture<>();

        var shutdownThread = new Thread(
                this::awaitTermination,
                "discipline-storage-shutdown"
        );

        shutdownThread.setDaemon(true);
        shutdownThread.start();

        return closeFuture;
    }

    /**
     * Ожидает полного завершения рабочего потока.
     * Метод выполняется не в Swing EDT.
     */
    private void awaitTermination() {
        boolean interrupted = false;

        try {
            while (true) {
                try {
                    if (executor.awaitTermination(
                            1,
                            TimeUnit.DAYS
                    )) {
                        closeFuture.complete(null);
                        return;
                    }
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Синхронное закрытие.
     * <p>
     * Не вызывать из Swing EDT.
     */
    @Override
    public void close() {
        closeAsync().join();
    }
}