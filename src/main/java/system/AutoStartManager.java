package system;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AutoStartManager {

    private static final Logger LOG =
            Logger.getLogger(AutoStartManager.class.getName());

    private static final String REG_KEY =
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";

    private static final String APP_NAME =
            "DisciplineApp";

    private AutoStartManager() {}

    // ================= ENABLE =================

    public static void enable() {
        try {
            Path exePath = getExePath();

            new ProcessBuilder(
                    "reg", "add",
                    REG_KEY,
                    "/v", APP_NAME,
                    "/t", "REG_SZ",
                    "/d", "\"" + exePath + "\"",
                    "/f"
            ).start();

        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "Failed to enable autostart", e);
        }
    }

    // ================= DISABLE =================

    public static void disable() {
        try {
            new ProcessBuilder(
                    "reg", "delete",
                    REG_KEY,
                    "/v", APP_NAME,
                    "/f"
            ).start();

        } catch (IOException e) {
            LOG.log(Level.WARNING,
                    "Failed to disable autostart", e);
        }
    }

    // ================= UTILS =================

    private static Path getExePath() {
        return Path.of(System.getProperty("java.home"))
                .getParent()       // runtime
                .getParent()       // app
                .resolve("DisciplineApp.exe");
    }
}

