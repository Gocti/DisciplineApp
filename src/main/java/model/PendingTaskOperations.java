package model;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Отслеживает задачи, для которых выполняются операции записи.
 * <p>
 * Используется общим интерфейсом приложения:
 * MainFrame и CalendarPanel.
 */
public final class PendingTaskOperations {

    private final Set<TaskKey> pendingTasks = new HashSet<>();

    /**
     * Пытается заблокировать указанные задачи.
     * <p>
     * Если хотя бы одна задача уже заблокирована,
     * операция не выполняется.
     */
    public synchronized boolean tryAcquire(
            LocalDate date,
            Collection<String> keys
    ) {
        if (date == null || keys == null || keys.isEmpty()) {
            return false;
        }

        var requested = new HashSet<TaskKey>();

        for (var key : keys) {
            if (key == null) {
                return false;
            }

            requested.add(new TaskKey(date, key));
        }

        if (requested.stream().anyMatch(pendingTasks::contains)) {
            return false;
        }

        pendingTasks.addAll(requested);
        return true;
    }

    /**
     * Снимает блокировку с указанных задач.
     */
    public synchronized void release(
            LocalDate date,
            Collection<String> keys
    ) {
        if (date == null || keys == null || keys.isEmpty()) {
            return;
        }

        for (var key : keys) {
            if (key != null) {
                pendingTasks.remove(new TaskKey(date, key));
            }
        }
    }

    /**
     * Проверяет, выполняется ли операция над задачей.
     */
    public synchronized boolean isPending(
            LocalDate date,
            String key
    ) {
        return date != null
                && key != null
                && pendingTasks.contains(new TaskKey(date, key));
    }

    /**
     * Ключ задачи в конкретную дату.
     */
    private record TaskKey(LocalDate date, String key) {
    }
}

