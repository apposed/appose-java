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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Interface for environment builders.
 * <p>
 * Builders are responsible for creating and configuring Appose environments.
 * </p>
 *
 * @author Curtis Rueden
 */
public interface Builder {

	/**
	 * Builds the environment. This is the terminator method for any fluid building chain.
	 *
	 * @return The newly constructed Appose {@link Environment}.
	 * @throws IOException If something goes wrong building the environment.
	 */
	Environment build() throws IOException;

	/**
	 * Sets an environment variable to be passed to worker processes.
	 *
	 * @param key The environment variable name.
	 * @param value The environment variable value.
	 * @return This builder instance, for fluent-style programming.
	 */
	Builder env(String key, String value);

	/**
	 * Sets multiple environment variables to be passed to worker processes.
	 *
	 * @param vars Map of environment variable names to values.
	 * @return This builder instance, for fluent-style programming.
	 */
	Builder env(Map<String, String> vars);

	/**
	 * Sets the name for the environment.
	 * The environment will be created in the standard Appose environments directory with this name.
	 *
	 * @param envName The name for the environment.
	 * @return This builder instance, for fluent-style programming.
	 */
	Builder name(String envName);

	/**
	 * Sets the base directory for the environment.
	 * For many builders, this overrides any name specified via {@link #name(String)}.
	 *
	 * @param envDir The directory for the environment.
	 * @return This builder instance, for fluent-style programming.
	 */
	default Builder base(String envDir) {
		return base(new File(envDir));
	}

	/**
	 * Sets the base directory for the environment.
	 * For many builders, this overrides any name specified via {@link #name(String)}.
	 *
	 * @param envDir The directory for the environment.
	 * @return This builder instance, for fluent-style programming.
	 */
	Builder base(File envDir);

	/**
	 * Adds channels/repositories to search for packages.
	 * The interpretation of channels is builder-specific; e.g.:
	 * - Pixi/Mamba: conda channels (e.g., "conda-forge", "bioconda")
	 * - UV: PyPI index URLs (e.g., custom package indexes)
	 * - Maven: Maven repository URLs
	 * - npm: npm registries
	 *
	 * @param channels Channel names or URLs to add.
	 * @return This builder instance, for fluent-style programming.
	 */
	default Builder channels(String... channels) {
		return channels(Arrays.asList(channels));
	}

	/**
	 * Adds channels/repositories to search for packages.
	 * The interpretation of channels is builder-specific: e.g.:
	 * - Pixi/Mamba: conda channels (e.g., "conda-forge", "bioconda")
	 * - UV: PyPI index URLs (e.g., custom package indexes)
	 * - Maven: Maven repository URLs
	 * - npm: npm registries
	 *
	 * @param channels List of channel names or URLs to add.
	 * @return This builder instance, for fluent-style programming.
	 */
	Builder channels(List<String> channels);

	/**
	 * Registers a callback method to be invoked when progress happens during environment building.
	 *
	 * @param subscriber Party to inform when build progress happens.
	 * @return This builder instance, for fluent-style programming.
	 */
	Builder subscribeProgress(ProgressConsumer subscriber);

	/**
	 * Registers a callback method to be invoked when output is generated during environment building.
	 *
	 * @param subscriber Party to inform when build output happens.
	 * @return This builder instance, for fluent-style programming.
	 */
	Builder subscribeOutput(Consumer<String> subscriber);

	/**
	 * Registers a callback method to be invoked when errors occur during environment building.
	 *
	 * @param subscriber Party to inform when build errors happen.
	 * @return This builder instance, for fluent-style programming.
	 */
	Builder subscribeError(Consumer<String> subscriber);

	/**
	 * Convenience method to log debug output to stderr.
	 * Default implementation subscribes both output and error to stderr.
	 *
	 * @return This builder instance, for fluent-style programming.
	 */
	default Builder logDebug() {
		return subscribeOutput(System.err::println).subscribeError(System.err::println);
	}

	/**
	 * Functional interface for progress callbacks.
	 */
	interface ProgressConsumer {
		void accept(String title, long current, long maximum);
	}
}
