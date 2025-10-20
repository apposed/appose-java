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
 * Builder for system environments that use the system PATH.
 * System environments don't install packages; they use whatever
 * Python/Groovy/etc. is available on the system.
 *
 * @author Curtis Rueden
 */
public class SystemBuilder extends BaseBuilder {

	private final String baseDirectory;
	private final List<String> customBinPaths = new ArrayList<>();

	public SystemBuilder() {
		this.baseDirectory = ".";
		// Appose.system() includes system PATH by default
		customBinPaths.addAll(Environments.systemPath());
	}

	public SystemBuilder(String baseDirectory) {
		this.baseDirectory = baseDirectory;
		// Wrapping a directory does NOT include system PATH by default
	}

	/**
	 * Appends additional binary paths to search for executables.
	 * Paths are searched in the order they are added.
	 *
	 * @param paths Additional binary paths to search
	 * @return This builder instance, for fluent-style programming.
	 */
	public SystemBuilder binPaths(String... paths) {
		customBinPaths.addAll(Arrays.asList(paths));
		return this;
	}

	/**
	 * Appends additional binary paths to search for executables.
	 * Paths are searched in the order they are added.
	 *
	 * @param paths Additional binary paths to search
	 * @return This builder instance, for fluent-style programming.
	 */
	public SystemBuilder binPaths(List<String> paths) {
		customBinPaths.addAll(paths);
		return this;
	}

	/**
	 * Appends the current process's system PATH directories to the environment's binary paths.
	 * This is a convenience method equivalent to {@code binPaths(Environments.systemPath())}.
	 *
	 * @return This builder instance, for fluent-style programming.
	 */
	public SystemBuilder appendSystemPath() {
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
	public SystemBuilder inheritRunningJava() {
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

	@Override
	public Environment build(String envName) throws IOException {
		// SystemBuilder uses baseDirectory instead of default appose location
		return build(new File(baseDirectory));
	}

	@Override
	public Environment build(File envDir) throws IOException {
		// Create directory if it doesn't exist
		if (!envDir.exists() && !envDir.mkdirs()) {
			throw new IOException("Failed to create base directory: " + envDir);
		}

		return createEnvironment(envDir);
	}

	private Environment createEnvironment(File base) {
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
	public String name() {
		return "system";
	}

	@Override
	public boolean supports(String scheme) {
		// SystemBuilder is a catch-all fallback
		return true;
	}

	@Override
	public double priority() {
		return 0.0; // Lowest priority - only used as fallback
	}

	@Override
	public String suggestEnvName() {
		return "system";
	}
}

