package updater;

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

    private UpdateManager() {}

    /**
     * @param installerUrl URL нового installer (msi / exe)
     */
    public static void update(String installerUrl) {
        Path installerPath =
                Path.of(System.getProperty("java.io.tmpdir"),
                        "DisciplineApp-update-installer.exe");

        try {
            download(installerUrl, installerPath);
            runInstaller(installerPath);
            System.exit(0);

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Update failed", e);
        }
    }

    // ================= DOWNLOAD =================

    private static void download(String url, Path target)
            throws IOException, InterruptedException {

        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build()) {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<Path> response;
            try {
                response = client.send(request,
                        HttpResponse.BodyHandlers.ofFile(
                                target,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING
                        ));
            } catch (IOException | InterruptedException e) {
                LOG.log(Level.SEVERE, "Download failed: " + url, e);
                throw e;
            }

            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " for " + url);
            }
        }
    }



    // ================= RUN =================

    private static void runInstaller(Path installer)
            throws IOException {

        new ProcessBuilder(
                installer.toAbsolutePath().toString()
        ).start();
    }
}

