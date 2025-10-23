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

package org.apposed.appose.pixi;

import org.apposed.appose.BaseBuilder;
import org.apposed.appose.Builder;
import org.apposed.appose.Environment;
import org.apposed.appose.util.FilePaths;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Type-safe builder for Pixi-based environments.
 *
 * @author Curtis Rueden
 */
public final class PixiBuilder extends BaseBuilder<PixiBuilder> {

	private final List<String> condaPackages = new ArrayList<>();
	private final List<String> pypiPackages = new ArrayList<>();

	public PixiBuilder() {}

	public PixiBuilder(String source) throws IOException {
		file(source);
	}

	public PixiBuilder(String source, String scheme) throws IOException {
		file(source);
		this.scheme = scheme;
	}

	// -- PixiBuilder methods --

	/**
	 * Adds conda packages to the environment.
	 *
	 * @param packages Conda package specifications (e.g., "numpy", "python>=3.8")
	 * @return This builder instance, for fluent-style programming.
	 */
	public PixiBuilder conda(String... packages) {
		condaPackages.addAll(Arrays.asList(packages));
		return this;
	}

	/**
	 * Adds PyPI packages to the environment.
	 *
	 * @param packages PyPI package specifications (e.g., "matplotlib", "requests==2.28.0")
	 * @return This builder instance, for fluent-style programming.
	 */
	public PixiBuilder pypi(String... packages) {
		pypiPackages.addAll(Arrays.asList(packages));
		return this;
	}

	// -- Builder methods --

	@Override
	public String name() {
		return "pixi";
	}

	@Override
	public Environment build() throws IOException {
		File envDir = envDir();

		// Check for incompatible existing environments
		if (new File(envDir, "conda-meta").exists() && !new File(envDir, ".pixi").exists()) {
			throw new IOException("Cannot use PixiBuilder: environment already managed by Mamba/Conda at " + envDir);
		}
		if (new File(envDir, "pyvenv.cfg").exists()) {
			throw new IOException("Cannot use PixiBuilder: environment already managed by uv/venv at " + envDir);
		}

		Pixi pixi = new Pixi();

		// Set up progress/output consumers.
		pixi.setOutputConsumer(msg -> outputSubscribers.forEach(sub -> sub.accept(msg)));
		pixi.setErrorConsumer(msg -> errorSubscribers.forEach(sub -> sub.accept(msg)));
		pixi.setPixiDownloadProgressConsumer((cur, max) -> {
			progressSubscribers.forEach(subscriber -> subscriber.accept("Downloading pixi", cur, max));
		});

		// Pass along intended build configuration.
		pixi.setEnvVars(envVars);

		try {
			pixi.installPixi();

			// Resolve configuration content (from file, content string, or null for programmatic)
			String configContent = resolveConfigContent();

			// Check if this is already a pixi project
			boolean isPixiDir = new File(envDir, "pixi.toml").isFile() || new File(envDir, ".pixi").isDirectory();

			if (isPixiDir && configContent == null && condaPackages.isEmpty() && pypiPackages.isEmpty()) {
				// Environment already exists, just use it
				return createEnvironment(pixi, envDir);
			}

			// Handle source-based build (file or content)
			if (configContent != null) {
				if (isPixiDir) {
					// Already initialized, just use it
					return createEnvironment(pixi, envDir);
				}

				// Infer scheme if not explicitly set
				if (scheme == null) {
					scheme = inferSchemeFromContent(configContent);
				}

				if (!envDir.exists() && !envDir.mkdirs()) {
					throw new IOException("Failed to create environment directory: " + envDir);
				}

				if ("pixi.toml".equals(scheme)) {
					// Write pixi.toml to envDir
					File pixiTomlFile = new File(envDir, "pixi.toml");
					Files.write(pixiTomlFile.toPath(), configContent.getBytes(StandardCharsets.UTF_8));
				} else if ("environment.yml".equals(scheme)) {
					// Write environment.yml and import
					File environmentYamlFile = new File(envDir, "environment.yml");
					Files.write(environmentYamlFile.toPath(), configContent.getBytes(StandardCharsets.UTF_8));
					pixi.runPixi("init", "--import", environmentYamlFile.getAbsolutePath(), envDir.getAbsolutePath());
				}

				// Add any programmatic channels to augment source file
				if (!channels.isEmpty()) {
					pixi.addChannels(envDir, channels.toArray(new String[0]));
				}
			} else {
				// Programmatic package building
				if (isPixiDir) {
					// Already initialized, just use it
					return createEnvironment(pixi, envDir);
				}

				if (!envDir.exists() && !envDir.mkdirs()) {
					throw new IOException("Failed to create environment directory: " + envDir);
				}

				pixi.init(envDir);

				// Add channels.
				if (!channels.isEmpty()) {
					pixi.addChannels(envDir, channels.toArray(new String[0]));
				}

				// Add conda packages.
				if (!condaPackages.isEmpty()) {
					pixi.addCondaPackages(envDir, condaPackages.toArray(new String[0]));
				}

				// Add PyPI packages.
				if (!pypiPackages.isEmpty()) {
					// Ensure python and pip are available
					List<String> basePackages = new ArrayList<>();
					basePackages.add("python");
					basePackages.add("pip");
					pixi.addCondaPackages(envDir, basePackages.toArray(new String[0]));
					pixi.addPypiPackages(envDir, pypiPackages.toArray(new String[0]));
				}

				// Add appose package if we're building programmatically with packages.
				if (!condaPackages.isEmpty() || !pypiPackages.isEmpty()) {
					pixi.addCondaPackages(envDir, "appose");
				}
			}

			return createEnvironment(pixi, envDir);
		} catch (InterruptedException | URISyntaxException e) {
			throw new IOException(e);
		}
	}

