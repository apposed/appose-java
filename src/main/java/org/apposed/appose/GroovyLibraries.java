/*-
 * #%L
 * Appose: multi-language interprocess cooperation with shared memory.
 * %%
 * Copyright (C) 2023 - 2026 Appose developers.
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

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.tools.GroovyClass;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Worker-side support for library code registered via
 * {@link Service#importLibrary}.
 * <p>
 * A library is a set of Groovy source files, which the service sends to the
 * worker. Once registered, the library's classes are visible to every task,
 * via normal {@code import} statements, through the class loader returned by
 * {@link #classLoader()}. Because those classes are loaded only once, their
 * static state persists across tasks, making them a natural home for expensive
 * objects such as loaded models, without any need for {@code task.export}.
 * </p>
 * <p>
 * Registration compiles the library's sources, but does not initialize its
 * classes; their static initializers run when a task first uses them.
 * </p>
 */
public final class GroovyLibraries {

	private GroovyLibraries() {
		// Prevent instantiation of utility class.
	}

	/** Registered libraries, by name. */
	private static final Map<String, Library> libraries = new LinkedHashMap<>();

	private static final ClassLoader classLoader = new ClassLoader(GroovyLibraries.class.getClassLoader()) {
		@Override
		protected Class<?> findClass(String name) throws ClassNotFoundException {
			for (Library library : snapshot()) {
				Class<?> c = library.loader.findLibraryClass(name);
				if (c != null) return c;
			}
			throw new ClassNotFoundException(name);
		}

		@Override
		protected URL findResource(String name) {
			for (Library library : snapshot()) {
				URL url = library.loader.findResource(name);
				if (url != null) return url;
			}
			return null;
		}

		@Override
		protected Enumeration<URL> findResources(String name) throws IOException {
			List<URL> urls = new ArrayList<>();
			for (Library library : snapshot()) {
				urls.addAll(Collections.list(library.loader.findResources(name)));
			}
			return Collections.enumeration(urls);
		}
	};

	/**
	 * Gets the class loader through which registered libraries are visible.
	 * Scripts should be evaluated with this loader as their parent.
	 */
	public static ClassLoader classLoader() {
		return classLoader;
	}

	/**
	 * Makes library code available to scripts in this process.
	 * <p>
	 * Registering a library whose source is unchanged is a no-op, so any
	 * already-initialized classes (and the state they hold) are retained. If
	 * the source has changed, the library's classes are replaced, so that
	 * subsequent scripts see the new code.
	 * </p>
	 *
	 * @param name The name of the library, identifying it for re-registration.
	 * @param files Map from relative POSIX path to source code.
	 * @param origin Path of the library on the service side.
	 * @param directory Whether the library is a directory (rather than a single
	 *          source file). If so, and the directory exists, it provides the
	 *          library's resources.
	 */
	public static synchronized void register(String name, Map<String, String> files,
		String origin, boolean directory)
	{
		Library existing = libraries.get(name);
		if (existing != null && existing.files.equals(files)) return;
		libraries.put(name, new Library(files, origin, directory));
	}

	private static synchronized List<Library> snapshot() {
		return new ArrayList<>(libraries.values());
	}

	private static class Library {

		final Map<String, String> files;
		final LibraryLoader loader;

		Library(Map<String, String> files, String origin, boolean directory) {
			this.files = new HashMap<>(files);
			URL[] urls = new URL[0];
			File dir = new File(origin);
			if (directory && dir.isDirectory()) {
				try {
					urls = new URL[] { dir.toURI().toURL() };
				}
				catch (MalformedURLException e) {
					// NB: Resources are unavailable, but the code still works.
				}
			}
			loader = new LibraryLoader(urls);

			// Compile all sources together, so that they may refer to each other.
			GroovyClassLoader gcl = new GroovyClassLoader(GroovyLibraries.class.getClassLoader());
			CompilationUnit cu = new CompilationUnit(CompilerConfiguration.DEFAULT, null, gcl);
			files.forEach((path, source) -> {
				String filename = directory ? origin + "/" + path : origin;
				cu.addSource(filename, source);
			});
			cu.compile(Phases.CLASS_GENERATION);
			for (Object o : cu.getClasses()) {
				GroovyClass c = (GroovyClass) o;
				loader.bytecode.put(c.getName(), c.getBytes());
			}
		}
	}

	/**
	 * Class loader for one library's compiled classes, plus its resources.
	 * <p>
	 * NB: A plain URLClassLoader, rather than a GroovyClassLoader, so that the
	 * library's source files on disk (alongside its resources) are never
	 * compiled: the library's code comes only from the registered source.
	 * </p>
	 */
	private static class LibraryLoader extends URLClassLoader {

		final Map<String, byte[]> bytecode = new HashMap<>();

		LibraryLoader(URL[] urls) {
			super(urls, GroovyLibraries.class.getClassLoader());
		}

		/** Gets the named class if it belongs to this library, or null if not. */
		synchronized Class<?> findLibraryClass(String name) {
			Class<?> c = findLoadedClass(name);
			if (c != null) return c;
			byte[] bytes = bytecode.get(name);
			return bytes == null ? null : defineClass(name, bytes, 0, bytes.length);
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			// NB: Library classes refer to each other by name, which resolves here.
			Class<?> c = findLibraryClass(name);
			return c != null ? c : super.loadClass(name, resolve);
		}
	}
}
