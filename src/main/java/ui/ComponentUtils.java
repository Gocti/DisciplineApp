package ui;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayDeque;
import java.util.Collections;

/**
 * Утилиты для рекурсивного обхода Swing-компонентов.
 * Заменяет дублирующиеся методы в MainFrame, CalendarPanel, SettingsPanel.
 */
public final class ComponentUtils {

    private ComponentUtils() {}

    /**
     * Итеративно обновляет цвета всех компонентов.
     * Пропускает JTable, JList, JProgressBar — у них собственные цвета.
     */
    public static void updateColorsIteratively(Component root, Color bg, Color fg) {
        var queue = new ArrayDeque<Component>();
        queue.add(root);
        while (!queue.isEmpty()) {
            var c = queue.remove();
            // Пропускаем компоненты с кастомными цветами
            if (c instanceof JTable || c instanceof JList<?> || c instanceof JProgressBar) {
                continue;
            }
            if (c instanceof JComponent) {
                c.setBackground(bg);
                c.setForeground(fg);
            }
            if (c instanceof Container cont) {
                Collections.addAll(queue, cont.getComponents());
            }
        }
    }

    /**
     * Итеративно обновляет шрифт всех компонентов.
     */
    public static void updateFontIteratively(Component root, Font f) {
        var queue = new ArrayDeque<Component>();
        queue.add(root);
        while (!queue.isEmpty()) {
            var c = queue.remove();
            c.setFont(f);
            if (c instanceof Container cont) {
                Collections.addAll(queue, cont.getComponents());
            }
        }
    }
}
