package app;

import java.math.BigInteger;
import java.util.regex.Pattern;

/**
 * Утилиты для проверки, нормализации и сравнения версий приложения.
 * <p>
 * Поддерживается только формат MAJOR.MINOR.PATCH.
 */
public final class VersionUtils {

    private static final Pattern VERSION_PATTERN =
            Pattern.compile(
                    "(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
            );

    private VersionUtils() {
    }

    /**
     * Удаляет пробелы по краям и необязательный префикс v или V.
     */
    public static String normalizeVersion(String version) {
        if (version == null) {
            return "";
        }

        var normalized = version.trim();

        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            return normalized.substring(1);
        }

        return normalized;
    }

    /**
     * Проверяет соответствие версии формату MAJOR.MINOR.PATCH.
     */
    public static boolean isValidVersion(String version) {
        return VERSION_PATTERN.matcher(
                normalizeVersion(version)
        ).matches();
    }

    /**
     * Проверяет, является ли latestVersion более новой версией.
     *
     * @throws IllegalArgumentException если одна из версий некорректна
     */
    public static boolean isNewerVersion(
            String latestVersion,
            String currentVersion
    ) {
        var latestParts = parseVersion(latestVersion);
        var currentParts = parseVersion(currentVersion);

        for (int i = 0; i < latestParts.length; i++) {
            int comparison = latestParts[i].compareTo(currentParts[i]);

            if (comparison != 0) {
                return comparison > 0;
            }
        }

        return false;
    }

    /**
     * Разбирает версию на три числовых компонента.
     */
    static BigInteger[] parseVersion(String version) {
        var normalized = normalizeVersion(version);
        var matcher = VERSION_PATTERN.matcher(normalized);

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Некорректный формат версии: " + version
            );
        }

        return new BigInteger[]{
                new BigInteger(matcher.group(1)),
                new BigInteger(matcher.group(2)),
                new BigInteger(matcher.group(3))
        };
    }
}