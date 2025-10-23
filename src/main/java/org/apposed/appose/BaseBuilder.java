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
import org.apposed.appose.util.FilePaths;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base class for environment builders.
 * Provides common implementation for the Builder interface.
 *
 * @param <T> The concrete builder type (self-type parameter).
 * @author Curtis Rueden
 */
public abstract class BaseBuilder<T extends BaseBuilder<T>> implements Builder<T> {

	public final List<ProgressConsumer> progressSubscribers = new ArrayList<>();
	public final List<Consumer<String>> outputSubscribers = new ArrayList<>();
	public final List<Consumer<String>> errorSubscribers = new ArrayList<>();
	public final Map<String, String> envVars = new HashMap<>();
	public final List<String> channels = new ArrayList<>();
	protected String envName;
	protected File envDir;
	protected String sourceContent;  // Configuration file content
	protected String scheme;         // Explicit scheme (e.g., "pixi.toml", "environment.yml")

	// -- Builder methods --

	@Override
	public Environment rebuild() throws IOException {
		File dir = envDir();
		if (dir.exists()) {
			FilePaths.deleteRecursively(dir);
		}
		return build();
	}

	@Override
	public Environment wrap(File envDir) throws IOException {
		FilePaths.ensureDirectory(envDir);
		// Set the base directory and build (which will detect existing env)
		base(envDir);
		return build();
	}

	@Override
	public T env(String key, String value) {
		envVars.put(key, value);
		return (T) this;
	}

	@Override
	public T env(Map<String, String> vars) {
		envVars.putAll(vars);
		return (T) this;
	}

	@Override
	public T name(String envName) {
		this.envName = envName;
		return (T) this;
	}

	@Override
	public T base(File envDir) {
		this.envDir = envDir;
		return (T) this;
	}

	@Override
	public T channels(List<String> channels) {
		this.channels.addAll(channels);
		return (T) this;
	}

	@Override
	public T content(String content) {
		this.sourceContent = content;
		return (T) this;
	}

	@Override
	public T scheme(String scheme) {
		this.scheme = scheme;
		return (T) this;
	}

	@Override
	public T subscribeProgress(ProgressConsumer subscriber) {
		progressSubscribers.add(subscriber);
		return (T) this;
	}

	@Override
	public T subscribeOutput(Consumer<String> subscriber) {
		outputSubscribers.add(subscriber);
		return (T) this;
	}

	@Override
	public T subscribeError(Consumer<String> subscriber) {
		errorSubscribers.add(subscriber);
		return (T) this;
	}

	// -- Internal methods --

	/**
	 * Suggests a name for the environment based on builder-specific logic.
	 * Used when no explicit name is provided.
	 *
	 * @return A suggested environment name, never {@code null}.
	 */
	protected abstract String suggestEnvName();

	protected String envName() {
		return envName != null ? envName : suggestEnvName();
	}

	protected File envDir() {
		if (envDir != null) return envDir;
		// No explicit environment directory set; fall back to
		// a subfolder of the Appose-managed environments directory.
		return Paths.get(Environments.apposeEnvsDir(), envName()).toFile();
	}

	// -- Source handling methods --

	/**
	 * Infers the scheme from configuration file content.
	 *
	 * @param content Configuration file content
	 * @return Inferred scheme
	 * @throws IllegalArgumentException If scheme cannot be inferred
	 */
	protected String inferSchemeFromContent(String content) {
		String trimmed = content.trim();

		// TOML detection (pixi.toml)
		if (trimmed.contains("[project]") ||
		    trimmed.contains("[dependencies]") ||
		    trimmed.matches("(?s).*\\[.*\\].*")) {
			return "pixi.toml";
		}

		// YAML detection (environment.yml)
		if (trimmed.startsWith("name:") ||
		    trimmed.startsWith("channels:") ||
		    trimmed.startsWith("dependencies:") ||
		    trimmed.matches("(?s)^[a-z_]+:\\s*.*")) {
			return "environment.yml";
		}

		// Plain text list (requirements.txt)
		if (trimmed.matches("(?s)^[a-zA-Z0-9_-]+(==|>=|<=|~=|!=)?.*")) {
			return "requirements.txt";
		}

		throw new IllegalArgumentException(
			"Cannot infer scheme from content. Please specify explicitly with .scheme()");
	}

	/**
	 * Infers the scheme from a filename.
	 *
	 * @param filename The filename
	 * @return Inferred scheme
	 * @throws IllegalArgumentException If scheme cannot be inferred
	 */
	protected String inferSchemeFromFilename(String filename) {
		if (filename.endsWith("pixi.toml")) return "pixi.toml";
		if (filename.endsWith(".yml") || filename.endsWith(".yaml")) return "environment.yml";
		if (filename.endsWith(".txt") || filename.equals("requirements.txt")) return "requirements.txt";

		throw new IllegalArgumentException(
			"Cannot infer scheme from filename: " + filename);
	}

	/**
	 * Gets the appropriate filename for a given scheme.
	 *
	 * @param scheme The scheme
	 * @return Filename for that scheme
	 */
	protected String getFilenameForScheme(String scheme) {
		switch (scheme) {
			case "pixi.toml": return "pixi.toml";
			case "environment.yml": return "environment.yml";
			case "requirements.txt": return "requirements.txt";
			default: throw new IllegalArgumentException("Unknown scheme: " + scheme);
		}
	}
}
