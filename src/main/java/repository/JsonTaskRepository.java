package repository;

import model.TaskEntry;
import model.TaskPriority;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.DeserializationFeature;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JSON-репозиторий задач с атомарной записью и резервной копией.
 * Изменение сначала сохраняется на диск и только затем становится видимым
 * в памяти. Операции записи синхронизированы, чтение выполняется
 * без блокировки на основе опубликованного снимка данных.
 */
public final class JsonTaskRepository implements TaskRepository {

    private static final Logger LOG =
            Logger.getLogger(JsonTaskRepository.class.getName());
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .withCoercionConfig(String.class, config -> config
                    .setCoercion(
                            CoercionInputShape.Integer,
                            CoercionAction.Fail
                    )
                    .setCoercion(
                            CoercionInputShape.Float,
                            CoercionAction.Fail
                    )
                    .setCoercion(
                            CoercionInputShape.Boolean,
                            CoercionAction.Fail
                    )
            )
            .withCoercionConfig(TaskPriority.class, config -> config
                    .setCoercion(
                            CoercionInputShape.Integer,
                            CoercionAction.Fail
                    )
                    .setCoercion(
                            CoercionInputShape.Float,
                            CoercionAction.Fail
                    )
            )
            .build();
    private static final TypeReference<Map<String, Map<String, TaskEntry>>> DATA_TYPE =
            new TypeReference<>() {};

    private static final int MIN_HOUR = 0;
    private static final int MAX_HOUR = 23;
    private static final int HOUR_KEY_LENGTH = 2;

    private static final Path DEFAULT_STORAGE_PATH = Path.of(
            System.getProperty("user.home"), ".discipline-app", "tasks.json"
    );

    private final Path storagePath;
    private final Path backupPath;

    @SuppressWarnings("java:S3077")
    private volatile Map<LocalDate, Map<String, TaskEntry>> tasks = Map.of();

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

    private Map<LocalDate, Map<String, TaskEntry>> immutableSnapshot(
            Map<LocalDate, Map<String, TaskEntry>> source
    ) {
        Map<LocalDate, Map<String, TaskEntry>> snapshot = new HashMap<>();

        source.forEach((date, dayTasks) ->
                snapshot.put(date, Map.copyOf(dayTasks))
        );

        return Map.copyOf(snapshot);
    }
    private void validateHourKey(String key) {
        if (key == null
                || key.length() != HOUR_KEY_LENGTH
                || key.charAt(0) < '0'
                || key.charAt(0) > '2'
                || key.charAt(1) < '0'
                || key.charAt(1) > '9') {
            throw new IllegalArgumentException(
                    "Invalid task hour key: " + key
            );
        }

        int hour = Integer.parseInt(key);

        if (hour < MIN_HOUR || hour > MAX_HOUR) {
            throw new IllegalArgumentException(
                    "Task hour must be between 00 and 23: " + key
            );
        }
    }

    @Override
    public Map<String, TaskEntry> getTasksForDate(LocalDate date) {
        Objects.requireNonNull(date, "date");

        Map<LocalDate, Map<String, TaskEntry>> snapshot = tasks;
        Map<String, TaskEntry> dayTasks = snapshot.get(date);

        return dayTasks == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(dayTasks));
    }

    @Override
    public TaskEntry getTask(LocalDate date, String key) {
        if (date == null || key == null) {
            return null;
        }

        Map<LocalDate, Map<String, TaskEntry>> snapshot = tasks;

        return snapshot
                .getOrDefault(date, Collections.emptyMap())
                .get(key);
    }


    @Override
    public synchronized void setTask(
            LocalDate date,
            String key,
            TaskEntry entry
    ) {
        setTasks(date, Map.of(key, entry));
    }

    @Override
    public synchronized void setTasks(
            LocalDate date,
            Map<String, TaskEntry> entries
    ) {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(entries, "entries");

        if (entries.isEmpty()) {
            return;
        }

        Map<LocalDate, Map<String, TaskEntry>> candidate = copyTasks(tasks);

        Map<String, TaskEntry> dayTasks =
                candidate.computeIfAbsent(date, ignored -> new HashMap<>());

        entries.forEach((key, entry) -> {
            validateHourKey(key);
            Objects.requireNonNull(entry, "entry");
            dayTasks.put(key, entry);
        });

        persist(candidate);
        tasks = immutableSnapshot(candidate);
    }



    @Override
    public synchronized void removeTask(LocalDate date, String key) {
        if (date == null || key == null) {
            return;
        }

        removeTasks(date, java.util.Set.of(key));
    }

    @Override
    public synchronized void removeTasks(LocalDate date, Set<String> keys) {
        if (date == null || keys == null || keys.isEmpty()) {
            return;
        }

        keys.forEach(this::validateHourKey);

        Map<String, TaskEntry> currentDay = tasks.get(date);
        if (currentDay == null || keys.stream().noneMatch(currentDay::containsKey)) {
            return;
        }

        Map<LocalDate, Map<String, TaskEntry>> candidate = copyTasks(tasks);
        Map<String, TaskEntry> candidateDay = candidate.get(date);

        keys.forEach(candidateDay::remove);

        if (candidateDay.isEmpty()) {
            candidate.remove(date);
        }

        persist(candidate);
        tasks = immutableSnapshot(candidate);
    }

    private void loadFromDisk() {
        if (!Files.exists(storagePath)) {
            if (Files.exists(backupPath)) recoverFromBackup(null);
            return;
        }
        try {
            tasks = immutableSnapshot(readTasks(storagePath));
        } catch (Exception primaryFailure) {
            LOG.log(
                    Level.SEVERE,
                    "Не удалось прочитать основной файл задач: " + storagePath,
                    primaryFailure
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

            tasks = immutableSnapshot(recovered);
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
            validateHourKey(taskEntry.getKey());

            if (taskEntry.getValue() == null) {
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
