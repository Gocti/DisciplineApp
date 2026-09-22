package app;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import tools.jackson.databind.ObjectMapper;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

public final class MainApp {

    private static final Logger LOG =
            Logger.getLogger(MainApp.class.getName());

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .build();

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Preferences PREFS =
            Preferences.userRoot().node("DisciplineApp");

    private static final String KEY_THEME = "themeMode";
    private static final String KEY_SCALE = "uiScale";
    private static final String KEY_ACCENT = "accentColor";
    private static final String KEY_FONT_NAME = "fontName";
    private static final String KEY_FONT_SIZE = "fontSize";
    private static final String CURRENT_VERSION = "1.0.0";

    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/Gocti/DisciplineApp/releases/latest";

    private static final Duration UPDATE_CHECK_DELAY = Duration.ofSeconds(2);
    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(5);

    private MainApp() {}

    // ===================== ENTRY POINT =====================
    static void main() {
        SwingUtilities.invokeLater(() -> {
            applyLookAndFeel();

            var frame = new ui.MainFrame();
            frame.setVisible(true);

            CompletableFuture.delayedExecutor(UPDATE_CHECK_DELAY.toSeconds(), TimeUnit.SECONDS).execute(
                    () -> checkForUpdatesAsync(frame)
            );
        });
    }

    /**
     * Асинхронная проверка обновлений — выполняется в ForkJoinPool.
     */
    private static void checkForUpdatesAsync(JFrame frame) {
        CompletableFuture.supplyAsync(MainApp::getLatestVersionFromGitHub)
                .thenAccept(latestVersion -> {
                    if (latestVersion == null) return;

                    var currentVersion = getCurrentVersion();
                    if (isNewerVersion(latestVersion, currentVersion)) {
                        SwingUtilities.invokeLater(() -> {
                            var result = JOptionPane.showConfirmDialog(
                                frame,
                                "Доступна новая версия: " + latestVersion + "\n" +
                                "Текущая версия: " + currentVersion + "\n\n" +
                                "Обновить приложение?",
                                "Обновление доступно",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.INFORMATION_MESSAGE
                            );

                            if (result == JOptionPane.YES_OPTION) {
                                updater.UpdateManager.updateAsync(frame);
                            }
                        });
                    }
                })
                .exceptionally(e -> {
                    LOG.log(Level.WARNING, "Ошибка проверки обновлений", e);
                    return null;
                });
    }

