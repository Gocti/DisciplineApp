package security;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class ProgramBlocker {

    private static final Path CONFIG_FILE =
            Paths.get(System.getProperty("user.home"),
                    ".disciplineapp", "blocked-programs.json");

    private static final ScheduledExecutorService EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ProgramBlocker");
                t.setDaemon(true);
                return t;
            });

    private static volatile boolean running = false;

    private ProgramBlocker() {}

    // ================= START / STOP =================

    public static synchronized void start() {
        if (running) return;
        running = true;

        EXECUTOR.scheduleAtFixedRate(
                ProgramBlocker::killBlockedPrograms,
                0,
                5,
                TimeUnit.SECONDS
        );
    }

    public static synchronized void stop() {
        running = false;
        EXECUTOR.shutdownNow();
    }

    // ================= BLOCK LOGIC =================

    private static void killBlockedPrograms() {
        if (!running) return;

        for (String process : getBlockedPrograms()) {
            try {
                new ProcessBuilder(
                        "taskkill",
                        "/IM", process,
                        "/F"
                ).start();
            } catch (IOException ignored) {}
        }
    }

    // ================= STORAGE =================

    private static final ObjectMapper JSON = new ObjectMapper();
    private static List<String> cachedPrograms = null;

    public static List<String> getBlockedPrograms() {
        if (cachedPrograms != null) return cachedPrograms;

        if (!Files.exists(CONFIG_FILE)) {
            cachedPrograms = defaultPrograms();
            saveToJson(cachedPrograms); // сразу создаём JSON
            return cachedPrograms;
        }

        try {
            cachedPrograms = Arrays.asList(JSON.readValue(CONFIG_FILE.toFile(), String[].class));
            return cachedPrograms;
        } catch (IOException e) {
            cachedPrograms = defaultPrograms();
            return cachedPrograms;
        }
    }

    public static void setBlockedPrograms(List<String> programs) {
        cachedPrograms = new ArrayList<>(programs);
        saveToJson(cachedPrograms);
    }

    public static void saveToJson(List<String> programs) {
        try {
            Path configFile = CONFIG_FILE;
            if (Files.notExists(configFile.getParent())) {
                Files.createDirectories(configFile.getParent());
            }
            JSON.writeValue(configFile.toFile(), programs);
        } catch (IOException e) {
            // Логируем через стандартный Logger
            java.util.logging.Logger logger = java.util.logging.Logger.getLogger(ProgramBlocker.class.getName());
            logger.severe("Ошибка при сохранении blocked-programs.json: " + e.getMessage());
            logger.severe(Arrays.toString(e.getStackTrace()));

            // Опционально: показываем пользователю диалог
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(
                            null,
                            "Не удалось сохранить список блокировки программ:\n" + e.getMessage(),
                            "Ошибка",
                            JOptionPane.ERROR_MESSAGE
                    )
            );
        }
    }



    private static List<String> defaultPrograms() {
        return List.of(
                "steam.exe",
                "discord.exe",
                "chrome.exe"
        );
    }
}


