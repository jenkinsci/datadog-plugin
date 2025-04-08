package org.datadog.jenkins.plugins.datadog.apm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SemverTest {

    @Test
    public void testParseValidVersion() {
        Semver version = Semver.parse("1.2.3");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(3, version.getPatch());
    }

    @Test
    public void testParseValidVersionWithSpaces() {
        Semver version = Semver.parse("\t\t1.2.3\n");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(3, version.getPatch());
    }

    @Test
    public void testParseValidVersionWithPreRelease() {
        Semver version = Semver.parse("1.2.3-alpha");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(3, version.getPatch());
        assertEquals("alpha", version.getPreRelease());
    }

    @Test
    public void testParseInvalidVersion() {
        assertThrows(IllegalArgumentException.class, () -> Semver.parse("1.2"));
        assertThrows(IllegalArgumentException.class, () -> Semver.parse("1.2.3.4"));
        assertThrows(IllegalArgumentException.class, () -> Semver.parse("1.2.a"));
    }

    @Test
    public void testCompareTo() {
        Semver version1 = Semver.parse("1.2.3");
        Semver version2 = Semver.parse("1.2.4");
        Semver version3 = Semver.parse("1.3.0");
        Semver version4 = Semver.parse("2.0.0");
        Semver version5 = Semver.parse("1.2.3");

        assertTrue(version1.compareTo(version2) < 0);
        assertTrue(version2.compareTo(version1) > 0);

        assertEquals(0, version1.compareTo(version1));
        assertEquals(0, version1.compareTo(version5));
        assertEquals(version1, version5);

        assertTrue(version2.compareTo(version3) < 0);
        assertTrue(version3.compareTo(version4) < 0);
    }

    @Test
    public void testCompareToWithPreRelease() {
        Semver version1 = Semver.parse("1.2.3-alpha");
        Semver version2 = Semver.parse("1.2.3-beta");

        assertTrue(version1.compareTo(version2) < 0);
        assertTrue(version2.compareTo(version1) > 0);

        assertEquals(0, version1.compareTo(version1));
    }

    @Test
    public void testToString() {
        Semver version = new Semver(1, 2, 3, null);
        assertEquals("1.2.3", version.toString());

        Semver versionWithPreRelease = new Semver(1, 2, 3, "alpha");
        assertEquals("1.2.3-alpha", versionWithPreRelease.toString());
    }
}