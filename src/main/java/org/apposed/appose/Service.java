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

import org.apposed.appose.util.Processes;
import org.apposed.appose.util.Types;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

	private Process process;
	private PrintWriter stdin;
	private Thread stdoutThread;
	private Thread stderrThread;
	private Thread monitorThread;

	private Consumer<String> debugListener;

	public Service(File cwd, String... args) {
		this(cwd, null, args);
	}

	public Service(File cwd, Map<String, String> envVars, String... args) {
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
	public void debug(Consumer<String> debugListener) {
		this.debugListener = debugListener;
	}

	/**
	 * Launches the worker process associated with this service.
	 *
	 * @return This service object, for chaining method calls (typically with {@link #task}).
	 * @throws IOException If the process fails to execute; see {@link ProcessBuilder#start()}.
	 */
	public Service start() throws IOException {
		if (process != null) {
			// Already started.
			return this;
		}

		String prefix = "Appose-Service-" + serviceID;
		ProcessBuilder pb = Processes.builder(cwd, envVars, false, args);
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
	 * @throws IOException If something goes wrong communicating with the worker.
	 */
	public Task task(String script) throws IOException {
		return task(script, null, null);
	}

	/**
	 * Creates a new task, passing the given script to the worker for execution.
	 *
	 * @param script The script for the worker to execute in its environment.
	 * @param inputs Optional list of key/value pairs to feed into the script as inputs.
	 * @return The newly created {@link Task} object tracking the execution.
	 * @throws IOException If something goes wrong communicating with the worker.
	 */
	public Task task(String script, Map<String, Object> inputs) throws IOException {
		return task(script, inputs, null);
	}

	/**
	 * Creates a new task, passing the given script to the worker for execution.
	 *
	 * @param script The script for the worker to execute in its environment.
	 * @param queue Optional queue target. Pass "main" to queue to worker's main thread.
	 * @return The newly created {@link Task} object tracking the execution.
	 * @throws IOException If something goes wrong communicating with the worker.
	 */
	public Task task(String script, String queue) throws IOException {
		return task(script, null, queue);
	}

	/**
	 * Creates a new task, passing the given script to the worker for execution.
	 *
	 * @param script The script for the worker to execute in its environment.
	 * @param inputs Optional list of key/value pairs to feed into the script as inputs.
	 * @param queue Optional queue target. Pass "main" to queue to worker's main thread.
	 * @return The newly created {@link Task} object tracking the execution.
	 * @throws IOException If something goes wrong communicating with the worker.
	 */
	public Task task(String script, Map<String, Object> inputs, String queue) throws IOException {
		start();
		return new Task(script, inputs, queue);
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
	/** Input loop processing lines from the worker stdout stream. */
	private void stdoutLoop() {
		BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
		while (true) {
			String line;
			try {
				line = stdout.readLine();
			}
			catch (IOException exc) {
				// Something went wrong reading the line. Panic!
				debugService(Types.stackTrace(exc));
				break;
			}

			if (line == null) {
				debugService("<worker stdout closed>");
				break;
			}
			try {
				Map<String, Object> response = Types.decode(line);
				debugService(line); // Echo the line to the debug listener.
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
				// Something went wrong reading the line. Panic!
				debugService(Types.stackTrace(exc));
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

	@SuppressWarnings("BusyWait")
	private void monitorLoop() {
		// Wait until the worker process terminates.
		while (process.isAlive() || stdoutThread.isAlive() || stderrThread.isAlive()) {
			try {
				Thread.sleep(10);
			}
			catch (InterruptedException exc) {
				debugService(Types.stackTrace(exc));
			}
		}
		debugService("<worker process termination detected>");

		// Do some sanity checks.
		int exitCode = process.exitValue();
		if (exitCode != 0) debugService("<worker process terminated with exit code " + exitCode + ">");
		int taskCount = tasks.size();
		if (taskCount > 0) {
			debugService("<worker process terminated with " +
				taskCount + " pending task" + (taskCount == 1 ? "" : "s") + ">");
		}

		Collection<Task> remainingTasks = tasks.values();
		if (!remainingTasks.isEmpty()) {
			// Notify any remaining tasks about the process crash.
			StringBuilder sb = new StringBuilder();
			String nl = System.lineSeparator();
			sb.append("Worker crashed with exit code ").append(exitCode).append(".").append(nl);
			String stdout = invalidLines.isEmpty() ? "<none>" : String.join(nl, invalidLines);
			String stderr = errorLines.isEmpty() ? "<none>" : String.join(nl, errorLines);
			sb.append(nl).append("[stdout]").append(nl).append(stdout).append(nl);
			sb.append(nl).append("[stderr]").append(nl).append(stderr).append(nl);
			String error = sb.toString();
			remainingTasks.forEach(task -> task.crash(error));
		}
		tasks.clear();
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
			return this == COMPLETE || this == CANCELED || this == FAILED || this == CRASHED;
		}
	}

	public enum RequestType {
		EXECUTE, CANCEL
	}

	public enum ResponseType {
		LAUNCH, UPDATE, COMPLETION, CANCELATION, FAILURE, CRASH;

		/** True iff response type is COMPLETE, CANCELED, FAILED, or CRASHED. */
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

		public synchronized Task start() {
			if (status != TaskStatus.INITIAL) throw new IllegalStateException();
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
		 */
		public synchronized void listen(Consumer<TaskEvent> listener) {
			if (status != TaskStatus.INITIAL) {
				throw new IllegalStateException("Task is not in the INITIAL state");
			}
			listeners.add(listener);
		}

		public synchronized void waitFor() throws InterruptedException {
			if (status == TaskStatus.INITIAL) start();
			if (status != TaskStatus.QUEUED && status != TaskStatus.RUNNING) return;
			wait();
		}

		/** Sends a task cancelation request to the worker process. */
		public void cancel() {
			request(RequestType.CANCEL, null);
		}

		/** Sends a request to the worker process. */
		private void request(RequestType requestType, Map<String, Object> args) {
			Map<String, Object> request = new HashMap<>();
			request.put("task", uuid);
			request.put("requestType", requestType.toString());
			if (args != null) request.putAll(args);
			String encoded = Types.encode(request);

			stdin.println(encoded);
			// NB: Flush is necessary to ensure worker receives the data!
			stdin.flush();
			debugService(encoded);
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
					if (outputs != null) mOutputs.putAll(outputs);
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

		@Override
		public String toString() {
			return String.format("uuid=%s, status=%s, error=%s", uuid, status, error);
		}
	}
}
