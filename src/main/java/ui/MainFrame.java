package ui;

import app.MainApp;
import model.TaskEntry;
import model.TaskModel;
import model.TaskPriority;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDate;
import java.util.ArrayList;

import org.jetbrains.annotations.NotNull;
import java.time.ZoneId;

public class MainFrame extends JFrame {

    private static final int HOURS_COUNT = 24;
    private static final String APP_TITLE = "DisciplineApp";

    /** Связь между отображаемым элементом и ключами задач */
    private record TaskInfo(String displayText, String keys) {

        @Override
        @NotNull
        public String toString() {
            return displayText;
        }
    }

    private final DefaultListModel<TaskInfo> todayTasksModel = new DefaultListModel<>();
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    private JLabel dateLabel;
    private LocalDate currentDate = LocalDate.now(SYSTEM_ZONE);
    private LocalDate selectedDate = LocalDate.now(SYSTEM_ZONE); // Выбранная дата для просмотра задач

    private transient TaskModel taskModel;
    private JList<TaskInfo> taskList;
    private CalendarPanel calendarPanel;
    private SettingsPanel settingsPanel;

    private Timer dateWatcherTimer;


    public MainFrame() {
        applyTheme();
        initFrame();
        initUi();
        loadTasksForSelectedDate();
        startDateWatcher();

        // Инициализируем трей
        TrayManager.install(this);
    }

    // ================= THEME =================
    private void applyTheme() {
        MainApp.applyLookAndFeel();
    }

    /** Вызывается из SettingsPanel */
    public void refreshTheme() {
        MainApp.applyLookAndFeel();

        if (settingsPanel != null) {
            settingsPanel.refreshTheme();
        }

        if (calendarPanel != null) {
            calendarPanel.refreshTheme();
        }

        revalidate();
        repaint();
    }

