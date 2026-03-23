/*-
 * #%L
 * Appose: multi-language interprocess cooperation with shared memory.
 * %%
 * Copyright (C) 2023 - 2026 Appose developers.
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

import org.apposed.appose.BuildException;
import org.apposed.appose.Environment;
import org.apposed.appose.util.FilePaths;
import org.apposed.appose.scheme.Schemes;
import org.apposed.appose.tool.Pixi;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
	private String pixiEnvironment;

	// -- PixiBuilder methods --

	/**
	 * Selects which pixi environment to activate within the manifest.
	 * Pixi supports multiple named environments in a single {@code pixi.toml};
	 * use this method to target one other than {@code "default"}.
	 * Maps to {@code pixi run --environment <name>}.
	 *
	 * @param name The pixi environment name (e.g. {@code "cuda"}, {@code "cpu"}).
	 * @return This builder instance, for fluent-style programming.
	 */
	public PixiBuilder environment(String name) {
		this.pixiEnvironment = name;
		return this;
	}

	/**
	 * Adds conda packages to the environment.
	 *
	 * @param packages Conda package specifications (e.g.: {@code "numpy", "python>=3.8"})
	 * @return This builder instance, for fluent-style programming.
	 */
	public PixiBuilder conda(String... packages) {
		condaPackages.addAll(Arrays.asList(packages));
		return this;
	}

	/**
	 * Adds PyPI packages to the environment.
	 *
	 * @param packages PyPI package specifications (e.g.: {@code "matplotlib", "requests==2.28.0"})
	 * @return This builder instance, for fluent-style programming.
	 */
	public PixiBuilder pypi(String... packages) {
		pypiPackages.addAll(Arrays.asList(packages));
		return this;
	}

	// -- Builder methods --

	@Override
	public String envType() {
		return "pixi";
	}

	@Override
	protected void addStateFields(Map<String, Object> state) {
		super.addStateFields(state);
		state.put("condaPackages", condaPackages);
		state.put("pypiPackages", pypiPackages);
		state.put("pixiEnvironment", pixiEnvironment);
	}

	@Override
	public Environment build() throws BuildException {
		File envDir = resolveEnvDir();

		// Check for incompatible existing environments.
		if (new File(envDir, "conda-meta").exists() && !new File(envDir, ".pixi").exists()) {
			throw new BuildException(this, "Cannot use PixiBuilder: environment already managed by Mamba/Conda at " + envDir);
		}
		if (new File(envDir, "pyvenv.cfg").exists()) {
			throw new BuildException(this, "Cannot use PixiBuilder: environment already managed by uv/venv at " + envDir);
		}

		// Validate content/scheme BEFORE installing any tools.
		if (content != null) {
			if (scheme == null) scheme = Schemes.fromContent(content);
			if (!"pixi.toml".equals(scheme.name()) && !"pyproject.toml".equals(scheme.name()) && !"environment.yml".equals(scheme.name())) {
				throw new IllegalArgumentException(
					"PixiBuilder only supports pixi.toml, pyproject.toml, and environment.yml schemes, got: " + scheme);
			}
		}

		Pixi pixi = new Pixi();

		// Set up progress/output consumers.
		pixi.setOutputConsumer(msg -> outputSubscribers.forEach(sub -> sub.accept(msg)));
		pixi.setErrorConsumer(msg -> errorSubscribers.forEach(sub -> sub.accept(msg)));
		pixi.setDownloadProgressConsumer((cur, max) -> {
			progressSubscribers.forEach(subscriber -> subscriber.accept("Downloading pixi", cur, max));
		});

		// Pass along intended build configuration.
		pixi.setEnvVars(envVars);
		pixi.setFlags(flags);

		try {
			// Always ensure the pixi tool itself is available.
			pixi.install();

			// If the env state matches our current configuration,
			// skip all package management and return immediately.
			if (isUpToDate(envDir)) {
				return buildPixiEnvironment(pixi, envDir);
			}

			// Build (or rebuild) the environment.
			if (content != null) {
				if (!envDir.exists() && !envDir.mkdirs()) {
					throw new BuildException(this, "Failed to create environment directory: " + envDir);
				}

				if ("pixi.toml".equals(scheme.name())) {
					// Write pixi.toml to envDir.
					File pixiTomlFile = new File(envDir, "pixi.toml");
					Files.write(pixiTomlFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
				}
				else if ("pyproject.toml".equals(scheme.name())) {
					// Write pyproject.toml to envDir (Pixi natively supports it).
					File pyprojectTomlFile = new File(envDir, "pyproject.toml");
					Files.write(pyprojectTomlFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
				}
				else if ("environment.yml".equals(scheme.name())) {
					// Write environment.yml and import.
					File environmentYamlFile = new File(envDir, "environment.yml");
					Files.write(environmentYamlFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
					pixi.exec("init", "--import", environmentYamlFile.getAbsolutePath(), envDir.getAbsolutePath());
				}

				// Add any programmatic channels to augment source file.
				if (!channels.isEmpty()) {
					pixi.addChannels(envDir, channels.toArray(new String[0]));
				}
			} else {
				// Programmatic package building.
				// Wipe any existing env before reinitializing, to avoid conflicts
				// with pixi init and to ensure no stale packages remain.
				if (envDir.exists()) FilePaths.deleteRecursively(envDir);
				if (!envDir.mkdirs()) {
					throw new BuildException(this, "Failed to create environment directory: " + envDir);
				}

				pixi.init(envDir);

				// Fail fast for vacuous environments.
				if (condaPackages.isEmpty() && pypiPackages.isEmpty()) {
					throw new IllegalStateException(
						"Cannot build empty environment programmatically. " +
						"Either provide a source file via Appose.pixi(source), or add packages via .conda() or .pypi()."
					);
				}

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
					pixi.addPypiPackages(envDir, pypiPackages.toArray(new String[0]));
				}

				// Verify that appose was included when building programmatically.
				boolean progBuild = !condaPackages.isEmpty() || !pypiPackages.isEmpty();
				if (progBuild) {
					boolean hasAppose =
						condaPackages.stream().anyMatch(pkg -> pkg.matches("^appose\\b.*")) ||
						pypiPackages.stream().anyMatch(pkg -> pkg.matches("^appose\\b.*"));
					if (!hasAppose) {
						throw new IllegalStateException(
							"Appose package must be explicitly included when building programmatically. " +
							"Add .conda(\"appose\") or .pypi(\"appose\") to your builder."
						);
					}
				}
			}

			runPixiInstall(pixi, envDir);
			writeApposeStateFile(envDir);
			return buildPixiEnvironment(pixi, envDir);
		}
		catch (IOException | InterruptedException e) {
			throw new BuildException(this, e);
		}
	}

	@Override
	public Environment wrap(File envDir) throws BuildException {
		try {
			FilePaths.ensureDirectory(envDir);

			// Look for pixi.toml configuration file first.
			File pixiToml = new File(envDir, "pixi.toml");
			if (pixiToml.exists() && pixiToml.isFile()) {
				// Read the content so rebuild() will work even after directory is deleted.
				content = new String(Files.readAllBytes(pixiToml.toPath()), StandardCharsets.UTF_8);
				scheme = Schemes.fromName("pixi.toml");
			} else {
				// Check for pyproject.toml.
				File pyprojectToml = new File(envDir, "pyproject.toml");
				if (pyprojectToml.exists() && pyprojectToml.isFile()) {
					// Read the content so rebuild() will work even after directory is deleted.
					content = new String(Files.readAllBytes(pyprojectToml.toPath()), StandardCharsets.UTF_8);
					scheme = Schemes.fromName("pyproject.toml");
				}
			}
		}
		catch (IOException e) {
			throw new BuildException(this, e);
		}

		// Set the base directory and build (which will detect existing env).
		base(envDir);
		return build();
	}

	// -- Helper methods --

	/** Returns a new list with an additional flag appended. */
	private static List<String> withFlag(List<String> flags, String flag) {
		List<String> result = new ArrayList<>(flags);
		result.add(flag);
		return result;
	}

	private void runPixiInstall(Pixi pixi, File envDir) throws IOException, InterruptedException {
		File manifestFile = new File(envDir, "pyproject.toml");
		if (!manifestFile.exists()) manifestFile = new File(envDir, "pixi.toml");

		// Set up install progress monitoring when subscribers are registered.
		PixiInstallMonitor monitor = null;
		if (!progressSubscribers.isEmpty()) {
			// Inject -vv if not already present, so stderr emits phase signals.
			if (!flags.contains("-v") && !flags.contains("-vv") && !flags.contains("-vvv")) {
				pixi.setFlags(withFlag(flags, "-vv"));
			}

			String envName = pixiEnvironment != null ? pixiEnvironment : "default";
			monitor = new PixiInstallMonitor(envDir, envName, progressSubscribers,
				msg -> errorSubscribers.forEach(sub -> sub.accept(msg)));
			pixi.setErrorConsumer(monitor::intercept);
		}

		// Ensure the pixi environment is fully installed.
		List<String> installCmd = new ArrayList<>(Arrays.asList(
				"install", "--manifest-path", manifestFile.getAbsolutePath()
		));
		if (pixiEnvironment != null) {
			installCmd.add("--environment");
			installCmd.add(pixiEnvironment);
		}
		try {
			pixi.exec(installCmd.toArray(new String[0]));
		}
		finally {
			if (monitor != null) {
				monitor.shutdown();
				// Restore the original error consumer.
				pixi.setErrorConsumer(msg -> errorSubscribers.forEach(sub -> sub.accept(msg)));
				// Restore the original flags.
				pixi.setFlags(flags);
			}
		}
	}

	private Environment buildPixiEnvironment(Pixi pixi, File envDir) {
		File manifestFile = new File(envDir, "pyproject.toml");
		if (!manifestFile.exists()) manifestFile = new File(envDir, "pixi.toml");

		String base = envDir.getAbsolutePath();
		String envName = pixiEnvironment != null ? pixiEnvironment : "default";
		List<String> launchArgs = new ArrayList<>(Arrays.asList(
				pixi.command, "run", "--manifest-path",
				manifestFile.getAbsolutePath()
		));
		if (pixiEnvironment != null) {
			launchArgs.add("--environment");
			launchArgs.add(pixiEnvironment);
		}
		List<String> binPaths = Collections.singletonList(
			envDir.toPath().resolve(".pixi").resolve("envs").resolve(envName).resolve("bin").toString()
		);
		return createEnv(base, binPaths, launchArgs);
	}
}
