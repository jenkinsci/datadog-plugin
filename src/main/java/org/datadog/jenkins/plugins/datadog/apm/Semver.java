package org.datadog.jenkins.plugins.datadog.apm;

public class Semver {
    public static Semver parse(String version) {
        version = version.trim();
        String[] mainAndPreRelease = version.split("-", 2);
        String[] parts = mainAndPreRelease[0].split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid version format");
        }

        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        int patch = Integer.parseInt(parts[2]);
        String preRelease = mainAndPreRelease.length > 1 ? mainAndPreRelease[1] : null;

        return new Semver(major, minor, patch, preRelease);
    }

    private final int major;
    private final int minor;
    private final int patch;
    private final String preRelease;

    public Semver(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.preRelease = null;
    }

    public Semver(int major, int minor, int patch, String preRelease) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.preRelease = preRelease;
    }

    // getters for major, minor, and patch
    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getPatch() {
        return patch;
    }

    public String getPreRelease() {
        return preRelease;
    }

    public int compareTo(Semver other) {
        // compare the major versions
        int majorComparison = Integer.compare(major, other.major);
        // if the major versions are not equal, return the comparison result
        if (majorComparison != 0) {
            return majorComparison;
        }

        // compare the minor versions
        int minorComparison = Integer.compare(minor, other.minor);
        // if the minor versions are not equal, return the comparison result
        if (minorComparison != 0) {
            return minorComparison;
        }

        // compare the patch versions
        int patchComparison = Integer.compare(patch, other.patch);
        if (patchComparison != 0) {
            return patchComparison;
        }

        if (preRelease == null && other.preRelease == null) {
            return 0;
        } else if (preRelease == null) {
            return 1;
        } else if (other.preRelease == null) {
            return -1;
        } else {
            return preRelease.compareTo(other.preRelease);
        }
    }

    @Override
    public String toString() {
        return String.format("%d.%d.%d%s", getMajor(), getMinor(), getPatch(), getPreRelease() != null ? "-" + getPreRelease() : "");
    }
}
