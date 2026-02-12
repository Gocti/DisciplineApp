package app;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatSystemProperties;

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

    // ===================== LOOK & FEEL =====================
    public static void applyLookAndFeel() {
        try {
            ThemeMode mode = getThemeMode();

            switch (mode) {
                case DARK -> FlatDarkLaf.setup();
                case LIGHT -> FlatLightLaf.setup();
                case SYSTEM -> {
                    boolean dark = FlatSystemProperties.getBoolean(
                            "ui.dark.mode",
                            false
                    );
                    if (dark) FlatDarkLaf.setup();
                    else FlatLightLaf.setup();
                }
            }

            UIManager.put("Component.scaleFactor", getScale());
            UIManager.put("@accentColor", getAccentColor());
            UIManager.put("defaultFont", getFontPref());

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

