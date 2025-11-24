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

package org.apposed.appose.builder;

import org.apposed.appose.BuilderFactory;
import org.apposed.appose.Nullable;
import org.apposed.appose.util.Plugins;

import java.io.File;
import java.util.List;

/**
 * Utility class for discovering and managing environment builder factories.
 *
 * @author Curtis Rueden
 */
public final class Builders {

	private Builders() {
		// Prevent instantiation of utility class.
	}

	/** All known {@link BuilderFactory} implementations, in priority order. */
	private static final List<BuilderFactory> BUILDERS = Plugins.discover(BuilderFactory.class,
		(a, b) -> Double.compare(b.priority(), a.priority()));

	/**
	 * Finds the first factory capable of building a particular type of environment.
	 * Factories are checked in priority order.
	 *
	 * @param envType The environment type to target.
	 * @return A factory supporting that environment type, or null if not found.
	 */
	public static @Nullable BuilderFactory findFactoryByEnvType(String envType) {
		return Plugins.find(BUILDERS, factory -> factory.envType().equalsIgnoreCase(envType));
	}

	/**
	 * Finds the first factory that supports the given scheme.
	 * Factories are checked in priority order.
	 *
	 * @param scheme The scheme to find a factory for.
	 * @return The first factory that supports the scheme, or null if none found.
	 */
	public static @Nullable BuilderFactory findFactoryByScheme(String scheme) {
		return Plugins.find(BUILDERS, factory -> factory.supportsScheme(scheme));
	}

	/**
	 * Finds the first factory that can wrap the given environment directory.
	 * Factories are checked in priority order.
	 *
	 * @param envDir The directory to find a factory for.
	 * @return The first factory that can wrap the directory, or null if none found.
	 */
	public static @Nullable BuilderFactory findFactoryForWrapping(String envDir) {
		return findFactoryForWrapping(new File(envDir));
	}

	/**
	 * Finds the first factory that can wrap the given environment directory.
	 * Factories are checked in priority order (highest priority first).
	 *
	 * @param envDir The directory to find a factory for.
	 * @return The first factory that can wrap the directory, or null if none found.
	 */
	public static @Nullable BuilderFactory findFactoryForWrapping(File envDir) {
		return Plugins.find(BUILDERS, factory -> factory.canWrap(envDir));
	}

	/**
	 * Checks if the given directory can be wrapped as a known environment type.
	 * This is a convenience method equivalent to {@code findFactoryForWrapping(envDir) != null}.
	 *
	 * @param envDir The directory to check.
	 * @return true if the directory can be wrapped by any known builder, false otherwise.
	 */
	public static boolean canWrap(String envDir) {
		return canWrap(new File(envDir));
	}

	/**
	 * Checks if the given directory can be wrapped as a known environment type.
	 * This is a convenience method equivalent to {@code findFactoryForWrapping(envDir) != null}.
	 *
	 * @param envDir The directory to check.
	 * @return true if the directory can be wrapped by any known builder, false otherwise.
	 */
	public static boolean canWrap(File envDir) {
		return findFactoryForWrapping(envDir) != null;
	}

	/**
	 * Returns the given directory's environment type.
	 * This is a convenience method equivalent to {@code findFactoryForWrapping(envDir).type()}.
	 *
	 * @param envDir The directory to check.
	 * @return The environment type (e.g., "pixi", "mamba", "uv"), or null if not a known environment.
	 */
	public static @Nullable String envType(String envDir) {
		return envType(new File(envDir));
	}

	/**
	 * Returns the given directory's environment type.
	 * This is a convenience method equivalent to {@code findFactoryForWrapping(envDir).type()}.
	 *
	 * @param envDir The directory to check.
	 * @return The environment type (e.g., "pixi", "mamba", "uv"), or null if not a known environment.
	 */
	public static @Nullable String envType(File envDir) {
		BuilderFactory factory = findFactoryForWrapping(envDir);
		return factory == null ? null : factory.envType();
	}
}
