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

import org.apposed.appose.BuildHandler;
import org.apposed.appose.Builder;
import org.apposed.appose.FilePaths;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A {@link BuildHandler} plugin powered by pixi.
 * Pixi is a modern package management tool that provides better environment
 * management than micromamba and supports both conda and PyPI packages natively.
 */
public class PixiHandler implements BuildHandler {

	private final List<String> channels = new ArrayList<>();
	private final List<String> condaIncludes = new ArrayList<>();
	private final List<String> pypiIncludes = new ArrayList<>();
	private String environmentYaml = null;
	private String pixiToml = null;

	@Override
	public boolean channel(String name, String location) {
		if (location == null) {
			// Assume it's a conda channel.
			channels.add(name);
			return true;
		}
		return false;
	}

	@Override
	public boolean include(String content, String scheme) {
		if (content == null) throw new NullPointerException("content must not be null");
		if (scheme == null) throw new NullPointerException("scheme must not be null");
		switch (scheme) {
			case "conda":
				// It's a conda package (or newline-separated package list).
				condaIncludes.addAll(lines(content));
				return true;
			case "pypi":
				// It's a PyPI package (or newline-separated package list).
				pypiIncludes.addAll(lines(content));
				return true;
			case "environment.yml":
				environmentYaml = content;
				return true;
			case "pixi.toml":
				pixiToml = content;
				return true;
		}
		return false;
	}

	@Override
	public String envName() {
		// Try to extract name from pixi.toml first
		if (pixiToml != null) {
			String[] lines = pixiToml.split("(\r\n|\n|\r)");
			Optional<String> name = Arrays.stream(lines)
				.filter(line -> line.trim().startsWith("name"))
				.map(line -> {
					// Parse: name = "foo" or name = 'foo'
					int equalsIndex = line.indexOf('=');
					if (equalsIndex < 0) return null;
					String value = line.substring(equalsIndex + 1).trim();
					value = value.replaceAll("^[\"']|[\"']$", ""); // Remove surrounding quotes
					return value;
				})
				.filter(s -> s != null && !s.isEmpty())
				.findFirst();
			if (name.isPresent()) return name.get();
		}

		// Fall back to environment.yml
		if (environmentYaml != null) {
			String[] lines = environmentYaml.split("(\r\n|\n|\r)");
			Optional<String> name = Arrays.stream(lines)
				.filter(line -> line.startsWith("name:"))
				.map(line -> line.substring(5).trim().replace("\"", ""))
				.findFirst();
			if (name.isPresent()) return name.get();
		}
		return null;
	}

