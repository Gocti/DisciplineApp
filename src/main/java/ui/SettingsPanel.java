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
    private final CalendarPanel calendar;

    public SettingsPanel(JFrame parent, CalendarPanel calendar) {
        this.calendar = calendar;

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
        programScroll.setBackground(bg);

        JButton addProgramBtn = createAddProgramButton(programModel, parent);
        addProgramBtn.setBackground(bg);
        addProgramBtn.setForeground(fg);
        JButton saveBlockedBtn = createSaveBlockedButton(programModel);
        saveBlockedBtn.setBackground(bg);
        saveBlockedBtn.setForeground(fg);

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

        light.setBackground(bg);
        dark.setBackground(bg);
        system.setBackground(bg);
        light.setForeground(fg);
        dark.setForeground(fg);
        system.setForeground(fg);

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

            // Обновляем тему без перезагрузки
            MainApp.applyLookAndFeel(false);
            
            // Обновляем цвета в текущей панели
            refreshTheme();
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
        fontBox.setBackground(bg);
        fontBox.setForeground(fg);
        fontBox.setSelectedItem(currentFont.getFamily());

        JSpinner fontSize = new JSpinner(
                new SpinnerNumberModel(currentFont.getSize(), 10, 28, 1)
        );
        fontSize.setBackground(bg);
        fontSize.setForeground(fg);

        JButton applyFontBtn = createApplyFontButton(fontBox, fontSize);

        // ================= RIGHT PANEL =================
        JPanel themePanel = new JPanel(new GridLayout(0, 1, 6, 6));
        themePanel.setBackground(bg);
        themePanel.setForeground(fg);
        themePanel.setBorder(BorderFactory.createTitledBorder("Оформление"));

        themePanel.add(light);
        themePanel.add(dark);
        themePanel.add(system);
        themePanel.add(Box.createVerticalStrut(8));
        themePanel.add(compactCalendar);
        
        JLabel fontLabel = new JLabel("Шрифт:");
        fontLabel.setBackground(bg);
        fontLabel.setForeground(fg);
        themePanel.add(fontLabel);
        
        themePanel.add(fontBox);
        
        JLabel sizeLabel = new JLabel("Размер:");
        sizeLabel.setBackground(bg);
        sizeLabel.setForeground(fg);
        themePanel.add(sizeLabel);
        
        themePanel.add(fontSize);
        themePanel.add(applyFontBtn);

        // ================= LAYOUT =================
        add(blockPanel, BorderLayout.CENTER);
        add(themePanel, BorderLayout.EAST);
    }

    // ================= THEME =================

    public void refreshTheme() {
        // Сначала обновляем цвета bg и fg
        boolean dark = MainApp.isCurrentThemeDark();
        
        bg = dark ? new Color(45, 45, 48) : Color.WHITE;
        fg = dark ? Color.WHITE : Color.BLACK;
        
        // Обновляем цвета всех компонентов
        updateColorsRecursively(this, bg, fg);
    }

    private void applyTheme() {
        ThemeMode mode = MainApp.getThemeMode();

        boolean dark = MainApp.isCurrentThemeDark();

        bg = dark ? new Color(45, 45, 48) : Color.WHITE;
        fg = dark ? Color.WHITE : Color.BLACK;

        setBackground(bg);
        setForeground(fg);
    }

    // ================= HELPERS =================

    private JButton createSaveBlockedButton(DefaultListModel<String> model) {
        JButton btn = new JButton("Сохранить блокировку");
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.addActionListener(e -> saveModelToBlocker(model));
        return btn;
    }

    private JButton createAddProgramButton(DefaultListModel<String> model, JFrame parent) {
        JButton btn = new JButton("Добавить программу");
        btn.setBackground(bg);
        btn.setForeground(fg);
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
        
        // Применяем цвета для тёмной темы
        if (fg == Color.WHITE) {
            chooser.setBackground(bg);
            chooser.setForeground(fg);
        }
        
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
    
    /** Обновляет цвета всех компонентов рекурсивно */
    private void updateColorsRecursively(Component c, Color bg, Color fg) {
        if (c instanceof JComponent) {
            c.setBackground(bg);
            c.setForeground(fg);
        }
        if (c instanceof Container cont) {
            for (Component child : cont.getComponents()) {
                updateColorsRecursively(child, bg, fg);
            }
        }
    }
    
    /** Создаёт кнопку применения шрифта */
    private JButton createApplyFontButton(JComboBox<String> fontBox, JSpinner fontSize) {
        JButton btn = new JButton("Применить шрифт");
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.addActionListener(e -> {
            Font f = new Font(
                    (String) fontBox.getSelectedItem(),
                    Font.PLAIN,
                    (int) fontSize.getValue()
            );
            MainApp.setFont(f);

            // Обновляем шрифт во всём приложении
            updateFontRecursively(SwingUtilities.getWindowAncestor(this), f);

            // Также обновляем шрифт по умолчанию для новых компонентов
            UIManager.put("defaultFont", f);
        });
        return btn;
    }
}

