package repository;

import model.TaskEntry;
import model.TaskPriority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonTaskRepositoryTest {

    private static final int TASK_COUNT = 64;

    @TempDir
    Path tempDir;

    @Test
    void shouldReturnUnmodifiableTaskSnapshot() {
        JsonTaskRepository repository =
                new JsonTaskRepository(tempDir.resolve("tasks.json"));

        LocalDate date = LocalDate.of(2026, 11, 15);

        repository.setTask(
                date,
                "10",
                new TaskEntry("Test task", TaskPriority.NORMAL)
        );

        Map<String, TaskEntry> snapshot =
                repository.getTasksForDate(date);

        assertEquals(1, snapshot.size());

        TaskEntry anotherTask =
                new TaskEntry("Another task", TaskPriority.NORMAL);

        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.put("11", anotherTask)
        );

        assertEquals(1, repository.getTasksForDate(date).size());
    }

    @Test
    void shouldExposeOnlyCompleteSnapshotsDuringConcurrentWrites()
            throws Exception {

        JsonTaskRepository repository =
                new JsonTaskRepository(tempDir.resolve("concurrent.json"));

        LocalDate date = LocalDate.of(2026, 11, 15);

        repository.setTasks(date, createBatch("0"));

        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(5)) {
            Future<?> writer = executor.submit(() -> {
                await(start);

                for (int version = 1; version <= 60; version++) {
                    repository.setTasks(date, createBatch(
                            Integer.toString(version)
                    ));
                }
            });

            List<Future<?>> readers = new ArrayList<>();

            for (int reader = 0; reader < 4; reader++) {
                readers.add(executor.submit(() -> {
                    await(start);

                    for (int iteration = 0; iteration < 300; iteration++) {
                        Map<String, TaskEntry> snapshot =
                                repository.getTasksForDate(date);

                        assertEquals(TASK_COUNT, snapshot.size());

                        long distinctVersions = snapshot.values().stream()
                                .map(TaskEntry::text)
                                .distinct()
                                .count();

                        assertEquals(1, distinctVersions);
                    }
                }));
            }

            start.countDown();

            writer.get();

            for (Future<?> reader : readers) {
                reader.get();
            }
        }

        Map<String, TaskEntry> finalSnapshot =
                repository.getTasksForDate(date);

        assertEquals(TASK_COUNT, finalSnapshot.size());

        assertEquals(
                "60",
                finalSnapshot.get("task-0").text()
        );
    }

    @Test
    void shouldNotLoseUpdatesFromConcurrentWriters() throws Exception {
        JsonTaskRepository repository =
                new JsonTaskRepository(tempDir.resolve("writers.json"));

        LocalDate date = LocalDate.of(2026, 11, 15);

        int writerCount = 8;
        int tasksPerWriter = 10;

        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> writers = new ArrayList<>();

        try (var executor = Executors.newFixedThreadPool(writerCount)) {
            for (int writer = 0; writer < writerCount; writer++) {
                int writerId = writer;

                writers.add(executor.submit(() -> {
                    await(start);

                    for (int task = 0; task < tasksPerWriter; task++) {
                        String key = "writer-" + writerId + "-task-" + task;

                        repository.setTask(
                                date,
                                key,
                                new TaskEntry(key, TaskPriority.NORMAL)
                        );
                    }
                }));
            }

            start.countDown();

            for (Future<?> writer : writers) {
                writer.get();
            }
        }

        Map<String, TaskEntry> result =
                repository.getTasksForDate(date);

        assertEquals(writerCount * tasksPerWriter, result.size());
    }

    private Map<String, TaskEntry> createBatch(String version) {
        Map<String, TaskEntry> batch = new LinkedHashMap<>();

        for (int i = 0; i < TASK_COUNT; i++) {
            batch.put(
                    "task-" + i,
                    new TaskEntry(version, TaskPriority.NORMAL)
            );
        }

        return batch;
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Поток был прерван", e);
        }
    }
}