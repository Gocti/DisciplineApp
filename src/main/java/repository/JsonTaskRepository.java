package repository;

import model.TaskEntry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JsonTaskRepository implements TaskRepository {

    private static final Logger LOG =
            Logger.getLogger(JsonTaskRepository.class.getName());

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Path DEFAULT_STORAGE_PATH = Path.of(
            System.getProperty("user.home"),
            ".discipline-app",
            "tasks.json"
    );

    private final Map<LocalDate, Map<String, TaskEntry>> tasks =
            new ConcurrentHashMap<>();

    private final Path storagePath;

    public JsonTaskRepository() {
        this(DEFAULT_STORAGE_PATH);
    }

    public JsonTaskRepository(Path storagePath) {
        this.storagePath = storagePath;
        loadFromDisk();
    }

    @Override
    public Map<String, TaskEntry> getTasksForDate(LocalDate date) {
        var dayTasks = tasks.get(date);

        if (dayTasks == null) {
            return Collections.emptyMap();
        }

        return Collections.unmodifiableMap(dayTasks);
    }

    @Override
    public TaskEntry getTask(LocalDate date, String key) {
        if (date == null || key == null) {
            return null;
        }

        return tasks
                .getOrDefault(date, Collections.emptyMap())
                .get(key);
    }

    @Override
    public void setTask(
            LocalDate date,
            String key,
            TaskEntry entry
    ) {
        if (date == null || key == null || entry == null) {
            throw new IllegalArgumentException(
                    "Arguments cannot be null"
            );
        }

        tasks
                .computeIfAbsent(
                        date,
                        ignored -> new ConcurrentHashMap<>()
                )
                .put(key, entry);

        saveToDisk();
    }

    @Override
    public void removeTask(LocalDate date, String key) {
        if (date == null || key == null) {
            return;
        }

        var map = tasks.get(date);

        if (map != null) {
            map.remove(key);

            if (map.isEmpty()) {
                tasks.remove(date, map);
            }

            saveToDisk();
        }
    }

    //подавление предупреждений SonarQube
    private void logLoadError(Exception e) {
        var message = "Не удалось загрузить задачи из {0}";

        var logRecord = new java.util.logging.LogRecord(
                Level.WARNING,
                message
        );

        logRecord.setParameters(new Object[]{storagePath});
        logRecord.setThrown(e);

        LOG.log(logRecord);
    }

    private void loadFromDisk() {
        if (!Files.exists(storagePath)) {
            return;
        }

        try {
            Map<String, Map<String, TaskEntry>> storedTasks =
                    JSON.readValue(
                            storagePath.toFile(),
                            new TypeReference<>() {}
                    );

            tasks.clear();

            for (var dateEntry : storedTasks.entrySet()) {
                tasks.put(
                        LocalDate.parse(dateEntry.getKey()),
                        new ConcurrentHashMap<>(
                                dateEntry.getValue()
                        )
                );
            }

        } catch (Exception e) {
            logLoadError(e);
        }
    }

    //подавление предупреждений SonarQube
    private void logSaveError(IOException e) {
        var logRecord = new java.util.logging.LogRecord(
                Level.WARNING,
                "Не удалось сохранить задачи в {0}"
        );

        logRecord.setParameters(new Object[]{storagePath});
        logRecord.setThrown(e);

        LOG.log(logRecord);
    }

    private void saveToDisk() {
        try {
            var parent = storagePath.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            var storedTasks =
                    new LinkedHashMap<String, Map<String, TaskEntry>>();

            tasks.entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry ->
                            storedTasks.put(
                                    entry.getKey().toString(),
                                    new LinkedHashMap<>(entry.getValue())
                            )
                    );

            JSON.writerWithDefaultPrettyPrinter()
                    .writeValue(
                            storagePath.toFile(),
                            storedTasks
                    );

        } catch (IOException e) {
            logSaveError(e);
        }
    }
}
