package updater;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import ui.MainFrame;
import app.VersionUtils;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UpdateManager {

    private static final Logger LOG =
            Logger.getLogger(UpdateManager.class.getName());

    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/Gocti/DisciplineApp/releases/latest";

    private static final String INSTALLER_NAME_PREFIX =
            "DisciplineApp-Setup-";

    private static final String INSTALLER_NAME_SUFFIX = ".exe";

    private static final Duration CONNECT_TIMEOUT =
            Duration.ofSeconds(10);

    private static final Duration DOWNLOAD_TIMEOUT =
            Duration.ofMinutes(5);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final AtomicBoolean UPDATE_IN_PROGRESS =
            new AtomicBoolean(false);

    private UpdateManager() {}

    /**
     * Загружает последнюю версию установщика с GitHub
     * и запускает её после корректного завершения приложения.
     */
    public static void updateAsync(Component owner) {

        // Не допускаем одновременного запуска нескольких обновлений.
        if (!UPDATE_IN_PROGRESS.compareAndSet(false, true)) {
            LOG.warning("Обновление уже выполняется. Повторный запуск проигнорирован.");
            return;
        }

        var updateThread = new Thread(() -> {
            var installerPath = Path.of(
                    System.getProperty("java.io.tmpdir"),
                    "DisciplineApp-update-installer.exe"
            );

            try {
                var release = getLatestRelease();

                download(
                        release.downloadUrl(),
                        installerPath,
                        release.sha256()
                );

                SwingUtilities.invokeLater(() ->
                        shutdownAndInstall(owner, installerPath)
                );

            } catch (IOException e) {
                UPDATE_IN_PROGRESS.set(false);

                LOG.log(
                        Level.SEVERE,
                        "Не удалось загрузить или проверить обновление",
                        e
                );

                showUpdateError(
                        owner,
                        "Не удалось загрузить или проверить установщик обновления.%n%s"
                                .formatted(e.getMessage())
                );

            } catch (InterruptedException e) {
                UPDATE_IN_PROGRESS.set(false);
                Thread.currentThread().interrupt();

                LOG.log(
                        Level.WARNING,
                        "Загрузка обновления прервана",
                        e
                );

                showUpdateError(
                        owner,
                        "Загрузка обновления была прервана."
                );

            } catch (RuntimeException e) {
                UPDATE_IN_PROGRESS.set(false);

                LOG.log(
                        Level.SEVERE,
                        "Непредвиденная ошибка обновления",
                        e
                );

                showUpdateError(
                        owner,
                        "Произошла непредвиденная ошибка при обновлении.%n%s"
                                .formatted(e.getMessage())
                );
            }

        }, "discipline-app-updater");

        updateThread.setDaemon(false);
        updateThread.start();
    }


    /**
     * Получает информацию о последнем релизе GitHub.
     */
    private static ReleaseInfo getLatestRelease()
            throws IOException, InterruptedException {

        var request = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_API_URL))
                .timeout(CONNECT_TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "DisciplineApp")
                .GET()
                .build();

        var response = HTTP_CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new IOException(
                    "GitHub API вернул HTTP %d".formatted(response.statusCode())
            );
        }

        var root = JSON.readTree(response.body());

        var version = VersionUtils.normalizeVersion(
                requireString(
                        root,
                        "tag_name",
                        "В ответе GitHub отсутствует корректный tag_name."
                )
        );

        if (!VersionUtils.isValidVersion(version)) {
            throw new IOException(
                    "GitHub вернул некорректную версию релиза: " + version
            );
        }

        var expectedAssetName =
                INSTALLER_NAME_PREFIX + version + INSTALLER_NAME_SUFFIX;

        var assets = root.get("assets");

        if (assets == null || !assets.isArray()) {
            throw new IOException(
                    "В информации о релизе отсутствует список assets."
            );
        }

        return findInstaller(assets, expectedAssetName);
    }

    /**
     * Ищет установщик с ожидаемым именем среди assets релиза.
     */
    private static ReleaseInfo findInstaller(
            JsonNode assets,
            String expectedAssetName
    ) throws IOException {

        for (JsonNode asset : assets) {
            if (isExpectedInstaller(asset, expectedAssetName)) {
                return parseInstallerAsset(asset);
            }
        }

        throw new IOException(
                "В последнем релизе не найден установщик %s"
                        .formatted(expectedAssetName)
        );
    }

    /**
     * Проверяет имя asset.
     */
    private static boolean isExpectedInstaller(
            JsonNode asset,
            String expectedAssetName
    ) {

        var nameNode = asset.get("name");

        return nameNode != null
                && nameNode.isString()
                && expectedAssetName.equals(nameNode.stringValue());
    }

    /**
     * Извлекает ссылку и SHA-256 установщика.
     */
    private static ReleaseInfo parseInstallerAsset(JsonNode asset)
            throws IOException {

        var downloadUrl = requireString(
                asset,
                "browser_download_url",
                "Установщик найден, но ссылка на скачивание отсутствует."
        );

        var digest = requireString(
                asset,
                "digest",
                "GitHub не предоставил SHA-256 установщика."
        );

        if (!digest.startsWith("sha256:")) {
            throw new IOException(
                    "GitHub не предоставил SHA-256 установщика."
            );
        }

        var sha256 = digest.substring("sha256:".length())
                .toLowerCase(java.util.Locale.ROOT);

        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IOException(
                    "GitHub вернул некорректную SHA-256 контрольную сумму."
            );
        }

        return new ReleaseInfo(downloadUrl, sha256);
    }

    /**
     * Получает обязательное строковое поле JSON.
     */
    private static String requireString(
            JsonNode object,
            String field,
            String errorMessage
    ) throws IOException {

        var node = object.get(field);

        if (node == null || !node.isString()) {
            throw new IOException(errorMessage);
        }

        return node.stringValue();
    }


    /**
     * Завершает приложение и запускает установщик
     * после закрытия TaskModel.
     * Вызывается на EDT.
     */
    private static void shutdownAndInstall(
            Component owner,
            Path installerPath
    ) {
        MainFrame mainFrame = findMainFrame(owner);

        if (mainFrame == null) {
            UPDATE_IN_PROGRESS.set(false);

            LOG.severe(
                    "Не удалось найти главное окно приложения для завершения."
            );

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
                UPDATE_IN_PROGRESS.set(false);

                LOG.log(
                        Level.SEVERE,
                        "Не удалось запустить установщик",
                        e
                );

                JOptionPane.showMessageDialog(
                        null,
                        "Не удалось запустить установщик обновления."
                                + System.lineSeparator()
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

    private static void showUpdateError(
            Component owner,
            String message
    ) {
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


    /**
     * Скачивает установщик во временный файл,
     * проверяет его SHA-256 и перемещает на конечное место.
     */
    private static void download(
            String url,
            Path target,
            String expectedSha256
    ) throws IOException, InterruptedException {

        var temporaryPath = target.resolveSibling(
                target.getFileName() + ".part"
        );

        try {
            Files.deleteIfExists(temporaryPath);

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(DOWNLOAD_TIMEOUT)
                    .header("User-Agent", "DisciplineApp")
                    .GET()
                    .build();

            var response = HTTP_CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofFile(
                            temporaryPath,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING
                    )
            );

            if (response.statusCode() != 200) {
                throw new IOException(
                        "HTTP " + response.statusCode()
                                + " при загрузке установщика."
                );
            }

            if (!Files.isRegularFile(temporaryPath)
                    || Files.size(temporaryPath) == 0) {
                throw new IOException(
                        "Скачанный установщик отсутствует или пуст."
                );
            }

            var actualSha256 = calculateSha256(temporaryPath);

            if (!MessageDigest.isEqual(
                    expectedSha256.getBytes(StandardCharsets.US_ASCII),
                    actualSha256.getBytes(StandardCharsets.US_ASCII)
            )) {
                throw new IOException(
                        "SHA-256 установщика не совпадает "
                                + "с контрольной суммой GitHub."
                );
            }

            moveInstaller(temporaryPath, target);

            LOG.info(() ->
                    "Установщик успешно загружен и проверен. SHA-256: %s"
                            .formatted(actualSha256)
            );

        } catch (IOException | InterruptedException e) {
            try {
                Files.deleteIfExists(temporaryPath);
            } catch (IOException cleanupException) {
                e.addSuppressed(cleanupException);
            }

            throw e;
        }
    }


    /**
     * Перемещает проверенный установщик на конечное место.
     * Использует атомарное перемещение, если оно поддерживается.
     */
    private static void moveInstaller(Path source, Path target)
            throws IOException {

        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException _) {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }



    /**
     * Вычисляет SHA-256 файла потоковым чтением.
     */
    private static String calculateSha256(Path file)
            throws IOException {

        try {
            var digest = MessageDigest.getInstance("SHA-256");

            try (InputStream input = Files.newInputStream(file)) {
                var buffer = new byte[8192];
                int bytesRead;

                while ((bytesRead = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }

            return HexFormat.of().formatHex(digest.digest());

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 не поддерживается текущей Java Runtime.",
                    e
            );
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

    // ================= VERSION =================
    private record ReleaseInfo(
            String downloadUrl,
            String sha256
    ) {}
}
