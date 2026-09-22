package ui;

import app.MainApp;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class TrayManager {

    private static final String APP_TITLE = "DisciplineApp";
    private static final Logger LOG = Logger.getLogger(TrayManager.class.getName());

    private static WeakReference<MainFrame> mainFrameRef;
    private static TrayIcon trayIcon;

    private TrayManager() {}

    public static void install(JFrame frame) {
        if (!SystemTray.isSupported())
            return;

        // Guard: не устанавливаем трей повторно
        if (trayIcon != null) return;

        if (frame instanceof MainFrame mainFrame) {
            mainFrameRef = new WeakReference<>(mainFrame);
        }

        var tray = SystemTray.getSystemTray();

        var menu = new PopupMenu();

        var showItem = new MenuItem("Открыть");
        var hideItem = new MenuItem("Скрыть");
        var exitItem = new MenuItem("Выход");

        menu.add(showItem);
        menu.add(hideItem);
        menu.addSeparator();
        menu.add(exitItem);

        var image = createTrayImage();

        trayIcon = new TrayIcon(image, APP_TITLE, menu);
        trayIcon.setImageAutoSize(true);

        showItem.addActionListener(_ ->
                SwingUtilities.invokeLater(() -> {
                    frame.setVisible(true);
                    frame.setState(Frame.NORMAL);
                    frame.toFront();
                }));

        hideItem.addActionListener(_ ->
                SwingUtilities.invokeLater(() ->
                        frame.setVisible(false)));

        exitItem.addActionListener(_ -> {
            tray.remove(trayIcon);
            trayIcon = null;
            performCleanup();
            System.exit(0);
        });

        trayIcon.addActionListener(_ ->
                SwingUtilities.invokeLater(() -> {
                    frame.setVisible(true);
                    frame.toFront();
                }));

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            LOG.log(Level.WARNING, "Не удалось добавить иконку в трей", e);
            trayIcon = null;
        }
    }

    /** Показывает toast-уведомление при скрытии окна */
    public static void notifyHidden() {
        if (trayIcon != null) {
            trayIcon.displayMessage(
                    APP_TITLE,
                    "Свернуто в трей",
                    TrayIcon.MessageType.INFO
            );
        }
    }

    private static void performCleanup() {
        var frame = mainFrameRef != null ? mainFrameRef.get() : null;
        if (frame != null) {
            frame.stopAllTimers();
        }
    }

    // ================= ICON =================

    private static Image createTrayImage() {
        var size = 16;
        var img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

        var g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g.setColor(MainApp.getAccentColor());
            g.fillOval(0, 0, size - 1, size - 1);

            g.setColor(Color.WHITE);
            g.setFont(g.getFont().deriveFont(Font.BOLD, 10f));
            var fm = g.getFontMetrics();
            var text = "D";
            var x = (size - fm.stringWidth(text)) / 2;
            var y = (size + fm.getAscent() - fm.getDescent()) / 2;
            g.drawString(text, x, y);
        } finally {
            g.dispose();
        }

        return img;
    }
}
