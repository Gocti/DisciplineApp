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
        var entry = new TaskEntry("Deep work", TaskPriority.NORMAL);

        try (var model = new TaskModel(
                new JsonTaskRepository(storagePath)
        )) {
            model.setTask(date, "09", entry);
        }

        try (var reloadedModel = new TaskModel(
                new JsonTaskRepository(storagePath)
        )) {
            assertEquals(entry, reloadedModel.getTask(date, "09"));
        }
    }

    @Test
    void getTasksForDateDoesNotCreateEmptyDateEntries() {
        var storagePath = tempDir.resolve("tasks.json");

        try (var model = new TaskModel(
                new JsonTaskRepository(storagePath)
        )) {
            assertTrue(
                    model.getTasksForDate(
                            LocalDate.of(2026, 5, 14)
                    ).isEmpty()
            );

            assertFalse(Files.exists(storagePath));
        }
    }

    @Test
    void removeTaskDeletesEmptyDateBucket() {
        var storagePath = tempDir.resolve("tasks.json");
        var date = LocalDate.of(2026, 5, 15);

        try (var model = new TaskModel(
                new JsonTaskRepository(storagePath)
        )) {
            model.setTask(
                    date,
                    "10",
                    new TaskEntry("Call", TaskPriority.NORMAL)
            );

            model.removeTask(date, "10");

            assertTrue(model.getTasksForDate(date).isEmpty());
        }

        try (var reloadedModel = new TaskModel(
                new JsonTaskRepository(storagePath)
        )) {
            assertNull(reloadedModel.getTask(date, "10"));
            assertTrue(reloadedModel.getTasksForDate(date).isEmpty());
        }
    }

    @Test
    void createsBackupOfPreviousValidState() {
        var storagePath = tempDir.resolve("tasks.json");
        var date = LocalDate.of(2026, 5, 16);
        var repository = new JsonTaskRepository(storagePath);

        repository.setTask(
                date,
                "09",
                new TaskEntry("First", TaskPriority.NORMAL)
        );

        repository.setTask(
                date,
                "10",
                new TaskEntry("Second", TaskPriority.IMPORTANT)
        );

        assertTrue(Files.exists(tempDir.resolve("tasks.json.bak")));

        var backupRepository = new JsonTaskRepository(
                tempDir.resolve("tasks.json.bak")
        );

        assertEquals(
                "First",
                backupRepository.getTask(date, "09").text()
        );

        assertNull(backupRepository.getTask(date, "10"));
    }

    @Test
    void restoresCorruptedPrimaryFileFromBackup() throws IOException {
        var storagePath = tempDir.resolve("tasks.json");
        var date = LocalDate.of(2026, 5, 17);
        var repository = new JsonTaskRepository(storagePath);

        repository.setTask(
                date,
                "09",
                new TaskEntry("Recover me", TaskPriority.NORMAL)
        );

        repository.setTask(
                date,
                "10",
                new TaskEntry("Newer state", TaskPriority.IMPORTANT)
        );

        Files.writeString(storagePath, "{ invalid json");

        var recovered = new JsonTaskRepository(storagePath);

        assertEquals(
                "Recover me",
                recovered.getTask(date, "09").text()
        );

        assertNull(recovered.getTask(date, "10"));
        assertTrue(Files.exists(storagePath));

        try (var files = Files.list(tempDir)) {
            assertTrue(files.anyMatch(path ->
                    path.getFileName().toString()
                            .startsWith("tasks.json.corrupt-")
            ));
        }
    }

    @Test
    void failsInsteadOfSilentlyStartingWithEmptyDataWhenFileIsCorrupt()
            throws IOException {

        var storagePath = tempDir.resolve("tasks.json");
        Files.writeString(storagePath, "{ invalid json");

        assertThrows(
                TaskStorageException.class,
                () -> new JsonTaskRepository(storagePath)
        );
    }

    @Test
    void failedSaveDoesNotCommitChangeInMemory() throws IOException {
        Path parentIsFile = tempDir.resolve("not-a-directory");
        Files.writeString(parentIsFile, "file");

        var storagePath = parentIsFile.resolve("tasks.json");
        var repository = new JsonTaskRepository(storagePath);
        var date = LocalDate.of(2026, 5, 18);
        var entry = new TaskEntry(
                "Must not appear",
                TaskPriority.NORMAL
        );

        assertThrows(
                TaskStorageException.class,
                () -> repository.setTask(date, "09", entry)
        );

        assertNull(repository.getTask(date, "09"));
    }

    @Test
    void returnedTaskMapIsAnImmutableSnapshot() {
        var repository = new JsonTaskRepository(
                tempDir.resolve("tasks.json")
        );

        var date = LocalDate.of(2026, 5, 19);

        repository.setTask(
                date,
                "09",
                new TaskEntry("Original", TaskPriority.NORMAL)
        );

        var snapshot = repository.getTasksForDate(date);

        repository.setTask(
                date,
                "10",
                new TaskEntry("Later", TaskPriority.NORMAL)
        );

        assertEquals(1, snapshot.size());

        var invalidEntry = new TaskEntry(
                "No",
                TaskPriority.NORMAL
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.put("11", invalidEntry)
        );
    }

    @Test
    void restoresMissingPrimaryFileFromBackup() throws IOException {
        var storagePath = tempDir.resolve("tasks.json");
        var date = LocalDate.of(2026, 5, 20);

        var repository = new JsonTaskRepository(storagePath);

        repository.setTask(
                date,
                "09",
                new TaskEntry("Previous state", TaskPriority.NORMAL)
        );

        repository.setTask(
                date,
                "10",
                new TaskEntry("Latest state", TaskPriority.IMPORTANT)
        );

        Files.delete(storagePath);

        var recovered = new JsonTaskRepository(storagePath);

        assertEquals(
                "Previous state",
                recovered.getTask(date, "09").text()
        );

        assertNull(recovered.getTask(date, "10"));

        assertTrue(Files.exists(storagePath));
        assertTrue(Files.exists(tempDir.resolve("tasks.json.bak")));
    }

    @Test
    void failsWhenBothPrimaryFileAndBackupAreCorrupted() throws IOException {
        var storagePath = tempDir.resolve("tasks.json");
        var backupPath = tempDir.resolve("tasks.json.bak");

        String primaryContent = "{ invalid primary json";
        String backupContent = "{ invalid backup json";

        Files.writeString(storagePath, primaryContent);
        Files.writeString(backupPath, backupContent);

        assertThrows(
                TaskStorageException.class,
                () -> new JsonTaskRepository(storagePath)
        );

        assertEquals(primaryContent, Files.readString(storagePath));
        assertEquals(backupContent, Files.readString(backupPath));
    }

    @Test
    void failsWhenPrimaryFileIsMissingAndBackupIsCorrupted() throws IOException {
        var storagePath = tempDir.resolve("tasks.json");
        var backupPath = tempDir.resolve("tasks.json.bak");

        String backupContent = "{ invalid backup json";
        Files.writeString(backupPath, backupContent);

        assertThrows(
                TaskStorageException.class,
                () -> new JsonTaskRepository(storagePath)
        );

        assertFalse(Files.exists(storagePath));
        assertEquals(backupContent, Files.readString(backupPath));
    }

}
