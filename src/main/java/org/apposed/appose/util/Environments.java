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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for working with environments.
 *
 * @author Curtis Rueden
 */
public final class Environments {

	private Environments() {
		// Prevent instantiation of utility class.
	}

	/**
	 * Retrieves the specified environment variables from the current process.
	 * Only variables that are set will be included in the returned map.
	 *
	 * @param keys The names of environment variables to retrieve.
	 * @return A map containing the requested environment variables and their values.
	 *         Variables that are not set will be omitted from the map.
	 */
	public static Map<String, String> envVars(String... keys) {
		Map<String, String> envVars = new HashMap<>();
		for (String key : keys) {
			String value = System.getenv(key);
			if (value != null) envVars.put(key, value);
		}
		return envVars;
	}

	/**
	 * Returns the current process's system PATH as a list of directory paths.
	 * <p>
	 * This splits the PATH environment variable on the platform-specific separator
	 * (colon on Unix-like systems, semicolon on Windows).
	 * </p>
	 *
	 * @return List of directory paths from the system PATH, or empty list if PATH is not set
	 */
	public static List<String> systemPath() {
		String pathEnv = System.getenv("PATH");
		if (pathEnv == null || pathEnv.isEmpty()) {
			return new ArrayList<>();
		}
		String separator = File.pathSeparator;
		return Arrays.asList(pathEnv.split(separator));
	}

	/**
	 * Gets the top-level directory for Appose-managed environments.
	 * Defaults to {@code ~/.local/share/appose} but can be overridden
	 * by setting the {@code appose.envs-dir} system property.
	 *
	 * @return The directory housing all Appose-managed environments.
	 */
	public static String apposeEnvsDir() {
		String envsDir = System.getProperty("appose.envs-dir");
		if (envsDir != null) return envsDir;
		String userHome = System.getProperty("user.home");
		return Paths.get(userHome, ".local", "share", "appose").toString();
	}
}
