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

import org.apposed.appose.util.Environments;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for simple environments without package management.
 * Simple environments don't install packages; they use whatever executables
 * are found via configured binary paths.
 *
 * @author Curtis Rueden
 */
public final class SimpleBuilder extends BaseBuilder<SimpleBuilder> {

	private final List<String> customBinPaths = new ArrayList<>();

	// -- SimpleBuilder methods --

	/**
	 * Appends additional binary paths to search for executables.
	 * Paths are searched in the order they are added.
	 *
	 * @param paths Additional binary paths to search
	 * @return This builder instance, for fluent-style programming.
	 */
	public SimpleBuilder binPaths(String... paths) {
		return binPaths(Arrays.asList(paths));
	}

	/**
	 * Appends additional binary paths to search for executables.
	 * Paths are searched in the order they are added.
	 *
	 * @param paths Additional binary paths to search
	 * @return This builder instance, for fluent-style programming.
	 */
	public SimpleBuilder binPaths(List<String> paths) {
		customBinPaths.addAll(paths);
		return this;
	}

	/**
	 * Appends the current process's system PATH directories to the environment's binary paths.
	 * This is a convenience method equivalent to {@code binPaths(Environments.systemPath())}.
	 *
	 * @return This builder instance, for fluent-style programming.
	 */
	public SimpleBuilder appendSystemPath() {
		customBinPaths.addAll(Environments.systemPath());
		return this;
	}

	/**
	 * Configures the environment to use the same Java installation as the parent process.
	 * This prepends {@code ${java.home}/bin} to the binary paths and sets the JAVA_HOME
	 * environment variable, ensuring worker processes use the same JVM version.
	 * <p>
	 * This is a convenience method equivalent to:
	 * <pre>
	 * binPaths(new File(System.getProperty("java.home"), "bin").getPath())
	 *     .env("JAVA_HOME", System.getProperty("java.home"))
	 * </pre>
	 * </p>
	 *
	 * @return This builder instance, for fluent-style programming.
	 */
	public SimpleBuilder inheritRunningJava() {
		String javaHome = System.getProperty("java.home");
		if (javaHome != null) {
			File javaHomeBin = new File(javaHome, "bin");
			if (javaHomeBin.isDirectory()) {
				// Prepend to beginning of list for highest priority
				customBinPaths.add(0, javaHomeBin.getAbsolutePath());
			}
			envVars.put("JAVA_HOME", javaHome);
		}
		return this;
	}

	// -- Builder methods --

	@Override
	public String name() {
		return "custom";
	}

	@Override
	public SimpleBuilder name(String envName) {
		throw new UnsupportedOperationException(
			"SimpleBuilder does not support named environments. " +
			"Use base(File) to specify the working directory.");
	}

	@Override
	public SimpleBuilder channels(List<String> channels) {
		throw new UnsupportedOperationException(
			"SimpleBuilder does not support package channels. " +
			"It uses existing executables without package management.");
	}

	@Override
	public Environment build() throws IOException {
		File base = envDir();
		if (base == null) base = new File(".");

		// Create directory if it doesn't exist
		if (!base.exists() && !base.mkdirs()) {
			throw new IOException("Failed to create base directory: " + base);
		}

		String basePath = base.getAbsolutePath();
		List<String> launchArgs = new ArrayList<>();
		List<String> binPaths = new ArrayList<>();

		// Add bin directory from the environment itself (highest priority)
		File binDir = new File(base, "bin");
		if (binDir.isDirectory()) {
			binPaths.add(binDir.getAbsolutePath());
		}

		// Add custom binary paths configured via builder methods
		binPaths.addAll(customBinPaths);

		Map<String, String> environmentVars = new HashMap<>(envVars);

		return new Environment() {
			@Override public String base() { return basePath; }
			@Override public List<String> binPaths() { return binPaths; }
			@Override public List<String> launchArgs() { return launchArgs; }
			@Override public Map<String, String> envVars() { return environmentVars; }
		};
	}

	@Override
	public String suggestEnvName() {
		throw new UnsupportedOperationException(
			"SimpleBuilder does not use named environments.");
	}

	// -- Internal methods --

	@Override
	protected File envDir() {
		return envDir != null ? envDir : new File(".");
	}
}
