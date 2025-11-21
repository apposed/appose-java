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

import org.apposed.appose.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Utility class for discovering and sorting through plugins.
 * <p>
 * Plugins are declared as implementations in {@code META-INF/services},
 * so that Java's {@link ServiceLoader} can discover them in an extensible way.
 * </p>
 *
 * @author Curtis Rueden
 */
public final class Plugins {

	private Plugins() {
		// Prevent instantiation of utility class.
	}

	/**
	 * Discovers all available implementations of an interface.
	 * Instances are sorted according to the given comparator function.
	 *
	 * @return List of discovered instances.
	 */
	public static <T> List<T> discover(Class<T> iface, Comparator<T> comparator) {
		ServiceLoader<T> loader = ServiceLoader.load(iface);
		List<T> singletons = new ArrayList<>();
		loader.forEach(singletons::add);
		if (comparator != null) singletons.sort(comparator);
		return singletons;
	}

	public static <E extends Throwable, T> @Nullable T find(Collection<T> plugins,
															Predicate<T> condition)
	{
		return plugins.stream().filter(condition).findFirst().orElse(null);
	}

	public static <T, U> @Nullable U create(Class<T> iface, Function<T, U> creator) {
		ServiceLoader<T> loader = ServiceLoader.load(iface);
		for (T factory : loader) {
			U result = creator.apply(factory);
			if (result != null) return result;
		}
		return null;
	}
}
