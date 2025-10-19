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
import org.apposed.appose.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/**
 * Type-safe builder for Micromamba-based environments.
 *
 * @author Curtis Rueden
 */
public class MambaBuilder extends BaseBuilder {

	private String source;
	private String scheme;

	// Public no-arg constructor for ServiceLoader
	public MambaBuilder() {}

	public MambaBuilder(String source) {
		this.source = source;
	}

	public MambaBuilder(String source, String scheme) {
		this.source = source;
		this.scheme = scheme;
	}

	@Override
	public Environment build(File envDir) throws IOException {
		// Check for incompatible existing environments
		if (new File(envDir, ".pixi").isDirectory()) {
			throw new IOException("Cannot use MambaBuilder: environment already managed by Pixi at " + envDir);
		}
		if (new File(envDir, "pyvenv.cfg").exists()) {
			throw new IOException("Cannot use MambaBuilder: environment already managed by UV/venv at " + envDir);
		}

		// Is this envDir an already-existing conda directory?
		boolean isCondaDir = new File(envDir, "conda-meta").isDirectory();
		if (isCondaDir) {
			// Environment already exists, just wrap it
			return createEnvironment(envDir);
		}

		// Building a new environment - source file is required
		if (source == null) {
			throw new IllegalStateException("No source file specified for MambaBuilder");
		}

		File sourceFile = new File(source);
		if (!sourceFile.exists()) {
			throw new IOException("Source file not found: " + source);
		}

		// Determine scheme if not specified
		if (scheme == null) {
			if (source.endsWith(".yml") || source.endsWith(".yaml")) {
				scheme = "environment.yml";
			} else {
				throw new IllegalArgumentException("MambaBuilder only supports environment.yml files");
			}
		}

		if (!"environment.yml".equals(scheme)) {
			throw new IllegalArgumentException("MambaBuilder only supports environment.yml scheme, got: " + scheme);
		}

		// Read environment.yml content
		String yamlContent = new String(Files.readAllBytes(sourceFile.toPath()));

		Mamba mamba = new Mamba(Mamba.BASE_PATH);

		// Setup progress/output consumers
		mamba.setOutputConsumer(msg -> outputSubscribers.forEach(sub -> sub.accept(msg)));
		mamba.setErrorConsumer(msg -> errorSubscribers.forEach(sub -> sub.accept(msg)));
		mamba.setMambaDownloadProgressConsumer((cur, max) -> {
			progressSubscribers.forEach(subscriber -> subscriber.accept("Downloading micromamba", cur, max));
		});

		try {
			mamba.installMicromamba();

			// Create environment from YAML
			// Note: Simplified - no directory contortions!
			// If envDir exists but isn't a conda dir, mamba will fail with clear error
			mamba.createWithYaml(envDir, sourceFile.getAbsolutePath());

			return createEnvironment(envDir);
		} catch (InterruptedException | URISyntaxException e) {
			throw new IOException(e);
		}
	}

	private Environment createEnvironment(File envDir) {
		Mamba mamba = new Mamba(Mamba.BASE_PATH);
		String base = envDir.getAbsolutePath();
		List<String> launchArgs = Arrays.asList(mamba.mambaCommand, "run", "-p", base);
		List<String> binPaths = Arrays.asList(envDir.toPath().resolve("bin").toString());

		return new Environment() {
			@Override public String base() { return base; }
			@Override public List<String> binPaths() { return binPaths; }
			@Override public List<String> launchArgs() { return launchArgs; }
		};
	}

	@Override
	public String name() {
		return "mamba";
	}

	@Override
	public boolean supports(String scheme) {
		return "environment.yml".equals(scheme);
	}

	@Override
	public double priority() {
		return 50.0; // Lower priority than pixi for environment.yml
	}

	@Override
	public String suggestEnvName() {
		// Try to extract name from environment.yml
		if (source != null) {
			File sourceFile = new File(source);
			if (sourceFile.exists()) {
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
