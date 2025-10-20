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
import java.io.IOException;
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

	/**
	 * Registers a callback method to be invoked when progress happens during environment building.
	 *
	 * @param subscriber Party to inform when build progress happens.
	 * @return This builder instance, for fluent-style programming.
	 */
	public BaseBuilder subscribeProgress(ProgressConsumer subscriber) {
		progressSubscribers.add(subscriber);
		return this;
	}

	/**
	 * Registers a callback method to be invoked when output is generated during environment building.
	 *
	 * @param subscriber Party to inform when build output happens.
	 * @return This builder instance, for fluent-style programming.
	 */
	public BaseBuilder subscribeOutput(Consumer<String> subscriber) {
		outputSubscribers.add(subscriber);
		return this;
	}

	/**
	 * Registers a callback method to be invoked when errors occur during environment building.
	 *
	 * @param subscriber Party to inform when build errors happen.
	 * @return This builder instance, for fluent-style programming.
	 */
	public BaseBuilder subscribeError(Consumer<String> subscriber) {
		errorSubscribers.add(subscriber);
		return this;
	}

	/**
	 * Convenience method to log debug output to stderr.
	 *
	 * @return This builder instance, for fluent-style programming.
	 */
	public BaseBuilder logDebug() {
		return subscribeOutput(System.err::println).subscribeError(System.err::println);
	}

	/**
	 * Sets an environment variable to be passed to worker processes.
	 *
	 * @param key The environment variable name.
	 * @param value The environment variable value.
	 * @return This builder instance, for fluent-style programming.
	 */
	public BaseBuilder env(String key, String value) {
		envVars.put(key, value);
		return this;
	}

	/**
	 * Sets multiple environment variables to be passed to worker processes.
	 *
	 * @param vars Map of environment variable names to values.
	 * @return This builder instance, for fluent-style programming.
	 */
	public BaseBuilder env(Map<String, String> vars) {
		envVars.putAll(vars);
		return this;
	}

	/**
	 * Builds the environment with an auto-generated name.
	 *
	 * @return The newly constructed Appose {@link Environment}.
	 * @throws IOException If something goes wrong building the environment.
	 */
	public Environment build() throws IOException {
		return build((String) null);
	}

	// ===== BUILDER INTERFACE IMPLEMENTATION =====

	/**
	 * Builds the environment at the specified directory.
	 * Concrete builders must implement this method.
	 *
	 * @param envDir The directory for the environment.
	 * @return The newly constructed Appose {@link Environment}.
	 * @throws IOException If something goes wrong building the environment.
	 */
	@Override
	public abstract Environment build(File envDir) throws IOException;

	/**
	 * Suggests a name for the environment based on builder-specific logic.
	 * Concrete builders must implement this method.
	 *
	 * @return A suggested environment name.
	 */
	@Override
	public abstract String suggestEnvName();
}
