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

package org.apposed.appose.util;

import org.apposed.appose.BuilderFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Utility class for discovering and managing environment builder factories.
 *
 * @author Curtis Rueden
 */
public final class Builders {

	private Builders() {
		// Prevent instantiation of utility class.
	}

	private static List<BuilderFactory> cachedFactories;

	/**
	 * Discovers all available builder factories via ServiceLoader.
	 * Factories are cached and sorted by priority (highest first).
	 *
	 * @return List of discovered factories, sorted by priority.
	 */
	public static synchronized List<BuilderFactory> discoverFactories() {
		if (cachedFactories == null) {
			ServiceLoader<BuilderFactory> loader = ServiceLoader.load(BuilderFactory.class);
			cachedFactories = new ArrayList<>();
			for (BuilderFactory factory : loader) {
				cachedFactories.add(factory);
			}
			// Sort by priority (descending - highest priority first)
			cachedFactories.sort((a, b) -> Double.compare(b.priority(), a.priority()));
		}
		return cachedFactories;
	}

	/**
	 * Finds a factory by name.
	 *
	 * @param name The builder name to search for.
	 * @return The factory with matching name, or null if not found.
	 */
	public static BuilderFactory findFactoryByName(String name) {
		for (BuilderFactory factory : discoverFactories()) {
			if (factory.name().equalsIgnoreCase(name)) {
				return factory;
			}
		}
		return null;
	}

	/**
	 * Finds the first factory that supports the given scheme.
	 * Factories are checked in priority order.
	 *
	 * @param scheme The scheme to find a factory for.
	 * @return The first factory that supports the scheme, or null if none found.
	 */
	public static BuilderFactory findFactoryByScheme(String scheme) {
		for (BuilderFactory factory : discoverFactories()) {
			if (factory.supports(scheme)) {
				return factory;
			}
		}
		return null;
	}

	/**
	 * Finds the first factory that can wrap the given environment directory.
	 * Factories are checked in priority order (highest priority first).
	 *
	 * @param envDir The directory to find a factory for.
	 * @return The first factory that can wrap the directory, or null if none found.
	 */
	public static BuilderFactory findFactoryForWrapping(File envDir) {
		for (BuilderFactory factory : discoverFactories()) {
			if (factory.canWrap(envDir)) {
				return factory;
			}
		}
		return null;
	}

	public static BuilderFactory findFactoryBySource(String source) {
		// FIXME: This logic needs to be extensible by factory.
		// E.g. toml could be pyproject.toml or pixi.toml -- need to check inside it if name is not obvious.
		// Maybe schemes should have their own plugin type... -_-
		if (source == null) throw new NullPointerException("Cannot auto-detect scheme: no source specified");
		if (source.endsWith(".toml")) return findFactoryByName("pixi");
		if (source.endsWith(".yml") || source.endsWith(".yaml")) return findFactoryByName("pixi");
		if (source.endsWith(".txt")) return findFactoryByName("uv");
		throw new IllegalArgumentException("Cannot auto-detect scheme from: " + source);
	}
}