	@Override
	public Environment wrap(File envDir) throws IOException {
		FilePaths.ensureDirectory(envDir);

		// Look for pixi.toml configuration file
		File pixiToml = new File(envDir, "pixi.toml");
		if (pixiToml.exists() && pixiToml.isFile()) {
			// Read the content so rebuild() will work even after directory is deleted
			sourceContent = new String(Files.readAllBytes(pixiToml.toPath()), StandardCharsets.UTF_8);
		}

		// Set the base directory and build (which will detect existing env)
		base(envDir);
		return build();
	}

	/**
	 * Adds conda channels to search for packages.
	 *
	 * @param channels Channel names (e.g., "conda-forge", "bioconda")
	 * @return This builder instance, for fluent-style programming.
	 */
	@Override
	public PixiBuilder channels(String... channels) {
		return super.channels(channels);
	}

	// -- Internal methods --

	@Override
	protected String suggestEnvName() {
		// Try to extract name from pixi.toml or environment.yml content
		if (sourceContent != null) {
			String[] lines = sourceContent.split("\n");
			for (String line : lines) {
				line = line.trim();
				if (line.startsWith("name") && line.contains("=")) {
					// pixi.toml format: name = "foo"
					int equalsIndex = line.indexOf('=');
					String value = line.substring(equalsIndex + 1).trim();
					value = value.replaceAll("^[\"']|[\"']$", "");
					if (!value.isEmpty()) return value;
				} else if (line.startsWith("name:")) {
					// environment.yml format: name: foo
					String value = line.substring(5).trim().replace("\"", "");
					if (!value.isEmpty()) return value;
				}
			}
		}
		return "appose-pixi-env";
	}

	// -- Helper methods --

	private Environment createEnvironment(Pixi pixi, File envDir) {
		String base = envDir.getAbsolutePath();
		List<String> launchArgs = Arrays.asList(
				pixi.pixiCommand, "run", "--manifest-path",
				new File(envDir, "pixi.toml").getAbsolutePath()
		);
		List<String> binPaths = Arrays.asList(
				envDir.toPath().resolve(".pixi").resolve("envs").resolve("default").resolve("bin").toString()
		);

		return new Environment() {
			@Override public String base() { return base; }
			@Override public List<String> binPaths() { return binPaths; }
			@Override public List<String> launchArgs() { return launchArgs; }
			@Override public Map<String, String> envVars() { return PixiBuilder.this.envVars; }
			@Override public Builder<?> builder() { return PixiBuilder.this; }
		};
	}
}
