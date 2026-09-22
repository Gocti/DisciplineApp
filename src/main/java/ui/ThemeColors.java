package ui;

import java.awt.*;

/**
 * Общие цветовые константы для всех UI-компонентов.
 * Убирает дублирование между CalendarPanel и SettingsPanel.
 */
public final class ThemeColors {

    private ThemeColors() {}

    // ===== Dark theme =====
    public static final Color DARK_BG = new Color(45, 45, 48);
    public static final Color DARK_HEADER_BG = new Color(60, 63, 65);
    public static final Color DARK_GRID = new Color(70, 130, 180);

    // ===== Light theme =====
    public static final Color LIGHT_BG = Color.WHITE;
    public static final Color LIGHT_HEADER_BG = new Color(230, 230, 230);
    public static final Color LIGHT_GRID = new Color(180, 180, 180);

    // ===== Task states (light) =====
    public static final Color LIGHT_DONE_COLOR = new Color(100, 170, 100);
    public static final Color LIGHT_IMPORTANT_COLOR = new Color(150, 120, 190);

    // ===== Task states (dark) =====
    public static final Color DARK_DONE_COLOR = new Color(80, 200, 80);
    public static final Color DARK_IMPORTANT_COLOR = new Color(190, 140, 230);

    // ===== Foreground =====
    public static final Color DARK_FG = Color.WHITE;
    public static final Color LIGHT_FG = Color.BLACK;
}
