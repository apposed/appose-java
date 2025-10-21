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

import java.io.File;
import java.nio.file.Path;
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
 * @author Curtis Rueden
 */
public abstract class BaseBuilder implements Builder {

	public final List<ProgressConsumer> progressSubscribers = new ArrayList<>();
	public final List<Consumer<String>> outputSubscribers = new ArrayList<>();
	public final List<Consumer<String>> errorSubscribers = new ArrayList<>();
	public final Map<String, String> envVars = new HashMap<>();
	public final List<String> channels = new ArrayList<>();
	protected String envName;
	protected File envDir;

	// -- Builder methods --

	@Override
	public Builder subscribeProgress(ProgressConsumer subscriber) {
		progressSubscribers.add(subscriber);
		return this;
	}

	@Override
	public Builder subscribeOutput(Consumer<String> subscriber) {
		outputSubscribers.add(subscriber);
		return this;
	}

	@Override
	public Builder subscribeError(Consumer<String> subscriber) {
		errorSubscribers.add(subscriber);
		return this;
	}

	@Override
	public BaseBuilder env(String key, String value) {
		envVars.put(key, value);
		return this;
	}

	@Override
	public BaseBuilder env(Map<String, String> vars) {
		envVars.putAll(vars);
		return this;
	}

	@Override
	public Builder name(String envName) {
		this.envName = envName;
		return this;
	}

	@Override
	public Builder base(File envDir) {
		this.envDir = envDir;
		return this;
	}

	@Override
	public Builder channels(List<String> channels) {
		this.channels.addAll(channels);
		return this;
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
		String envsDirPath = System.getProperty("appose.envs-dir");
		if (envsDirPath == null) {
			String userHome = System.getProperty("user.home");
			Path p = Paths.get(userHome, ".local", "share", "appose");
			envsDirPath = p.toString();
		}
		String envDirName = envName == null ? suggestEnvName() : envName;
		assert envDirName != null;
		return Paths.get(envsDirPath, envDirName).toFile();
	}
}
