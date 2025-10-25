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
import java.util.Map;

/**
 * Utility class for working with processes.
 *
 * @author Curtis Rueden
 */
public final class Processes {

	private Processes() {
		// Prevent instantiation of utility class.
	}

	/**
	 * Creates a ProcessBuilder with environment variables applied.
	 *
	 * @param workingDir Working directory for the process (can be null).
	 * @param envVars Environment variables to set (can be null or empty).
	 * @param inheritIO Whether to inherit IO streams from parent process.
	 * @param command Command and arguments to execute.
	 * @return Configured ProcessBuilder ready to start.
	 */
	public static ProcessBuilder builder(File workingDir, Map<String, String> envVars,
		boolean inheritIO, String... command)
	{
		ProcessBuilder pb = new ProcessBuilder(command);
		if (workingDir != null) {
			pb.directory(workingDir);
		}
		if (envVars != null && !envVars.isEmpty()) {
			pb.environment().putAll(envVars);
		}
		if (inheritIO) {
			pb.inheritIO();
		}
		return pb;
	}
}
