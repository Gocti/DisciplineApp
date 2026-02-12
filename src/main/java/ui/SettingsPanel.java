package ui;

import app.MainApp;
import app.ThemeMode;
import security.ProgramBlocker;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Collections;
import java.util.List;

public class SettingsPanel extends JPanel {

    private Color bg;
    private Color fg;

    public SettingsPanel(JFrame parent, CalendarPanel calendar) {

        applyTheme();
        setLayout(new BorderLayout(12, 12));
        setBackground(bg);

        // ================= BLOCKED PROGRAMS =================
        DefaultListModel<String> programModel = new DefaultListModel<>();
        JList<String> programList = new JList<>(programModel);
        programList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        programList.setBackground(bg);
        programList.setForeground(fg);

        ProgramBlocker.getBlockedPrograms().forEach(programModel::addElement);

        JScrollPane programScroll = new JScrollPane(programList);
        programScroll.setBorder(BorderFactory.createTitledBorder("Блокируемые программы"));

        JButton addProgramBtn = createAddProgramButton(programModel, parent);
        JButton saveBlockedBtn = createSaveBlockedButton(programModel);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.setBackground(bg);
        buttons.add(addProgramBtn);
        buttons.add(saveBlockedBtn);

        JPanel blockPanel = new JPanel(new BorderLayout(5, 5));
        blockPanel.setBackground(bg);
        blockPanel.add(programScroll, BorderLayout.CENTER);
        blockPanel.add(buttons, BorderLayout.SOUTH);

        // ================= THEME =================
        JRadioButton light = new JRadioButton("Светлая тема");
        JRadioButton dark = new JRadioButton("Тёмная тема");
        JRadioButton system = new JRadioButton("Тема системы");

        ButtonGroup themeGroup = new ButtonGroup();
        themeGroup.add(light);
        themeGroup.add(dark);
        themeGroup.add(system);

        switch (MainApp.getThemeMode()) {
            case LIGHT -> light.setSelected(true);
            case DARK -> dark.setSelected(true);
            case SYSTEM -> system.setSelected(true);
        }

        ActionListener themeListener = e -> {
            if (light.isSelected()) MainApp.setThemeMode(ThemeMode.LIGHT);
            else if (dark.isSelected()) MainApp.setThemeMode(ThemeMode.DARK);
            else MainApp.setThemeMode(ThemeMode.SYSTEM);

            MainApp.applyLookAndFeel();
            SwingUtilities.updateComponentTreeUI(parent);

            applyTheme();
            calendar.refreshTheme();
        };

        light.addActionListener(themeListener);
        dark.addActionListener(themeListener);
        system.addActionListener(themeListener);

        // ================= CALENDAR =================
        JCheckBox compactCalendar = new JCheckBox("Компактная таблица календаря");
        compactCalendar.setBackground(bg);
        compactCalendar.setForeground(fg);
        compactCalendar.addActionListener(e ->
                calendar.setCompactView(compactCalendar.isSelected())
        );

        // ================= FONT =================
        Font currentFont = MainApp.getFontPref();

        JComboBox<String> fontBox = new JComboBox<>(
                GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getAvailableFontFamilyNames()
        );
        fontBox.setSelectedItem(currentFont.getFamily());

        JSpinner fontSize = new JSpinner(
                new SpinnerNumberModel(currentFont.getSize(), 10, 28, 1)
        );

        JButton applyFontBtn = new JButton("Применить шрифт");
        applyFontBtn.addActionListener(e -> {
            Font f = new Font(
                    (String) fontBox.getSelectedItem(),
                    Font.PLAIN,
                    (int) fontSize.getValue()
            );
            MainApp.setFont(f);
            updateFontRecursively(parent, f);
        });

        // ================= RIGHT PANEL =================
        JPanel themePanel = new JPanel(new GridLayout(0, 1, 6, 6));
        themePanel.setBackground(bg);
        themePanel.setBorder(BorderFactory.createTitledBorder("Оформление"));

        themePanel.add(light);
        themePanel.add(dark);
        themePanel.add(system);
        themePanel.add(Box.createVerticalStrut(8));
        themePanel.add(compactCalendar);
        themePanel.add(new JLabel("Шрифт:"));
        themePanel.add(fontBox);
        themePanel.add(new JLabel("Размер:"));
        themePanel.add(fontSize);
        themePanel.add(applyFontBtn);

        // ================= LAYOUT =================
        add(blockPanel, BorderLayout.CENTER);
        add(themePanel, BorderLayout.EAST);
    }

    // ================= THEME =================

    private void applyTheme() {
        ThemeMode mode = MainApp.getThemeMode();

        boolean dark = switch (mode) {
            case DARK -> true;
            case LIGHT -> false;
            case SYSTEM ->
                    UIManager.getColor("Panel.background").getRed() < 128;
        };

        bg = dark ? new Color(45, 45, 48) : Color.WHITE;
        fg = dark ? Color.WHITE : Color.BLACK;

        setBackground(bg);
        repaint();
    }

    public void refreshTheme() {
        applyTheme();
    }

    // ================= HELPERS =================

    private JButton createSaveBlockedButton(DefaultListModel<String> model) {
        JButton btn = new JButton("Сохранить блокировку");
        btn.addActionListener(e -> saveModelToBlocker(model));
        return btn;
    }

    private JButton createAddProgramButton(DefaultListModel<String> model, JFrame parent) {
        JButton btn = new JButton("Добавить программу");
        btn.addActionListener(e -> {
            JFileChooser chooser = createExeFileChooser(parent);
            if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                if (file.exists() && file.canRead()) {
                    String name = file.getName();
                    if (!model.contains(name)) {
                        model.addElement(name);
                        saveModelToBlocker(model);
                    }
                }
            }
        });
        return btn;
    }

    private JFileChooser createExeFileChooser(JFrame parent) {
        JFileChooser chooser = new JFileChooser(FileSystemView.getFileSystemView());
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".exe");
            }
            @Override public String getDescription() {
                return "Executable files (*.exe)";
            }
        });
        return chooser;
    }

    private void saveModelToBlocker(DefaultListModel<String> model) {
        List<String> list = Collections.list(model.elements());
        ProgramBlocker.setBlockedPrograms(list);
        try {
            ProgramBlocker.saveToJson(list);
            JOptionPane.showMessageDialog(this, "Список блокировки сохранён");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateFontRecursively(Component c, Font f) {
        c.setFont(f);
        if (c instanceof Container cont) {
            for (Component child : cont.getComponents()) {
                updateFontRecursively(child, f);
            }
        }
    }
}

