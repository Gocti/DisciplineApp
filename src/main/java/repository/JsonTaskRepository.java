package repository;

import model.TaskEntry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JSON-репозиторий задач с атомарной записью и резервной копией.
 * Изменение сначала сохраняется на диск и только затем становится видимым
 * в памяти. Все операции синхронизированы на уровне экземпляра.
 */
public final class JsonTaskRepository implements TaskRepository {

    private static final Logger LOG =
            Logger.getLogger(JsonTaskRepository.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Map<String, TaskEntry>>> DATA_TYPE =
            new TypeReference<>() {};

    private static final Path DEFAULT_STORAGE_PATH = Path.of(
            System.getProperty("user.home"), ".discipline-app", "tasks.json"
    );

    private final Path storagePath;
    private final Path backupPath;
    private Map<LocalDate, Map<String, TaskEntry>> tasks = new HashMap<>();

    public JsonTaskRepository() {
        this(DEFAULT_STORAGE_PATH);
    }

    public JsonTaskRepository(Path storagePath) {
        this.storagePath = Objects.requireNonNull(storagePath, "storagePath")
                .toAbsolutePath().normalize();
        this.backupPath = this.storagePath.resolveSibling(
                this.storagePath.getFileName() + ".bak"
        );
        loadFromDisk();
    }

    @Override
    public synchronized Map<String, TaskEntry> getTasksForDate(LocalDate date) {
        Objects.requireNonNull(date, "date");
        Map<String, TaskEntry> dayTasks = tasks.get(date);
        return dayTasks == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(dayTasks));
    }

    @Override
    public synchronized TaskEntry getTask(LocalDate date, String key) {
        if (date == null || key == null) return null;
        return tasks.getOrDefault(date, Collections.emptyMap()).get(key);
    }

    @Override
    public synchronized void setTask(LocalDate date, String key, TaskEntry entry) {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(entry, "entry");

        Map<LocalDate, Map<String, TaskEntry>> candidate = copyTasks(tasks);
        candidate.computeIfAbsent(date, ignored -> new HashMap<>()).put(key, entry);
        persist(candidate);
        tasks = candidate;
    }

    @Override
    public synchronized void removeTask(LocalDate date, String key) {
        if (date == null || key == null) return;
        Map<String, TaskEntry> currentDay = tasks.get(date);
        if (currentDay == null || !currentDay.containsKey(key)) return;

        Map<LocalDate, Map<String, TaskEntry>> candidate = copyTasks(tasks);
        Map<String, TaskEntry> candidateDay = candidate.get(date);
        candidateDay.remove(key);
        if (candidateDay.isEmpty()) candidate.remove(date);

        persist(candidate);
        tasks = candidate;
    }

    private void loadFromDisk() {
        if (!Files.exists(storagePath)) {
            if (Files.exists(backupPath)) recoverFromBackup(null);
            return;
        }
        try {
            tasks = readTasks(storagePath);
        } catch (Exception primaryFailure) {
            LOG.log(Level.SEVERE,
                    () -> "Не удалось прочитать основной файл задач: " + storagePath
            );
            recoverFromBackup(primaryFailure);
        }
    }

    private void recoverFromBackup(Exception primaryFailure) {
        if (!Files.exists(backupPath)) {
            throw new TaskStorageException(
                    "Основной файл задач повреждён или недоступен, резервная копия отсутствует: "
                            + storagePath,
                    primaryFailure
            );
        }

        try {
            Map<LocalDate, Map<String, TaskEntry>> recovered = readTasks(backupPath);
            if (Files.exists(storagePath)) {
                Path quarantine = storagePath.resolveSibling(
                        storagePath.getFileName() + ".corrupt-" + System.currentTimeMillis()
                );
                moveReplacing(storagePath, quarantine);
            }

            Files.createDirectories(storagePath.getParent());
            Path temp = createTempFile("restore-");
            try {
                Files.copy(backupPath, temp, StandardCopyOption.REPLACE_EXISTING);
                forceFile(temp);
                moveReplacing(temp, storagePath);
            } finally {
                deleteQuietly(temp);
            }

            tasks = recovered;
            LOG.warning(() -> "Задачи восстановлены из резервной копии: " + backupPath);
        } catch (Exception backupFailure) {
            if (primaryFailure != null) backupFailure.addSuppressed(primaryFailure);
            throw new TaskStorageException(
                    "Не удалось восстановить задачи из резервной копии: " + backupPath,
                    backupFailure
            );
        }
    }

