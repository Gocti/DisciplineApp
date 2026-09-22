package model;

import repository.JsonTaskRepository;
import repository.TaskRepository;

import java.time.LocalDate;
import java.util.Map;

public class TaskModel {

    private final TaskRepository repository;

    public TaskModel() {
        this(new JsonTaskRepository());
    }

    TaskModel(TaskRepository repository) {
        this.repository = repository;
    }

    public Map<String, TaskEntry> getTasksForDate(LocalDate date) {
        return repository.getTasksForDate(date);
    }

    public TaskEntry getTask(LocalDate date, String key) {
        return repository.getTask(date, key);
    }

    public void setTask(LocalDate date, String key, TaskEntry entry) {
        repository.setTask(date, key, entry);
    }

    public void removeTask(LocalDate date, String key) {
        repository.removeTask(date, key);
    }
}
