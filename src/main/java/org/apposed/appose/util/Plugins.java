package org.apposed.appose.util;

import org.apposed.appose.SharedMemory;
import org.apposed.appose.ShmFactory;

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

	public static <E extends Throwable, T> T find(Collection<T> plugins,
		Predicate<T> condition)
	{
		return plugins.stream().filter(condition).findFirst().orElse(null);
	}

	public static <T, U> U create(Class<T> iface, Function<T, U> creator) {
		ServiceLoader<T> loader = ServiceLoader.load(iface);
		for (T factory : loader) {
			U result = creator.apply(factory);
			if (result != null) return result;
		}
		return null;
	}
}
