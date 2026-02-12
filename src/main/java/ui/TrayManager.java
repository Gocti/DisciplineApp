package ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public final class TrayManager {

    private TrayManager() {}

    public static void install(JFrame frame) {
        if (!SystemTray.isSupported())
            return;

        SystemTray tray = SystemTray.getSystemTray();

        PopupMenu menu = new PopupMenu();

        MenuItem showItem = new MenuItem("Открыть");
        MenuItem hideItem = new MenuItem("Скрыть");
        MenuItem exitItem = new MenuItem("Выход");

        menu.add(showItem);
        menu.add(hideItem);
        menu.addSeparator();
        menu.add(exitItem);

        Image image = createTrayImage();

        TrayIcon icon = new TrayIcon(image, "Discipline App", menu);
        icon.setImageAutoSize(true);

        showItem.addActionListener(e ->
                SwingUtilities.invokeLater(() -> {
                    frame.setVisible(true);
                    frame.setState(Frame.NORMAL);
                    frame.toFront();
                }));

        hideItem.addActionListener(e ->
                SwingUtilities.invokeLater(() ->
                        frame.setVisible(false)));

        exitItem.addActionListener(e -> {
            tray.remove(icon);
            System.exit(0);
        });

        icon.addActionListener(e ->
                SwingUtilities.invokeLater(() -> {
                    frame.setVisible(true);
                    frame.toFront();
                }));

        try {
            tray.add(icon);
        } catch (AWTException ignored) {
        }
    }

    // ================= ICON =================

    private static Image createTrayImage() {
        int size = 16;
        BufferedImage img =
                new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = img.createGraphics();
        g.setColor(new Color(60, 120, 200));
        g.fillOval(0, 0, size - 1, size - 1);

        g.setColor(Color.WHITE);
        g.drawString("D", 4, 12);

        g.dispose();
        return img;
    }
}

