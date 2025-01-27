package org.apposed.appose;

import java.io.File;

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

    public static boolean isExecutable(File file) {
        // Note: On Windows, what we are really looking for is EXE files,
        // not any file with the executable bit set, unlike on POSIX.
        return OS == OperatingSystem.WINDOWS ?
            file.exists() && file.getName().toLowerCase().endsWith(".exe") :
            file.canExecute();
    }
}
