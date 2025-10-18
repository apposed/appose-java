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
import java.util.List;
import java.util.ServiceLoader;

/**
 * Utility class for discovering and managing environment builders.
 *
 * @author Curtis Rueden
 */
public class Builders {

	private static List<Builder> cachedBuilders;

	/**
	 * Discovers all available builders via ServiceLoader.
	 * Builders are cached and sorted by priority (highest first).
	 *
	 * @return List of discovered builders, sorted by priority.
	 */
	public static synchronized List<Builder> discover() {
		if (cachedBuilders == null) {
			ServiceLoader<Builder> loader = ServiceLoader.load(Builder.class);
			cachedBuilders = new ArrayList<>();
			for (Builder builder : loader) {
				cachedBuilders.add(builder);
			}
			// Sort by priority (descending - highest priority first)
			cachedBuilders.sort((a, b) -> Double.compare(b.priority(), a.priority()));
		}
		return cachedBuilders;
	}

	/**
	 * Finds a builder by name.
	 *
	 * @param name The builder name to search for.
	 * @return The builder with matching name, or null if not found.
	 */
	public static Builder findByName(String name) {
		for (Builder builder : discover()) {
			if (builder.name().equalsIgnoreCase(name)) {
				return builder;
			}
		}
		return null;
	}

	/**
	 * Finds the first builder that supports the given scheme.
	 * Builders are checked in priority order.
	 *
	 * @param scheme The scheme to find a builder for.
	 * @return The first builder that supports the scheme, or null if none found.
	 */
	public static Builder findByScheme(String scheme) {
		for (Builder builder : discover()) {
			if (builder.supports(scheme)) {
				return builder;
			}
		}
		return null;
	}

	/**
	 * Determines the environment directory based on the name and builder type.
	 *
	 * @param builder The builder to get suggestions from.
	 * @param envName The environment name, or null to use builder's suggestion.
	 * @return The environment directory.
	 */
	public static File determineEnvDir(Builder builder, String envName) {
		if (envName == null) {
			envName = builder.suggestEnvName();
		}
		Path envPath = Paths.get(System.getProperty("user.home"), ".local", "share", "appose", envName);
		return envPath.toFile();
	}
}
