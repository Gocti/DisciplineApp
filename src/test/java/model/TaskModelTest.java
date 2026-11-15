package model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repository.JsonTaskRepository;
import repository.TaskStorageException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class TaskModelTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsTasksBetweenInstances() {
        var storagePath = tempDir.resolve("tasks.json");
        var date = LocalDate.of(2026, 5, 13);
        var model = new TaskModel(new JsonTaskRepository(storagePath));
        var entry = new TaskEntry("Deep work", TaskPriority.NORMAL);

        model.setTask(date, "09", entry);

        var reloadedModel = new TaskModel(new JsonTaskRepository(storagePath));
        assertEquals(entry, reloadedModel.getTask(date, "09"));
    }

    @Test
    void getTasksForDateDoesNotCreateEmptyDateEntries() {
        var storagePath = tempDir.resolve("tasks.json");
        var model = new TaskModel(new JsonTaskRepository(storagePath));

        assertTrue(model.getTasksForDate(LocalDate.of(2026, 5, 14)).isEmpty());
        assertFalse(Files.exists(storagePath));
    }

    @Test
    void removeTaskDeletesEmptyDateBucket() {
        var storagePath = tempDir.resolve("tasks.json");
        var date = LocalDate.of(2026, 5, 15);
        var model = new TaskModel(new JsonTaskRepository(storagePath));
        model.setTask(date, "10", new TaskEntry("Call", TaskPriority.NORMAL));

        model.removeTask(date, "10");

        assertTrue(model.getTasksForDate(date).isEmpty());
        var reloadedModel = new TaskModel(new JsonTaskRepository(storagePath));
        assertNull(reloadedModel.getTask(date, "10"));
        assertTrue(reloadedModel.getTasksForDate(date).isEmpty());
    }

    @Test
    void createsBackupOfPreviousValidState() {
        var storagePath = tempDir.resolve("tasks.json");
        var date = LocalDate.of(2026, 5, 16);
        var repository = new JsonTaskRepository(storagePath);
        repository.setTask(date, "09", new TaskEntry("First", TaskPriority.NORMAL));
        repository.setTask(date, "10", new TaskEntry("Second", TaskPriority.IMPORTANT));

        assertTrue(Files.exists(tempDir.resolve("tasks.json.bak")));
        var backupRepository = new JsonTaskRepository(tempDir.resolve("tasks.json.bak"));
        assertEquals("First", backupRepository.getTask(date, "09").text());
        assertNull(backupRepository.getTask(date, "10"));
    }

    @Test
    void restoresCorruptedPrimaryFileFromBackup() throws IOException {
        var storagePath = tempDir.resolve("tasks.json");
        var date = LocalDate.of(2026, 5, 17);
        var repository = new JsonTaskRepository(storagePath);
        repository.setTask(date, "09", new TaskEntry("Recover me", TaskPriority.NORMAL));
        repository.setTask(date, "10", new TaskEntry("Newer state", TaskPriority.IMPORTANT));

        Files.writeString(storagePath, "{ invalid json");

        var recovered = new JsonTaskRepository(storagePath);
        assertEquals("Recover me", recovered.getTask(date, "09").text());
        assertNull(recovered.getTask(date, "10"));
        assertTrue(Files.exists(storagePath));
        try (var files = Files.list(tempDir)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString()
                    .startsWith("tasks.json.corrupt-")));
        }
    }

    @Test
    void failsInsteadOfSilentlyStartingWithEmptyDataWhenFileIsCorrupt() throws IOException {
        var storagePath = tempDir.resolve("tasks.json");
        Files.writeString(storagePath, "{ invalid json");

        assertThrows(TaskStorageException.class, () -> new JsonTaskRepository(storagePath));
    }

    @Test
    void failedSaveDoesNotCommitChangeInMemory() throws IOException {
        Path parentIsFile = tempDir.resolve("not-a-directory");
        Files.writeString(parentIsFile, "file");
        var storagePath = parentIsFile.resolve("tasks.json");
        var repository = new JsonTaskRepository(storagePath);
        var date = LocalDate.of(2026, 5, 18);

        assertThrows(TaskStorageException.class, () -> repository.setTask(
                date, "09", new TaskEntry("Must not appear", TaskPriority.NORMAL)));
        assertNull(repository.getTask(date, "09"));
    }

    @Test
    void returnedTaskMapIsAnImmutableSnapshot() {
        var repository = new JsonTaskRepository(tempDir.resolve("tasks.json"));
        var date = LocalDate.of(2026, 5, 19);
        repository.setTask(date, "09", new TaskEntry("Original", TaskPriority.NORMAL));
        var snapshot = repository.getTasksForDate(date);

        repository.setTask(date, "10", new TaskEntry("Later", TaskPriority.NORMAL));

        assertEquals(1, snapshot.size());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.put("11", new TaskEntry("No", TaskPriority.NORMAL)));
    }
}
