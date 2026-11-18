package app;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

class VersionUtilsTest {

    @Test
    void shouldAcceptValidVersions() {
        assertTrue(VersionUtils.isValidVersion("1.0.0"));
        assertTrue(VersionUtils.isValidVersion("2.15.7"));
        assertTrue(VersionUtils.isValidVersion("0.0.0"));
    }

    @Test
    void shouldAcceptVersionPrefixes() {
        assertTrue(VersionUtils.isValidVersion("v1.2.3"));
        assertTrue(VersionUtils.isValidVersion("V1.2.3"));
        assertTrue(VersionUtils.isValidVersion("  v1.2.3  "));
    }

    @Test
    void shouldNormalizeVersion() {
        assertEquals("1.2.3", VersionUtils.normalizeVersion("v1.2.3"));
        assertEquals("1.2.3", VersionUtils.normalizeVersion("V1.2.3"));
        assertEquals("1.2.3", VersionUtils.normalizeVersion(" 1.2.3 "));
        assertEquals("", VersionUtils.normalizeVersion(null));
    }

    @Test
    void shouldRejectInvalidVersions() {
        assertFalse(VersionUtils.isValidVersion(null));
        assertFalse(VersionUtils.isValidVersion(""));
        assertFalse(VersionUtils.isValidVersion("1"));
        assertFalse(VersionUtils.isValidVersion("1.0"));
        assertFalse(VersionUtils.isValidVersion("1.x.0"));
        assertFalse(VersionUtils.isValidVersion("1.0-beta"));
        assertFalse(VersionUtils.isValidVersion("1.0.0.1"));
        assertFalse(VersionUtils.isValidVersion("01.0.0"));
        assertFalse(VersionUtils.isValidVersion("1.01.0"));
        assertFalse(VersionUtils.isValidVersion("1.0.01"));
    }

    @Test
    void shouldDetectNewerPatchVersion() {
        assertTrue(
                VersionUtils.isNewerVersion("1.0.1", "1.0.0")
        );
    }

    @Test
    void shouldDetectNewerMinorVersion() {
        assertTrue(
                VersionUtils.isNewerVersion("1.1.0", "1.0.9")
        );
    }

    @Test
    void shouldDetectNewerMajorVersion() {
        assertTrue(
                VersionUtils.isNewerVersion("2.0.0", "1.99.99")
        );
    }

    @Test
    void shouldReturnFalseForEqualVersions() {
        assertFalse(
                VersionUtils.isNewerVersion("1.0.0", "1.0.0")
        );
    }

    @Test
    void shouldReturnFalseForOlderVersions() {
        assertFalse(
                VersionUtils.isNewerVersion("1.0.9", "1.1.0")
        );

        assertFalse(
                VersionUtils.isNewerVersion("1.9.9", "2.0.0")
        );
    }

    @Test
    void shouldCompareVersionsWithPrefixes() {
        assertTrue(
                VersionUtils.isNewerVersion("v1.0.1", "V1.0.0")
        );

        assertFalse(
                VersionUtils.isNewerVersion("V1.0.0", "v1.0.0")
        );
    }

    @Test
    void shouldCompareVeryLargeVersionComponents() {
        var latest = "999999999999999999999999999999.0.0";
        var current = "999999999999999999999999999998.99.99";

        assertTrue(
                VersionUtils.isNewerVersion(latest, current)
        );
    }

    @Test
    void shouldParseVersionIntoThreeComponents() {
        var parts = VersionUtils.parseVersion("1.25.300");

        assertArrayEquals(
                new BigInteger[]{
                        BigInteger.ONE,
                        BigInteger.valueOf(25),
                        BigInteger.valueOf(300)
                },
                parts
        );
    }

    @Test
    void shouldRejectInvalidVersionDuringParsing() {
        assertThrows(
                IllegalArgumentException.class,
                () -> VersionUtils.parseVersion("1.x.0")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> VersionUtils.parseVersion("1.0-beta")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> VersionUtils.parseVersion(null)
        );
    }

    @Test
    void shouldRejectInvalidVersionsDuringComparison() {
        assertThrows(
                IllegalArgumentException.class,
                () -> VersionUtils.isNewerVersion("1.x.0", "1.0.0")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> VersionUtils.isNewerVersion("1.0.1", "1.0")
        );
    }
}