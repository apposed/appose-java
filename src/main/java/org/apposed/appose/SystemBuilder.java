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

package org.apposed.appose;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builder for system environments that use the system PATH.
 * System environments don't install packages; they use whatever
 * Python/Groovy/etc. is available on the system.
 *
 * @author Curtis Rueden
 */
public class SystemBuilder extends BaseBuilder {

	private String baseDirectory;
	private boolean useSystemPath;

	// Package-private constructor for Appose class
	SystemBuilder() {
		this.baseDirectory = ".";
		this.useSystemPath = true; // Appose.system() includes system PATH
	}

	SystemBuilder(String baseDirectory) {
		this.baseDirectory = baseDirectory;
		this.useSystemPath = false; // Wrapping a directory does NOT include system PATH
	}

	/**
	 * Configures whether to include the system PATH.
	 *
	 * @param useSystemPath true to include system PATH
	 * @return This builder instance, for fluent-style programming.
	 */
	public SystemBuilder useSystemPath(boolean useSystemPath) {
		this.useSystemPath = useSystemPath;
		return this;
	}

	@Override
	public Environment build(File envDir) throws IOException {
		// For SystemBuilder, we use envDir directly instead of the default location
		// Create directory if it doesn't exist
		if (!envDir.exists() && !envDir.mkdirs()) {
			throw new IOException("Failed to create base directory: " + envDir);
		}

		return createEnvironment(envDir);
	}

	@Override
	protected File determineEnvDir(String envName) {
		// SystemBuilder uses baseDirectory instead of standard appose location
		return new File(baseDirectory);
	}

	private Environment createEnvironment(File base) {
		String basePath = base.getAbsolutePath();
		List<String> launchArgs = new ArrayList<>();
		List<String> binPaths = new ArrayList<>();
		List<String> classpath = new ArrayList<>();

		// Add bin directory from the environment itself
		File binDir = new File(base, "bin");
		if (binDir.isDirectory()) {
			binPaths.add(binDir.getAbsolutePath());
		}

		// Optionally add system PATH directories
		if (useSystemPath) {
			String pathEnv = System.getenv("PATH");
			if (pathEnv != null) {
				String[] paths = pathEnv.split(File.pathSeparator);
				binPaths.addAll(Arrays.asList(paths));
			}
		}

		return new Environment() {
			@Override public String base() { return basePath; }
			@Override public List<String> binPaths() { return binPaths; }
			@Override public List<String> classpath() { return classpath; }
			@Override public List<String> launchArgs() { return launchArgs; }
		};
	}

	@Override
	protected String suggestEnvName() {
		return "system";
	}
}