    private Map<String, TaskEntry> validateDayTasks(
            LocalDate date,
            Map<String, TaskEntry> dayTasks
    ) throws IOException {

        if (dayTasks == null) {
            throw new IOException(
                    "Список задач за %s равен null".formatted(date)
            );
        }

        Map<String, TaskEntry> validated = new HashMap<>();

        for (Map.Entry<String, TaskEntry> taskEntry : dayTasks.entrySet()) {
            if (taskEntry.getKey() == null || taskEntry.getValue() == null) {
                throw new IOException(
                        "Обнаружена пустая задача за %s".formatted(date)
                );
            }

            validated.put(taskEntry.getKey(), taskEntry.getValue());
        }

        return validated;
    }

    private Map<LocalDate, Map<String, TaskEntry>> readTasks(Path source) throws IOException {
        Map<String, Map<String, TaskEntry>> stored = JSON.readValue(source.toFile(), DATA_TYPE);
        if (stored == null) throw new IOException("JSON содержит null вместо объекта задач");

        Map<LocalDate, Map<String, TaskEntry>> result = new HashMap<>();
        for (Map.Entry<String, Map<String, TaskEntry>> dateEntry : stored.entrySet()) {
            LocalDate date;
            try {
                date = LocalDate.parse(dateEntry.getKey());
            } catch (DateTimeParseException | NullPointerException e) {
                throw new IOException("Некорректная дата в хранилище: " + dateEntry.getKey(), e);
            }

            result.put(date, validateDayTasks(date, dateEntry.getValue()));
        }
        return result;
    }

    private void persist(Map<LocalDate, Map<String, TaskEntry>> data) {
        Path temp = null;
        Path backupTemp = null;
        try {
            Files.createDirectories(storagePath.getParent());
            temp = createTempFile("tasks-");
            JSON.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), toStorageFormat(data));
            forceFile(temp);

            // Резервируем предыдущий основной файл только после успешной сериализации.
            if (Files.exists(storagePath)) {
                backupTemp = createTempFile("tasks-backup-");
                Files.copy(storagePath, backupTemp, StandardCopyOption.REPLACE_EXISTING);
                forceFile(backupTemp);
                moveReplacing(backupTemp, backupPath);
                backupTemp = null;
            }

            moveReplacing(temp, storagePath);
            temp = null;
        } catch (Exception e) {
            throw new TaskStorageException(
                    "Не удалось сохранить задачи в " + storagePath
                            + ". Изменение не подтверждено.", e
            );
        } finally {
            deleteQuietly(temp);
            deleteQuietly(backupTemp);
        }
    }

    private Map<String, Map<String, TaskEntry>> toStorageFormat(
            Map<LocalDate, Map<String, TaskEntry>> source
    ) {
        Map<String, Map<String, TaskEntry>> result = new LinkedHashMap<>();
        source.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(dateEntry -> {
            Map<String, TaskEntry> sorted = new LinkedHashMap<>();
            dateEntry.getValue().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(taskEntry -> sorted.put(taskEntry.getKey(), taskEntry.getValue()));
            result.put(dateEntry.getKey().toString(), sorted);
        });
        return result;
    }

    private Map<LocalDate, Map<String, TaskEntry>> copyTasks(
            Map<LocalDate, Map<String, TaskEntry>> source
    ) {
        Map<LocalDate, Map<String, TaskEntry>> copy = new HashMap<>();
        source.forEach((date, dayTasks) -> copy.put(date, new HashMap<>(dayTasks)));
        return copy;
    }

    private Path createTempFile(String prefix) throws IOException {
        return Files.createTempFile(storagePath.getParent(), prefix, ".tmp");
    }

    private void forceFile(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException _) {
            LOG.warning("Атомарная замена не поддерживается для " + target
                    + "; используется обычная замена.");
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException _) {
            LOG.warning("Не удалось удалить временный файл: " + path);
        }
    }
}
