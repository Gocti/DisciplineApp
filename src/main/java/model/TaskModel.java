package model;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class TaskModel {

    private final Map<LocalDate, Map<String, TaskEntry>> tasks = new HashMap<>();

    public Map<String, TaskEntry> getTasksForDate(LocalDate date) {
        return tasks.computeIfAbsent(date, d -> new HashMap<>());
    }

    public void setTask(LocalDate date, String key, TaskEntry entry) {
        getTasksForDate(date).put(key, entry);
    }

    public TaskEntry getTask(LocalDate date, String key) {
        return getTasksForDate(date).get(key);
    }

    public void removeTask(LocalDate date, String key) {
        getTasksForDate(date).remove(key);
    }
}


