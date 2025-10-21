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

package org.apposed.appose.uv;

import org.apposed.appose.BaseBuilder;
import org.apposed.appose.Environment;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Type-safe builder for UV-based virtual environments.
 *
 * @author Curtis Rueden
 */
public final class UvBuilder extends BaseBuilder<UvBuilder> {

	private String source;
	private String scheme;
	private String pythonVersion;
	private final List<String> packages = new ArrayList<>();

	public UvBuilder() {}

	public UvBuilder(String source) {
		this.source = source;
	}

	public UvBuilder(String source, String scheme) {
		this.source = source;
		this.scheme = scheme;
	}

	// -- UvBuilder methods --

	/**
	 * Specifies the Python version to use for the virtual environment.
	 *
	 * @param version Python version (e.g., "3.11", "3.10")
	 * @return This builder instance, for fluent-style programming.
	 */
	public UvBuilder python(String version) {
		this.pythonVersion = version;
		return this;
	}

	/**
	 * Adds PyPI packages to install in the virtual environment.
	 *
	 * @param packages PyPI package specifications (e.g., "numpy", "requests==2.28.0")
	 * @return This builder instance, for fluent-style programming.
	 */
	public UvBuilder include(String... packages) {
		this.packages.addAll(Arrays.asList(packages));
		return this;
	}

	// -- Builder methods --

	@Override
	public Environment build() throws IOException {
		File envDir = envDir();

		// Check for incompatible existing environments
		if (new File(envDir, ".pixi").isDirectory()) {
			throw new IOException("Cannot use UvBuilder: environment already managed by Pixi at " + envDir);
		}
		if (new File(envDir, "conda-meta").isDirectory()) {
			throw new IOException("Cannot use UvBuilder: environment already managed by Mamba/Conda at " + envDir);
		}

		Uv uv = new Uv(Uv.BASE_PATH);

		// Set up progress/output consumers.
		uv.setOutputConsumer(msg -> outputSubscribers.forEach(sub -> sub.accept(msg)));
		uv.setErrorConsumer(msg -> errorSubscribers.forEach(sub -> sub.accept(msg)));
		uv.setUvDownloadProgressConsumer((cur, max) -> {
			progressSubscribers.forEach(subscriber -> subscriber.accept("Downloading uv", cur, max));
		});

		// Pass along intended build configuration.
		uv.setEnvVars(envVars);

		try {
			uv.installUv();

			// Check if this is already a UV virtual environment
			boolean isUvVenv = new File(envDir, "pyvenv.cfg").isFile();

			if (isUvVenv && source == null && packages.isEmpty()) {
				// Environment already exists, just use it
				return createEnvironment(envDir);
			}

			// Handle file-based source
			if (source != null) {
				File sourceFile = new File(source);
				if (!sourceFile.exists()) {
					throw new IOException("Source file not found: " + source);
				}

				// Determine scheme if not specified
				if (scheme == null) {
					if (source.endsWith(".txt")) {
						scheme = "requirements.txt";
					} else {
						throw new IllegalArgumentException("Cannot determine scheme from file: " + source);
					}
				}

				if (!"requirements.txt".equals(scheme)) {
					throw new IllegalArgumentException("UvBuilder only supports requirements.txt scheme, got: " + scheme);
				}

				// Create virtual environment if it doesn't exist
				if (!isUvVenv) {
					uv.createVenv(envDir, pythonVersion);
				}

				// Install packages from requirements.txt
				uv.pipInstallFromRequirements(envDir, sourceFile.getAbsolutePath());
			} else {
				// Programmatic package building
				if (!isUvVenv) {
					// Create virtual environment
					uv.createVenv(envDir, pythonVersion);
				}

				// FIXME: assign channels/indices to uv object here?

				// Install packages
				if (!packages.isEmpty()) {
					List<String> allPackages = new ArrayList<>(packages);
					// Always include appose if we're installing packages
					if (!allPackages.contains("appose")) {
						allPackages.add("appose");
					}
					uv.pipInstall(envDir, allPackages.toArray(new String[0]));
				}
			}

			return createEnvironment(envDir);
		} catch (InterruptedException | URISyntaxException e) {
			throw new IOException(e);
		}
	}

	/**
	 * Adds PyPI index URLs for package discovery.
	 * These are alternative or additional package indexes beyond the default pypi.org.
	 *
	 * @param indexes Index URLs (e.g., custom PyPI mirrors or private package repositories)
	 * @return This builder instance, for fluent-style programming.
	 */
	@Override
	public UvBuilder channels(String... indexes) {
		return super.channels(indexes);
	}

	// -- Internal methods --

	@Override
	protected String suggestEnvName() {
		// Try to extract name from requirements.txt if present
		if (source != null) {
			File sourceFile = new File(source);
			if (sourceFile.exists() && sourceFile.getName().equals("requirements.txt")) {
				// Use parent directory name as env name
				String parentName = sourceFile.getParentFile() != null ?
					sourceFile.getParentFile().getName() : null;
				if (parentName != null && !parentName.isEmpty()) {
					return "appose-" + parentName;
				}
			}
		}
		return "appose-uv-env";
	}

	// -- Helper methods --

	private Environment createEnvironment(File envDir) {
		String base = envDir.getAbsolutePath();

		// UV virtual environments use standard venv structure
		String binDir = System.getProperty("os.name").toLowerCase().contains("windows") ? "Scripts" : "bin";
		List<String> binPaths = Collections.singletonList(
				envDir.toPath().resolve(binDir).toString()
		);

		// No special launch args needed - executables are directly in bin/Scripts
		List<String> launchArgs = Collections.emptyList();

		return new Environment() {
			@Override public String base() { return base; }
			@Override public List<String> binPaths() { return binPaths; }
			@Override public List<String> launchArgs() { return launchArgs; }
			@Override public Map<String, String> envVars() { return UvBuilder.this.envVars; }
		};
	}
}
