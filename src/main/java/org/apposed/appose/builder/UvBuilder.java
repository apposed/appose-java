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

package org.apposed.appose.builder;

import org.apposed.appose.Builder;
import org.apposed.appose.Environment;
import org.apposed.appose.util.FilePaths;
import org.apposed.appose.util.Platforms;
import org.apposed.appose.util.Schemes;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

	private String pythonVersion;
	private final List<String> packages = new ArrayList<>();

	public UvBuilder() {}

	public UvBuilder(String source) throws IOException {
		file(source);
	}

	public UvBuilder(String source, String scheme) throws IOException {
		file(source);
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
	public String name() {
		return "uv";
	}

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

		Uv uv = new Uv();

		// Set up progress/output consumers.
		uv.setOutputConsumer(msg -> outputSubscribers.forEach(sub -> sub.accept(msg)));
		uv.setErrorConsumer(msg -> errorSubscribers.forEach(sub -> sub.accept(msg)));
		uv.setDownloadProgressConsumer((cur, max) -> {
			progressSubscribers.forEach(subscriber -> subscriber.accept("Downloading uv", cur, max));
		});

		// Pass along intended build configuration.
		uv.setEnvVars(envVars);
		uv.setFlags(flags);

		// Check for unsupported features
		if (!channels.isEmpty()) {
			throw new UnsupportedOperationException(
				"UvBuilder does not yet support programmatic index configuration. " +
				"Please specify custom indices in your requirements.txt file using " +
				"'--index-url' or '--extra-index-url' directives.");
		}

		try {
			uv.installUv();

			// Check if this is already a UV virtual environment
			boolean isUvVenv = new File(envDir, "pyvenv.cfg").isFile();

			if (isUvVenv && sourceContent == null && packages.isEmpty()) {
				// Environment already exists and no new config/packages, just use it
				return createEnvironment(envDir);
			}

			// Handle source-based build (file or content)
			if (sourceContent != null) {
				// Infer scheme if not explicitly set
				if (scheme == null) scheme = Schemes.fromContent(sourceContent).name();

				if (!"requirements.txt".equals(scheme) && !"pyproject.toml".equals(scheme)) {
					throw new IllegalArgumentException("UvBuilder only supports requirements.txt and pyproject.toml schemes, got: " + scheme);
				}

				if ("pyproject.toml".equals(scheme)) {
					// Handle pyproject.toml - uses uv sync
					// Create envDir if it doesn't exist
					if (!envDir.exists() && !envDir.mkdirs()) {
						throw new IOException("Failed to create environment directory: " + envDir);
					}

					// Write pyproject.toml to envDir
					File pyprojectFile = new File(envDir, "pyproject.toml");
					Files.write(pyprojectFile.toPath(), sourceContent.getBytes(StandardCharsets.UTF_8));

					// Run uv sync to create .venv and install dependencies
					uv.sync(envDir, pythonVersion);
				} else {
					// Handle requirements.txt - traditional venv + pip install
					// Create virtual environment if it doesn't exist
					if (!isUvVenv) {
						uv.createVenv(envDir, pythonVersion);
					}

					// Write requirements.txt to envDir
					File reqsFile = new File(envDir, "requirements.txt");
					Files.write(reqsFile.toPath(), sourceContent.getBytes(StandardCharsets.UTF_8));

					// Install packages from requirements.txt
					uv.pipInstallFromRequirements(envDir, reqsFile.getAbsolutePath());
				}
			} else {
				// Programmatic package building
				if (!isUvVenv) {
					// Create virtual environment
					uv.createVenv(envDir, pythonVersion);
				}

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

	@Override
	public Environment wrap(File envDir) throws IOException {
		FilePaths.ensureDirectory(envDir);

		// Check for pyproject.toml first (preferred for UV projects)
		File pyprojectToml = new File(envDir, "pyproject.toml");
		if (pyprojectToml.exists() && pyprojectToml.isFile()) {
			// Read the content so rebuild() will work even after directory is deleted
			sourceContent = new String(Files.readAllBytes(pyprojectToml.toPath()), StandardCharsets.UTF_8);
			scheme = "pyproject.toml";
		} else {
			// Fall back to requirements.txt
			File requirementsTxt = new File(envDir, "requirements.txt");
			if (requirementsTxt.exists() && requirementsTxt.isFile()) {
				// Read the content so rebuild() will work even after directory is deleted
				sourceContent = new String(Files.readAllBytes(requirementsTxt.toPath()), StandardCharsets.UTF_8);
				scheme = "requirements.txt";
			}
		}

		// Set the base directory and build (which will detect existing env)
		base(envDir);
		return build();
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

	// -- Helper methods --

	private Environment createEnvironment(File envDir) {
		String base = envDir.getAbsolutePath();

		// Determine venv location based on project structure
		// If .venv exists, it's a pyproject.toml-managed project (uv sync)
		// Otherwise, envDir itself is the venv (uv venv + pip install)
		File venvDir = new File(envDir, ".venv");
		File actualVenvDir = venvDir.exists() ? venvDir : envDir;

		// UV virtual environments use standard venv structure
		String binDir = Platforms.isWindows() ? "Scripts" : "bin";
		List<String> binPaths = Collections.singletonList(
				actualVenvDir.toPath().resolve(binDir).toString()
		);

		// No special launch args needed - executables are directly in bin/Scripts
		List<String> launchArgs = Collections.emptyList();

		return new Environment() {
			@Override public String base() { return base; }
			@Override public List<String> binPaths() { return binPaths; }
			@Override public List<String> launchArgs() { return launchArgs; }
			@Override public Map<String, String> envVars() { return UvBuilder.this.envVars; }
			@Override public Builder<?> builder() { return UvBuilder.this; }
		};
	}
}
