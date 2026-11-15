package updater;

import ui.MainFrame;

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

    private static final String INSTALLER_URL =
            "https://github.com/Gocti/DisciplineApp/releases/latest/download/DisciplineApp-installer.exe";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private UpdateManager() {}

    /**
     * Скачивает установщик и запускает его после корректного завершения приложения.
     */
    public static void updateAsync(Component owner) {
        var updateThread = new Thread(() -> {
            var installerPath = Path.of(
                    System.getProperty("java.io.tmpdir"),
                    "DisciplineApp-update-installer.exe"
            );

            try {
                download(INSTALLER_URL, installerPath);

                SwingUtilities.invokeLater(() ->
                        shutdownAndInstall(owner, installerPath)
                );

            } catch (IOException e) {
                LOG.log(Level.SEVERE, "Не удалось скачать установщик обновления", e);
                showUpdateError(
                        owner,
                        "Не удалось скачать установщик обновления."
                );

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.log(Level.WARNING, "Загрузка обновления прервана", e);
                showUpdateError(
                        owner,
                        "Загрузка обновления была прервана."
                );
            }
        }, "discipline-app-updater");

        updateThread.setDaemon(false);
        updateThread.start();
    }

    /**
     * Завершает приложение и запускает установщик после закрытия TaskModel.
     * Вызывается на EDT.
     */
    private static void shutdownAndInstall(Component owner, Path installerPath) {
        MainFrame mainFrame = findMainFrame(owner);

        if (mainFrame == null) {
            LOG.severe("Не удалось найти главное окно приложения для завершения.");

            showUpdateError(
                    owner,
                    "Не удалось корректно завершить приложение для обновления."
            );
            return;
        }

        mainFrame.requestShutdown(() -> {
            try {
                runInstaller(installerPath);
                System.exit(0);

            } catch (IOException e) {
                LOG.log(Level.SEVERE, "Не удалось запустить установщик", e);

                JOptionPane.showMessageDialog(
                        null,
                        "Не удалось запустить установщик обновления.\n"
                                + "Приложение будет закрыто.",
                        "Ошибка обновления",
                        JOptionPane.ERROR_MESSAGE
                );

                System.exit(1);
            }
        });
    }

    private static MainFrame findMainFrame(Component owner) {
        if (owner instanceof MainFrame mainFrame) {
            return mainFrame;
        }

        if (owner != null) {
            Window window = SwingUtilities.getWindowAncestor(owner);

            if (window instanceof MainFrame mainFrame) {
                return mainFrame;
            }
        }

        for (Window window : Window.getWindows()) {
            if (window instanceof MainFrame mainFrame) {
                return mainFrame;
            }
        }

        return null;
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
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();

        var response = HTTP_CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofFile(
                        target,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                )
        );

        if (response.statusCode() != 200) {
            Files.deleteIfExists(target);

            throw new IOException(
                    "HTTP " + response.statusCode()
                            + " при загрузке установщика."
            );
        }

        if (!Files.isRegularFile(target) || Files.size(target) == 0) {
            Files.deleteIfExists(target);
            throw new IOException("Скачанный установщик отсутствует или пуст.");
        }
    }

    // ================= RUN =================

    private static void runInstaller(Path installer)
            throws IOException {

        new ProcessBuilder(installer.toAbsolutePath().toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }
}
