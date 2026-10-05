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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * An Appose *service* provides access to a linked Appose *worker* running in a
 * different process. Using the service, programs create Appose {@link Task}s
 * that run asynchronously in the worker process, which notifies the service of
 * updates via communication over pipes (stdin and stdout).
 */
public class Service implements AutoCloseable {

	private static int serviceCount = 0;

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

	private Process process;
	private PrintWriter stdin;
	private Thread stdoutThread;
	private Thread stderrThread;
	private Thread monitorThread;

	private @Nullable Consumer<String> debugListener;
	private @Nullable String initScript;
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

		// If an init script is provided, write it to a temporary file
		// and pass its path via environment variable.
		if (initScript != null && !initScript.isEmpty()) {
			File initFile = File.createTempFile("appose-init-", ".txt");
			initFile.deleteOnExit();
			Files.write(initFile.toPath(), initScript.getBytes(StandardCharsets.UTF_8));
			envVars.put("APPOSE_INIT_SCRIPT", initFile.getAbsolutePath());
		}

		ProcessBuilder pb = Processes.builder(cwd, envVars, args);
		process = pb.start();
		stdin = new PrintWriter(process.getOutputStream());
		stdoutThread = new Thread(this::stdoutLoop, prefix + "-Stdout");
		stderrThread = new Thread(this::stderrLoop, prefix + "-Stderr");
		monitorThread = new Thread(this::monitorLoop, prefix + "-Monitor");
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
	 * Closes the worker process's input stream, in order to shut it down.
	 * Pending tasks will run to completion before the worker process terminates.
	 * <p>
	 * To shut down the service more forcibly, interrupting any pending tasks,
	 * use {@link #kill()} instead.
	 * </p>
	 * <p>
	 * To wait until the service's worker process has completely shut down
	 * and all output has been reported, call {@link #waitFor()} afterward.
	 * </p>
	 */
	@Override
	public void close() {
		stdin.close();
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
	 */
	public void kill() {
		process.destroyForcibly();
	}

	/**
	 * Waits for the service's worker process to terminate.
	 *
	 * @return Exit value of the worker process.
	 * @throws InterruptedException If any of the worker process's monitoring
	 * 	                             threads are interrupted before shutting down.
	 */
	public int waitFor() throws InterruptedException {
		process.waitFor();

		// Wait for worker output processing threads to finish up.
		stdoutThread.join();
		stderrThread.join();
		monitorThread.join();

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
				Map<String, Object> response = Messages.decode(line);
				debugService(line); // Echo the line to the debug listener.
				if (ResponseType.CALL.toString().equals(response.get("responseType"))) {
					// The worker is calling back into a service object.
					// Handle it on its own thread, so that this loop stays
					// free to process other responses in the meantime.
					Thread t = new Thread(() -> handleCall(response),
						"Appose-Service-" + serviceID + "-Call");
					t.setDaemon(true);
					t.start();
					continue;
				}
				Object uuid = response.get("task");
				if (uuid == null) {
					debugService("Invalid service message:" + line);
					continue;
				}
				Task task = tasks.get(uuid.toString());
				if (task == null) {
					debugService("No such task: " + uuid);
					continue;
				}
				task.handle(response);
			}
			catch (Exception exc) {
				// Something went wrong decoding the line of JSON.
				// Skip it and keep going, but log it first.
				debugService(String.format("<INVALID> %s", line));
				invalidLines.add(line);
			}
		}
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
		// Wait until the worker process terminates.
		while (process.isAlive() || stdoutThread.isAlive() || stderrThread.isAlive()) {
			try {
				process.waitFor();
			}
			catch (InterruptedException exc) {
				// Treat interruption as a request to shut down.
				debugService(Messages.stackTrace(exc));
				break;
			}
		}
		debugService("<worker process termination detected>");

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
		sb.append("Worker crashed with exit code ").append(exitCode).append(".").append(nl);
		String stdout = invalidLines.isEmpty() ? "<none>" : String.join(nl, invalidLines);
		String stderr = errorLines.isEmpty() ? "<none>" : String.join(nl, errorLines);
		sb.append(nl).append("[stdout]").append(nl).append(stdout).append(nl);
		sb.append(nl).append("[stderr]").append(nl).append(stderr).append(nl);
		String error = sb.toString();
		tasks.values().forEach(task -> task.crash(error));
		tasks.clear();
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
		String encoded = Messages.encode(request, this::export);
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
		LAUNCH, UPDATE, CALL, COMPLETION, CANCELATION, FAILURE, CRASH;

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
			status = TaskStatus.QUEUED;

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
