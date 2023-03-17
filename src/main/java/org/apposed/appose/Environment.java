package org.apposed.appose;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Environment {

	private File base;

	public Environment(File base) {
		this.base = base;
	}

	public File base() {
		return this.base;
	}

	/**
	 * Creates a Python script service.
	 * <p>
	 * This is a <b>high level</b> way to create a service, enabling execution of
	 * Python scripts asynchronously on its linked process running a
	 * {@code python-worker}.
	 * </p>
	 * 
	 * @return The newly created service.
	 * @see #groovy To create a service for Groovy script execution.
	 * @throws IOException If something goes wrong starting the worker process.
	 */
	public Service python() throws IOException {
		throw new UnsupportedOperationException("Unimplemented");
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
	public Service groovy(String... jvmArgs) throws IOException {
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
	public Service groovy(List<String> classPath, String... jvmArgs)
		throws IOException
	{
		return java(GroovyWorker.class.getName(), classPath, jvmArgs);
	}

	public Service java(String mainClass, String... jvmArgs)
		throws IOException
	{
		return java(mainClass, Collections.emptyList(), jvmArgs);
	}

	public Service java(String mainClass, List<String> classPath,
		String... jvmArgs) throws IOException
	{
		// Collect classpath elements into a set, to avoid duplicate entries.
		Set<String> cp = new LinkedHashSet<>();

		// Ensure that the classpath includes Appose and its dependencies.
		// NB: This list must match Appose's dependencies in pom.xml!
		List<Class<?>> apposeDeps = Arrays.asList(//
			org.apposed.appose.GroovyWorker.class, // ----> org.apposed:appose
			org.apache.groovy.util.ScriptRunner.class, // --> org.codehaus.groovy:groovy
			groovy.json.JsonOutput.class, // ---------------> org.codehaus.groovy:groovy-json
			org.apache.ivy.Ivy.class, // -------------------> org.apache.ivy:ivy
			com.sun.jna.Pointer.class, // ------------------> com.sun.jna:jna
			com.sun.jna.platform.linux.LibRT.class, // -----> com.sun.jna:jna-platform
			com.sun.jna.platform.win32.Kernel32.class // ---> com.sun.jna:jna-platform
		);
		for (Class<?> depClass : apposeDeps) {
			File location = location(depClass);
			if (location != null) cp.add(location.getCanonicalPath());
		}

		// Append any explicitly requested classpath elements.
		cp.addAll(classPath);

		// Build up the service arguments.
		List<String> args = new ArrayList<>();
		args.add("bin/java");
		args.add("-cp");
		args.add(String.join(File.pathSeparator, cp));
		args.addAll(Arrays.asList(jvmArgs));
		args.add(mainClass);

		// Create the service.
		return service(args.toArray(new String[0]));
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
	 * @param args Command line arguments to launch the service (e.g.
	 *          {@code "customApposeWorker.sh" "-v"}.
	 * @return The newly created service.
	 * @see #groovy To create a service for Groovy script execution.
	 * @see #python To create a service for Python script execution.
	 * @throws IOException If something goes wrong starting the worker process.
	 */
	public Service service(String... args) throws IOException {
		if (args.length == 0) throw new IllegalArgumentException("No executable given");
		File exe = new File(args[0]);
		if (!exe.isAbsolute()) exe = new File(base, args[0]);
		if (!exe.exists()) {
			// Good ol' Windows! Nothing beats Windows.
			exe = new File(exe.getAbsolutePath() + ".exe");
		}
		if (!exe.exists()) throw new IllegalArgumentException("Executable not found: " + args[0]);
		args[0] = exe.getCanonicalPath();
		return new Service(base, args);
	}

	/**
	 * Gets the path to the JAR file containing the given class. Technically
	 * speaking, it might not actually be a JAR file, it might be a raw class
	 * file, or even something weirder... But for our purposes, we'll just
	 * assume it's going to be something you can put onto a classpath.
	 */
	private File location(Class<?> c) {
		try {
			return new File(c.getProtectionDomain().getCodeSource().getLocation().toURI());
		}
		catch (URISyntaxException exc) {
//			throw new IllegalArgumentException("Location of class '" + c.getName() + "' is unclear", exc);
			// If we cannot retrieve the location of a class, just keep going.
			return null;
		}
	}
}
