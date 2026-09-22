package repository;

import model.TaskEntry;

import java.time.LocalDate;
import java.util.Map;

public interface TaskRepository {

    Map<String, TaskEntry> getTasksForDate(LocalDate date);

    TaskEntry getTask(LocalDate date, String key);

    void setTask(LocalDate date, String key, TaskEntry entry);

    void removeTask(LocalDate date, String key);
}
