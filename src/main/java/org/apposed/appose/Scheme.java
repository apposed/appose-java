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

/**
 * Represents a configuration file scheme for environment builders.
 * <p>
 * Each scheme encapsulates format-specific knowledge about a configuration file type
 * (e.g., pixi.toml, pyproject.toml, environment.yml, requirements.txt).
 * </p>
 *
 * @author Curtis Rueden
 */
public interface Scheme {

	/**
	 * Gets the name of this scheme.
	 *
	 * @return The scheme name (e.g., "pixi.toml", "environment.yml")
	 */
	String name();

	/**
	 * Extracts the environment name from configuration content.
	 * <p>
	 * If no name is found in the content, returns {@code null}.
	 * </p>
	 *
	 * @param content Configuration file content
	 * @return The environment name, or {@code null} if not found
	 */
	String envName(String content);

	/**
	 * Tests whether this scheme can handle the given configuration content.
	 * <p>
	 * Implementations should use heuristics to detect their format.
	 * </p>
	 *
	 * @param content Configuration file content
	 * @return {@code true} if this scheme supports the content format
	 */
	boolean supports(String content);
}
