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

import org.apposed.appose.builder.BuildException;
import org.apposed.appose.util.FilePaths;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Environment {

	String base();
	List<String> binPaths();
	List<String> launchArgs();

	/**
	 * Environment variables to set when launching worker processes.
	 * By default, returns an empty map.
	 *
	 * @return A map of environment variable names to values.
	 */
	default Map<String, String> envVars() {
		return Collections.emptyMap();
	}

	/**
	 * Returns the builder that created this environment.
	 * This enables re-configuration and rebuilding of the environment.
	 *
	 * @return The builder instance.
	 */
	Builder<?> builder();

	/**
	 * Returns the type of this environment (e.g., "pixi", "mamba", "uv", "system").
	 * By default, delegates to the builder's name.
	 *
	 * @return The environment type name.
	 */
	default String type() {
		return builder().name();
	}

	/**
	 * Rebuilds this environment from scratch.
	 * This deletes the existing environment directory and rebuilds it using the
	 * current builder configuration.
	 *
	 * @return The newly rebuilt environment.
	 * @throws BuildException If something goes wrong during rebuild.
	 */
	default Environment rebuild() throws BuildException {
		return builder().rebuild();
	}

	/**
	 * Deletes the existing environment directory, if any.
	 *
	 * @return This environment, for fluid chaining.
	 * @throws BuildException If something goes wrong during deletion.
	 */
	default Environment delete() throws BuildException {
		try {
			builder().delete();
		}
		catch (IOException e) {
			throw new BuildException(builder(), e);
		}
		return this;
	}

	/**
	 * Creates a Python script service.
	 * <p>
	 * This is a <b>high level</b> way to create a service, enabling execution of
	 * Python scripts asynchronously on its linked process running a
	 * {@code python_worker}.
	 * </p>
	 * 
	 * @return The newly created service.
	 * @see #groovy To create a service for Groovy script execution.
	 * @throws IOException If something goes wrong starting the worker process.
	 */
	default Service python() throws IOException {
		List<String> pythonExes = Arrays.asList("python", "python3", "python.exe");
		return service(pythonExes, "-c",
			"import appose.python_worker; appose.python_worker.main()")
			.syntax("python");
	}

	/**
	 * Creates a Groovy script service with no additional classpath elements.
	 * <a href="https://groovy-lang.org/">Groovy</a> is a script language for the
	 * JVM, capable of running Java bytecode conveniently and succinctly, as well
	 * as downloading and importing dependencies dynamically at runtime using its
	 * <a href="https://groovy-lang.org/Grape">Grape</a> subsystem.
	 * <p>
	 * This is a <b>high level</b> way to create a service, enabling execution of
	 * Groovy scripts asynchronously on its linked process running a
	 * {@link GroovyWorker}.
	 * </p>
	 * 
	 * @param jvmArgs Command line arguments to pass to the JVM invocation (e.g.
	 *          {@code -Xmx4g}).
	 * @return The newly created service.
	 * @see #groovy(List, String[])
	 * @see #python()
	 * @throws IOException If something goes wrong starting the worker process.
	 */
	default Service groovy(String... jvmArgs) throws IOException {
		return groovy(Collections.emptyList(), jvmArgs);
	}

	/**
	 * Creates a Groovy script service.
	 * <a href="https://groovy-lang.org/">Groovy</a> is a script language for the
	 * JVM, capable of running Java bytecode conveniently and succinctly, as well
	 * as downloading and importing dependencies dynamically at runtime using its
	 * <a href="https://groovy-lang.org/Grape">Grape</a> subsystem.
	 * <p>
	 * This is a <b>high level</b> way to create a service, enabling execution of
	 * Groovy scripts asynchronously on its linked process running a
	 * {@link GroovyWorker}.
	 * </p>
	 * 
	 * @param classPath Additional classpath elements to pass to the JVM via its
	 *          {@code -cp} command line option.
	 * @param jvmArgs Command line arguments to pass to the JVM invocation (e.g.
	 *          {@code -Xmx4g}).
	 * @return The newly created service.
	 * @see #groovy(String[])
	 * @see #python()
	 * @throws IOException If something goes wrong starting the worker process.
	 */
	default Service groovy(List<String> classPath, String... jvmArgs)
		throws IOException
	{
		return java(GroovyWorker.class.getName(), classPath, jvmArgs)
			.syntax("groovy");
	}

	default Service java(String mainClass, String... jvmArgs)
		throws IOException
	{
		return java(mainClass, Collections.emptyList(), jvmArgs);
	}

	default Service java(String mainClass, List<String> classPath,
		String... jvmArgs) throws IOException
	{
		// Collect classpath elements into a set, to avoid duplicate entries.
		Set<String> cp = new LinkedHashSet<>();

		// Ensure that the classpath includes Appose and its dependencies.
		// NB: This list must match Appose's dependencies in pom.xml!
		List<Class<?>> apposeDeps = Arrays.asList(
			org.apposed.appose.GroovyWorker.class, // ------------------------> org.apposed:appose
			org.apache.groovy.util.ScriptRunner.class, // --------------------> org.codehaus.groovy:groovy
			groovy.json.JsonOutput.class, // ---------------------------------> org.codehaus.groovy:groovy-json
			org.apache.ivy.Ivy.class, // -------------------------------------> org.apache.ivy:ivy
			com.sun.jna.Pointer.class, // ------------------------------------> com.sun.jna:jna
			com.sun.jna.platform.linux.LibRT.class, // -----------------------> com.sun.jna:jna-platform
			com.sun.jna.platform.win32.Kernel32.class, // --------------------> com.sun.jna:jna-platform
			org.apache.commons.compress.archivers.ArchiveException.class // --> org.apache.commons:commons-compress
		);
		for (Class<?> depClass : apposeDeps) {
			File location = FilePaths.location(depClass);
			if (location != null) cp.add(location.getCanonicalPath());
		}

		// Append any explicitly requested classpath elements.
		cp.addAll(classPath);

		// Build up the service arguments.
		List<String> args = new ArrayList<>();
		args.add("-cp");
		args.add(String.join(File.pathSeparator, cp));
		args.addAll(Arrays.asList(jvmArgs));
		args.add(mainClass);

		// Create the service.
		List<String> javaExes = Arrays.asList(
			"java", "java.exe",
			"bin/java", "bin/java.exe",
			"jre/bin/java", "jre/bin/java.exe"
		);
		return service(javaExes, args.toArray(new String[0]));
	}

	/**
	 * Creates a service with the given command line arguments.
	 * <p>
	 * This is a <b>low level</b> way to create a service. It assumes the
	 * specified executable conforms to the {@link Appose Appose worker process
	 * contract}, meaning it accepts requests on stdin and produces responses on
	 * stdout, both formatted according to Appose's assumptions.
	 * </p>
	 *
	 * @param exes List of executables to try for launching the worker process.
	 * @param args Command line arguments to pass to the worker process
	 *          (e.g. <code>{"-v", "--enable-everything"}</code>).
	 * @return The newly created service.
	 * @see #groovy To create a service for Groovy script execution.
	 * @see #python() To create a service for Python script execution.
	 * @throws IOException If something goes wrong starting the worker process.
	 */
	default Service service(List<String> exes, String... args) throws IOException {
		if (exes == null || exes.isEmpty()) throw new IllegalArgumentException("No executable given");

		// Calculate exe string.
		List<String> launchArgs = launchArgs();
		final String exe;
		if (!launchArgs.isEmpty()) {
			// When there are launchArgs (e.g., "pixi run" or "mamba run"), use the bare
			// executable name. The launcher will activate the environment and find the
			// executable. This approach also avoids issues with pixi/mamba not properly
			// handling executable paths containing spaces.
			exe = exes.get(0);
		}
		else {
			// No launchArgs, so we need to find and use the full path to the executable.
			File exeFile = FilePaths.findExe(binPaths(), exes);
			if (exeFile == null) {
				throw new IllegalArgumentException("No executables found amongst candidates: " + exes);
			}
			// Use getAbsolutePath() instead of getCanonicalPath() to preserve symlinks.
			// This is important for Python virtual environments where the python symlink
			// needs to be used directly (not the resolved target) for proper site-packages access.
			exe = exeFile.getAbsolutePath();
		}

		// Construct final args list: launchArgs + exe + args
		List<String> allArgs = new ArrayList<>(launchArgs);
		allArgs.add(exe);
		allArgs.addAll(Arrays.asList(args));

		return new Service(new File(base()), envVars(), allArgs.toArray(new String[0]));
	}
}
