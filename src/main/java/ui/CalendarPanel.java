package ui;

import app.MainApp;
import app.ThemeMode;
import model.TaskEntry;
import model.TaskModel;
import model.TaskPriority;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

public class CalendarPanel extends JPanel {

    private static final String[] DAYS = {
            "Понедельник", "Вторник", "Среда",
            "Четверг", "Пятница", "Суббота", "Воскресенье"
    };

    // ===== COLORS (меняются по теме) =====
    private Color bg;
    private Color headerBg;
    private Color grid;
    private Color text;

    private final TaskModel taskModel;

    private LocalDate weekStart;
    private JLabel title;
    private JButton todayBtn;

    private JTable table;
    private DefaultTableModel model;

    public CalendarPanel(TaskModel taskModel) {
        this.taskModel = taskModel;
        this.weekStart = LocalDate.now().with(DayOfWeek.MONDAY);

        setLayout(new BorderLayout(5, 5));
        applyTheme();          // 🔥 ThemeMode используется
        initHeader();
        initTable();
        loadTasks();
        startReminderTimer();
    }


    // ================= THEME =================
    public void setCompactView(boolean compact) {
        if (table == null) return;

        table.setRowHeight(compact ? 24 : 34);
        table.setShowGrid(!compact);
        table.repaint();
    }

    public void refreshTheme() {
        applyTheme();
        SwingUtilities.updateComponentTreeUI(this);
        repaint();
    }

    private void applyTheme() {
        ThemeMode mode = MainApp.getThemeMode();

        boolean dark = switch (mode) {
            case DARK -> true;
            case LIGHT -> false;
            case SYSTEM -> UIManager.getLookAndFeelDefaults()
                    .getColor("Panel.background").getRed() < 128;
        };

        if (dark) {
            bg = new Color(45, 45, 48);
            headerBg = new Color(60, 63, 65);
            grid = new Color(70, 130, 180);
            text = Color.WHITE;
        } else {
            bg = Color.WHITE;
            headerBg = new Color(230, 230, 230);
            grid = new Color(180, 180, 180);
            text = Color.BLACK;
        }

        setBackground(bg);
    }

    // ================= HEADER =================
    private void initHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(bg);

        JPanel weekRow = new JPanel(new BorderLayout());
        weekRow.setBackground(bg);

        JButton prev = new JButton("Прошлая неделя");
        JButton next = new JButton("Следующая неделя");

        title = new JLabel("", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setForeground(text);

        prev.addActionListener(e -> changeWeek(-1));
        next.addActionListener(e -> changeWeek(1));

        weekRow.add(prev, BorderLayout.WEST);
        weekRow.add(title, BorderLayout.CENTER);
        weekRow.add(next, BorderLayout.EAST);

        todayBtn = new JButton("Сегодня");
        todayBtn.addActionListener(e -> {
            weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
            updateTitle();
            loadTasks();
            updateTodayButton();
        });

        JPanel todayRow = new JPanel(new FlowLayout(FlowLayout.CENTER));
        todayRow.setBackground(bg);
        todayRow.add(todayBtn);

        header.add(weekRow);
        header.add(todayRow);

        updateTitle();
        updateTodayButton();
        add(header, BorderLayout.NORTH);
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
        LocalDate current = LocalDate.now().with(DayOfWeek.MONDAY);
        todayBtn.setEnabled(!weekStart.equals(current));
    }

    // ================= TABLE =================
    private void initTable() {
        String[] cols = new String[DAYS.length + 1];
        cols[0] = "Час";
        System.arraycopy(DAYS, 0, cols, 1, DAYS.length);

        model = new DefaultTableModel(cols, 24) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        for (int h = 0; h < 24; h++)
            model.setValueAt(String.format("%02d", h), h, 0);

        table = new JTable(model);
        table.setRowHeight(34);
        table.setDefaultRenderer(Object.class, new TaskCellRenderer());
        table.setGridColor(grid);
        table.setShowGrid(true);

        applyTableTheme();
        addClickEditor();
        addContextMenu();

        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    private void applyTableTheme() {
        table.setBackground(bg);
        table.setForeground(text);
        table.getTableHeader().setBackground(headerBg);
        table.getTableHeader().setForeground(text);
    }

    // ================= LOAD TASKS =================
    private void loadTasks() {
        for (int r = 0; r < 24; r++)
            for (int c = 1; c <= 7; c++)
                model.setValueAt(null, r, c);

        for (int d = 0; d < 7; d++) {
            LocalDate date = weekStart.plusDays(d);
            Map<String, TaskEntry> tasks = taskModel.getTasksForDate(date);

            for (int h = 0; h < 24; h++) {
                String key = String.format("%02d", h);
                if (tasks.containsKey(key))
                    model.setValueAt(tasks.get(key), h, d + 1);
            }
        }
    }

    // ================= REMINDER =================
    private void startReminderTimer() {
        new Timer(60_000, e -> {
            String hour = String.format("%02d", LocalTime.now().getHour());
            for (int d = 0; d < 7; d++) {
                TaskEntry task = taskModel.getTask(weekStart.plusDays(d), hour);
                if (task != null && !task.isDone()) {
                    JOptionPane.showMessageDialog(
                            this, task.getText(),
                            "Напоминание " + hour + ":00",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                }
            }
        }).start();
    }

    // ================= RENDERER =================
    private class TaskCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean selected,
                boolean focus, int row, int col) {

            super.getTableCellRendererComponent(table, value, selected, focus, row, col);

            setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, grid));
            setForeground(text);
            setBackground(col == 0 ? headerBg : bg);

            if (value instanceof TaskEntry entry) {
                setText(entry.getText());
                if (entry.isDone())
                    setBackground(new Color(120, 180, 120));
                else if (entry.getPriority() == TaskPriority.IMPORTANT)
                    setBackground(new Color(160, 130, 200));
            } else {
                setText("");
            }

            return this;
        }
    }

    // ================= CLICK EDITOR =================
    private void addClickEditor() {
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) return;

                int r = table.rowAtPoint(e.getPoint());
                int c = table.columnAtPoint(e.getPoint());
                if (r < 0 || c <= 0) return;
                if (model.getValueAt(r, c) != null) return;

                String hour = (String) model.getValueAt(r, 0);
                LocalDate date = weekStart.plusDays(c - 1);

                String text = JOptionPane.showInputDialog(
                        CalendarPanel.this,
                        "Введите задачу",
                        DAYS[c - 1] + ", " + date + " — " + hour + ":00"
                );

                if (text == null || text.isBlank()) return;

                TaskEntry entry = new TaskEntry(text, TaskPriority.NORMAL);
                model.setValueAt(entry, r, c);
                taskModel.setTask(date, hour, entry);
            }
        });
    }

    // ================= CONTEXT MENU =================
    private void addContextMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem done = new JMenuItem("Выполнено");
        JMenuItem delete = new JMenuItem("Удалить");

        done.addActionListener(e -> {
            int r = table.getSelectedRow();
            int c = table.getSelectedColumn();
            if (r < 0 || c <= 0) return;

            TaskEntry entry = (TaskEntry) model.getValueAt(r, c);
            if (entry != null) {
                entry.setDone(true);
                table.repaint();
            }
        });

        delete.addActionListener(e -> {
            int r = table.getSelectedRow();
            int c = table.getSelectedColumn();
            if (r < 0 || c <= 0) return;
            model.setValueAt(null, r, c);
        });

        menu.add(done);
        menu.add(delete);
        table.setComponentPopupMenu(menu);
    }
}








