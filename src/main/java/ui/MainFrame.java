package ui;

import app.MainApp;
import model.TaskEntry;
import model.TaskModel;
import model.TaskPriority;
import model.PendingTaskOperations;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.CompletionException;

import org.jetbrains.annotations.NotNull;


public class MainFrame extends JFrame {

    private static final int HOURS_COUNT = 24;
    private static final String APP_TITLE = "DisciplineApp";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** Связь между отображаемым элементом и ключами задач */
    private record TaskInfo(String displayText, String keys, TaskPriority priority) {
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

    private final transient TaskModel taskModel;
    private JList<TaskInfo> taskList;
    private CalendarPanel calendarPanel;
    private SettingsPanel settingsPanel;

    private Timer dateWatcherTimer;

    private boolean closing;

    private final PendingTaskOperations pendingOperations;


    public MainFrame(TaskModel taskModel) {
        this.taskModel = taskModel;
        this.pendingOperations = taskModel.getPendingOperations();

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
            if (closing) {
                return;
            }

            closing = true;
            exitItem.setEnabled(false);

            // Блокируем взаимодействие с окном на время завершения.
            var glassPane = getRootPane().getGlassPane();
            glassPane.setCursor(
                    java.awt.Cursor.getPredefinedCursor(
                            java.awt.Cursor.WAIT_CURSOR
                    )
            );
            glassPane.setVisible(true);

            setTitle(getTitle() + " — Завершение...");

            taskModel.closeAsync()
                    .whenComplete((_, error) ->
                            SwingUtilities.invokeLater(() -> {
                                if (error != null) {
                                    closing = false;
                                    exitItem.setEnabled(true);
                                    glassPane.setVisible(false);

                                    showStorageError(
                                            "завершить приложение",
                                            error
                                    );
                                    return;
                                }

                                // Все операции хранилища завершены.
                                stopAllTimers();
                                dispose();
                                System.exit(0);
                            })
                    );
        });

        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
        getRootPane().setJMenuBar(menuBar);
    }

    // ================= UI =================
    private void initUi() {
        setLayout(new BorderLayout(12, 12));

        dateLabel = new JLabel("Сегодня: " + currentDate.format(DATE_FORMAT));
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
        taskList.setCellRenderer((list, value, _, selected, _) -> {
            var text = value.displayText();

            if (value.priority() == TaskPriority.IMPORTANT) {
                text = "★ " + text;
            }

            var label = new JLabel(text);

            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));

            if (selected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            } else {
                label.setBackground(list.getBackground());
                label.setForeground(list.getForeground());
            }

            var font = label.getFont();

            if (value.priority() == TaskPriority.IMPORTANT) {
                label.setFont(font.deriveFont(Font.BOLD));
            } else {
                label.setFont(font.deriveFont(Font.PLAIN));
            }

            return label;
        });
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

        var todayBtn = new JButton("Сегодня");
        todayBtn.addActionListener(_ ->
                setSelectedDate(LocalDate.now(SYSTEM_ZONE))
        );

        var nextDayBtn = new JButton(">");
        nextDayBtn.setToolTipText("Следующий день");
        nextDayBtn.addActionListener(_ -> changeSelectedDate(1));

        navPanel.add(prevDayBtn);
        navPanel.add(todayBtn);
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
        if (taskList != null
                && taskList.getParent() instanceof JViewport viewport
                && viewport.getParent() instanceof JScrollPane scroll) {

            scroll.setBorder(
                    BorderFactory.createTitledBorder(
                            "Задачи на " + formatDate(selectedDate)
                    )
            );

            scroll.revalidate();
            scroll.repaint();
        }
    }

    private String formatDate(LocalDate date) {
        var today = LocalDate.now(SYSTEM_ZONE);

        if (date.equals(today)) {
            return "сегодня";
        }

        if (date.equals(today.minusDays(1))) {
            return "вчера";
        }

        if (date.equals(today.plusDays(1))) {
            return "завтра";
        }

        return date.format(DATE_FORMAT);
    }


    private void deleteSelectedTask() {
        var selected = taskList.getSelectedValue();

        if (selected == null) {
            return;
        }

        var confirm = JOptionPane.showConfirmDialog(
                this,
                "Удалить задачу?",
                "Подтверждение",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        var date = selectedDate;
        var keys = Set.copyOf(
                java.util.Arrays.stream(selected.keys().split(","))
                        .collect(java.util.stream.Collectors.toSet())
        );

        if (!pendingOperations.tryAcquire(date, keys)) {
            JOptionPane.showMessageDialog(
                    this,
                    "Одна или несколько задач уже изменяются. Дождитесь завершения операции.",
                    "Операция выполняется",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        taskList.setEnabled(false);

        taskModel.removeTasksAsync(date, keys)
                .whenComplete((_, error) ->
                        SwingUtilities.invokeLater(() -> {
                            pendingOperations.release(date, keys);
                            taskList.setEnabled(true);

                            if (error != null) {
                                showStorageError("удалить задачу", error);
                                return;
                            }

                            if (selectedDate.equals(date)) {
                                loadTasksForSelectedDate();
                            }

                            calendarPanel.loadTasks();
                        })
                );
    }


    // ================= Storage Error =================

    private void showStorageError(String action, Throwable error) {
        var cause = error;

        while (cause instanceof CompletionException
                && cause.getCause() != null) {
            cause = cause.getCause();
        }

        JOptionPane.showMessageDialog(
                this,
                "Не удалось " + action + ".\n\n"
                        + cause.getMessage(),
                "Ошибка хранилища",
                JOptionPane.ERROR_MESSAGE
        );
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
        TaskPriority lastPriority = null;
        var startHour = -1;
        var lastKeys = new ArrayList<String>();

        for (int h = 0; h < HOURS_COUNT; h++) {
            var key = String.format("%02d", h);
            var entry = map.get(key);

            if (entry != null && lastText == null) {
                startHour = h;
                lastText = entry.text();
                lastPriority = entry.priority();
                lastKeys.add(key);

            } else if (entry != null
                    && entry.text().equals(lastText)
                    && entry.priority() == lastPriority) {

                lastKeys.add(key);

            } else if (lastText != null) {
                var display = String.format(
                        "%02d–%02d (%d ч) — %s",
                        startHour,
                        h,
                        h - startHour,
                        lastText
                );

                todayTasksModel.addElement(
                        new TaskInfo(
                                display,
                                String.join(",", lastKeys),
                                lastPriority
                        )
                );

                startHour = -1;
                lastText = null;
                lastPriority = null;
                lastKeys.clear();

                if (entry != null) {
                    startHour = h;
                    lastText = entry.text();
                    lastPriority = entry.priority();
                    lastKeys.add(key);
                }
            }
        }

        if (lastText != null) {
            var display = String.format(
                    "%02d–%02d (%d ч) — %s",
                    startHour,
                    HOURS_COUNT,
                    HOURS_COUNT - startHour,
                    lastText
            );

            todayTasksModel.addElement(
                    new TaskInfo(
                            display,
                            String.join(",", lastKeys),
                            lastPriority
                    )
            );
        }

        updateTasksPanelBorder();
    }

    private void showAddTaskDialog() {
        var dialog = new JDialog(
                this,
                "Добавить задачу",
                Dialog.ModalityType.APPLICATION_MODAL
        );

        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(this);

        var taskNameField = new JTextField();

        var hourValues = new String[HOURS_COUNT];
        for (int hour = 0; hour < HOURS_COUNT; hour++) {
            hourValues[hour] = String.format("%02d:00", hour);
        }

        var taskHourBox = new JComboBox<>(hourValues);
        taskHourBox.setSelectedItem("09:00");

        var intervalCheckBox = new JCheckBox("Выбрать интервал");

        var priorityBox = new JComboBox<>(new String[]{"Обычная", "Важная"});

        var startHourBox = new JComboBox<>(hourValues);
        startHourBox.setSelectedItem("09:00");

        var endHourBox = new JComboBox<>(hourValues);
        endHourBox.setSelectedItem("10:00");

        startHourBox.setEnabled(false);
        endHourBox.setEnabled(false);

        intervalCheckBox.addActionListener(_ -> {
            var intervalSelected = intervalCheckBox.isSelected();

            taskHourBox.setEnabled(!intervalSelected);
            startHourBox.setEnabled(intervalSelected);
            endHourBox.setEnabled(intervalSelected);
        });

        var addButton = new JButton("Добавить");
        var cancelButton = new JButton("Отмена");

        addButton.addActionListener(_ -> {
            if (addTask(
                    dialog,
                    taskNameField,
                    taskHourBox,
                    intervalCheckBox,
                    priorityBox,
                    startHourBox,
                    endHourBox
            )) {
                dialog.dispose();
            }
        });

        cancelButton.addActionListener(_ -> dialog.dispose());

        var mainPanel = new JPanel(new GridBagLayout());
        var gbc = new GridBagConstraints();

        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        mainPanel.add(
                new JLabel("Название задачи:"),
                gbc
        );

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        mainPanel.add(
                taskNameField,
                gbc
        );

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        mainPanel.add(
                new JLabel("Час задачи:"),
                gbc
        );

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        mainPanel.add(
                taskHourBox,
                gbc
        );

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.WEST;

        mainPanel.add(
                new JLabel("Приоритет:"),
                gbc
        );

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        mainPanel.add(
                priorityBox,
                gbc
        );

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        mainPanel.add(
                intervalCheckBox,
                gbc
        );

        gbc.gridy = 4;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        mainPanel.add(
                new JLabel("Начало:"),
                gbc
        );

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(
                startHourBox,
                gbc
        );

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(
                new JLabel("Конец:"),
                gbc
        );

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(
                endHourBox,
                gbc
        );

        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        mainPanel.add(
                createDialogButtonPanel(addButton, cancelButton),
                gbc
        );

        dialog.add(mainPanel);
        dialog.setVisible(true);
    }


    private boolean addTask(
            JDialog dialog,
            JTextField taskNameField,
            JComboBox<String> taskHourBox,
            JCheckBox intervalCheckBox,
            JComboBox<String> priorityBox,
            JComboBox<String> startHourBox,
            JComboBox<String> endHourBox
    ) {
        var taskName = taskNameField.getText().trim();

        if (taskName.isEmpty()) {
            JOptionPane.showMessageDialog(
                    dialog,
                    "Пожалуйста, заполните название задачи.",
                    "Ошибка",
                    JOptionPane.ERROR_MESSAGE
            );
            return false;
        }

        var priority = priorityBox.getSelectedIndex() == 1
                ? TaskPriority.IMPORTANT
                : TaskPriority.NORMAL;

        var taskEntry = new TaskEntry(taskName, priority);

        if (intervalCheckBox.isSelected()) {
            return addIntervalTask(
                    dialog,
                    startHourBox,
                    endHourBox,
                    taskEntry
            );
        }

        return addSingleTask(taskHourBox, taskEntry);
    }

    private boolean addIntervalTask(
            JDialog dialog,
            JComboBox<String> startHourBox,
            JComboBox<String> endHourBox,
            TaskEntry taskEntry
    ) {
        var startHour = startHourBox.getSelectedIndex();
        var endHour = endHourBox.getSelectedIndex();

        if (endHour <= startHour) {
            JOptionPane.showMessageDialog(
                    dialog,
                    "Время окончания должно быть позже времени начала.",
                    "Ошибка",
                    JOptionPane.ERROR_MESSAGE
            );
            return false;
        }

        var entries = new java.util.LinkedHashMap<String, TaskEntry>();

        for (int hour = startHour; hour < endHour; hour++) {
            entries.put(String.format("%02d", hour), taskEntry);
        }

        return saveTasksAsync(selectedDate, entries);
    }

    private boolean addSingleTask(
            JComboBox<String> taskHourBox,
            TaskEntry taskEntry
    ) {
        var taskKey = String.format(
                "%02d",
                taskHourBox.getSelectedIndex()
        );

        return saveTasksAsync(
                selectedDate,
                java.util.Map.of(taskKey, taskEntry)
        );
    }

    private boolean saveTasksAsync(
            LocalDate date,
            java.util.Map<String, TaskEntry> entries
    ) {
        var keys = Set.copyOf(entries.keySet());

        if (!pendingOperations.tryAcquire(date, keys)) {
            JOptionPane.showMessageDialog(
                    this,
                    "Одна или несколько выбранных часов уже изменяются.",
                    "Операция выполняется",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return false;
        }

        taskModel.setTasksAsync(date, entries)
                .whenComplete((_, error) ->
                        SwingUtilities.invokeLater(() -> {
                            pendingOperations.release(date, keys);

                            if (error != null) {
                                showStorageError("сохранить задачу", error);
                                return;
                            }

                            if (selectedDate.equals(date)) {
                                loadTasksForSelectedDate();
                            }

                            calendarPanel.loadTasks();
                        })
                );

        return true;
    }

    private void refreshTaskViews() {
        loadTasksForSelectedDate();
        calendarPanel.loadTasks();

        var calendarWeekStart = calendarPanel.getWeekStart();

        if (selectedDate.isBefore(calendarWeekStart)
                || selectedDate.isAfter(calendarWeekStart.plusDays(6))) {

            var newWeekStart =
                    selectedDate.with(java.time.DayOfWeek.MONDAY);

            calendarPanel.setWeekStart(newWeekStart);
            calendarPanel.loadTasks();
        }
    }

    private JPanel createDialogButtonPanel(JButton addButton, JButton cancelButton) {
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(addButton);
        buttonPanel.add(cancelButton);
        return buttonPanel;
    }
}
