package ui;

import app.MainApp;
import model.TaskEntry;
import model.TaskModel;
import model.TaskPriority;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CalendarPanel extends JPanel {

    // ===== CONSTANTS =====
    private static final int HOURS_COUNT = 24;
    private static final int DAYS_COUNT = 7;
    private static final int ROW_HEIGHT_NORMAL = 34;
    private static final int ROW_HEIGHT_COMPACT = 24;
    private static final int HOUR_COLUMN_WIDTH = 60;
    private static final float TITLE_FONT_SIZE = 16f;
    private static final int REMINDER_INTERVAL_MS = 60_000;
    private static final int MAX_TASK_TEXT_LENGTH = 200;

    private static final String[] DAYS = {
            "Понедельник", "Вторник", "Среда",
            "Четверг", "Пятница", "Суббота", "Воскресенье"
    };

    // ===== THEME COLORS (instance fields) =====
    private Color bg;
    private Color headerBg;
    private Color grid;
    private Color text;
    private Color doneColor;
    private Color importantColor;

    private final transient TaskModel taskModel;

    private LocalDate weekStart;
    private JLabel title;
    private JButton todayBtn;
    private JButton prevBtn;
    private JButton nextBtn;
    private JPanel headerPanel;
    private JPanel weekRowPanel;
    private JPanel todayRowPanel;
    private JLabel cornerLabel;

    private JTable table;
    private JTable rowHeaderTable;
    private DefaultTableModel model;
    private TaskCellRenderer taskCellRenderer;

    private Timer reminderTimer;
    // Thread-safe set для напоминаний
    private final Set<String> acknowledgedReminders = ConcurrentHashMap.newKeySet();
    private LocalDate lastReminderDate = null;

    // ===== Non-modal reminder dialog =====
    private JDialog reminderDialog;
    private JLabel reminderLabel;

    public CalendarPanel(TaskModel taskModel) {
        this.taskModel = taskModel;
        this.weekStart = LocalDate.now(ZoneId.systemDefault()).with(java.time.DayOfWeek.MONDAY);

        setLayout(new BorderLayout(5, 5));
        applyTheme();
        initHeader();
        initTable();
        initReminderDialog();
        loadTasks();
        startReminderTimer();
    }

    // ================= NON-MODAL REMINDER =================

    /** Создаёт немодальное окно напоминания */
    private void initReminderDialog() {
        // windowForComponent безопаснее — вернёт окно даже если компонент ещё не добавлен
        var parent = SwingUtilities.windowForComponent(this);
        Frame owner = null;
        if (parent instanceof Frame f) {
            owner = f;
        } else if (parent instanceof Dialog d) {
            owner = (Frame) d.getOwner();
        }
        reminderDialog = new JDialog(owner, "Напоминание", Dialog.ModalityType.MODELESS);
        reminderDialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        reminderDialog.setLayout(new BorderLayout(8, 8));

        reminderLabel = new JLabel("", SwingConstants.CENTER);
        reminderLabel.setFont(reminderLabel.getFont().deriveFont(Font.BOLD, 14f));
        reminderLabel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        reminderDialog.add(reminderLabel, BorderLayout.CENTER);

        var closeBtn = new JButton("OK");
        closeBtn.addActionListener(_ -> reminderDialog.setVisible(false));
        var btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.add(closeBtn);
        reminderDialog.add(btnPanel, BorderLayout.SOUTH);

        reminderDialog.pack();
        reminderDialog.setLocationRelativeTo(parent);
    }

    private void showReminder(String msg, String time) {
        reminderLabel.setText("<html><center>" + msg + "<br><br>" + time + "</center></html>");
        reminderDialog.setTitle("Напоминание — " + time);
        reminderDialog.pack();
        reminderDialog.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));
        reminderDialog.setVisible(true);
        reminderDialog.toFront();
    }

    // ================= THEME =================
    public void setCompactView(boolean compact) {
        if (table == null || rowHeaderTable == null) {
            return;
        }

        var rowHeight = compact
                ? ROW_HEIGHT_COMPACT
                : ROW_HEIGHT_NORMAL;

        table.setRowHeight(rowHeight);
        rowHeaderTable.setRowHeight(rowHeight);

        if (cornerLabel != null) {
            cornerLabel.setPreferredSize(
                    new Dimension(HOUR_COLUMN_WIDTH, rowHeight)
            );
        }

        table.setShowGrid(!compact);
        rowHeaderTable.setShowGrid(!compact);

        table.revalidate();
        rowHeaderTable.revalidate();
        table.repaint();
        rowHeaderTable.repaint();
    }

    public void refreshTheme() {
        applyTheme();
        applyTableTheme();

        if (taskCellRenderer != null) {
            taskCellRenderer.refreshTheme();
        }
        if (table != null && table.getTableHeader() != null) {
            table.getTableHeader().setBackground(headerBg);
            table.getTableHeader().setForeground(text);
        }
        if (rowHeaderTable != null) {
            rowHeaderTable.setBackground(bg);
            rowHeaderTable.setForeground(text);
            rowHeaderTable.getTableHeader().setBackground(headerBg);
            rowHeaderTable.getTableHeader().setForeground(text);
        }
        if (headerPanel != null) headerPanel.setBackground(bg);
        if (weekRowPanel != null) weekRowPanel.setBackground(bg);
        if (todayRowPanel != null) todayRowPanel.setBackground(bg);
        if (title != null) title.setForeground(text);
        if (prevBtn != null) {
            prevBtn.setBackground(headerBg);
            prevBtn.setForeground(text);
        }
        if (nextBtn != null) {
            nextBtn.setBackground(headerBg);
            nextBtn.setForeground(text);
        }
        if (todayBtn != null) {
            todayBtn.setBackground(headerBg);
            todayBtn.setForeground(text);
        }
        if (cornerLabel != null) {
            cornerLabel.setBackground(headerBg);
            cornerLabel.setForeground(text);
        }

        var font = MainApp.getFontPref();
        ComponentUtils.updateFontIteratively(this, font);
        table.repaint();
        revalidate();
    }

    public void stopReminderTimer() {
        if (reminderTimer != null) {
            reminderTimer.stop();
        }
    }

    private void applyTheme() {
        var dark = MainApp.isCurrentThemeDark();
        if (dark) {
            bg = ThemeColors.DARK_BG;
            headerBg = ThemeColors.DARK_HEADER_BG;
            grid = ThemeColors.DARK_GRID;
            text = Color.WHITE;
            doneColor = ThemeColors.DARK_DONE_COLOR;
            importantColor = ThemeColors.DARK_IMPORTANT_COLOR;
        } else {
            bg = ThemeColors.LIGHT_BG;
            headerBg = ThemeColors.LIGHT_HEADER_BG;
            grid = ThemeColors.LIGHT_GRID;
            text = Color.BLACK;
            doneColor = ThemeColors.LIGHT_DONE_COLOR;
            importantColor = ThemeColors.LIGHT_IMPORTANT_COLOR;
        }
        setBackground(bg);
    }

    // ================= HEADER =================
    private void initHeader() {
        headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBackground(bg);

        weekRowPanel = new JPanel(new BorderLayout());
        weekRowPanel.setBackground(bg);

        prevBtn = new JButton("Прошлая неделя");
        nextBtn = new JButton("Следующая неделя");

        title = new JLabel("", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, TITLE_FONT_SIZE));
        title.setForeground(text);

        prevBtn.addActionListener(_ -> changeWeek(-1));
        nextBtn.addActionListener(_ -> changeWeek(1));

        weekRowPanel.add(prevBtn, BorderLayout.WEST);
        weekRowPanel.add(title, BorderLayout.CENTER);
        weekRowPanel.add(nextBtn, BorderLayout.EAST);

        todayBtn = new JButton("Сегодня");
        todayBtn.addActionListener(_ -> {
            weekStart = LocalDate.now(ZoneId.systemDefault()).with(java.time.DayOfWeek.MONDAY);
            updateTitle();
            loadTasks();
            updateTodayButton();
        });

        todayRowPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        todayRowPanel.setBackground(bg);
        todayRowPanel.add(todayBtn);

        headerPanel.add(weekRowPanel);
        headerPanel.add(todayRowPanel);

        updateTitle();
        updateTodayButton();
        add(headerPanel, BorderLayout.NORTH);
    }

    private void changeWeek(int delta) {
        weekStart = weekStart.plusWeeks(delta);
        updateTitle();
        loadTasks();
        updateTodayButton();
    }

    private void updateTitle() {
        title.setText("Неделя: " + weekStart + " — " + weekStart.plusDays(6));
    }

    private void updateTodayButton() {
        var current = LocalDate.now(ZoneId.systemDefault()).with(java.time.DayOfWeek.MONDAY);
        todayBtn.setEnabled(!weekStart.equals(current));
    }

    // ================= TABLE =================
    private void initTable() {
        var cols = new String[DAYS_COUNT];
        System.arraycopy(DAYS, 0, cols, 0, DAYS_COUNT);

        model = new DefaultTableModel(cols, HOURS_COUNT) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        taskCellRenderer = new TaskCellRenderer();
        table = new JTable(model);
        table.setRowHeight(ROW_HEIGHT_NORMAL);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setGridColor(grid);
        table.setShowGrid(true);
        table.setDefaultRenderer(Object.class, taskCellRenderer);
        table.getTableHeader().setReorderingAllowed(false);

        var rowHeaderCols = new String[] { "Час" };
        DefaultTableModel rowHeaderModel = new DefaultTableModel(rowHeaderCols, HOURS_COUNT) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        for (int h = 0; h < HOURS_COUNT; h++) {
            rowHeaderModel.setValueAt(String.format("%02d:00", h), h, 0);
        }

        rowHeaderTable = new JTable(rowHeaderModel);
        rowHeaderTable.setRowHeight(ROW_HEIGHT_NORMAL);
        rowHeaderTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        rowHeaderTable.getColumnModel().getColumn(0).setPreferredWidth(HOUR_COLUMN_WIDTH);
        rowHeaderTable.getColumnModel().getColumn(0).setMinWidth(HOUR_COLUMN_WIDTH);
        rowHeaderTable.getColumnModel().getColumn(0).setMaxWidth(HOUR_COLUMN_WIDTH);
        rowHeaderTable.setGridColor(grid);
        rowHeaderTable.setShowGrid(true);
        rowHeaderTable.getTableHeader().setReorderingAllowed(false);

        table.setSelectionModel(rowHeaderTable.getSelectionModel());
        rowHeaderTable.setSelectionModel(table.getSelectionModel());

        applyTableTheme();
        addClickEditor();
        addContextMenu();

        cornerLabel = new JLabel("Час", SwingConstants.CENTER);
        cornerLabel.setOpaque(true);
        cornerLabel.setBackground(headerBg);
        cornerLabel.setForeground(text);
        cornerLabel.setPreferredSize(new Dimension(HOUR_COLUMN_WIDTH, ROW_HEIGHT_NORMAL));
        cornerLabel.setFont(cornerLabel.getFont().deriveFont(Font.BOLD));

        var tableHeaderPanel = new JPanel(new BorderLayout());
        tableHeaderPanel.add(cornerLabel, BorderLayout.WEST);
        tableHeaderPanel.add(table.getTableHeader(), BorderLayout.CENTER);

        var dataPanel = new JPanel(new BorderLayout());
        dataPanel.add(rowHeaderTable, BorderLayout.WEST);
        dataPanel.add(table, BorderLayout.CENTER);

        var scroll = new JScrollPane(dataPanel);
        scroll.setColumnHeaderView(tableHeaderPanel);

        add(scroll, BorderLayout.CENTER);
    }

    private void applyTableTheme() {
        table.setBackground(bg);
        table.setForeground(text);
        table.getTableHeader().setBackground(headerBg);
        table.getTableHeader().setForeground(text);
        if (rowHeaderTable != null) {
            rowHeaderTable.setBackground(bg);
            rowHeaderTable.setForeground(text);
            rowHeaderTable.getTableHeader().setBackground(headerBg);
            rowHeaderTable.getTableHeader().setForeground(text);
        }
    }

    // ================= LOAD TASKS =================
    public void loadTasks() {
        for (int r = 0; r < HOURS_COUNT; r++) {
            for (int c = 0; c < DAYS_COUNT; c++) {
                model.setValueAt(null, r, c);
            }
        }

        for (int d = 0; d < DAYS_COUNT; d++) {
            var date = weekStart.plusDays(d);
            var tasks = taskModel.getTasksForDate(date);
            for (int h = 0; h < HOURS_COUNT; h++) {
                var key = String.format("%02d", h);
                if (tasks.containsKey(key)) {
                    model.setValueAt(tasks.get(key), h, d);
                }
            }
        }
    }

    // ================= REMINDER =================
    private void startReminderTimer() {
        reminderTimer = new Timer(REMINDER_INTERVAL_MS, _ -> checkReminders());
        reminderTimer.start();
    }

    private void checkReminders() {
        var today = LocalDate.now(ZoneId.systemDefault());
        var now = LocalTime.now(ZoneId.systemDefault());
        var currentHour = String.format("%02d", now.getHour());

        // Сбрасываем трекер при смене даты
        if (!today.equals(lastReminderDate)) {
            acknowledgedReminders.clear();
            lastReminderDate = today;
        }

        // Проверяем задачи ТОЛЬКО за сегодня
        var task = taskModel.getTask(today, currentHour);
        if (task == null || task.done()) return;

        var reminderKey = today + "|" + currentHour;
        if (acknowledgedReminders.contains(reminderKey)) return;

        // Напоминаем только если задача была создана ДО текущего момента
        // и мы уже прошли хотя бы половину часа (избегаем спама для только что созданных задач)
        if (now.getMinute() < 2) return;  // первые 2 минуты часа — не напоминаем

        acknowledgedReminders.add(reminderKey);
        showReminder(task.text(), currentHour + ":00");
    }

    // ================= RENDERER =================
    private class TaskCellRenderer extends DefaultTableCellRenderer {

        private transient Border cellBorder;
        private void refreshTheme() {
            cellBorder = null;
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean selected,
                boolean focus,
                int row,
                int col
        ) {
            super.getTableCellRendererComponent(
                    table, value, selected, focus, row, col
            );

            setBorder(getCellBorder());
            setForeground(text);
            setBackground(col == 0 ? headerBg : bg);

            if (value instanceof TaskEntry(
                    String text1,
                    TaskPriority priority,
                    boolean done
            )) {
                setText(text1);

                if (done) {
                    setBackground(doneColor);
                } else if (priority == TaskPriority.IMPORTANT) {
                    setBackground(importantColor);
                }
            } else {
                setText("");
            }

            return this;
        }

        private Border getCellBorder() {
            if (cellBorder == null) {
                cellBorder = BorderFactory.createMatteBorder(
                        0, 0, 1, 1, grid
                );
            }

            return cellBorder;
        }
    }

    // ================= CLICK EDITOR =================
    private void addClickEditor() {
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) return;
                if (e.getClickCount() < 2) return;

                var r = table.rowAtPoint(e.getPoint());
                var c = table.columnAtPoint(e.getPoint());
                if (r < 0 || c < 0) return;

                var hour = String.format("%02d", r);
                var date = weekStart.plusDays(c);
                var existing = (TaskEntry) model.getValueAt(r, c);

                var rawInput = JOptionPane.showInputDialog(
                        CalendarPanel.this,
                        "Введите задачу",
                        DAYS[c] + ", " + date + " — " + hour + ":00",
                        JOptionPane.PLAIN_MESSAGE,
                        null, null,
                        existing != null ? existing.text() : ""
                );

                if (!(rawInput instanceof String input) || input.isBlank()) {
                    return;
                }

                handleTaskInput(r, c, input, existing);
            }
        });
    }

    private void handleTaskInput(
            int row,
            int column,
            String input,
            TaskEntry existing
    ) {
        if (input.length() > MAX_TASK_TEXT_LENGTH) {
            JOptionPane.showMessageDialog(
                    this,
                    "Текст задачи слишком длинный (макс. "
                            + MAX_TASK_TEXT_LENGTH + " символов)",
                    "Ошибка",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        var hour = String.format("%02d", row);
        var date = weekStart.plusDays(column);

        var entry = existing != null
                ? existing.withText(input)
                : new TaskEntry(input, TaskPriority.NORMAL);

        model.setValueAt(entry, row, column);
        taskModel.setTask(date, hour, entry);
        notifyTaskChanged();
    }

    // ================= CONTEXT MENU =================
    private void addContextMenu() {
        var menu = new JPopupMenu();

        var done = new JMenuItem("Выполнено");
        var delete = new JMenuItem("Удалить");

        done.addActionListener(_ -> {
            var r = table.getSelectedRow();
            var c = table.getSelectedColumn();
            if (r < 0 || c < 0) return;

            var entry = (TaskEntry) model.getValueAt(r, c);
            if (entry != null) {
                var date = weekStart.plusDays(c);
                var key = String.format("%02d", r);
                taskModel.setTask(date, key, entry.withDone(true));
                model.setValueAt(entry.withDone(true), r, c);
                notifyTaskChanged();
            }
        });

        delete.addActionListener(_ -> {
            var r = table.getSelectedRow();
            var c = table.getSelectedColumn();
            if (r < 0 || c < 0) return;

            var confirm = JOptionPane.showConfirmDialog(
                    this, "Удалить задачу?", "Подтверждение",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE
            );
            if (confirm != JOptionPane.YES_OPTION) return;

            model.setValueAt(null, r, c);

            var date = weekStart.plusDays(c);
            var key = String.format("%02d", r);
            taskModel.removeTask(date, key);
            notifyTaskChanged();
        });

        menu.add(done);
        menu.add(delete);
        table.setComponentPopupMenu(menu);
    }

    // ================= TASK CHANGE CALLBACK =================
    public interface TaskChangeListener {
        void onTaskChanged();
    }

    private transient TaskChangeListener taskChangeListener;

    public void setTaskChangeListener(TaskChangeListener listener) {
        this.taskChangeListener = listener;
    }

    private void notifyTaskChanged() {
        if (taskChangeListener != null) {
            SwingUtilities.invokeLater(taskChangeListener::onTaskChanged);
        }
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public void setWeekStart(LocalDate weekStart) {
        this.weekStart = weekStart;
        updateTitle();
        updateTodayButton();
        loadTasks();
    }
}
