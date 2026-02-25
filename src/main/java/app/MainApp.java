package app;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;
import java.util.prefs.Preferences;

public final class MainApp {

    private static final Preferences PREFS =
            Preferences.userRoot().node("DisciplineApp");

    private static final String KEY_THEME = "themeMode";
    private static final String KEY_SCALE = "uiScale";
    private static final String KEY_ACCENT = "accentColor";
    private static final String KEY_FONT_NAME = "fontName";
    private static final String KEY_FONT_SIZE = "fontSize";
    private static final String KEY_VERSION = "version";
    private static final String CURRENT_VERSION = "1.0.0";

    private MainApp() {}

    // ===================== ENTRY POINT =====================
    static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            applyLookAndFeel();
            
            // Создаём главное окно
            ui.MainFrame frame = new ui.MainFrame();
            frame.setVisible(true);
            
            // Проверяем обновления через 2 секунды после запуска
            Timer checkUpdateTimer = new Timer(2000, e -> checkForUpdates(frame));
            checkUpdateTimer.setRepeats(false);
            checkUpdateTimer.start();
        });
    }

    /**
     * Проверяет наличие обновлений
     */
    private static void checkForUpdates(JFrame frame) {
        try {
            String savedVersion = PREFS.get(KEY_VERSION, CURRENT_VERSION);
            
            // Получаем последнюю версию из GitHub API
            String latestVersion = getLatestVersionFromGitHub();
            
            if (latestVersion != null && !latestVersion.equals(savedVersion)) {
                int result = JOptionPane.showConfirmDialog(
                    frame,
                    "Доступна новая версия: " + latestVersion + "\n" +
                    "Текущая версия: " + savedVersion + "\n\n" +
                    "Обновить приложение?",
                    "Обновление доступно",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.INFORMATION_MESSAGE
                );
                
                if (result == JOptionPane.YES_OPTION) {
                    String installerUrl = "https://github.com/Gocti/DisciplineApp/releases/latest/download/DisciplineApp-installer.exe";
                    updater.UpdateManager.update(installerUrl);
                }
            }
        } catch (Exception e) {
            // Тихо игнорируем ошибки проверки обновлений
        }
    }
    
    /**
     * Получает последнюю версию приложения из GitHub API
     */
    private static String getLatestVersionFromGitHub() {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .build();
            
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://api.github.com/repos/Gocti/DisciplineApp/releases/latest"))
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "DisciplineApp")
                    .GET()
                    .build();
            
            java.net.http.HttpResponse<String> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String json = response.body();
                // Парсим JSON вручную или через простой поиск
                int tagStart = json.indexOf("\"tag_name\":\"");
                if (tagStart >= 0) {
                    tagStart += 12;
                    int tagEnd = json.indexOf("\"", tagStart);
                    if (tagEnd > tagStart) {
                        return json.substring(tagStart, tagEnd);
                    }
                }
            }
        } catch (Exception e) {
            // Игнорируем ошибки
        }
        return null;
    }

    // ===================== THEME MODE =====================
    public static ThemeMode getThemeMode() {
        try {
            return ThemeMode.valueOf(
                    PREFS.get(KEY_THEME, ThemeMode.SYSTEM.name())
            );
        } catch (Exception e) {
            return ThemeMode.SYSTEM;
        }
    }

    public static void setThemeMode(ThemeMode mode) {
        PREFS.put(KEY_THEME, mode.name());
    }

    // ===================== SYSTEM DARK MODE =====================
    private static boolean isSystemDarkMode() {
        try {
            // Windows 10/11 - читаем из реестра
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                ProcessBuilder processBuilder = new ProcessBuilder(
                    "reg", "query",
                    "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v", "AppsUseLightTheme"
                );
                Process process = processBuilder.start();
                
                java.util.Scanner scanner = new java.util.Scanner(process.getInputStream());
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.contains("AppsUseLightTheme")) {
                        // 0 = тёмная тема, 1 = светлая тема
                        return line.trim().endsWith("0x0");
                    }
                }
            }
            return false;
        } catch (Exception e) {
            // По умолчанию светлая тема
            return false;
        }
    }
    
    /** Публичный метод для проверки системной темы */
    public static boolean isCurrentThemeDark() {
        ThemeMode mode = getThemeMode();
        return switch (mode) {
            case DARK -> true;
            case LIGHT -> false;
            case SYSTEM -> isSystemDarkMode();
        };
    }

    // ===================== LOOK & FEEL =====================
    public static void applyLookAndFeel() {
        applyLookAndFeel(false);
    }
    
    public static void applyLookAndFeel(boolean refreshWindows) {
        try {
            ThemeMode mode = getThemeMode();

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

            // Применяем настройки после установки LaF
            UIManager.put("Component.scaleFactor", getScale());
            UIManager.put("@accentColor", getAccentColor());
            UIManager.put("defaultFont", getFontPref());
            
            // Обновляем все открытые окна без перезагрузки
            for (Window w : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(w);
            }

        } catch (Exception e) {
            FlatLightLaf.setup();
        }
    }

    // ===================== SCALE =====================
    public static double getScale() {
        return PREFS.getDouble(KEY_SCALE, 1.0);
    }

    public static void setScale(double scale) {
        PREFS.putDouble(KEY_SCALE, scale);
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
        String name = PREFS.get(KEY_FONT_NAME, "Arial");
        int size = PREFS.getInt(KEY_FONT_SIZE, 14);
        return new Font(name, Font.PLAIN, size);
    }

    // ===================== VERSION =====================
    public static String getCurrentVersion() {
        return PREFS.get(KEY_VERSION, CURRENT_VERSION);
    }

    public static void setVersion(String version) {
        PREFS.put(KEY_VERSION, version);
    }
}

