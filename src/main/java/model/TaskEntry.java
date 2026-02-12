package model;

public class TaskEntry {

    private String text;
    private TaskPriority priority;
    private boolean done;

    public TaskEntry(String text, TaskPriority priority) {
        this.text = text;
        this.priority = priority;
        this.done = false;
    }

    // ===== getters =====
    public String getText() {
        return text;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public boolean isDone() {
        return done;
    }

    // ===== setters =====
    public void setText(String text) {
        this.text = text;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    // ===== важно для JTable =====
    @Override
    public String toString() {
        return text;
    }
}


