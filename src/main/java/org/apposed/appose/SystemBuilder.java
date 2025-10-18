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

	// Package-private constructor for Appose class
	SystemBuilder() {
		this.baseDirectory = ".";
	}

	SystemBuilder(String baseDirectory) {
		this.baseDirectory = baseDirectory;
	}

	@Override
	public Environment build(String envName) throws IOException {
		File base = new File(baseDirectory);
		// Create directory if it doesn't exist
		if (!base.exists() && !base.mkdirs()) {
			throw new IOException("Failed to create base directory: " + baseDirectory);
		}

		return createEnvironment(base);
	}

	private Environment createEnvironment(File base) {
		String basePath = base.getAbsolutePath();
		List<String> launchArgs = new ArrayList<>();
		List<String> binPaths = new ArrayList<>();
		List<String> classpath = new ArrayList<>();

		// Add system PATH directories
		String pathEnv = System.getenv("PATH");
		if (pathEnv != null) {
			String[] paths = pathEnv.split(File.pathSeparator);
			binPaths.addAll(Arrays.asList(paths));
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
