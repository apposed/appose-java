package org.apposed.appose.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class Plugins {

	private Plugins() {
		// Prevent instantiation of utility class.
	}

	/**
	 * Discovers all available implementations of an interface via ServiceLoader.
	 * Instances are sorted according to the given comparator function.
	 *
	 * @return List of discovered instances.
	 */
	static <T> List<T> discover(Class<T> iface, Comparator<T> comparator) {
		ServiceLoader<T> loader = ServiceLoader.load(iface);
		List<T> singletons = new ArrayList<>();
		loader.forEach(singletons::add);
		if (comparator != null) singletons.sort(comparator);
		return singletons;
	}

	static <E extends Throwable, T> T find(Collection<T> plugins,
		Predicate<T> condition, String failMessage)
	{
		return plugins.stream().filter(condition).findFirst()
			.orElseThrow(() -> new IllegalArgumentException(failMessage));
	}
}
