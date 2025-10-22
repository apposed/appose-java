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

package org.apposed.appose.mamba;

import org.apposed.appose.BaseBuilder;
import org.apposed.appose.Builder;
import org.apposed.appose.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Type-safe builder for Micromamba-based environments.
 *
 * @author Curtis Rueden
 */
public final class MambaBuilder extends BaseBuilder<MambaBuilder> {

	public MambaBuilder() {}

	public MambaBuilder(String source) {
		this.sourceFile = source;
	}

	public MambaBuilder(String source, String scheme) {
		this.sourceFile = source;
		this.scheme = scheme;
	}

	// -- Builder methods --

	@Override
	public String name() {
		return "mamba";
	}

	/**
	 * Adds conda channels to search for packages.
	 *
	 * @param channels Channel names (e.g., "conda-forge", "bioconda")
	 * @return This builder instance, for fluent-style programming.
	 */
	@Override
	public MambaBuilder channels(String... channels) {
		return super.channels(channels);
	}

	@Override
	public Environment build() throws IOException {
		File envDir = envDir();

		// Check for incompatible existing environments
		if (new File(envDir, ".pixi").isDirectory()) {
			throw new IOException("Cannot use MambaBuilder: environment already managed by Pixi at " + envDir);
		}
		if (new File(envDir, "pyvenv.cfg").exists()) {
			throw new IOException("Cannot use MambaBuilder: environment already managed by UV/venv at " + envDir);
		}

		// Resolve configuration content (from file, content string, or null)
		String configContent = resolveConfigContent();

		// Is this envDir an already-existing conda directory?
		boolean isCondaDir = new File(envDir, "conda-meta").isDirectory();
		if (isCondaDir && configContent == null) {
			// Environment already exists and no new config, just wrap it
			return createEnvironment(envDir);
		}

		// Building a new environment - config content is required
		if (configContent == null) {
			throw new IllegalStateException("No source specified for MambaBuilder. Use .file() or .content()");
		}

		// Infer scheme if not explicitly set
		if (scheme == null) {
			if (sourceFile != null) {
				scheme = inferSchemeFromFilename(new File(sourceFile).getName());
			} else {
				scheme = inferSchemeFromContent(configContent);
			}
		}

		if (!"environment.yml".equals(scheme)) {
			throw new IllegalArgumentException("MambaBuilder only supports environment.yml scheme, got: " + scheme);
		}

		Mamba mamba = new Mamba();

		// Set up progress/output consumers.
		mamba.setOutputConsumer(msg -> outputSubscribers.forEach(sub -> sub.accept(msg)));
		mamba.setErrorConsumer(msg -> errorSubscribers.forEach(sub -> sub.accept(msg)));
		mamba.setMambaDownloadProgressConsumer((cur, max) -> {
			progressSubscribers.forEach(subscriber -> subscriber.accept("Downloading micromamba", cur, max));
		});

		// Pass along intended build configuration.
		mamba.setEnvVars(envVars);

		// Check for unsupported features
		if (!channels.isEmpty()) {
			throw new UnsupportedOperationException(
				"MambaBuilder does not yet support programmatic channel configuration. " +
				"Please specify channels in your environment.yml file.");
		}

		try {
			mamba.installMicromamba();

			// Two-step build: create empty env, write config, then update
			// Step 1: Create empty environment
			mamba.create(envDir);

			// Step 2: Write environment.yml to envDir
			File envYaml = new File(envDir, "environment.yml");
			Files.write(envYaml.toPath(), configContent.getBytes(StandardCharsets.UTF_8));

			// Step 3: Update environment from yml
			mamba.update(envDir, envYaml);

			return createEnvironment(envDir);
		} catch (InterruptedException | URISyntaxException e) {
			throw new IOException(e);
		}
	}

	private Environment createEnvironment(File envDir) {
		Mamba mamba = new Mamba();
		String base = envDir.getAbsolutePath();
		List<String> launchArgs = Arrays.asList(mamba.mambaCommand, "run", "-p", base);
		List<String> binPaths = Collections.singletonList(envDir.toPath().resolve("bin").toString());

		return new Environment() {
			@Override public String base() { return base; }
			@Override public List<String> binPaths() { return binPaths; }
			@Override public List<String> launchArgs() { return launchArgs; }
			@Override public Map<String, String> envVars() { return MambaBuilder.this.envVars; }
			@Override public Builder<?> builder() { return MambaBuilder.this; }
		};
	}

	@Override
	public String suggestEnvName() {
		// Try to extract name from environment.yml
		if (sourceFile != null) {
			File f = new File(sourceFile);
			if (f.exists()) {
				try (BufferedReader reader = new BufferedReader(new FileReader(sourceFile))) {
					String line;
					while ((line = reader.readLine()) != null) {
						line = line.trim();
						if (line.startsWith("name:")) {
							String value = line.substring(5).trim().replace("\"", "");
							if (!value.isEmpty()) return value;
						}
					}
				} catch (IOException e) {
					// Fall through to default
				}
			}
		}
		return "appose-mamba-env";
	}
}
