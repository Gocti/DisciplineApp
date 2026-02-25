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

    private MainApp() {}

    // ===================== ENTRY POINT =====================
    static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            applyLookAndFeel();
            new ui.MainFrame().setVisible(true);
        });
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
}

