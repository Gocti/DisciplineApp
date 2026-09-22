package model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repository.JsonTaskRepository;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskModelTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsTasksBetweenInstances() {
        var storagePath = tempDir.resolve("tasks.json");
        var date = LocalDate.of(2026, 5, 13);

        var model = new TaskModel(
                new JsonTaskRepository(storagePath)
        );

        var entry = new TaskEntry(
                "Deep work",
                TaskPriority.NORMAL
        );

        model.setTask(date, "09", entry);

        var reloadedModel = new TaskModel(
                new JsonTaskRepository(storagePath)
        );

        assertEquals(
                entry,
                reloadedModel.getTask(date, "09")
        );
    }

    @Test
    void getTasksForDateDoesNotCreateEmptyDateEntries() {
        var storagePath = tempDir.resolve("tasks.json");
        var date = LocalDate.of(2026, 5, 14);

        var model = new TaskModel(
                new JsonTaskRepository(storagePath)
        );

        assertTrue(
                model.getTasksForDate(date).isEmpty()
        );

        assertFalse(
                storagePath.toFile().exists()
        );
    }

    @Test
    void removeTaskDeletesEmptyDateBucket() {
        var storagePath = tempDir.resolve("tasks.json");
        var date = LocalDate.of(2026, 5, 15);

        var model = new TaskModel(
                new JsonTaskRepository(storagePath)
        );

        model.setTask(
                date,
                "10",
                new TaskEntry(
                        "Call",
                        TaskPriority.NORMAL
                )
        );

        model.removeTask(date, "10");

        assertTrue(
                model.getTasksForDate(date).isEmpty()
        );

        var reloadedModel = new TaskModel(
                new JsonTaskRepository(storagePath)
        );

        assertNull(
                reloadedModel.getTask(date, "10")
        );

        assertTrue(
                reloadedModel.getTasksForDate(date).isEmpty()
        );
    }
}
