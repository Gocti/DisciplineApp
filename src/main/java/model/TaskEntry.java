package model;

import java.util.Objects;

/**
 * Неизменяемая модель задачи.
 * <p>
 * Используется как объект доменного уровня и не зависит от UI,
 * хранения данных или конкретной реализации интерфейса.
 */
public record TaskEntry(String text, TaskPriority priority, boolean done) {

    public TaskEntry {
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(priority, "priority must not be null");
    }

    public TaskEntry(String text, TaskPriority priority) {
        this(text, priority, false);
    }

    public TaskEntry withDone(boolean done) {
        return new TaskEntry(text, priority, done);
    }

    public TaskEntry withText(String text) {
        return new TaskEntry(text, priority, done);
    }
}