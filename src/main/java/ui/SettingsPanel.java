package ui;

import app.MainApp;
import app.ThemeMode;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class SettingsPanel extends JPanel {

    private Color bg;
    private Color fg;

    private final JPanel themePanel;

    public SettingsPanel(CalendarPanel calendar, MainFrame mainFrame) {

        applyTheme();

        setLayout(new BorderLayout(12, 12));
        setBackground(bg);
        setForeground(fg);

        // ================= THEME =================

        var light = new JRadioButton("Светлая тема");
        var dark = new JRadioButton("Тёмная тема");
        var system = new JRadioButton("Тема системы");

        light.setBackground(bg);
        dark.setBackground(bg);
        system.setBackground(bg);

        light.setForeground(fg);
        dark.setForeground(fg);
        system.setForeground(fg);

        var themeGroup = new ButtonGroup();
        themeGroup.add(light);
        themeGroup.add(dark);
        themeGroup.add(system);

        switch (MainApp.getThemeMode()) {
            case LIGHT -> light.setSelected(true);
            case DARK -> dark.setSelected(true);
            case SYSTEM -> system.setSelected(true);
        }

        ActionListener themeListener = _ -> {

            if (light.isSelected()) {
                MainApp.setThemeMode(ThemeMode.LIGHT);
            } else if (dark.isSelected()) {
                MainApp.setThemeMode(ThemeMode.DARK);
            } else {
                MainApp.setThemeMode(ThemeMode.SYSTEM);
            }

            mainFrame.refreshTheme();
        };

        light.addActionListener(themeListener);
        dark.addActionListener(themeListener);
        system.addActionListener(themeListener);

        // ================= CALENDAR =================

        var compactCalendar = new JCheckBox("Компактная таблица календаря");

        compactCalendar.setBackground(bg);
        compactCalendar.setForeground(fg);

        compactCalendar.addActionListener(
                _ -> calendar.setCompactView(
                        compactCalendar.isSelected()
                )
        );

        // ================= FONT =================

        var currentFont = MainApp.getFontPref();

        var availableFonts =
                GraphicsEnvironment
                        .getLocalGraphicsEnvironment()
                        .getAvailableFontFamilyNames();

        var fontBox = new JComboBox<>(availableFonts);

        fontBox.setBackground(bg);
        fontBox.setForeground(fg);
        fontBox.setSelectedItem(currentFont.getFamily());

        var fontSize = new JSpinner(
                new SpinnerNumberModel(
                        currentFont.getSize(),
                        10,
                        28,
                        1
                )
        );

        fontSize.setBackground(bg);
        fontSize.setForeground(fg);

        var applyFontBtn =
                createApplyFontButton(
                        fontBox,
                        fontSize
                );

        // ================= RIGHT PANEL =================

        themePanel =
                new JPanel(
                        new GridLayout(
                                0,
                                1,
                                6,
                                6
                        )
                );

        themePanel.setBackground(bg);
        themePanel.setForeground(fg);

        updateThemePanelBorder();

        themePanel.add(light);
        themePanel.add(dark);
        themePanel.add(system);

        themePanel.add(
                Box.createVerticalStrut(8)
        );

        themePanel.add(compactCalendar);

        var fontLabel = new JLabel("Шрифт:");

        fontLabel.setBackground(bg);
        fontLabel.setForeground(fg);

        themePanel.add(fontLabel);
        themePanel.add(fontBox);

        var sizeLabel = new JLabel("Размер:");

        sizeLabel.setBackground(bg);
        sizeLabel.setForeground(fg);

        themePanel.add(sizeLabel);
        themePanel.add(fontSize);
        themePanel.add(applyFontBtn);

        // ================= LAYOUT =================

        add(
                themePanel,
                BorderLayout.CENTER
        );
    }

    // ================= THEME =================

    public void refreshTheme() {

        var dark = MainApp.isCurrentThemeDark();

        bg = dark
                ? ThemeColors.DARK_BG
                : ThemeColors.LIGHT_BG;

        fg = dark
                ? ThemeColors.DARK_FG
                : ThemeColors.LIGHT_FG;

        setBackground(bg);
        setForeground(fg);

        ComponentUtils.updateColorsIteratively(
                this,
                bg,
                fg
        );

        updateThemePanelBorder();

        revalidate();
        repaint();
    }

    private void applyTheme() {

        var dark = MainApp.isCurrentThemeDark();

        bg = dark
                ? ThemeColors.DARK_BG
                : ThemeColors.LIGHT_BG;

        fg = dark
                ? ThemeColors.DARK_FG
                : ThemeColors.LIGHT_FG;

        setBackground(bg);
        setForeground(fg);
    }

    private void updateThemePanelBorder() {

        if (themePanel == null) {
            return;
        }

        var titledBorder =
                BorderFactory.createTitledBorder(
                        "Оформление"
                );

        titledBorder.setTitleColor(fg);

        themePanel.setBorder(titledBorder);
    }

    // ================= FONT =================

    private JButton createApplyFontButton(
            JComboBox<String> fontBox,
            JSpinner fontSize
    ) {

        var btn = new JButton(
                "Применить шрифт"
        );

        btn.setBackground(bg);
        btn.setForeground(fg);

        btn.addActionListener(_ -> {

            var selectedName =
                    (String) fontBox.getSelectedItem();

            if (selectedName == null
                    || selectedName.isBlank()) {

                JOptionPane.showMessageDialog(
                        this,
                        "Шрифт не выбран",
                        "Ошибка",
                        JOptionPane.WARNING_MESSAGE
                );

                return;
            }

            var size =
                    (int) fontSize.getValue();

            var font =
                    new Font(
                            selectedName,
                            Font.PLAIN,
                            size
                    );

            // Применяем шрифт по умолчанию
            // для новых компонентов.
            UIManager.put(
                    "defaultFont",
                    font
            );

            MainApp.setFont(font);

            // Применяем шрифт к уже созданному окну.
            var window =
                    SwingUtilities.getWindowAncestor(
                            this
                    );

            if (window != null) {

                ComponentUtils.updateFontIteratively(
                        window,
                        font
                );

                window.revalidate();
                window.repaint();
            }
        });

        return btn;
    }
}
