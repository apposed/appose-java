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

import org.apposed.appose.scheme.EnvironmentYmlScheme;
import org.apposed.appose.scheme.PixiTomlScheme;
import org.apposed.appose.scheme.PyProjectTomlScheme;
import org.apposed.appose.scheme.RequirementsTxtScheme;

import java.util.Arrays;
import java.util.List;

/**
 * Utility class for discovering and working with configuration file schemes.
 *
 * @author Curtis Rueden
 */
public final class Schemes {

	/**
	 * All known scheme implementations, in priority order.
	 * <p>
	 * More specific schemes should come first to ensure correct detection.
	 * For example, pyproject.toml must be checked before pixi.toml since
	 * both are TOML files but pyproject.toml has more specific markers.
	 * </p>
	 */
	private static final List<Scheme> ALL = Arrays.asList(
		new PyProjectTomlScheme(),  // Most specific TOML
		new PixiTomlScheme(),        // Generic pixi TOML
		new EnvironmentYmlScheme(),  // YAML format
		new RequirementsTxtScheme()  // Plain text (least specific)
	);

	private Schemes() {
		// Prevent instantiation
	}

	/**
	 * Detects and returns the appropriate scheme for the given configuration content.
	 *
	 * @param content Configuration file content
	 * @return The matching scheme
	 * @throws IllegalArgumentException If no scheme can handle the content
	 */
	public static Scheme fromContent(String content) {
		for (Scheme scheme : ALL) {
			if (scheme.supports(content)) {
				return scheme;
			}
		}
		throw new IllegalArgumentException(
			"Cannot infer scheme from content. Please specify explicitly with .scheme()");
	}

	/**
	 * Returns the scheme with the given name.
	 *
	 * @param name Scheme name (e.g., "pixi.toml", "environment.yml")
	 * @return The matching scheme
	 * @throws IllegalArgumentException If no scheme matches the name
	 */
	public static Scheme fromName(String name) {
		for (Scheme scheme : ALL) {
			if (scheme.name().equals(name)) {
				return scheme;
			}
		}
		throw new IllegalArgumentException("Unknown scheme: " + name);
	}

	/**
	 * Infers the scheme from a filename.
	 *
	 * @param filename The filename
	 * @return The matching scheme
	 * @throws IllegalArgumentException If scheme cannot be inferred from filename
	 */
	public static Scheme fromFilename(String filename) {
		if (filename.endsWith("pixi.toml")) return fromName("pixi.toml");
		if (filename.endsWith("pyproject.toml")) return fromName("pyproject.toml");
		if (filename.endsWith(".yml") || filename.endsWith(".yaml")) return fromName("environment.yml");
		if (filename.endsWith(".txt") || filename.equals("requirements.txt")) return fromName("requirements.txt");

		throw new IllegalArgumentException("Cannot infer scheme from filename: " + filename);
	}
}
