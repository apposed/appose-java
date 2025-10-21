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

import java.io.File;

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