    /**
     * Получает последнюю версию приложения из GitHub API через Jackson.
     */
    private static String getLatestVersionFromGitHub() {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_URL))
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "DisciplineApp")
                    .GET()
                    .build();

            var response = HTTP_CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 200) {
                var root = JSON.readTree(response.body());
                var tagNode = root.get("tag_name");

                if (tagNode != null) {
                    @SuppressWarnings("deprecation")
                    var text = tagNode.asText();

                    if (text != null && !text.isEmpty()) {
                        return normalizeVersion(text);
                    }
                }

                LOG.warning("Поле tag_name не найдено в ответе GitHub");
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception _) {
            return null;
        }

        return null;
    }

    // ===================== THEME MODE =====================
    public static ThemeMode getThemeMode() {
        try {
            return ThemeMode.valueOf(
                    PREFS.get(KEY_THEME, ThemeMode.SYSTEM.name())
            );
        } catch (Exception _) {
            return ThemeMode.SYSTEM;
        }
    }

    public static void setThemeMode(ThemeMode mode) {
        PREFS.put(KEY_THEME, mode.name());
    }

    // ===================== SYSTEM DARK MODE =====================
    private static boolean isSystemDarkMode() {
        if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
            return false;
        }
        Process process = null;
        try {
            var systemRoot = System.getenv("SystemRoot");
            if (systemRoot == null || systemRoot.isBlank()) {
                return false;
            }

            var processBuilder = new ProcessBuilder(
                    systemRoot + "\\System32\\reg.exe",
                    "query",
                    "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v", "AppsUseLightTheme"
            );

            process = processBuilder.start();

            var finished = process.waitFor(PROCESS_TIMEOUT);

            if (!finished) {
                process.destroyForcibly();
                return false;
            }

            try (var is = process.getInputStream()) {
                var output = new String(
                        is.readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8
                );

                for (var line : output.split("\r?\n")) {
                    if (line.contains("AppsUseLightTheme")) {
                        return line.trim().endsWith("0x0");
                    }
                }
            }

            return false;

        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception _) {
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /** Публичный метод для проверки системной темы */
    public static boolean isCurrentThemeDark() {
        var mode = getThemeMode();
        return switch (mode) {
            case DARK -> true;
            case LIGHT -> false;
            case SYSTEM -> isSystemDarkMode();
        };
    }

    // ===================== LOOK & FEEL =====================
    public static void applyLookAndFeel() {
        try {
            var mode = getThemeMode();

            switch (mode) {
                case DARK -> FlatDarkLaf.setup();
                case LIGHT -> FlatLightLaf.setup();
                case SYSTEM -> {
                    if (isSystemDarkMode()) {
                        FlatDarkLaf.setup();
                    } else {
                        FlatLightLaf.setup();
                    }
                }
            }

            var scale = Math.clamp(getScale(), 0.5, 2.0);
            UIManager.put("Component.scaleFactor", scale);
            UIManager.put("@accentColor", getAccentColor());
            UIManager.put("defaultFont", getFontPref());

            for (Window w : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(w);
            }

        } catch (Exception _) {
            FlatLightLaf.setup();
        }
    }

    private static boolean isNewerVersion(String latestVersion, String currentVersion) {
        var latestParts = parseVersion(normalizeVersion(latestVersion));
        var currentParts = parseVersion(normalizeVersion(currentVersion));
        var length = Math.max(latestParts.length, currentParts.length);

        for (int i = 0; i < length; i++) {
            var latest = i < latestParts.length ? latestParts[i] : 0;
            var current = i < currentParts.length ? currentParts[i] : 0;
            if (latest != current) {
                return latest > current;
            }
        }
        return false;
    }

    private static int[] parseVersion(String version) {
        return Arrays.stream(version.split("\\."))
                .mapToInt(part -> {
                    try {
                        return Integer.parseInt(part);
                    } catch (NumberFormatException _) {
                        return 0;
                    }
                })
                .toArray();
    }

    private static String normalizeVersion(String version) {
        if (version == null) {
            return CURRENT_VERSION;
        }
        var normalized = version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        return normalized.isEmpty() ? CURRENT_VERSION : normalized;
    }

    // ===================== SCALE =====================
    public static double getScale() {
        return PREFS.getDouble(KEY_SCALE, 1.0);
    }

    public static void setScale(double scale) {
        // Валидация при сохранении
        PREFS.putDouble(KEY_SCALE, Math.clamp(scale, 0.5, 2.0));
    }

    // ===================== ACCENT COLOR =====================
    public static Color getAccentColor() {
        return new Color(
                PREFS.getInt(KEY_ACCENT, new Color(0x4A90E2).getRGB())
        );
    }

    public static void setAccentColor(Color c) {
        PREFS.putInt(KEY_ACCENT, c.getRGB());
    }

    // ===================== FONT =====================
    public static void setFont(Font f) {
        PREFS.put(KEY_FONT_NAME, f.getFamily());
        PREFS.putInt(KEY_FONT_SIZE, f.getSize());
    }

    public static Font getFontPref() {
        var name = PREFS.get(KEY_FONT_NAME, "Arial");
        var size = PREFS.getInt(KEY_FONT_SIZE, 14);
        return new Font(name, Font.PLAIN, size);
    }

    // ===================== VERSION =====================
    public static String getCurrentVersion() {
        var packageVersion = MainApp.class.getPackage().getImplementationVersion();
        return normalizeVersion(packageVersion);
    }


}
