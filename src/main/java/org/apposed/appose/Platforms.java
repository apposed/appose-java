package org.apposed.appose;

public final class Platforms {
    private Platforms() { }

    public enum OperatingSystem { LINUX, MACOS, WINDOWS, UNKNOWN }

    /** The detected operating system. */
    public static final OperatingSystem OS;

    static {
        final String osName = System.getProperty("os.name").toLowerCase();
        if (osName.startsWith("windows")) OS = OperatingSystem.WINDOWS;
        else if (osName.startsWith("mac")) OS = OperatingSystem.MACOS;
        else if (osName.contains("linux") || osName.endsWith("ix")) OS = OperatingSystem.LINUX;
        else OS = OperatingSystem.UNKNOWN;
    }
}
