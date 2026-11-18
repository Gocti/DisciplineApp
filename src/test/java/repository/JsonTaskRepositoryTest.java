package repository;

import model.TaskEntry;
import model.TaskPriority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import java.io.IOException;

class JsonTaskRepositoryTest {

    private static final int TASK_COUNT = 24;

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
                finalSnapshot.get("00").text()
        );
    }

    @Test
    void shouldNotLoseUpdatesFromConcurrentWriters() throws Exception {
        JsonTaskRepository repository =
                new JsonTaskRepository(tempDir.resolve("writers.json"));

        LocalDate date = LocalDate.of(2026, 11, 15);

        int writerCount = 8;
        int tasksPerWriter = 3;

        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> writers = new ArrayList<>();

        try (var executor = Executors.newFixedThreadPool(writerCount)) {
            for (int writer = 0; writer < writerCount; writer++) {
                int writerId = writer;

                writers.add(executor.submit(() -> {
                    await(start);

                    for (int task = 0; task < tasksPerWriter; task++) {
                        String key = "%02d".formatted(
                                writerId * tasksPerWriter + task
                        );

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

    @Test
    void shouldRejectInvalidKeyWhenRemovingSingleTask() {
        Path storagePath = tempDir.resolve("remove-single.json");
        JsonTaskRepository repository = new JsonTaskRepository(storagePath);
        LocalDate date = LocalDate.of(2026, 11, 18);

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.removeTask(date, "24")
        );

        assertFalse(Files.exists(storagePath));
    }

    @Test
    void shouldRejectEntireRemovalWhenAnyKeyIsInvalid() throws IOException {
        Path storagePath = tempDir.resolve("remove-multiple.json");
        JsonTaskRepository repository = new JsonTaskRepository(storagePath);

        LocalDate date = LocalDate.of(2026, 11, 18);

        repository.setTask(
                date,
                "10",
                new TaskEntry("Important task", TaskPriority.NORMAL)
        );

        String originalContent = Files.readString(storagePath);
        Set<String> keys = Set.of("10", "24");

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.removeTasks(date, keys)
        );

        assertEquals(
                "Important task",
                repository.getTask(date, "10").text()
        );

        assertEquals(
                originalContent,
                Files.readString(storagePath)
        );
    }

    @Test
    void shouldRejectEntireBatchWhenAnyKeyIsInvalid() {
        Path storagePath = tempDir.resolve("set-batch.json");
        JsonTaskRepository repository = new JsonTaskRepository(storagePath);

        LocalDate date = LocalDate.of(2026, 11, 18);

        Map<String, TaskEntry> entries = new LinkedHashMap<>();

        entries.put(
                "10",
                new TaskEntry("Valid task", TaskPriority.NORMAL)
        );

        entries.put(
                "24",
                new TaskEntry("Invalid task", TaskPriority.NORMAL)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.setTasks(date, entries)
        );

        assertFalse(repository.getTasksForDate(date).containsKey("10"));
        assertFalse(Files.exists(storagePath));
    }

    @Test
    void shouldPreservePreviousStateWhenBackupUpdateFails() throws IOException {
        Path storagePath = tempDir.resolve("backup-failure.json");
        Path backupPath = tempDir.resolve("backup-failure.json.bak");

        JsonTaskRepository repository = new JsonTaskRepository(storagePath);

        LocalDate date = LocalDate.of(2026, 11, 18);

        TaskEntry originalTask =
                new TaskEntry("Original task", TaskPriority.NORMAL);

        TaskEntry newTask =
                new TaskEntry("New task", TaskPriority.IMPORTANT);

        repository.setTask(date, "09", originalTask);

        String originalContent = Files.readString(storagePath);

        // Создаём непустой каталог вместо файла резервной копии.
        Files.createDirectory(backupPath);
        Files.writeString(backupPath.resolve("keep.txt"), "keep");

        assertThrows(
                TaskStorageException.class,
                () -> repository.setTask(date, "10", newTask)
        );

        // В памяти осталось исходное состояние.
        assertEquals(
                originalTask,
                repository.getTask(date, "09")
        );

        assertNull(repository.getTask(date, "10"));

        // Основной файл не изменился.
        assertEquals(
                originalContent,
                Files.readString(storagePath)
        );

        // Каталог резервной копии и его содержимое сохранены.
        assertTrue(Files.isDirectory(backupPath));
        assertEquals(
                "keep",
                Files.readString(backupPath.resolve("keep.txt"))
        );
    }

    @Test
    void shouldPreserveBackupWhenRestoringPrimaryFileFails()
            throws IOException {

        Path storagePath = tempDir.resolve("restore-failure.json");
        Path backupPath = tempDir.resolve("restore-failure.json.bak");

        LocalDate date = LocalDate.of(2026, 11, 18);

        // Создаём основной файл и резервную копию.
        JsonTaskRepository initialRepository =
                new JsonTaskRepository(storagePath);

        initialRepository.setTask(
                date,
                "09",
                new TaskEntry("Original task", TaskPriority.NORMAL)
        );

        initialRepository.setTask(
                date,
                "10",
                new TaskEntry("Second task", TaskPriority.IMPORTANT)
        );

        String backupContent = Files.readString(backupPath);

        // Повреждаем основной файл.
        String corruptedContent = "{ invalid json";
        Files.writeString(storagePath, corruptedContent);

        // Имитируем ошибку при финальном восстановлении основного файла.
        FileMover failingMover = createFailingMover(storagePath);

        assertThrows(
                TaskStorageException.class,
                () -> new JsonTaskRepository(storagePath, failingMover)
        );

        // Основной файл перемещён в карантин.
        assertFalse(Files.exists(storagePath));

        // Резервная копия осталась неизменной.
        assertTrue(Files.exists(backupPath));
        assertEquals(
                backupContent,
                Files.readString(backupPath)
        );

        // Повреждённый файл сохранился в карантине.
        try (var files = Files.list(tempDir)) {
            Path quarantine = files
                    .filter(path -> path.getFileName().toString()
                            .startsWith("restore-failure.json.corrupt-"))
                    .findFirst()
                    .orElseThrow();

            assertEquals(
                    corruptedContent,
                    Files.readString(quarantine)
            );
        }
    }

    @Test
    void shouldRestoreTasksOnNextStartupAfterPreviousRestoreFailed()
            throws IOException {

        Path storagePath = tempDir.resolve("retry-restore.json");
        Path backupPath = tempDir.resolve("retry-restore.json.bak");

        LocalDate date = LocalDate.of(2026, 11, 18);

        // Создаём две версии данных.
        JsonTaskRepository initialRepository =
                new JsonTaskRepository(storagePath);

        initialRepository.setTask(
                date,
                "09",
                new TaskEntry("Recoverable task", TaskPriority.NORMAL)
        );

        initialRepository.setTask(
                date,
                "10",
                new TaskEntry("Newer task", TaskPriority.IMPORTANT)
        );

        String backupContent = Files.readString(backupPath);

        // Повреждаем основной файл.
        Files.writeString(storagePath, "{ invalid json");

        // Первая попытка восстановления завершается ошибкой.
        FileMover failingMover = createFailingMover(storagePath);

        assertThrows(
                TaskStorageException.class,
                () -> new JsonTaskRepository(storagePath, failingMover)
        );

        assertFalse(Files.exists(storagePath));

        // Проверяем, что резервная копия сохранилась.
        assertEquals(
                backupContent,
                Files.readString(backupPath)
        );

        // Вторая попытка: используем обычный репозиторий.
        JsonTaskRepository recoveredRepository =
                new JsonTaskRepository(storagePath);

        assertTrue(Files.exists(storagePath));

        assertEquals(
                "Recoverable task",
                recoveredRepository.getTask(date, "09").text()
        );

        assertNull(recoveredRepository.getTask(date, "10"));

        // Проверяем, что восстановленный файл можно прочитать повторно.
        JsonTaskRepository restartedRepository =
                new JsonTaskRepository(storagePath);

        assertEquals(
                "Recoverable task",
                restartedRepository.getTask(date, "09").text()
        );

        assertNull(restartedRepository.getTask(date, "10"));
    }

    @Test
    void shouldPreservePreviousStateWhenFinalFileReplacementFails()
            throws IOException {

        Path storagePath = tempDir.resolve("final-move-failure.json");

        LocalDate date = LocalDate.of(2026, 11, 18);

        TaskEntry originalTask =
                new TaskEntry("Original task", TaskPriority.NORMAL);

        TaskEntry newTask =
                new TaskEntry("New task", TaskPriority.IMPORTANT);

        // Создаём исходный JSON обычным репозиторием.
        JsonTaskRepository initialRepository =
                new JsonTaskRepository(storagePath);

        initialRepository.setTask(date, "09", originalTask);

        String originalContent = Files.readString(storagePath);

        // Имитируем сбой только при финальной замене основного файла.
        FileMover failingMover = createFailingMover(storagePath);

        JsonTaskRepository repository =
                new JsonTaskRepository(storagePath, failingMover);

        assertThrows(
                TaskStorageException.class,
                () -> repository.setTask(date, "10", newTask)
        );

        // В памяти осталось исходное состояние.
        assertEquals(originalTask, repository.getTask(date, "09"));
        assertNull(repository.getTask(date, "10"));

        // Основной JSON не изменился.
        assertEquals(originalContent, Files.readString(storagePath));

        // Резервная копия содержит исходный JSON.
        Path backupPath = tempDir.resolve("final-move-failure.json.bak");

        assertEquals(originalContent, Files.readString(backupPath));
    }

    private FileMover createFailingMover(Path storagePath) {
        Path normalizedStoragePath = storagePath.toAbsolutePath().normalize();

        return (source, target) -> {
            if (target.equals(normalizedStoragePath)) {
                throw new IOException("Simulated final replacement failure");
            }

            Files.move(
                    source,
                    target,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
        };
    }

    private Map<String, TaskEntry> createBatch(String version) {
        Map<String, TaskEntry> batch = new LinkedHashMap<>();

        for (int hour = 0; hour < TASK_COUNT; hour++) {
            batch.put(
                    "%02d".formatted(hour),
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