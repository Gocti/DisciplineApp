package ui;

import app.MainApp;
import app.ThemeMode;
import model.TaskEntry;
import model.TaskModel;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.Map;

public class MainFrame extends JFrame {

    private final DefaultListModel<String> todayTasksModel = new DefaultListModel<>();

    private JLabel dateLabel;
    private LocalDate currentDate = LocalDate.now();

    private TaskModel taskModel;
    private JList<String> taskList;
    private JTabbedPane tabs;
    private CalendarPanel calendarPanel;
    private SettingsPanel settingsPanel;

    public MainFrame() {
        applyTheme();
        initFrame();
        initUi();
        loadTodayTasks();
        startDateWatcher();
        
        // Инициализируем трей
        TrayManager.install(this);
    }

    // ================= THEME =================
    private void applyTheme() {
        ThemeMode mode = MainApp.getThemeMode();
        MainApp.applyLookAndFeel(false);
    }

    /** Вызывается из SettingsPanel */
    public void refreshTheme() {
        MainApp.applyLookAndFeel(false);
        
        // Обновляем цвета всех компонентов
        updateColorsRecursively(this);
        
        // Обновляем шрифт
        Font font = MainApp.getFontPref();
        updateFontRecursively(this, font);
    }
    
    private void updateColorsRecursively(Component c) {
        Color bg = UIManager.getColor("Panel.background");
        Color fg = UIManager.getColor("Panel.foreground");
        
        if (c instanceof JComponent) {
            c.setBackground(bg);
            c.setForeground(fg);
        }
        
        if (c instanceof Container cont) {
            for (Component child : cont.getComponents()) {
                updateColorsRecursively(child);
            }
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

    // ================= FRAME =================
    private void initFrame() {
        setTitle("Прога для блокировки");
        setSize(900, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        
        // Обработчик закрытия окна - сворачивание в трей
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                setVisible(false);
            }
        });
    }

    // ================= UI =================
    private void initUi() {
        setLayout(new BorderLayout(12, 12));

        taskModel = new TaskModel();

        dateLabel = new JLabel("Сегодня: " + currentDate);
        dateLabel.setFont(dateLabel.getFont().deriveFont(Font.BOLD, 16f));
        dateLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 0));

        calendarPanel = new CalendarPanel(taskModel);
        settingsPanel = new SettingsPanel(this, calendarPanel);

        tabs = new JTabbedPane();
        tabs.addTab("Задачи", createTasksPanel());
        tabs.addTab("Календарь", calendarPanel);
        tabs.addTab("Настройки", settingsPanel);

        add(dateLabel, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }

    private JPanel createTasksPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        taskList = new JList<>(todayTasksModel);
        JScrollPane scroll = new JScrollPane(taskList);
        scroll.setBorder(BorderFactory.createTitledBorder("Задачи на сегодня"));

        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // ================= DATE WATCHER =================
    private void startDateWatcher() {
        new Timer(60_000, e -> {
            LocalDate now = LocalDate.now();
            if (!now.equals(currentDate)) {
                currentDate = now;
                dateLabel.setText("Сегодня: " + currentDate);
                loadTodayTasks();
            }
        }).start();
    }

    // ================= LOGIC =================
    private void loadTodayTasks() {
        todayTasksModel.clear();

        Map<String, TaskEntry> map = taskModel.getTasksForDate(LocalDate.now());
        if (map == null || map.isEmpty()) return;

        String lastText = null;
        int startHour = -1;

        for (int h = 0; h <= 24; h++) {
            String key = (h < 24) ? String.format("%02d", h) : null;
            TaskEntry entry = (key == null) ? null : map.get(key);

            if (entry != null && lastText == null) {
                startHour = h;
                lastText = entry.getText();
            } else if ((entry == null || !entry.getText().equals(lastText)) && lastText != null) {
                todayTasksModel.addElement(
                        String.format("%02d–%02d (%d ч) — %s",
                                startHour, h, h - startHour, lastText)
                );
                lastText = null;
            }
        }
    }
}







