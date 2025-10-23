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

/**
 * Factory interface for creating builder instances.
 * <p>
 * Implementations are discovered at runtime and managed by
 * the {@link org.apposed.appose.util.Builders} utility class.
 * </p>
 *
 * @author Curtis Rueden
 */
public interface BuilderFactory {
	/**
	 * Creates a new builder instance with no configuration.
	 *
	 * @return A new builder instance
	 */
	Builder<?> createBuilder();

	/**
	 * Creates a new builder instance configured with a source file.
	 *
	 * @param source The source file path
	 * @return A new configured builder instance
	 */
	Builder<?> createBuilder(String source) throws IOException;

	/**
	 * Creates a new builder instance configured with a source file and scheme.
	 *
	 * @param source The source file path
	 * @param scheme The scheme (e.g., "environment.yml", "pixi.toml")
	 * @return A new configured builder instance
	 */
	Builder<?> createBuilder(String source, String scheme) throws IOException;

	/**
	 * Returns the name of this builder (e.g., "pixi", "mamba", "system").
	 *
	 * @return The builder name
	 */
	String name();

	/**
	 * Checks if this builder supports the given scheme.
	 *
	 * @param scheme The scheme to check (e.g., "environment.yml", "conda", "pypi")
	 * @return true if this builder supports the scheme
	 */
	boolean supportsScheme(String scheme);

	/**
	 * Checks if this builder can build from the given source file.
	 * This allows builders to inspect file extensions or content to determine compatibility.
	 *
	 * @param source The source file path to check
	 * @return true if this builder can build from the source
	 */
	boolean supportsSource(String source);

	/**
	 * Returns the priority of this builder for scheme resolution.
	 * Higher priority builders are preferred when multiple builders
	 * support the same scheme.
	 *
	 * @return The priority value (higher = more preferred)
	 */
	double priority();

	/**
	 * Checks if this builder can wrap the given existing environment directory.
	 * Used by {@link Appose#wrap(File)} to auto-detect environment type.
	 *
	 * @param envDir The directory to check
	 * @return true if this builder can wrap the directory
	 */
	boolean canWrap(File envDir);
}
