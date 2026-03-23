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
import org.apposed.appose.Builder;
import org.apposed.appose.Environment;
import org.apposed.appose.Scheme;
import org.apposed.appose.util.Environments;
import org.apposed.appose.util.FilePaths;
import org.apposed.appose.util.Json;
import org.apposed.appose.scheme.Schemes;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Base class for environment builders.
 * Provides common implementation for the Builder interface.
 *
 * @param <T> The concrete builder type (self-type parameter).
 * @author Curtis Rueden
 */
public abstract class BaseBuilder<T extends BaseBuilder<T>> implements Builder<T> {

	protected final List<ProgressConsumer> progressSubscribers = new ArrayList<>();
	protected final List<Consumer<String>> outputSubscribers = new ArrayList<>();
	protected final List<Consumer<String>> errorSubscribers = new ArrayList<>();
	protected final Map<String, String> envVars = new HashMap<>();
	protected final List<String> channels = new ArrayList<>();
	protected final List<String> flags = new ArrayList<>();
	protected String envName;
	protected File envDir;

	/** Configuration file content. */
	protected String content;

	/** Explicit scheme (e.g., "pixi.toml", "environment.yml"). */
	protected Scheme scheme;

	// -- Builder methods --

	@Override
	public void delete() throws IOException {
		File dir = resolveEnvDir();
		if (dir.exists()) FilePaths.deleteRecursively(dir);
	}

	@Override
	public Environment wrap(File envDir) throws BuildException {
		try {
			FilePaths.ensureDirectory(envDir);
		}
		catch (IOException e) {
			throw new BuildException(this, e);
		}
		// Set the base directory and build (which will detect existing env).
		base(envDir);
		return build();
	}

	@Override
	public T env(String key, String value) {
		envVars.put(key, value);
		return typedThis();
	}

	@Override
	public T env(Map<String, String> vars) {
		envVars.putAll(vars);
		return typedThis();
	}

	@Override
	public T name(String envName) {
		this.envName = envName;
		return typedThis();
	}

	@Override
	public T base(File envDir) {
		this.envDir = envDir;
		return typedThis();
	}

	@Override
	public T channels(List<String> channels) {
		this.channels.addAll(channels);
		return typedThis();
	}

	@Override
	public T content(String content) {
		this.content = content;
		return typedThis();
	}

	@Override
	public T scheme(String scheme) {
		this.scheme = Schemes.fromName(scheme);
		return typedThis();
	}

	@Override
	public T subscribeProgress(ProgressConsumer subscriber) {
		progressSubscribers.add(subscriber);
		return typedThis();
	}

	@Override
	public T subscribeOutput(Consumer<String> subscriber) {
		outputSubscribers.add(subscriber);
		return typedThis();
	}

	@Override
	public T subscribeError(Consumer<String> subscriber) {
		errorSubscribers.add(subscriber);
		return typedThis();
	}

	@Override
	public T flags(List<String> flags) {
		this.flags.addAll(flags);
		return typedThis();
	}

	// -- Internal methods --

	@SuppressWarnings("unchecked")
	private T typedThis() {
		return (T) this;
	}

	/**
	 * Populates the given map with this builder's state fields for
	 * {@code appose.json} comparison. Subclasses should override this method,
	 * calling {@code super.addStateFields(state)} first, then adding their own fields.
	 *
	 * @param state The map to populate with state fields.
	 */
	protected void addStateFields(Map<String, Object> state) {
		state.put("content", content);
		state.put("scheme", scheme != null ? scheme.name() : null);
		state.put("channels", channels);
		state.put("flags", flags);
		state.put("envVars", new TreeMap<>(envVars));
	}

	/**
	 * Returns true if {@code appose.json} in the given directory matches
	 * the current builder's state, meaning no rebuild is needed.
	 *
	 * @param envDir The environment directory to check.
	 * @return True if up to date, false if a rebuild is needed.
	 * @throws IOException If reading {@code appose.json} fails.
	 */
	protected boolean isUpToDate(File envDir) throws IOException {
		File apposeJson = new File(envDir, "appose.json");
		if (!apposeJson.isFile()) return false;
		String existing = new String(Files.readAllBytes(apposeJson.toPath()), StandardCharsets.UTF_8);
		return existing.equals(buildStateString());
	}

	/**
	 * Writes the current builder state to {@code appose.json} in the given directory.
	 * This should be called after a successful build to record the state,
	 * so that future calls can skip the build when the state is unchanged.
	 *
	 * @param envDir The environment directory.
	 * @throws IOException If writing fails.
	 */
	protected void writeApposeStateFile(File envDir) throws IOException {
		File apposeJson = new File(envDir, "appose.json");
		Files.write(apposeJson.toPath(), buildStateString().getBytes(StandardCharsets.UTF_8));
	}

	/** Determines the environment directory path. */
	protected File resolveEnvDir() {
		if (envDir != null) return envDir;

		// No explicit environment directory set; fall back to
		// a subfolder of the Appose-managed environments directory.
		String dirName = envName != null ? envName :
			// No explicit environment name set; extract name from the source content.
			resolveScheme().envName(content);
		return dirName == null ? null :
			Paths.get(Environments.apposeEnvsDir(), dirName).toFile();
	}

	/** Determines the scheme, detecting from content if needed. */
	protected Scheme resolveScheme() {
		if (scheme != null) return scheme;
		if (content != null) return Schemes.fromContent(content);
		throw new IllegalStateException("Cannot determine scheme: neither scheme nor content is set");
	}

	protected Environment createEnv(String base, List<String> binPaths, List<String> launchArgs) {
		return new Environment() {
			@Override public String base() { return base; }
			@Override public List<String> binPaths() { return binPaths; }
			@Override public List<String> launchArgs() { return launchArgs; }
			@Override public Map<String, String> envVars() { return envVars; }
			@Override public Builder<?> builder() { return BaseBuilder.this; }
		};
	}

	/**
	 * Builds a JSON string representing this builder's current configuration state.
	 * Used to determine whether an existing environment needs to be rebuilt.
	 *
	 * @return JSON string of builder state.
	 */
	private final String buildStateString() {
		Map<String, Object> state = new LinkedHashMap<>();
		state.put("builder", envType());
		addStateFields(state);
		return Json.toJson(state);
	}
}
