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
import org.apposed.appose.EnvStatus;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

	/** Lock file content for reproducible builds (e.g., uv.lock, pixi.lock), or null when unset. */
	protected String lockContent;

	/** Explicit scheme (e.g., "pixi.toml", "environment.yml"). */
	protected Scheme scheme;

	// -- Builder methods --

	@Override
	public void delete() throws IOException {
		File dir = resolveEnvDir();
		if (dir != null && dir.exists()) FilePaths.deleteRecursively(dir);
	}

	@Override
	public EnvStatus status() {
		File dir;
		try {
			dir = resolveEnvDir();
		}
		catch (IllegalStateException | IllegalArgumentException e) {
			// No name, directory or (recognizable) content: no target location to speak of.
			return EnvStatus.MISSING;
		}
		if (dir == null) return EnvStatus.MISSING;
		if (incompatibility(dir) != null) return EnvStatus.INCOMPATIBLE;
		if (!hasEnvironment(dir)) return EnvStatus.MISSING;
		if (!new File(dir, "appose.json").isFile()) return EnvStatus.EXTERNAL;
		try {
			return isUpToDate(dir) ? EnvStatus.CURRENT : EnvStatus.STALE;
		}
		catch (IOException | IllegalArgumentException e) {
			return EnvStatus.STALE;
		}
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
	public T lockContent(String lockContent) {
		this.lockContent = lockContent;
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
		// Note: build() infers the scheme from content before recording state,
		// so status() must do likewise for the comparison to match.
		Scheme s = scheme != null ? scheme : content != null ? Schemes.fromContent(content) : null;
		state.put("scheme", s != null ? s.name() : null);
		state.put("channels", channels);
		state.put("flags", flags);
		state.put("envVars", new TreeMap<>(envVars));
		// Record a hash of the lock file content so that lock changes trigger a
		// rebuild via the exact-match isUpToDate() comparison. Only added when a
		// lock is supplied, so lock-less builds produce a byte-identical
		// appose.json (backward compatibility).
		if (lockContent != null) state.put("lockHash", computeLockHash(lockContent));
	}

	/**
	 * Computes a SHA-256 hash (lowercase hex) of the given content, or null if
	 * the content is null. Used to snapshot lock files into {@code appose.json}
	 * without storing the (potentially large) lock content verbatim.
	 *
	 * @param content The content to hash (e.g., lock file content).
	 * @return The SHA-256 hex hash, or null if content is null.
	 */
	protected static String computeLockHash(String content) {
		if (content == null) return null;
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
			char[] hex = new char[digest.length * 2];
			for (int i = 0; i < digest.length; i++) {
				int v = digest[i] & 0xff;
				hex[i * 2] = Character.forDigit(v >>> 4, 16);
				hex[i * 2 + 1] = Character.forDigit(v & 0x0f, 16);
			}
			return new String(hex);
		}
		catch (NoSuchAlgorithmException e) {
			// SHA-256 is mandated by the JVM specification; this never happens.
			throw new RuntimeException("SHA-256 algorithm not available", e);
		}
	}

	/**
	 * Returns true if {@code appose.json} in the given directory matches
	 * the current builder's state. See also {@link #status()}, which also
	 * checks that the environment itself is present.
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

	/**
	 * Reads the builder state recorded in {@code appose.json} in the given directory.
	 *
	 * @param envDir The environment directory.
	 * @return The recorded state, or null if absent or unreadable.
	 * @throws IOException If reading {@code appose.json} fails.
	 */
	protected static Map<?, ?> readApposeState(File envDir) throws IOException {
		File apposeJson = new File(envDir, "appose.json");
		if (!apposeJson.isFile()) return null;
		Object state;
		try {
			state = Json.parseJson(FilePaths.readText(apposeJson));
		}
		catch (RuntimeException e) {
			return null; // Unreadable state; the env will just look stale.
		}
		return state instanceof Map ? (Map<?, ?>) state : null;
	}

	/**
	 * Restores the lock file content of a wrapped environment, so that
	 * {@link #rebuild()} reproduces it even after the directory is deleted.
	 * <p>
	 * Note: package managers write a lock file even for lock-less builds, so
	 * the lock is only restored if {@code appose.json} records that the
	 * environment was built from one.
	 * </p>
	 *
	 * @param envDir The environment directory.
	 * @param lockFileName Name of the lock file (e.g., "uv.lock", "pixi.lock").
	 * @throws IOException If reading the state or lock file fails.
	 */
	protected void restoreLockContent(File envDir, String lockFileName) throws IOException {
		if (lockContent != null) return;
		Map<?, ?> state = readApposeState(envDir);
		if (state == null || !state.containsKey("lockHash")) return;
		File lockFile = new File(envDir, lockFileName);
		if (!lockFile.isFile()) return;
		lockContent = FilePaths.readText(lockFile);
	}

	/**
	 * Tests whether the given directory actually contains a usable environment
	 * of this builder's type, regardless of who built it. Used by {@link #status()}.
	 * Note: unlike {@code BuilderFactory#canWrap}, this requires the environment
	 * itself to be present, not merely its configuration.
	 *
	 * @param envDir The target environment directory.
	 * @return True iff the environment is present.
	 */
	protected boolean hasEnvironment(File envDir) {
		return envDir.isDirectory();
	}

	/**
	 * Checks whether the given directory holds an environment of a different
	 * type, which this builder cannot build over. Used by {@link #status()}
	 * and {@link #checkCompatibility}.
	 *
	 * @param envDir The target environment directory.
	 * @return A description of the incompatibility, or null if compatible.
	 */
	protected String incompatibility(File envDir) {
		return null;
	}

	/**
	 * Fails if the given directory holds an environment of a different type.
	 *
	 * @param envDir The target environment directory.
	 * @throws BuildException If the environment is incompatible.
	 */
	protected void checkCompatibility(File envDir) throws BuildException {
		String reason = incompatibility(envDir);
		if (reason != null) {
			throw new BuildException(this, "Cannot use " + getClass().getSimpleName() + ": " + reason + " at " + envDir);
		}
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

	/** Functional interface for activating a named sub-environment. */
	@FunctionalInterface
	public interface Activator {
		Environment activate(String name) throws BuildException;
	}

	protected Environment createEnv(String base, List<String> binPaths, List<String> launchArgs) {
		return createEnv(base, binPaths, launchArgs, null);
	}

	protected Environment createEnv( String base, List<String> binPaths,
		List<String> launchArgs, Activator activator)
	{
		return new Environment() {
			@Override public String base() { return base; }
			@Override public List<String> binPaths() { return binPaths; }
			@Override public List<String> launchArgs() { return launchArgs; }
			@Override public Map<String, String> envVars() { return envVars; }
			@Override public Builder<?> builder() { return BaseBuilder.this; }

			@Override
			public Environment activate(String name) throws BuildException {
				if (activator == null) {
					throw new UnsupportedOperationException(
						getClass().getSimpleName() + " does not support named sub-environments"
					);
				}
				return activator.activate(name);
			}
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
