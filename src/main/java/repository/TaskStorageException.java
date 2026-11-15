package repository;

public class TaskStorageException extends RuntimeException {
    public TaskStorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public TaskStorageException(String message) {
        super(message);
    }
}
