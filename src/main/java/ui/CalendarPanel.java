package ui;

import app.MainApp;
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
    private JButton prevBtn;
    private JButton nextBtn;
    private JPanel headerPanel;
    private JPanel weekRowPanel;
    private JPanel todayRowPanel;
    private JLabel cornerLabel;

    private JTable table;
    private JTable rowHeaderTable;
    private DefaultTableModel model;
    private DefaultTableModel rowHeaderModel;

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
        applyTableTheme();
        
        // Обновляем цвета заголовка таблицы
        if (table != null && table.getTableHeader() != null) {
            table.getTableHeader().setBackground(headerBg);
            table.getTableHeader().setForeground(text);
        }
        
        // Обновляем цвета таблицы заголовков строк
        if (rowHeaderTable != null) {
            rowHeaderTable.setBackground(bg);
            rowHeaderTable.setForeground(text);
            rowHeaderTable.getTableHeader().setBackground(headerBg);
            rowHeaderTable.getTableHeader().setForeground(text);
        }
        
        // Обновляем цвета панелей заголовка
        if (headerPanel != null) headerPanel.setBackground(bg);
        if (weekRowPanel != null) weekRowPanel.setBackground(bg);
        if (todayRowPanel != null) todayRowPanel.setBackground(bg);
        
        // Обновляем цвета кнопок и заголовков
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
        
        // Обновляем corner label
        if (cornerLabel != null) {
            cornerLabel.setBackground(headerBg);
            cornerLabel.setForeground(text);
        }
        
        // Обновляем шрифт
        Font font = MainApp.getFontPref();
        updateFontRecursively(this, font);
        
        // Обновляем все компоненты
        revalidate();
        repaint();
    }
    
    private void updateFontRecursively(Component c, Font f) {
        c.setFont(f);
        if (c instanceof Container cont) {
            for (Component child : cont.getComponents()) {
                updateFontRecursively(child, f);
            }
        }
    }

    private void applyTheme() {
        boolean dark = MainApp.isCurrentThemeDark();

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
        headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBackground(bg);

        weekRowPanel = new JPanel(new BorderLayout());
        weekRowPanel.setBackground(bg);

        prevBtn = new JButton("Прошлая неделя");
        nextBtn = new JButton("Следующая неделя");

        title = new JLabel("", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setForeground(text);

        prevBtn.addActionListener(e -> changeWeek(-1));
        nextBtn.addActionListener(e -> changeWeek(1));

        weekRowPanel.add(prevBtn, BorderLayout.WEST);
        weekRowPanel.add(title, BorderLayout.CENTER);
        weekRowPanel.add(nextBtn, BorderLayout.EAST);

        todayBtn = new JButton("Сегодня");
        todayBtn.addActionListener(e -> {
            weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
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
        LocalDate current = LocalDate.now().with(DayOfWeek.MONDAY);
        todayBtn.setEnabled(!weekStart.equals(current));
    }

    // ================= TABLE =================
    private void initTable() {
        // Основная таблица (дни недели)
        String[] cols = new String[DAYS.length];
        System.arraycopy(DAYS, 0, cols, 0, DAYS.length);

        model = new DefaultTableModel(cols, 24) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        table = new JTable(model);
        table.setRowHeight(34);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setGridColor(grid);
        table.setShowGrid(true);
        table.setDefaultRenderer(Object.class, new TaskCellRenderer());
        table.getTableHeader().setReorderingAllowed(false);
        
        // Таблица заголовков строк (часы)
        String[] rowHeaderCols = new String[] { "Час" };
        rowHeaderModel = new DefaultTableModel(rowHeaderCols, 24) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        
        // Заполняем часами 00:00 - 23:00
        for (int h = 0; h < 24; h++) {
            rowHeaderModel.setValueAt(String.format("%02d:00", h), h, 0);
        }
        
        rowHeaderTable = new JTable(rowHeaderModel);
        rowHeaderTable.setRowHeight(34);
        rowHeaderTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        rowHeaderTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        rowHeaderTable.getColumnModel().getColumn(0).setMinWidth(60);
        rowHeaderTable.getColumnModel().getColumn(0).setMaxWidth(60);
        rowHeaderTable.setGridColor(grid);
        rowHeaderTable.setShowGrid(true);
        rowHeaderTable.getTableHeader().setReorderingAllowed(false);
        
        // Синхронизируем выделение и прокрутку
        table.setSelectionModel(rowHeaderTable.getSelectionModel());
        rowHeaderTable.setSelectionModel(table.getSelectionModel());
        
        // Синхронизируем прокрутку строк
        table.addMouseWheelListener(e -> {
            if (e.getWheelRotation() != 0) {
                rowHeaderTable.getScrollableUnitIncrement(
                    table.getVisibleRect(), SwingConstants.VERTICAL, 1);
            }
        });
        
        applyTableTheme();
        addClickEditor();
        addContextMenu();

        // Заголовок для фиксированного столбца (пустой угол)
        cornerLabel = new JLabel("Час", SwingConstants.CENTER);
        cornerLabel.setOpaque(true);
        cornerLabel.setBackground(headerBg);
        cornerLabel.setForeground(text);
        cornerLabel.setPreferredSize(new Dimension(60, 34));
        cornerLabel.setFont(cornerLabel.getFont().deriveFont(Font.BOLD));

        // Панель для заголовков
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(cornerLabel, BorderLayout.WEST);
        headerPanel.add(table.getTableHeader(), BorderLayout.CENTER);

        // Панель для таблиц
        JPanel dataPanel = new JPanel(new BorderLayout());
        dataPanel.add(rowHeaderTable, BorderLayout.WEST);
        dataPanel.add(table, BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(dataPanel);
        scroll.setColumnHeaderView(headerPanel);

        add(scroll, BorderLayout.CENTER);
    }

    private void applyTableTheme() {
        // Основная таблица
        table.setBackground(bg);
        table.setForeground(text);
        table.getTableHeader().setBackground(headerBg);
        table.getTableHeader().setForeground(text);
        
        // Таблица заголовков строк
        if (rowHeaderTable != null) {
            rowHeaderTable.setBackground(bg);
            rowHeaderTable.setForeground(text);
            rowHeaderTable.getTableHeader().setBackground(headerBg);
            rowHeaderTable.getTableHeader().setForeground(text);
        }
    }

    // ================= LOAD TASKS =================
    private void loadTasks() {
        // Очищаем основную таблицу (без столбца часов)
        for (int r = 0; r < 24; r++) {
            for (int c = 0; c < 7; c++) {
                model.setValueAt(null, r, c);
            }
        }

        // Заполняем задачами
        for (int d = 0; d < 7; d++) {
            LocalDate date = weekStart.plusDays(d);
            Map<String, TaskEntry> tasks = taskModel.getTasksForDate(date);

            for (int h = 0; h < 24; h++) {
                String key = String.format("%02d", h);
                if (tasks.containsKey(key)) {
                    model.setValueAt(tasks.get(key), h, d);
                }
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
                if (r < 0 || c < 0) return;
                if (model.getValueAt(r, c) != null) return;

                String hour = String.format("%02d", r);
                LocalDate date = weekStart.plusDays(c);

                String text = JOptionPane.showInputDialog(
                        CalendarPanel.this,
                        "Введите задачу",
                        DAYS[c] + ", " + date + " — " + hour + ":00"
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
            if (r < 0 || c < 0) return;

            TaskEntry entry = (TaskEntry) model.getValueAt(r, c);
            if (entry != null) {
                entry.setDone(true);
                table.repaint();
            }
        });

        delete.addActionListener(e -> {
            int r = table.getSelectedRow();
            int c = table.getSelectedColumn();
            if (r < 0 || c < 0) return;
            model.setValueAt(null, r, c);
        });

        menu.add(done);
        menu.add(delete);
        table.setComponentPopupMenu(menu);
    }
}








