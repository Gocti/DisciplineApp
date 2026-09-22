package updater;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class UpdateManager {

    private static final Logger LOG =
            Logger.getLogger(UpdateManager.class.getName());

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private UpdateManager() {}

    /**
     * Скачивает и запуска installer с GitHub, затем корректно завершает работу.
     */
    public static void updateAsync(Component owner) {
        var updateThread = new Thread(() -> {
            var installerUrl = "https://github.com/Gocti/DisciplineApp/releases/latest/download/DisciplineApp-installer.exe";
            var installerPath = Path.of(
                    System.getProperty("java.io.tmpdir"),
                    "DisciplineApp-update-installer.exe"
            );

            try {
                download(installerUrl, installerPath);
                runInstaller(installerPath);
                gracefulShutdown();
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "Update failed", e);
                showUpdateError(owner, "Не удалось скачать или запустить установщик обновления.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.log(Level.WARNING, "Update interrupted", e);
                showUpdateError(owner, "Проверка обновления была прервана.");
            }
        }, "discipline-app-updater");
        updateThread.setDaemon(false);
        updateThread.start();
    }

    private static void showUpdateError(Component owner, String message) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(
                        owner,
                        message,
                        "Ошибка обновления",
                        JOptionPane.ERROR_MESSAGE
                )
        );
    }

    // ================= DOWNLOAD =================

    private static void download(String url, Path target)
            throws IOException, InterruptedException {

        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        var response = HTTP_CLIENT.send(request,
                HttpResponse.BodyHandlers.ofFile(
                        target,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                ));

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " при загрузке " + url);
        }
    }

    // ================= RUN =================

    private static void runInstaller(Path installer)
            throws IOException {

        // Запускаем отдельный процесс и сразу освобождаем файлы приложения для обновления.
        new ProcessBuilder(
                installer.toAbsolutePath().toString()
        ).redirectOutput(ProcessBuilder.Redirect.DISCARD)
         .redirectError(ProcessBuilder.Redirect.DISCARD)
         .start();
    }

    // ================= GRACEFUL SHUTDOWN =================

    /**
     * Закрывает окна и завершает JVM.
     * System.exit необходим — AWT EDT не daemon-поток.
     */
    private static void gracefulShutdown() {
        try {
            for (var w : java.awt.Window.getWindows()) {
                w.dispose();
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Ошибка при закрытии окон", e);
        }
        System.exit(0);
    }
}
