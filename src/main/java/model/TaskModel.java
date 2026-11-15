package model;

import repository.JsonTaskRepository;
import repository.TaskRepository;
import repository.TaskStorageExecutor;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class TaskModel implements AutoCloseable {

    private final TaskRepository repository;
    private final TaskStorageExecutor storageExecutor;

    private final PendingTaskOperations pendingOperations =
            new PendingTaskOperations();

    public TaskModel() {
        this(new JsonTaskRepository());
    }

    TaskModel(TaskRepository repository) {
        this.repository = repository;
        this.storageExecutor = new TaskStorageExecutor();
    }

    public Map<String, TaskEntry> getTasksForDate(LocalDate date) {
        return repository.getTasksForDate(date);
    }

    public TaskEntry getTask(LocalDate date, String key) {
        return repository.getTask(date, key);
    }

    /**
     * Пытается зарезервировать задачи для изменения.
     */
    public boolean tryAcquireTasks(
            LocalDate date,
            Collection<String> keys
    ) {
        return pendingOperations.tryAcquire(date, keys);
    }

    /**
     * Освобождает ранее зарезервированные задачи.
     */
    public void releaseTasks(
            LocalDate date,
            Collection<String> keys
    ) {
        pendingOperations.release(date, keys);
    }

    /**
     * Проверяет, изменяется ли задача в данный момент.
     */
    public boolean isTaskPending(
            LocalDate date,
            String key
    ) {
        return pendingOperations.isPending(date, key);
    }

    /**
     * Синхронно сохраняет задачу.
     * Не вызывать из Swing EDT.
     */
     void setTask(
            LocalDate date,
            String key,
            TaskEntry entry
    ) {
        repository.setTask(date, key, entry);
    }

    /**
     * Синхронно удаляет задачу.
     * Не вызывать из Swing EDT.
     */
     void removeTask(LocalDate date, String key) {
        repository.removeTask(date, key);
    }

    /**
     * Асинхронно сохраняет задачу.
     */
    public CompletableFuture<Void> setTaskAsync(
            LocalDate date,
            String key,
            TaskEntry entry
    ) {
        return storageExecutor.submit(
                () -> repository.setTask(date, key, entry)
        );
    }

    /**
     * Асинхронно удаляет задачу.
     */
    public CompletableFuture<Void> removeTaskAsync(
            LocalDate date,
            String key
    ) {
        return storageExecutor.submit(
                () -> repository.removeTask(date, key)
        );
    }

    /**
     * Асинхронно сохраняет несколько задач.
     */
    public CompletableFuture<Void> setTasksAsync(
            LocalDate date,
            Map<String, TaskEntry> entries
    ) {
        var snapshot = Map.copyOf(entries);

        return storageExecutor.submit(
                () -> repository.setTasks(date, snapshot)
        );
    }

    /**
     * Асинхронно удаляет несколько задач.
     */
    public CompletableFuture<Void> removeTasksAsync(
            LocalDate date,
            Set<String> keys
    ) {
        var snapshot = Set.copyOf(keys);

        return storageExecutor.submit(
                () -> repository.removeTasks(date, snapshot)
        );
    }

    /**
     * Асинхронно закрывает хранилище,
     * дожидаясь завершения всех операций записи.
     */
    public CompletableFuture<Void> closeAsync() {
        return storageExecutor.closeAsync();
    }

    /**
     * Синхронное закрытие.
     * Не вызывать из Swing EDT.
     */
    @Override
    public void close() {
        storageExecutor.close();
    }
}
