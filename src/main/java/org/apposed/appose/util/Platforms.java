/*-
 * #%L
 * Appose: multi-language interprocess cooperation with shared memory.
 * %%
 * Copyright (C) 2023 - 2025 Appose developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.apposed.appose.util;

import com.sun.java.swing.plaf.windows.resources.windows;

import java.io.File;
import java.util.Arrays;

/**
 * Utility class housing platform-specific logic.
 *
 * @author Curtis Rueden
 */
public final class Platforms {

	private Platforms() {
		// Prevent instantiation of utility class.
	}

	public enum OperatingSystem { LINUX, MACOS, WINDOWS, UNKNOWN }
	public enum CpuArchitecture { ARM64, ARMV6, ARMV7, PPC64, PPC64LE, RV64GC, S390X, X32, X64, UNKNOWN }

	/** The detected operating system. */
	public static final OperatingSystem OS;

	/** The detected CPU architecture. */
	public static final CpuArchitecture ARCH;

	/** A string of the form "OS|ARCH". */
	public static final String PLATFORM;

	static {
		final String osName = System.getProperty("os.name").toLowerCase();
		if (osName.contains("linux") || osName.endsWith("ix")) OS = OperatingSystem.LINUX;
		else if (osName.startsWith("mac") || osName.startsWith("darwin")) OS = OperatingSystem.MACOS;
		else if (osName.startsWith("windows")) OS = OperatingSystem.WINDOWS;
		else OS = OperatingSystem.UNKNOWN;

		final String cpuArch = System.getProperty("os.arch").toLowerCase();
		if (Arrays.asList("aarch64", "arm64").contains(cpuArch)) ARCH = CpuArchitecture.ARM64;
		else if (Arrays.asList("arm", "armv6").contains(cpuArch)) ARCH = CpuArchitecture.ARMV6;
		else if (Arrays.asList("aarch32", "arm32", "armv7").contains(cpuArch)) ARCH = CpuArchitecture.ARMV7;
		else if (Arrays.asList("powerpc64", "ppc64").contains(cpuArch)) ARCH = CpuArchitecture.PPC64;
		else if (Arrays.asList("powerpc64le", "ppc64le").contains(cpuArch)) ARCH = CpuArchitecture.PPC64LE;
		else if (Arrays.asList("riskv64", "riscv64gc", "rv64gc").contains(cpuArch)) ARCH = CpuArchitecture.RV64GC;
		else if (Arrays.asList("s390x").contains(cpuArch)) ARCH = CpuArchitecture.S390X;
		else if (Arrays.asList("i386", "i486", "i586", "i686", "x32", "x86", "x86-32", "x86_32").contains(cpuArch)) ARCH = CpuArchitecture.X32;
		else if (Arrays.asList("amd64", "x86-64", "x86_64", "x64").contains(cpuArch)) ARCH = CpuArchitecture.X64;
		else ARCH = CpuArchitecture.UNKNOWN;

		PLATFORM = OS + "|" + ARCH;
	}

	public static boolean isWindows() { return OS == OperatingSystem.WINDOWS; }
	public static boolean isMacOS() { return OS == OperatingSystem.MACOS; }
	public static boolean isLinux() { return OS == OperatingSystem.LINUX; }

	public static boolean isExecutable(File file) {
		// Note: On Windows, what we are really looking for is EXE files,
		// not any file with the executable bit set, unlike on POSIX.
		return OS == OperatingSystem.WINDOWS ?
			file.exists() && file.getName().toLowerCase().endsWith(".exe") :
			file.canExecute();
	}
}
