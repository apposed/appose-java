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

import org.apposed.appose.util.Builders;

import java.io.File;
import java.io.IOException;
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

	// ===== DISCOVERY METHODS =====

	/**
	 * Returns the name of this builder (e.g., "pixi", "mamba", "uv").
	 *
	 * @return The builder name.
	 */
	String name();

	/**
	 * Checks if this builder supports the given scheme.
	 *
	 * @param scheme The scheme to check (e.g., "pixi.toml", "environment.yml", "conda", "pypi").
	 * @return true if this builder supports the scheme.
	 */
	boolean supports(String scheme);

	/**
	 * Returns the priority of this builder for scheme resolution.
	 * Higher priority builders are checked first when multiple builders
	 * support the same scheme.
	 *
	 * @return The priority value (higher = preferred).
	 */
	double priority();

	// ===== CORE BUILDING METHODS =====

	/**
	 * Builds the environment at the specified directory.
	 *
	 * @param envDir The directory for the environment.
	 * @return The newly constructed Appose {@link Environment}.
	 * @throws IOException If something goes wrong building the environment.
	 */
	Environment build(File envDir) throws IOException;

	/**
	 * Builds the environment with the specified name.
	 * Default implementation resolves the name to a directory and delegates to build(File).
	 *
	 * @param envName The name for the environment, or null to auto-generate.
	 * @return The newly constructed Appose {@link Environment}.
	 * @throws IOException If something goes wrong building the environment.
	 */
	default Environment build(String envName) throws IOException {
		File envDir = Builders.determineEnvDir(this, envName);
		return build(envDir);
	}

	/**
	 * Builds the environment with an auto-generated name.
	 * Default implementation calls build(String) with null.
	 *
	 * @return The newly constructed Appose {@link Environment}.
	 * @throws IOException If something goes wrong building the environment.
	 */
	default Environment build() throws IOException {
		return build((String) null);
	}

	// ===== FLUENT CONFIGURATION METHODS =====

	/**
	 * Registers a callback method to be invoked when progress happens during environment building.
	 *
	 * @param subscriber Party to inform when build progress happens.
	 * @return This builder instance, for fluent-style programming.
	 */
	Builder subscribeProgress(BaseBuilder.ProgressConsumer subscriber);

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

	// ===== NAMING =====

	/**
	 * Suggests a name for the environment based on builder-specific logic.
	 * Used when no explicit name is provided.
	 *
	 * @return A suggested environment name.
	 */
	String suggestEnvName();
}
