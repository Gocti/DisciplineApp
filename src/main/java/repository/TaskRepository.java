
package repository;

import model.TaskEntry;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

public interface TaskRepository {

    Map<String, TaskEntry> getTasksForDate(LocalDate date);

    TaskEntry getTask(LocalDate date, String key);

    void setTask(LocalDate date, String key, TaskEntry entry);

    void setTasks(LocalDate date, Map<String, TaskEntry> entries);

    void removeTask(LocalDate date, String key);

    void removeTasks(LocalDate date, Set<String> keys);
}

