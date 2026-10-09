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

import groovy.lang.Closure;
import groovy.lang.MetaClass;
import groovy.lang.MetaMethod;
import groovy.lang.MetaProperty;
import groovy.lang.MissingPropertyException;
import org.apposed.appose.syntax.Syntaxes;
import org.apposed.appose.util.Json;
import org.apposed.appose.util.Messages;
import org.apposed.appose.util.Processes;
import org.apposed.appose.util.Proxies;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.MethodClosure;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An Appose *service* provides access to a linked Appose *worker* running in a
 * different process. Using the service, programs create Appose {@link Task}s
 * that run asynchronously in the worker process, which notifies the service of
 * updates via communication over pipes (stdin and stdout).
 * <p>
 * A service still running when the JVM shuts down is shut down too: it is
 * closed, and killed if its worker has not exited within its exit timeout
 * (see {@link #exitTimeout(long, TimeUnit)}).
 * </p>
 */
public class Service implements AutoCloseable {

	private static int serviceCount = 0;

	/** Services started so far, to shut down when the JVM does. */
	private static final Set<Service> startedServices =
		Collections.newSetFromMap(new WeakHashMap<>());
	private static boolean shutdownHookRegistered;

	private final File cwd;
	private final Map<String, String> envVars;
	private final String[] args;
	private final Map<String, Task> tasks = new ConcurrentHashMap<>();
	private final int serviceID;

	/**
	 * List of unparseable (non-JSON) lines seen since the service started.
	 * If the worker process crashes, we use these lines as the content
	 * of an error message to any still pending tasks when reporting the crash.
	 */
	private final List<String> invalidLines = new ArrayList<>();

	/** The worker's self-description, from its HELLO message. */
	private volatile @Nullable Map<String, Object> workerInfo;

	/** Why the worker is incompatible with this service, if it is. */
	private volatile @Nullable String incompatibility;

	/**
	 * List of lines emitted to the standard error stream seen since the service
	 * started. If the worker process crashes, we use these lines as the content
	 * of an error message to any still pending tasks when reporting the crash.
	 */
	private final List<String> errorLines = new ArrayList<>();

	/**
	 * Non-JSON-serializable objects passed to the worker, which the worker
	 * can access remotely via service object proxies.
	 */
	private final Map<String, Object> exports = new ConcurrentHashMap<>();
	private final AtomicInteger exportCount = new AtomicInteger();

	/** Managed shared memory, as used over the connection to this worker. */
	private final MemoryLink memoryLink = MemoryBackends.backend().link(new Peer() {
		@Override
		public void send(Map<String, Object> message) {
			Service.this.send(message);
		}

		@Override
		public void export(String name, Object obj) {
			exports.put(name, obj);
		}
	});

	/** Whether closing was requested, once the tasks already started have finished. */
	private volatile boolean closing;

	private Process process;
	private PrintWriter stdin;
	private Thread stdoutThread;
	private Thread stderrThread;
	private Thread monitorThread;

	private volatile long exitTimeoutNanos = TimeUnit.SECONDS.toNanos(5);
	private @Nullable Consumer<String> debugListener;
	private @Nullable String initScript;
	private final List<String> libraryScripts = new ArrayList<>();
	private ScriptSyntax syntax;


	public Service(File cwd, String... args) {
		this(cwd, null, args);
	}

	public Service(File cwd, @Nullable Map<String, String> envVars, String... args) {
		this.cwd = cwd;
		this.envVars = envVars != null ? new HashMap<>(envVars) : new HashMap<>();
		this.args = args.clone();
		serviceID = serviceCount++;
	}

	/**
	 * Registers a callback function to receive messages
	 * describing current service/worker activity.
	 *
	 * @param debugListener A function that accepts a single string argument.
	 */
	public Service debug(Consumer<String> debugListener) {
		this.debugListener = debugListener;
		return this;
	}

	/**
	 * Sets how long to wait, when the JVM shuts down, for the worker process
	 * to shut down gracefully before killing it. The default is 5 seconds.
	 * Pass {@link Long#MAX_VALUE} to wait indefinitely.
	 *
	 * @param timeout Maximum time to wait.
	 * @param unit Unit of the timeout.
	 * @return This service object, for chaining method calls.
	 */
	public Service exitTimeout(long timeout, TimeUnit unit) {
		exitTimeoutNanos = unit.toNanos(timeout);
		return this;
	}

	/**
	 * Registers a script to be executed when the worker process first starts up,
	 * before any tasks are processed. This is useful for early initialization that
	 * must happen before the worker's main loop begins, such as importing libraries
	 * that may interfere with I/O operations.
	 * <p>
	 * Example: On Windows, importing numpy can hang when stdin is open for reading
	 * (described at
	 * <a href="https://github.com/numpy/numpy/issues/24290">numpy/numpy#24290</a>).
	 * Using {@code service.init("import numpy")} works around this by importing
	 * numpy before the worker's I/O loop starts.
	 * </p>
	 *
	 * @param script The script code to execute during worker initialization.
	 * @return This service object, for chaining method calls.
	 * @throws IllegalStateException If the service has already started.
	 */
	public synchronized Service init(String script) {
		if (process != null) throw new IllegalStateException("Service already started");
		this.initScript = script;
		return this;
	}

	/**
	 * Registers library code with the worker, so that tasks can use it via
	 * normal import statements.
	 * <p>
	 * The library is ordinary source code in the worker's language, which can
	 * be developed in an IDE like any other code, with no reference to
	 * Appose's {@code task} variable. Its state persists across tasks: in
	 * Python, a library is a module, whose module-level state lives on in the
	 * worker's {@code sys.modules}; in Groovy, a library is a set of classes,
	 * whose static state lives on because they are loaded only once. Either
	 * way, e.g. a cache of expensive-to-load models stays "warm" for all
	 * subsequent tasks, without the need for {@code task.export}.
	 * </p>
	 * <p>
	 * The library's source is read now and sent to the worker, so later changes
	 * to the files on disk are not seen unless the library is registered again.
	 * Registering changed source replaces the library (discarding its state);
	 * registering unchanged source is a no-op. Resources, on the other hand,
	 * are read from the directory on disk when accessed, as for any installed
	 * library; so they are available only to libraries given as a directory,
	 * on the same filesystem as the worker.
	 * </p>
	 * <p>
	 * If called before the service starts, registration happens during worker
	 * startup, before the init script (see {@link #init}), which may then use
	 * the library itself. Otherwise, registration happens via a task, and this
	 * method blocks until it completes.
	 * </p>
	 * <p>
	 * The meaning of {@code name} and {@code path} depends on the language:
	 * </p>
	 * <ul>
	 * <li><strong>Python:</strong> {@code name} is the module name to import the
	 *     library as. The path is a single source file (imported as a module),
	 *     or a directory (imported as a package, with subdirectories as
	 *     subpackages).</li>
	 * <li><strong>Groovy:</strong> {@code name} only identifies the library, for
	 *     re-registration; the classes are imported by the packages and names
	 *     declared in their source. The path is a single source file, or a
	 *     source root directory (e.g. {@code src/main/groovy}), whose
	 *     subdirectories correspond to packages.</li>
	 * </ul>
	 *
	 * @param name The name of the library.
	 * @param path A single source file, or a directory of source files.
	 * @return This service object, for chaining method calls.
	 * @throws IOException If the library's source files cannot be read.
	 * @throws InterruptedException If interrupted while registering the library.
	 * @throws TaskException If the worker fails to register the library.
	 * @throws IllegalArgumentException If the name is not a valid identifier,
	 *           or the path is neither a file nor a directory.
	 * @throws IllegalStateException If no script syntax has been configured for this service.
	 * @throws UnsupportedOperationException If this service's script syntax
	 *           does not support libraries.
	 * @see #importLibrarySource(String, String)
	 * @see #importLibrarySource(String, Map)
	 */
	public Service importLibrary(String name, File path)
		throws IOException, InterruptedException, TaskException
	{
		checkLibraryName(name);
		Syntaxes.validate(this);
		File file = path.getAbsoluteFile();
		String origin = file.toPath().normalize().toString().replace(File.separatorChar, '/');
		Map<String, String> files = new LinkedHashMap<>();
		if (file.isFile()) {
			files.put(file.getName(), readString(file.toPath()));
			return registerLibrary(syntax.importLibrary(name, files, origin, false));
		}
		if (!file.isDirectory()) {
			throw new IllegalArgumentException("No such file or directory: " + file);
		}
		String suffix = syntax.librarySuffix();
		Path root = file.toPath();
		List<Path> sources;
		try (Stream<Path> walk = Files.walk(root)) {
			sources = walk
				.filter(p -> p.getFileName().toString().endsWith(suffix))
				.filter(p -> Files.isRegularFile(p))
				.filter(p -> !p.toString().contains("__pycache__"))
				.sorted()
				.collect(Collectors.toList());
		}
		for (Path p : sources) {
			String relPath = root.relativize(p).toString().replace(File.separatorChar, '/');
			files.put(relPath, readString(p));
		}
		return registerLibrary(syntax.importLibrary(name, files, origin, true));
	}

	/**
	 * Registers library code, given directly as a single string of source
	 * code, with the worker. See {@link #importLibrary(String, File)} for
	 * details.
	 *
	 * @param name The name of the library.
	 * @param source The library's source code (a Python module, or a
	 *          Groovy source file).
	 * @return This service object, for chaining method calls.
	 * @throws InterruptedException If interrupted while registering the library.
	 * @throws TaskException If the worker fails to register the library.
	 * @throws IllegalArgumentException If the name is not a valid identifier.
	 * @throws IllegalStateException If no script syntax has been configured for this service.
	 * @throws UnsupportedOperationException If this service's script syntax
	 *           does not support libraries.
	 */
	public Service importLibrarySource(String name, String source)
		throws InterruptedException, TaskException
	{
		checkLibraryName(name);
		Syntaxes.validate(this);
		String fileName = name + syntax.librarySuffix();
		Map<String, String> files = Collections.singletonMap(fileName, source);
		return registerLibrary(syntax.importLibrary(name, files, sourceOrigin(fileName), false));
	}

	/**
	 * Registers library code, given directly as source code of multiple files,
	 * with the worker. See {@link #importLibrary(String, File)} for details.
	 *
	 * @param name The name of the library.
	 * @param sources Map from relative POSIX path (e.g. {@code "__init__.py"}
	 *          or {@code "sub/mod.py"}) to source code, as if the library were
	 *          given as a directory.
	 * @return This service object, for chaining method calls.
	 * @throws InterruptedException If interrupted while registering the library.
	 * @throws TaskException If the worker fails to register the library.
	 * @throws IllegalArgumentException If the name is not a valid identifier.
	 * @throws IllegalStateException If no script syntax has been configured for this service.
	 * @throws UnsupportedOperationException If this service's script syntax
	 *           does not support libraries.
	 */
	public Service importLibrarySource(String name, Map<String, String> sources)
		throws InterruptedException, TaskException
	{
		checkLibraryName(name);
		Syntaxes.validate(this);
		Map<String, String> files = new LinkedHashMap<>(sources);
		return registerLibrary(syntax.importLibrary(name, files, sourceOrigin(name), true));
	}

	/**
	 * Adds environment variables to pass to the worker process.
	 *
	 * @param vars Key/value pairs to add to the worker's environment.
	 * @return This service object, for chaining method calls.
	 * @throws IllegalStateException If the service has already started.
	 */
	public synchronized Service env(Map<String, String> vars) {
		if (process != null) throw new IllegalStateException("Service already started");
		if (vars != null) envVars.putAll(vars);
		return this;
	}

	/**
	 * Launches the worker process associated with this service.
	 *
	 * @return This service object, for chaining method calls (typically with {@link #task}).
	 * @throws IOException If the process fails to execute; see {@link ProcessBuilder#start()}.
	 */
	public synchronized Service start() throws IOException {
		if (process != null) {
			// Already started.
			return this;
		}

		String prefix = "Appose-Service-" + serviceID;

		// If libraries or an init script are provided, write them to temporary
		// files and pass their paths via environment variables. The worker
		// registers the libraries first, so that the init script can use them.
		writeStartupScript(String.join("", libraryScripts), "appose-libraries-", "APPOSE_LIBRARY_SCRIPT");
		writeStartupScript(initScript, "appose-init-", "APPOSE_INIT_SCRIPT");

		// Tell the worker which backend provides managed shared memory,
		// so that it may send its arrays that way.
		envVars.put(MemoryBackends.ENV_VAR, MemoryBackends.backendName());

		ProcessBuilder pb = Processes.builder(cwd, envVars, args);
		process = pb.start();
		track(this);
		stdin = new PrintWriter(process.getOutputStream());

		// NB: These threads block until the worker's output streams close, so
		// they must be daemon threads. The JVM waits for every non-daemon thread
		// before shutting down, so it would wait forever on a worker that only a
		// shutdown hook (e.g. ours) shuts down.
		stdoutThread = new Thread(this::stdoutLoop, prefix + "-Stdout");
		stderrThread = new Thread(this::stderrLoop, prefix + "-Stderr");
		monitorThread = new Thread(this::monitorLoop, prefix + "-Monitor");
		stdoutThread.setDaemon(true);
		stderrThread.setDaemon(true);
		monitorThread.setDaemon(true);
		stderrThread.start();
		stdoutThread.start();
		monitorThread.start();
		return this;
	}

	/**
	 * Creates a new task, passing the given script to the worker for execution.
	 *
	 * @param script The script for the worker to execute in its environment.
	 * @return The newly created {@link Task} object tracking the execution.
	 * @throws UncheckedIOException If something goes wrong auto-starting the service.
	 */
	public Task task(String script) {
		return task(script, null, null);
	}

	/**
	 * Creates a new task, passing the given script to the worker for execution.
	 *
	 * @param script The script for the worker to execute in its environment.
	 * @param inputs Optional list of key/value pairs to feed into the script as inputs.
	 * @return The newly created {@link Task} object tracking the execution.
	 * @throws UncheckedIOException If something goes wrong auto-starting the service.
	 */
	public Task task(String script, Map<String, Object> inputs) {
		return task(script, inputs, null);
	}

	/**
	 * Creates a new task, passing the given script to the worker for execution.
	 *
	 * @param script The script for the worker to execute in its environment.
	 * @param queue Optional queue target. Pass "main" to queue to worker's main thread.
	 * @return The newly created {@link Task} object tracking the execution.
	 * @throws UncheckedIOException If something goes wrong auto-starting the service.
	 */
	public Task task(String script, String queue) {
		return task(script, null, queue);
	}

	/**
	 * Creates a new task, passing the given script to the worker for execution.
	 *
	 * @param script The script for the worker to execute in its environment.
	 * @param inputs Optional list of key/value pairs to feed into the script as inputs.
	 * @param queue Optional queue target. Pass "main" to queue to worker's main thread.
	 * @return The newly created {@link Task} object tracking the execution.
	 * @throws UncheckedIOException If something goes wrong auto-starting the service.
	 */
	public Task task(String script, Map<String, Object> inputs, String queue) {
		try {
			start();
		}
		catch (IOException exc) {
			throw new UncheckedIOException("Service autostart failed", exc);
		}
		return new Task(script, inputs, queue);
	}

	/**
	 * Declares the script syntax of this service.
	 * <p>
	 * This value determines which {@link ScriptSyntax} implementation is used for
	 * generating language-specific scripts.
	 * </p>
	 * <p>
	 * This method is called directly by {@link Environment#python} and
	 * {@link Environment#groovy} when creating services of those types.
	 * It can also be called manually to support custom languages with
	 * registered {@link ScriptSyntax} plugins.
	 * </p>
	 *
	 * @param syntax The type identifier (e.g., "python", "groovy").
	 * @return This service object, for chaining method calls.
	 * @throws IllegalArgumentException If no syntax plugin is found for the given type.
	 */
	public Service syntax(String syntax) {
		this.syntax = Syntaxes.get(syntax);
		return this;
	}

	/**
	 * Sets the script syntax strategy for this service directly.
	 * <p>
	 * This method is provided for advanced use cases where you want to use
	 * a custom syntax implementation without registering it as a plugin.
	 * Most users should use {@link #syntax(String)} instead.
	 * </p>
	 *
	 * @param syntax The script syntax strategy to use.
	 * @return This service object, for chaining method calls.
	 */
	public Service syntax(ScriptSyntax syntax) {
		this.syntax = syntax;
		return this;
	}

	/**
	 * Gets the script syntax strategy for this service.
	 *
	 * @return The script syntax strategy, or {@code null} if not set.
	 */
	public ScriptSyntax syntax() {
		return syntax;
	}

	/**
	 * Retrieves a variable from the worker process's global scope.
	 * <p>
	 * This is a convenience method that creates a task to evaluate the variable
	 * by name and returns its value. The variable must have been previously
	 * exported using {@code task.export()} to be accessible across tasks.
	 * </p>
	 *
	 * @param name The name of the variable to retrieve from the worker process.
	 * @return The value of the variable.
	 * @throws InterruptedException If the current thread is interrupted while waiting.
	 * @throws TaskException If the task fails to retrieve the variable.
	 * @throws IllegalStateException If no script syntax has been configured for this service.
	 */
	public Object getVar(String name) throws InterruptedException, TaskException {
		Syntaxes.validate(this);
		String script = syntax.getVar(name);
		Task task = task(script).waitFor();
		return task.result();
	}

	/**
	 * Sets a variable in the worker process's global scope and exports it for
	 * future use across tasks.
	 * <p>
	 * This is a convenience method that creates a task to assign the given value
	 * to a variable in the worker's global scope, making it accessible to
	 * subsequent tasks. The variable is automatically exported using
	 * {@code task.export()}.
	 * </p>
	 *
	 * @param name The name of the variable to set in the worker process.
	 * @param value The value to assign to the variable.
	 * @throws InterruptedException If the current thread is interrupted while waiting.
	 * @throws TaskException If the task fails to set the variable.
	 * @throws IllegalStateException If no script syntax has been configured for this service.
	 */
	public void putVar(String name, Object value) throws InterruptedException, TaskException {
		Syntaxes.validate(this);
		Map<String, Object> inputs = new HashMap<>();
		inputs.put("_value", value);
		String script = syntax.putVar(name, "_value");
		task(script, inputs).waitFor();
	}

	/**
	 * Calls a function in the worker process with the given arguments and returns
	 * the result.
	 * <p>
	 * This is a convenience method that creates a task to invoke a function by
	 * name with the specified arguments. The function must be accessible in the
	 * worker's global scope (either built-in or previously defined/imported).
	 * </p>
	 * <p>
	 * Arguments are passed as inputs to the task and referenced by name in the
	 * generated script (arg0, arg1, etc.).
	 * </p>
	 *
	 * @param function The name of the function to call in the worker process.
	 * @param args The arguments to pass to the function.
	 * @return The result of the function call.
	 * @throws InterruptedException If the current thread is interrupted while waiting.
	 * @throws TaskException If the function call fails.
	 * @throws IllegalStateException If no script syntax has been configured for this service.
	 */
	public Object call(String function, Object... args) throws InterruptedException, TaskException {
		Syntaxes.validate(this);
		Map<String, Object> inputs = new HashMap<>();
		List<String> varNames = new ArrayList<>();
		for (int i=0; i<args.length; i++) {
			String varName = "arg" + i;
			inputs.put(varName, args[i]);
			varNames.add(varName);
		}
		String script = syntax.call(function, varNames);
		Task task = task(script, inputs).waitFor();
		return task.result();
	}

	/**
	 * Creates a proxy object providing strongly typed access to a remote object
	 * in this service's worker process.
	 * <p>
	 * This is a convenience method for interacting with objects in the worker
	 * process using a natural, object-oriented API instead of manually constructing
	 * script strings. Method calls on the proxy are transparently forwarded to the
	 * remote object via {@link Task}s.
	 * </p>
	 * <p>
	 * Example usage:
	 * </p>
	 * <pre>
	 * Service service = env.python();
	 * service.task("task.export(calculator=Calculator())").waitFor();
	 * CalculatorInterface calc = service.proxy("calculator", CalculatorInterface.class);
	 * int result = calc.add(2, 3); // Executes remotely, returns 5
	 * </pre>
	 * <p>
	 * <strong>Important:</strong> The variable must be explicitly exported using
	 * {@code task.export(varName=value)} in a previous task. Only exported variables
	 * are accessible across tasks within the same service.
	 * </p>
	 * <p>
	 * <strong>Note:</strong> Type matching is honor-system based. The interface
	 * must actually match the remote object's methods, or you'll get runtime errors.
	 * </p>
	 *
	 * @param <T> The interface type that the proxy will implement.
	 * @param var The name of the exported variable in the worker process referencing the remote object.
	 * @param api The interface class that the proxy should implement.
	 * @return A proxy object that forwards method calls to the remote object.
	 * @see #proxy(String, Class, String) To control which queue handles the method calls.
	 * @see Proxies#create(Service, String, Class) For detailed documentation on proxy behavior.
	 */
	public <T> T proxy(String var, Class<T> api) {
		return proxy(var, api, null);
	}

	/**
	 * Creates a proxy object providing strongly typed access to a remote object
	 * in this service's worker process, with control over task execution queuing.
	 * <p>
	 * This is a convenience method for interacting with objects in the worker
	 * process using a natural, object-oriented API instead of manually constructing
	 * script strings. Method calls on the proxy are transparently forwarded to the
	 * remote object via {@link Task}s.
	 * </p>
	 * <p>
	 * <strong>Important:</strong> The variable must be explicitly exported using
	 * {@code task.export(varName=value)} in a previous task. Only exported variables
	 * are accessible across tasks within the same service.
	 * </p>
	 * <p>
	 * <strong>Blocking behavior:</strong> Each method call blocks until the remote
	 * execution completes. If the remote execution fails, a {@link RuntimeException}
	 * is thrown with the error message from the worker.
	 * </p>
	 *
	 * @param <T> The interface type that the proxy will implement.
	 * @param var The name of the exported variable in the worker process referencing the remote object.
	 * @param api The interface class that the proxy should implement.
	 * @param queue Optional queue identifier for task execution. Pass {@code "main"} to ensure
	 *              execution on the worker's main thread, or {@code null} for default behavior.
	 * @return A proxy object that forwards method calls to the remote object.
	 * @see #task(String, String) For understanding queue behavior.
	 * @see Proxies#create(Service, String, Class, String) For detailed documentation on proxy behavior.
	 */
	public <T> T proxy(String var, Class<T> api, String queue) {
		return Proxies.create(this, var, api, queue);
	}

	/**
	 * Closes the worker process's input stream, in order to shut it down,
	 * once the tasks already started have finished. Until then, those tasks
	 * can still call into service objects, and allocate managed shared memory
	 * (e.g. for their outputs); no new task can start.
	 * <p>
	 * To shut down the service more forcibly, interrupting any pending tasks,
	 * use {@link #kill()} instead.
	 * </p>
	 * <p>
	 * To wait until the service's worker process has completely shut down
	 * and all output has been reported, call {@link #waitFor()} afterward;
	 * or call {@link #close(long, TimeUnit)} instead, to wait for a bounded
	 * time before killing the worker process.
	 * </p>
	 *
	 * @throws IllegalStateException If the service has not been started.
	 */
	@Override
	public void close() {
		requireProcess();
		closing = true;
		closeIfIdle();
	}

	/**
	 * Closes the worker process's input stream, if closing was requested
	 * and no started task is still pending.
	 */
	private void closeIfIdle() {
		if (!closing) return;
		for (Task task : tasks.values()) {
			if (task.status == TaskStatus.QUEUED || task.status == TaskStatus.RUNNING) return;
		}
		synchronized (stdin) {
			stdin.close();
		}
	}

	/**
	 * Closes the worker process's input stream, in order to shut it down, then
	 * waits up to the given time for the worker process to terminate, killing
	 * it (see {@link #kill()}) if it has not. Pending tasks run to completion,
	 * unless interrupted by the kill.
	 *
	 * @param timeout Maximum time to wait before killing the worker process;
	 *          zero kills it at once.
	 * @param unit Unit of the timeout.
	 * @return Exit value of the worker process.
	 * @throws InterruptedException If interrupted while waiting.
	 * @throws IllegalStateException If the service has not been started.
	 */
	public int close(long timeout, TimeUnit unit) throws InterruptedException {
		close();
		long timeoutNanos = unit.toNanos(timeout);
		try {
			return waitFor(timeoutNanos, TimeUnit.NANOSECONDS);
		}
		catch (TimeoutException exc) {
			kill();
		}
		// NB: The killed worker dies promptly, but the threads processing its
		// output may not: a descendant that escaped the kill could keep the
		// streams open, or a listener could be stuck. Do not wait forever.
		process.waitFor();
		joinThreads(System.nanoTime(), timeoutNanos);
		return process.exitValue();
	}

	/**
	 * Forces the service's worker process to begin shutting down. Any tasks still
	 * pending completion will be interrupted, reporting {@link TaskStatus#CRASHED}.
	 * <p>
	 * To shut down the service more gently, allowing any pending tasks to run to
	 * completion, use {@link #close()} instead.
	 * </p>
	 * <p>
	 * To wait until the service's worker process has completely shut down
	 * and all output has been reported, call {@link #waitFor()} afterward.
	 * </p>
	 * <p>
	 * This kills the worker's descendant processes too, not only the process
	 * launched directly; e.g. {@code pixi run} launches the actual worker
	 * process as its child. See {@link Processes#killTree(Process)}. On Java 8,
	 * which cannot enumerate descendants, only the process launched directly is
	 * killed; a worker behind a launcher then lives on until its tasks finish,
	 * and its tasks are not reported as crashed until it exits.
	 * </p>
	 *
	 * @throws IllegalStateException If the service has not been started.
	 */
	public void kill() {
		requireProcess();
		if (!Processes.killTree(process)) {
			debugService("<killed worker process only; its descendants cannot be found on Java 8>");
		}
	}

	/**
	 * Waits for the service's worker process to terminate,
	 * and for all its output to be reported.
	 *
	 * @return Exit value of the worker process.
	 * @throws InterruptedException If any of the worker process's monitoring
	 * 	                             threads are interrupted before shutting down.
	 * @throws IllegalStateException If the service has not been started.
	 */
	public int waitFor() throws InterruptedException {
		requireProcess();
		process.waitFor();

		// Wait for worker output processing threads to finish up.
		stdoutThread.join();
		stderrThread.join();
		monitorThread.join();

		return process.exitValue();
	}

	/**
	 * Waits up to the given time for the service's worker process to
	 * terminate, and for all its output to be reported.
	 *
	 * @param timeout Maximum time to wait.
	 * @param unit Unit of the timeout.
	 * @return Exit value of the worker process.
	 * @throws InterruptedException If interrupted while waiting.
	 * @throws TimeoutException If the timeout elapses first.
	 * @throws IllegalStateException If the service has not been started.
	 */
	public int waitFor(long timeout, TimeUnit unit)
		throws InterruptedException, TimeoutException
	{
		requireProcess();
		long start = System.nanoTime();
		long timeoutNanos = unit.toNanos(timeout);
		if (!process.waitFor(timeoutNanos, TimeUnit.NANOSECONDS) ||
			!joinThreads(start, timeoutNanos))
		{
			throw new TimeoutException("Worker process did not terminate within " +
				timeout + " " + unit.toString().toLowerCase());
		}
		return process.exitValue();
	}

	/**
	 * Gets the exit value of the service's worker process.
	 *
	 * @return Exit value of the worker process.
	 * @throws IllegalStateException If the worker process has not yet
	 *           terminated, or has not been started.
	 */
	public int exitValue() {
		requireProcess();
		if (process.isAlive()) throw new IllegalStateException("Worker process has not terminated");
		return process.exitValue();
	}

	/**
	 * Returns true if the service's worker process is currently running,
	 * or false if it has not yet started or has already shut down or crashed.
	 *
	 * @return Whether the service's worker process is currently running.
	 */
	public boolean isAlive() {
		return process != null && process.isAlive();
	}

	/**
	 * Unparseable lines emitted by the worker process on its stdout stream,
	 * collected over the lifetime of the service.
	 * Can be useful for analyzing why a worker process has crashed.
	 */
	public @Nullable Map<String, Object> workerInfo() {
		return workerInfo;
	}

	public List<String> invalidLines() {
		return Collections.unmodifiableList(invalidLines);
	}

	/**
	 * Lines emitted by the worker process on its stderr stream,
	 * collected over the lifetime of the service.
	 * Can be useful for analyzing why a worker process has crashed.
	 */
	public List<String> errorLines() {
		return Collections.unmodifiableList(errorLines);
	}

	/**
	 * Gets an object that this service exported to its worker process.
	 * <p>
	 * When a task input is not JSON-serializable, the service exports it, and
	 * the worker receives a proxy that calls back into the original object.
	 * </p>
	 *
	 * @param varName The name under which the object was exported.
	 * @return The exported object.
	 * @throws IllegalArgumentException If no object was exported with that name.
	 */
	public Object exported(String varName) {
		Object obj = exports.get(varName);
		if (obj == null) {
			throw new IllegalArgumentException("No such service object: " + varName);
		}
		return obj;
	}

	/** Input loop processing lines from the worker stdout stream. */
	/**
	 * Checks that the worker is compatible with this service: both must
	 * implement the same major.minor version of Appose.
	 */
	private void handleHello(Map<String, Object> hello) {
		workerInfo = hello;
		String workerVersion = String.valueOf(hello.get("version"));
		String serviceVersion = Appose.version();
		String minor = minor(serviceVersion);
		if (minor != null && minor.equals(minor(workerVersion))) return;
		Object implementation = hello.getOrDefault("implementation", "worker");
		rejectWorker(implementation + " " + workerVersion + " is incompatible with " +
			"appose-java " + serviceVersion + ": the worker must also implement Appose " +
			minor + ".x.");
	}

	/** Shuts down an incompatible worker, crashing its tasks with the reason. */
	private void rejectWorker(String reason) {
		String skip = System.getenv("APPOSE_SKIP_VERSION_CHECK");
		if (skip == null || skip.isEmpty()) skip = System.getProperty("appose.skipVersionCheck");
		if (skip != null && !skip.isEmpty()) {
			debugService("<ignoring incompatible worker> " + reason);
			return;
		}
		incompatibility = reason + " Set APPOSE_SKIP_VERSION_CHECK=1 " +
			"(or -Dappose.skipVersionCheck=true) to skip this check.";
		debugService("<incompatible worker> " + incompatibility);
		// NB: Closing stdin also ends any descendant process actually running
		// the worker (e.g. beneath pixi run), which kill() does not reach.
		stdin.close();
		kill();
	}

	/**
	 * Extracts the major.minor part of a version string, e.g. "1.1" from
	 * "1.1.2", "1.1.0.dev0" or "1.1.3-SNAPSHOT"; or null if unparseable.
	 */
	static @Nullable String minor(String version) {
		Matcher m = Pattern.compile("^(\\d+)\\.(\\d+)").matcher(version);
		return m.find() ? m.group(1) + "." + m.group(2) : null;
	}

	private void writeStartupScript(@Nullable String script, String prefix, String envVar)
		throws IOException
	{
		if (script == null || script.isEmpty()) return;
		File file = File.createTempFile(prefix, ".txt");
		file.deleteOnExit();
		Files.write(file.toPath(), script.getBytes(StandardCharsets.UTF_8));
		envVars.put(envVar, file.getAbsolutePath());
	}

	private static void checkLibraryName(String name) {
		boolean valid = !name.isEmpty() && Character.isJavaIdentifierStart(name.charAt(0)) &&
			name.chars().allMatch(Character::isJavaIdentifierPart);
		if (!valid) throw new IllegalArgumentException("Invalid library name: " + name);
	}

	private static String sourceOrigin(String fileName) {
		// NB: Not wrapped in <...>, which Python's linecache would refuse to
		// resolve via the module's loader, leaving tracebacks without source lines.
		return "<appose>/" + fileName;
	}

	private static String readString(Path path) throws IOException {
		return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
	}

	private Service registerLibrary(String script) throws InterruptedException, TaskException {
		synchronized (this) {
			if (process == null) {
				libraryScripts.add(script);
				return this;
			}
		}
		task(script).waitFor();
		return this;
	}

	private void stdoutLoop() {
		BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
		while (true) {
			String line;
			try {
				line = stdout.readLine();
			}
			catch (IOException exc) {
				// Something went wrong reading the stdout line. Panic!
				debugService(Messages.stackTrace(exc));
				break;
			}

			if (line == null) {
				debugService("<worker stdout closed>");
				break;
			}
			try {
				// NB: Handle each response in its own method, so that no
				// reference to it lingers here while awaiting the next one.
				handleResponse(line);
			}
			catch (Exception exc) {
				// Something went wrong decoding the line of JSON.
				// Skip it and keep going, but log it first.
				debugService(String.format("<INVALID> %s", line));
				invalidLines.add(line);
			}
		}
	}

	/** Processes a line of output from the worker's stdout stream. */
	private void handleResponse(String line) {
		Map<String, Object> response;
		try {
			response = Messages.decode(line, memoryLink);
		}
		catch (RuntimeException exc) {
			reject(line, Messages.stackTrace(exc));
			throw exc;
		}
		debugService(line); // Echo the line to the debug listener.
		Object responseType = response.get("responseType");
		if (ResponseType.HELLO.toString().equals(responseType)) {
			handleHello(response);
			return;
		}
		if (workerInfo == null && incompatibility == null) {
			// NB: Workers predating the HELLO handshake begin with
			// some other message, e.g. LAUNCH for the first task.
			String minor = minor(Appose.version());
			rejectWorker("Worker did not identify itself, so it probably " +
				"predates Appose " + minor + "; this service requires Appose " + minor + ".x.");
		}
		if (incompatibility != null) return;
		if (ResponseType.RELEASE.toString().equals(responseType)) {
			// The worker gives up references to managed regions.
			memoryLink.released(response.get("regions"));
			return;
		}
		if (ResponseType.CALL.toString().equals(responseType)) {
			// The worker is calling back into a service object.
			// Handle it on its own thread, so that this loop stays
			// free to process other responses in the meantime.
			Thread t = new Thread(() -> handleCall(response),
				"Appose-Service-" + serviceID + "-Call");
			t.setDaemon(true);
			t.start();
			return;
		}
		Object uuid = response.get("task");
		if (uuid == null) {
			debugService("Invalid service message:" + line);
			return;
		}
		Task task = tasks.get(uuid.toString());
		if (task == null) {
			debugService("No such task: " + uuid);
			return;
		}
		task.handle(response);
	}

	/**
	 * Reports a response from the worker that could not be decoded, to
	 * whoever awaits it: fails the task it concludes, or the call it makes.
	 */
	private void reject(String line, String error) {
		Object raw;
		try {
			raw = Json.parseJson(line);
		}
		catch (RuntimeException exc) {
			return;
		}
		if (!(raw instanceof Map)) return;
		Map<?, ?> map = (Map<?, ?>) raw;
		Object responseType = map.get("responseType");
		if (ResponseType.CALL.toString().equals(responseType)) {
			Map<String, Object> reply = new HashMap<>();
			reply.put("requestType", RequestType.REPLY.toString());
			reply.put("call", map.get("call"));
			reply.put("error", "Service could not decode the call:\n" + error);
			send(reply);
			return;
		}
		Object uuid = map.get("task");
		Task task = uuid == null ? null : tasks.get(uuid.toString());
		boolean terminal = ResponseType.COMPLETION.toString().equals(responseType) ||
			ResponseType.CANCELATION.toString().equals(responseType) ||
			ResponseType.FAILURE.toString().equals(responseType);
		if (task == null || !terminal) return;
		Map<String, Object> failure = new HashMap<>();
		failure.put("task", task.uuid);
		failure.put("responseType", ResponseType.FAILURE.toString());
		failure.put("error", "Service could not decode the task's " +
			responseType + " response:\n" + error);
		task.handle(failure);
	}

	/** Input loop processing lines from the worker stderr stream. */
	private void stderrLoop() {
		BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
		while (true) {
			String line;
			try {
				line = stderr.readLine();
			}
			catch (IOException exc) {
				// Something went wrong reading the stderr line. Panic!
				debugService(Messages.stackTrace(exc));
				break;
			}
			if (line == null) {
				debugService("<worker stderr closed>");
				break;
			}
			debugWorker(line);
			errorLines.add(line);
		}
	}

	private void monitorLoop() {
		// Wait until the worker process terminates and its output is drained.
		try {
			process.waitFor();
			// NB: A descendant that outlives the worker process, such as the
			// actual worker behind a launcher, may hold its streams open longer.
			stdoutThread.join();
			stderrThread.join();
		}
		catch (InterruptedException exc) {
			// Treat interruption as a request to shut down.
			debugService(Messages.stackTrace(exc));
		}
		debugService("<worker process termination detected>");

		// Once the worker's output is fully processed (its last outputs may
		// refer to managed regions it holds), drop its remaining references.
		memoryLink.close();

		// Do some sanity checks.
		int exitCode = process.exitValue();
		if (exitCode != 0) debugService("<worker process terminated with exit code " + exitCode + ">");
		int taskCount = tasks.size();
		if (taskCount == 0) return; // No hanging tasks to clean up.

		debugService("<worker process terminated with " +
			taskCount + " pending task" + (taskCount == 1 ? "" : "s") + ">");

		// Notify remaining tasks about the process crash.
		StringBuilder sb = new StringBuilder();
		String nl = System.lineSeparator();
		if (incompatibility != null) sb.append(incompatibility).append(nl).append(nl);
		sb.append("Worker crashed with exit code ").append(exitCode).append(".").append(nl);
		String stdout = invalidLines.isEmpty() ? "<none>" : String.join(nl, invalidLines);
		String stderr = errorLines.isEmpty() ? "<none>" : String.join(nl, errorLines);
		sb.append(nl).append("[stdout]").append(nl).append(stdout).append(nl);
		sb.append(nl).append("[stderr]").append(nl).append(stderr).append(nl);
		String error = sb.toString();
		tasks.values().forEach(task -> task.crash(error));
		tasks.clear();
	}

	private void requireProcess() {
		if (process == null) throw new IllegalStateException("Service has not been started");
	}

	/**
	 * Waits for the worker output processing threads to finish up.
	 *
	 * @param start {@link System#nanoTime()} value when the wait began.
	 * @param timeoutNanos Maximum nanoseconds to wait, since the start.
	 * @return Whether all the threads finished.
	 */
	private boolean joinThreads(long start, long timeoutNanos) throws InterruptedException {
		Thread[] threads = {stdoutThread, stderrThread, monitorThread};
		for (Thread thread : threads) {
			long remaining = timeoutNanos - (System.nanoTime() - start);
			if (remaining > 0) TimeUnit.NANOSECONDS.timedJoin(thread, remaining);
		}
		for (Thread thread : threads) {
			if (thread.isAlive()) return false;
		}
		return true;
	}

	/** Remembers a started service, so that it can be shut down with the JVM. */
	private static synchronized void track(Service service) {
		if (!shutdownHookRegistered) {
			Runtime.getRuntime().addShutdownHook(
				new Thread(Service::shutDownServices, "Appose-Shutdown"));
			shutdownHookRegistered = true;
		}
		startedServices.add(service);
	}

	/**
	 * Shuts down all services still running, giving each worker up to its
	 * service's exit timeout to exit gracefully before killing it.
	 */
	private static void shutDownServices() {
		List<Service> services;
		synchronized (Service.class) {
			services = startedServices.stream()
				.filter(Service::isAlive)
				.collect(Collectors.toList());
		}
		// Close every service first, so that their timeouts elapse concurrently.
		long start = System.nanoTime();
		for (Service service : services) {
			try {
				service.close();
			}
			catch (RuntimeException exc) {
				service.debugService(Messages.stackTrace(exc));
			}
		}
		for (Service service : services) {
			long remaining = service.exitTimeoutNanos - (System.nanoTime() - start);
			try {
				service.close(Math.max(0, remaining), TimeUnit.NANOSECONDS);
			}
			catch (InterruptedException exc) {
				// Shut down the remaining services forcibly, without waiting.
				Thread.currentThread().interrupt();
				services.forEach(Service::kill);
				return;
			}
			catch (RuntimeException exc) {
				service.debugService(Messages.stackTrace(exc));
			}
		}
	}

	/**
	 * Exports a non-JSON-serializable object, so that the worker
	 * can access it remotely via a service object proxy.
	 *
	 * @return A service_object reference to the exported object.
	 */
	private Map<String, Object> export(Object obj) {
		String varName = "_appose_service_" + exportCount.getAndIncrement();
		exports.put(varName, obj);
		Map<String, Object> ref = new LinkedHashMap<>();
		ref.put("appose_type", "service_object");
		ref.put("var_name", varName);
		return ref;
	}

	/** Sends a request to the worker process. */
	private void send(Map<String, Object> request) {
		List<SharedMemoryView> managed = new ArrayList<>();
		String encoded = Messages.encode(request, this::export, memoryLink, managed);
		// NB: Record the worker's new references before sending, while the
		// views are still held, so that no region is freed in between.
		memoryLink.sent(managed);
		// NB: Requests may be sent from multiple threads, and must not interleave.
		synchronized (stdin) {
			stdin.println(encoded);
			// NB: Flush is necessary to ensure worker receives the data!
			stdin.flush();
		}
		debugService(encoded);
	}

	/**
	 * Performs an operation requested by the worker on an exported service
	 * object, then sends the outcome back to the worker as a REPLY.
	 */
	private void handleCall(Map<String, Object> request) {
		Map<String, Object> reply = new HashMap<>();
		reply.put("requestType", RequestType.REPLY.toString());
		reply.put("call", request.get("call"));
		try {
			Object obj = exported((String) request.get("var"));
			Object op = request.get("op");
			Object result;
			if ("get".equals(op)) {
				result = getAttribute(obj, (String) request.get("name"));
			}
			else if ("call".equals(op)) {
				Object args = Proxies.proxifyWorkerObjects(request.get("args"), this);
				result = invoke(obj, args == null ? new Object[0] : ((List<?>) args).toArray());
			}
			else if ("dir".equals(op)) {
				result = dir(obj);
			}
			else throw new IllegalArgumentException("Invalid call operation: " + op);
			reply.put("result", result);
			send(reply);
		}
		catch (Throwable t) {
			reply.remove("result");
			reply.put("error", Messages.stackTrace(t));
			try {
				send(reply);
			}
			catch (Throwable t2) {
				// The worker is unreachable; nothing more to do.
				debugService(Messages.stackTrace(t2));
			}
		}
	}

	/**
	 * Gets the named property of the given object, or else
	 * a callable reference to its method of that name.
	 */
	private static Object getAttribute(Object obj, String name) {
		try {
			return InvokerHelper.getProperty(obj, name);
		}
		catch (MissingPropertyException exc) {
			if (InvokerHelper.getMetaClass(obj).respondsTo(obj, name).isEmpty()) throw exc;
			return new MethodClosure(obj, name);
		}
	}

	/**
	 * Calls the given object as a function: a closure, an instance of a
	 * functional interface, or an object with a {@code call} method.
	 */
	private static Object invoke(Object obj, Object[] args) {
		if (obj instanceof Closure) return ((Closure<?>) obj).call(args);
		String methodName = functionalMethodName(obj.getClass());
		return InvokerHelper.invokeMethod(obj, methodName == null ? "call" : methodName, args);
	}

	/**
	 * Gets the name of the single abstract method of a functional interface
	 * implemented by the given class, or null if there is no such interface.
	 */
	private static String functionalMethodName(Class<?> c) {
		for (Class<?> type = c; type != null; type = type.getSuperclass()) {
			for (Class<?> iface : type.getInterfaces()) {
				String name = abstractMethodName(iface);
				if (name != null) return name;
				name = functionalMethodName(iface);
				if (name != null) return name;
			}
		}
		return null;
	}

	private static String abstractMethodName(Class<?> iface) {
		String name = null;
		for (Method m : iface.getMethods()) {
			if (!Modifier.isAbstract(m.getModifiers())) continue;
			if (isObjectMethod(m)) continue;
			if (name != null && !name.equals(m.getName())) return null;
			name = m.getName();
		}
		return name;
	}

	private static boolean isObjectMethod(Method m) {
		try {
			Object.class.getMethod(m.getName(), m.getParameterTypes());
			return true;
		}
		catch (NoSuchMethodException exc) {
			return false;
		}
	}

	/** Lists the names of the given object's properties and methods. */
	private static List<String> dir(Object obj) {
		MetaClass metaClass = InvokerHelper.getMetaClass(obj);
		Set<String> names = new TreeSet<>();
		for (MetaProperty p : metaClass.getProperties()) names.add(p.getName());
		for (MetaMethod m : metaClass.getMethods()) names.add(m.getName());
		return new ArrayList<>(names);
	}

	private void debugService(String message) { debug("SERVICE", message); }
	private void debugWorker(String message) { debug("WORKER", message); }

	/**
	 * Passes a message to the listener registered
	 * via the {@link #debug(Consumer)} method.
	 */
	private void debug(String prefix, String message) {
		if (debugListener == null) return;
		debugListener.accept("[" + prefix + "-" + serviceID + "] " + message);
	}

	public enum TaskStatus {
		INITIAL, QUEUED, RUNNING, COMPLETE, CANCELED, FAILED, CRASHED;

		/**
		 * @return true iff status is {@link #COMPLETE}, {@link #CANCELED}, {@link #FAILED}, or {@link #CRASHED}.
		 */
		public boolean isFinished() {
			return this == COMPLETE || isError();
		}

		/**
		 * @return true iff status is {@link #CANCELED}, {@link #FAILED}, or {@link #CRASHED}.
		 */
		public boolean isError() {
			return this == CANCELED || this == FAILED || this == CRASHED;
		}
	}

	public enum RequestType {
		EXECUTE, REPLY, CANCEL
	}

	public enum ResponseType {
		HELLO, LAUNCH, UPDATE, CALL, RELEASE, COMPLETION, CANCELATION, FAILURE, CRASH;

		/** True iff response type is COMPLETION, CANCELATION, FAILURE, or CRASH. */
		public boolean isTerminal() {
			return Arrays.asList(COMPLETION, CANCELATION, FAILURE, CRASH).contains(this);
		}
	}

	/**
	 * An Appose *task* is an asynchronous operation performed by its associated
	 * Appose {@link Service}. It is analogous to a {@code Future}.
	 */
	public class Task {

		public final String uuid = UUID.randomUUID().toString();
		public final String script;
		private final Map<String, Object> mInputs = new HashMap<>();
		private final Map<String, Object> mOutputs = new HashMap<>();
		public final Map<String, Object> inputs = Collections.unmodifiableMap(mInputs);
		public final Map<String, Object> outputs = Collections.unmodifiableMap(mOutputs);
		public final String queue;

		public TaskStatus status = TaskStatus.INITIAL;
		public String error;

		private final List<Consumer<TaskEvent>> listeners = new ArrayList<>();

		public Task(String script, Map<String, Object> inputs, String queue) {
			this.script = script;
			if (inputs != null) mInputs.putAll(inputs);
			tasks.put(uuid, this);
			this.queue = queue;
		}

		/**
		 * Begins executing the task.
		 *
		 * @return This task, for fluid method chaining.
		 * @throws IllegalStateException If task is not in {@link TaskStatus#INITIAL} state.
		 */
		public synchronized Task start() {
			validateInitialState();
			if (closing) throw new IllegalStateException("Service is closing; no new task can start");
			status = TaskStatus.QUEUED;
			if (incompatibility != null) {
				tasks.remove(uuid);
				crash(incompatibility);
				return this;
			}

			Map<String, Object> args = new HashMap<>();
			args.put("script", script);
			args.put("inputs", inputs);
			args.put("queue", queue);
			request(RequestType.EXECUTE, args);

			return this;
		}

		/**
		 * Registers a listener to be notified of updates to the task.
		 *
		 * @param listener Function to invoke in response to task status updates.
		 * @return This task, for fluid method chaining.
		 * @throws IllegalStateException If task is not in {@link TaskStatus#INITIAL} state.
		 */
		public synchronized Task listen(Consumer<TaskEvent> listener) {
			validateInitialState();
			listeners.add(listener);
			return this;
		}

		/**
		 * Blocks until the task has finished executing.
		 * <p>
		 * If the task completes successfully ({@link TaskStatus#COMPLETE}), this method
		 * returns normally. If the task experiences an error ({@link TaskStatus#FAILED},
		 * {@link TaskStatus#CANCELED}, or {@link TaskStatus#CRASHED}), this method throws
		 * a {@link TaskException} containing the error details.
		 * </p>
		 *
		 * @return This task, for fluid method chaining.
		 * @throws TaskException If the task does not complete successfully.
		 */
		public synchronized Task waitFor() throws InterruptedException, TaskException {
			if (status == TaskStatus.INITIAL) start();
			if (status == TaskStatus.QUEUED || status == TaskStatus.RUNNING) wait();

			// Check if the task failed and throw an exception if so.
			if (status.isError()) {
				String message = "Task " + status.toString().toLowerCase() + ": " +
					(error != null ? error : "No error message available");
				throw new TaskException(message, this);
			}

			return this;
		}

		/** Sends a task cancelation request to the worker process. */
		public void cancel() {
			request(RequestType.CANCEL, null);
		}

		/**
		 * Returns the result of this task's execution.
		 * <p>
		 * This is a convenience method equivalent to {@code outputs.get("result")}.
		 * The result will be {@code null} if the task has not completed successfully
		 * or if no result was set.
		 * </p>
		 *
		 * @return The task's result value, or {@code null} if none exists.
		 */
		public Object result() {
			return outputs.get("result");
		}

		@Override
		public String toString() {
			return String.format("uuid=%s, status=%s, error=%s", uuid, status, error);
		}

		/** Sends a request to the worker process. */
		private void request(RequestType requestType, Map<String, Object> args) {
			Map<String, Object> request = new HashMap<>();
			request.put("task", uuid);
			request.put("requestType", requestType.toString());
			if (args != null) request.putAll(args);
			send(request);
		}

		private void handle(Map<String, Object> response) {
			String maybeResponseType = (String) response.get("responseType");
			if (maybeResponseType == null) {
				debugService("Message type not specified");
				return;
			}
			ResponseType responseType = ResponseType.valueOf(maybeResponseType);

			switch (responseType) {
				case LAUNCH:
					status = TaskStatus.RUNNING;
					break;
				case UPDATE:
					// No extra action needed.
					break;
				case COMPLETION:
					tasks.remove(uuid);
					status = TaskStatus.COMPLETE;
					@SuppressWarnings({ "rawtypes", "unchecked" })
					Map<String, Object> outputs = (Map) response.get("outputs");
					if (outputs != null) {
						// Convert any worker_object references to proxies before storing outputs.
						for (Map.Entry<String, Object> entry : outputs.entrySet()) {
							Object value = Proxies.proxifyWorkerObjects(entry.getValue(), Service.this);
							mOutputs.put(entry.getKey(), value);
						}
					}
					break;
				case CANCELATION:
					tasks.remove(uuid);
					status = TaskStatus.CANCELED;
					break;
				case FAILURE:
					tasks.remove(uuid);
					status = TaskStatus.FAILED;
					Object error = response.get("error");
					this.error = error == null ? null : error.toString();
					break;
				default:
					debugService("Invalid service message type: " + responseType);
					return;
			}

			String message = (String) response.get("message");
			Number nCurrent = (Number) response.get("current");
			Number nMaximum = (Number) response.get("maximum");
			long current = nCurrent == null ? 0 : nCurrent.longValue();
			long maximum = nMaximum == null ? 0 : nMaximum.longValue();
			Map<String, Object> info = (Map<String, Object>) response.get("info");
			TaskEvent event = new TaskEvent(this, responseType, message, current, maximum, info);
			listeners.forEach(l -> l.accept(event));

			if (status.isFinished()) {
				synchronized (this) {
					notifyAll();
				}
				closeIfIdle();
			}
		}

		private void crash(String error) {
			TaskEvent event = new TaskEvent(this, ResponseType.CRASH);
			status = TaskStatus.CRASHED;
			this.error = error;
			listeners.forEach(l -> l.accept(event));
			synchronized (this) {
				notifyAll();
			}
		}

		private void validateInitialState() {
			if (status == TaskStatus.INITIAL) return;
			throw new IllegalStateException("Task is not in the INITIAL state");
		}
	}
}