    // ================= FRAME =================
    private void initFrame() {
        setTitle(APP_TITLE);
        setSize(900, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Скрываем окно, но оставляем фоновые механизмы работать.
                setVisible(false);
                TrayManager.notifyHidden();
            }
        });

        // Меню полного выхода
        var menuBar = new JMenuBar();
        var fileMenu = new JMenu("Файл");

        var exitItem = new JMenuItem("Выход");
        exitItem.addActionListener(_ -> {
            stopAllTimers();
            dispose();
            System.exit(0);
        });
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
        getRootPane().setJMenuBar(menuBar);
    }

    // ================= UI =================
    private void initUi() {
        setLayout(new BorderLayout(12, 12));

        taskModel = new TaskModel();

        dateLabel = new JLabel("Сегодня: " + currentDate);
        dateLabel.setFont(dateLabel.getFont().deriveFont(Font.BOLD, 16f));
        dateLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 0));

        calendarPanel = new CalendarPanel(taskModel);
        calendarPanel.setTaskChangeListener(this::loadTasksForSelectedDate);

        settingsPanel = new SettingsPanel(
                calendarPanel,
                this
        );

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Задачи", createTasksPanel());
        tabs.addTab("Календарь", calendarPanel);
        tabs.addTab("Настройки", settingsPanel);

        add(dateLabel, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }

    private JPanel createTasksPanel() {
        var panel = new JPanel(new BorderLayout(10, 10));

        // Панель навигации по дням
        var navPanel = createDateNavigationPanel();
        panel.add(navPanel, BorderLayout.NORTH);

        // Панель с кнопкой добавления задачи
        var buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        var addTaskBtn = new JButton("Добавить задачу");
        addTaskBtn.addActionListener(_ -> showAddTaskDialog());
        buttonPanel.add(addTaskBtn);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        taskList = new JList<>(todayTasksModel);
        var scroll = new JScrollPane(taskList);
        scroll.getViewport().setBackground(UIManager.getColor("Panel.background"));
        scroll.setBorder(BorderFactory.createTitledBorder("Задачи на " + formatDate(selectedDate)));

        // Контекстное меню для удаления конкретной задачи
        var popup = new JPopupMenu();
        var deleteItem = new JMenuItem("Удалить задачу");
        deleteItem.addActionListener(_ -> deleteSelectedTask());
        popup.add(deleteItem);
        taskList.setComponentPopupMenu(popup);

        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createDateNavigationPanel() {
        var navPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));

        var prevDayBtn = new JButton("<");
        prevDayBtn.setToolTipText("Предыдущий день");
        prevDayBtn.addActionListener(_ -> changeSelectedDate(-1));

        var yesterdayBtn = new JButton("Вчера");
        yesterdayBtn.addActionListener(_ -> setSelectedDate(LocalDate.now(SYSTEM_ZONE).minusDays(1)));

        var todayBtn = new JButton("Сегодня");
        todayBtn.addActionListener(_ -> setSelectedDate(LocalDate.now(SYSTEM_ZONE)));

        var tomorrowBtn = new JButton("Завтра");
        tomorrowBtn.addActionListener(_ -> setSelectedDate(LocalDate.now(SYSTEM_ZONE).plusDays(1)));

        var nextDayBtn = new JButton(">");
        nextDayBtn.setToolTipText("Следующий день");
        nextDayBtn.addActionListener(_ -> changeSelectedDate(1));

        navPanel.add(prevDayBtn);
        navPanel.add(yesterdayBtn);
        navPanel.add(todayBtn);
        navPanel.add(tomorrowBtn);
        navPanel.add(nextDayBtn);

        return navPanel;
    }

    private void changeSelectedDate(int days) {
        setSelectedDate(selectedDate.plusDays(days));
    }

    private void setSelectedDate(LocalDate date) {
        selectedDate = date;
        loadTasksForSelectedDate();
        updateTasksPanelBorder();
    }

    private void updateTasksPanelBorder() {
        if (taskList != null && taskList.getParent() instanceof JScrollPane scroll) {
            scroll.setBorder(BorderFactory.createTitledBorder("Задачи на " + formatDate(selectedDate)));
            scroll.revalidate();
        }
    }

    private String formatDate(LocalDate date) {
        var today = LocalDate.now(SYSTEM_ZONE);
        if (date.equals(today)) {
            return "сегодня";
        } else if (date.equals(today.minusDays(1))) {
            return "вчера";
        } else if (date.equals(today.plusDays(1))) {
            return "завтра";
        } else {
            return date.toString();
        }
    }

    private void deleteSelectedTask() {
        var selected = taskList.getSelectedValue();
        if (selected == null) return;

        var confirm = JOptionPane.showConfirmDialog(
                this, "Удалить задачу?",
                "Подтверждение", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) return;

        // Удаляем все ключи, связанные с выбранной датой
        for (var key : selected.keys().split(",")) {
            taskModel.removeTask(selectedDate, key);
        }
        calendarPanel.loadTasks(); // Обновляем календарь
        loadTasksForSelectedDate();
    }

    // ================= DATE WATCHER =================
    private void startDateWatcher() {
        dateWatcherTimer = new Timer(60_000, _ -> {
            var now = LocalDate.now(SYSTEM_ZONE);
            if (!now.equals(currentDate)) {
                var oldToday = currentDate;
                currentDate = now;
                dateLabel.setText("Сегодня: " + currentDate);
                // Если выбранная дата была старой сегодняшней датой, обновляем на новую сегодняшнюю
                if (selectedDate.equals(oldToday)) {
                    selectedDate = currentDate;
                    loadTasksForSelectedDate();
                    updateTasksPanelBorder();
                }
            }
        });
        dateWatcherTimer.start();
    }

    /** Остановка всех таймеров */
    public void stopAllTimers() {
        if (dateWatcherTimer != null) dateWatcherTimer.stop();
        if (calendarPanel != null) calendarPanel.stopReminderTimer();
    }

    // ================= LOGIC =================
    private void loadTasksForSelectedDate() {
        todayTasksModel.clear();

        var map = taskModel.getTasksForDate(selectedDate);
        if (map.isEmpty()) {
            updateTasksPanelBorder();
            return;
        }

        String lastText = null;
        var startHour = -1;
        var lastKeys = new ArrayList<String>();

        for (int h = 0; h < HOURS_COUNT; h++) {
            var key = String.format("%02d", h);
            var entry = map.get(key);

            if (entry != null && lastText == null) {
                startHour = h;
                lastText = entry.text();
                lastKeys.add(key);
            } else if (entry != null && entry.text().equals(lastText)) {
                lastKeys.add(key);
            } else if (lastText != null) {
                var display = String.format("%02d–%02d (%d ч) — %s",
                        startHour, h, h - startHour, lastText);
                todayTasksModel.addElement(new TaskInfo(display, String.join(",", lastKeys)));
                lastText = null;
                lastKeys.clear();
            }
        }

        // Flush last task
        if (lastText != null) {
            var display = String.format("%02d–%02d (%d ч) — %s",
                    startHour, HOURS_COUNT, HOURS_COUNT - startHour, lastText);
            todayTasksModel.addElement(new TaskInfo(display, String.join(",", lastKeys)));
        }

        // Обновляем заголовок панели задач
        updateTasksPanelBorder();
    }

    private void showAddTaskDialog() {
        // Диалоговое окно для добавления задачи
        JDialog dialog = new JDialog(this, "Добавить задачу", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(400, 200);
        dialog.setLocationRelativeTo(this);

        // Элементы формы
        JTextField taskNameField = new JTextField();
        var hourValues = new String[HOURS_COUNT];
        for (int hour = 0; hour < HOURS_COUNT; hour++) {
            hourValues[hour] = String.format("%02d:00", hour);
        }
        JComboBox<String> taskHourBox = new JComboBox<>(hourValues);
        taskHourBox.setSelectedItem("09:00");
        JButton addButton = new JButton("Добавить");
        JButton cancelButton = new JButton("Отмена");

        // Обработчик кнопки добавления
        addButton.addActionListener(_ -> {
            String taskName = taskNameField.getText().trim();

            if (taskName.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Пожалуйста, заполните все поля.",
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Создаем задачу для выбранной даты
            var taskKey = String.format("%02d", taskHourBox.getSelectedIndex());
            var taskEntry = new TaskEntry(taskName, TaskPriority.NORMAL);
            taskModel.setTask(selectedDate, taskKey, taskEntry);

            // Закрываем диалог
            dialog.dispose();

            // Обновляем список задач и календарь
            loadTasksForSelectedDate();
            calendarPanel.loadTasks();

            // Если выбранная дата не входит в текущую неделю календаря, переключаем календарь на эту неделю
            var calendarWeekStart = calendarPanel.getWeekStart();
            if (selectedDate.isBefore(calendarWeekStart) || selectedDate.isAfter(calendarWeekStart.plusDays(6))) {
                var newWeekStart = selectedDate.with(java.time.DayOfWeek.MONDAY);
                calendarPanel.setWeekStart(newWeekStart);
                calendarPanel.loadTasks();
            }
        });

        // Обработчик кнопки отмены
        cancelButton.addActionListener(_ -> dialog.dispose());

        // Основная панель диалога
        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        mainPanel.add(new JLabel("Название задачи:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        mainPanel.add(taskNameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        mainPanel.add(new JLabel("Час задачи:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        mainPanel.add(taskHourBox, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        mainPanel.add(createDialogButtonPanel(addButton, cancelButton), gbc);

        dialog.add(mainPanel);
        dialog.setVisible(true);
    }

    private JPanel createDialogButtonPanel(JButton addButton, JButton cancelButton) {
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(addButton);
        buttonPanel.add(cancelButton);
        return buttonPanel;
    }
}