	@Override
	public void build(File envDir, Builder builder) throws IOException {
		Pixi pixi = new Pixi(Pixi.BASE_PATH);
		boolean isPixiDir = new File(envDir, "pixi.toml").isFile() ||
		                     new File(envDir, ".pixi").isDirectory();

		// If nothing for this handler to do, check if we should still configure
		if (environmentYaml == null && pixiToml == null &&
		    channels.isEmpty() && condaIncludes.isEmpty() && pypiIncludes.isEmpty()) {
			if (isPixiDir) {
				// If directory already exists and is a pixi project,
				// inject needed pixi stuff into the configuration.
				fillConfig(pixi, envDir, builder.config);
			}
			return;
		}

		// Validate that we're not mixing incompatible configurations
		if (environmentYaml != null && pixiToml != null) {
			throw new UnsupportedOperationException(
				"Cannot use both environment.yml and pixi.toml. Please use only one.");
		}

		// Check if this is already a pixi project
		if (isPixiDir) {
			// This environment has already been populated.
			// TODO: Should we update it? For now, we just use it.
			fillConfig(pixi, envDir, builder.config);
			return;
		}

		// Setup progress/output consumers
		try {
			pixi.setOutputConsumer(msg -> builder.outputSubscribers.forEach(sub -> sub.accept(msg)));
			pixi.setErrorConsumer(msg -> builder.errorSubscribers.forEach(sub -> sub.accept(msg)));
			pixi.setPixiDownloadProgressConsumer((cur, max) -> {
				builder.progressSubscribers.forEach(subscriber -> subscriber.accept("Downloading pixi", cur, max));
			});

			pixi.installPixi();

			// Handle the case where envDir might have existing content from other handlers
			// For pixi, we'll work directly in envDir since pixi manages subdirectories itself
			if (!envDir.exists()) {
				if (!envDir.mkdirs()) {
					throw new IOException("Failed to create environment directory: " + envDir);
				}
			}

			// Initialize pixi project or import environment.yml
			if (pixiToml != null) {
				// Write out pixi.toml file
				File pixiTomlFile = new File(envDir, "pixi.toml");
				try (FileWriter fout = new FileWriter(pixiTomlFile)) {
					fout.write(pixiToml);
				}
				// Pixi will automatically install from pixi.toml when we run commands
			} else if (environmentYaml != null) {
				// Write out environment.yml file
				File environmentYamlFile = new File(envDir, "environment.yml");
				try (FileWriter fout = new FileWriter(environmentYamlFile)) {
					fout.write(environmentYaml);
				}
				// Initialize pixi project with environment.yml import
				// Use absolute path to environment.yml so pixi can find it
				pixi.runPixi("init", "--import", environmentYamlFile.getAbsolutePath(), envDir.getAbsolutePath());
			} else {
				// No config file provided, create a new pixi project
				pixi.init(envDir);
			}

			// Add channels if specified
			if (!channels.isEmpty()) {
				pixi.addChannels(envDir, channels.toArray(new String[0]));
			}

			// Add conda packages if specified
			if (!condaIncludes.isEmpty()) {
				pixi.addCondaPackages(envDir, condaIncludes.toArray(new String[0]));
			}

			// Add PyPI packages if specified
			if (!pypiIncludes.isEmpty()) {
				// Ensure pip and python are available for PyPI packages
				List<String> condaPackages = new ArrayList<>();
				condaPackages.add("python");
				condaPackages.add("pip");
				pixi.addCondaPackages(envDir, condaPackages.toArray(new String[0]));
				pixi.addPypiPackages(envDir, pypiIncludes.toArray(new String[0]));
			}

			// Add appose Python package if we're building programmatically with packages
			// (it's needed for the Python worker)
			if (!condaIncludes.isEmpty() || !pypiIncludes.isEmpty()) {
				List<String> apposePackage = new ArrayList<>();
				apposePackage.add("appose");
				pixi.addCondaPackages(envDir, apposePackage.toArray(new String[0]));
			}

			fillConfig(pixi, envDir, builder.config);
		}
		catch (InterruptedException | URISyntaxException e) {
			throw new IOException(e);
		}
	}

	private List<String> lines(String content) {
		return Arrays.stream(content.split("(\r\n|\n|\r)"))
			.map(String::trim)
			.filter(s -> !s.isEmpty() && !s.startsWith("#"))
			.collect(Collectors.toList());
	}

	private void fillConfig(Pixi pixi, File envDir, Map<String, List<String>> config) {
		// Use `pixi run -manifest-path $envDir/pixi.toml ...` to run within this environment.
		config.computeIfAbsent("launchArgs", k -> new ArrayList<>());
		config.get("launchArgs").addAll(Arrays.asList(
			pixi.pixiCommand, "run", "--manifest-path",
			new File(envDir, "pixi.toml").getAbsolutePath()
		));

		// Add the environment's bin directory to binPaths
		// Pixi environments are structured as: <project-dir>/.pixi/envs/default/
		Path pixiEnvPath = envDir.toPath().resolve(".pixi").resolve("envs").resolve("default");
		config.computeIfAbsent("binPaths", k -> new ArrayList<>());
		config.get("binPaths").add(pixiEnvPath.resolve("bin").toString());
	}
}
