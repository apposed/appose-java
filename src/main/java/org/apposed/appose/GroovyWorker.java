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

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.apposed.appose.Service.RequestType;
import org.apposed.appose.Service.ResponseType;
import org.apposed.appose.util.Types;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The Appose worker for running Groovy scripts.
 * <p>
 * Like all Appose workers, this program conforms to the Appose worker process
 * contract, meaning it accepts requests on stdin and produces responses on
 * stdout, both formatted according to Appose's assumptions.
 * </p>
 * <p>
 * For details, see the
 * <a href="https://github.com/apposed/appose/blob/-/README.md#workers">Appose
 * README</a>.
 * </p>
 */
public class GroovyWorker {

	private static final Map<String, Object> initVars = new ConcurrentHashMap<>();
	private final Map<String, Task> tasks = new ConcurrentHashMap<>();
	private final Deque<Task> queue = new ArrayDeque<>();
	private final Map<String, Object> exports = new ConcurrentHashMap<>();
	private boolean running = true;

	public GroovyWorker() {
		new Thread(this::processInput, "Appose-Receiver").start();
		new Thread(this::cleanupThreads, "Appose-Janitor").start();
	}

	/** Processes tasks from the task queue. */
	public void run() {
		while (running) {
			if (queue.isEmpty()) {
				// Nothing queued, so wait a bit.
				try {
					Thread.sleep(50);
				}
				catch (InterruptedException ignored) { }
				continue;
			}
			Task task = queue.pop();
			task.run();
		}
	}

	private void processInput() {
		BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
		while (true) {
			String line;
			try {
				line = stdin.readLine();
			}
			catch (IOException exc) {
				line = null;
			}
			if (line == null) {
				running = false;
				break;
			}

			Map<String, Object> request = Types.decode(line);
			String uuid = (String) request.get("task");
			String requestType = (String) request.get("requestType");

			switch (RequestType.valueOf(requestType)) {
				case EXECUTE:
					String script = (String) request.get("script");
					@SuppressWarnings({"rawtypes", "unchecked"})
					Map<String, Object> inputs = (Map) request.get("inputs");
					String queue = (String) request.get("queue");
					Task task = new Task(uuid, script, inputs);
					tasks.put(uuid, task);
					if ("main".equals(queue)) {
						// Add the task to the main thread queue.
						this.queue.add(task);
					}
					else {
						// Create a thread and save a reference to it,
						// in case its script somehow kills the thread.
						task.thread = new Thread(task::run, "Appose-" + uuid);
						task.thread.start();
					}
					break;

				case CANCEL:
					Task taskToCancel = tasks.get(uuid);
					if (taskToCancel == null) {
						System.err.println("No such task: " + uuid);
						continue;
					}
					taskToCancel.cancelRequested = true;
					break;
			}
		}
	}

	private void cleanupThreads() {
		while (running) {
			try {
				Thread.sleep(50);
			}
			catch (InterruptedException ignored) { }
			Map<String, Task> dead = tasks.entrySet().stream()
					.filter(Objects::nonNull)
					.filter(entry -> isTaskDead(entry.getValue()))
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
			for (Map.Entry<String, Task> entry : dead.entrySet()) {
				String uuid = entry.getKey();
				Task task = entry.getValue();
				tasks.remove(uuid);
				if (!task.finished) {
					// The task died before reporting a terminal status.
					// We report this situation as failure by thread death.
					task.fail("thread death");
				}
			}
		}
	}

	private boolean isTaskDead(Task task) {
		return task.thread != null && !task.thread.isAlive();
	}

	public static void main(String... args) {
		// Execute init script if provided via environment variable.
		// This happens before the worker's I/O loop starts, which is useful
		// for initialization that must happen before the worker begins processing tasks.
		String initScriptPath = System.getenv("APPOSE_INIT_SCRIPT");
		if (initScriptPath != null) {
			File initFile = new File(initScriptPath);
			if (initFile.exists()) {
				try {
					String initCode = new String(Files.readAllBytes(initFile.toPath()), StandardCharsets.UTF_8);
					Binding binding = new Binding();
					GroovyShell shell = new GroovyShell(binding);
					shell.evaluate(initCode);
					// Store all variables from the init script for use in tasks
					initVars.putAll(binding.getVariables());
					// Clean up the temp file
					initFile.delete();
				}
				catch (Exception e) {
					System.err.println("[WARNING] Init script failed: " + e.getMessage());
				}
			}
		}

		new GroovyWorker().run();
	}

	/**
	 * Task object tracking the execution of a script. Accessible from the Groovy
	 * script.
	 */
	public class Task {
		public final String uuid;
		public final Map<Object, Object> outputs = new ConcurrentHashMap<>();
		public boolean cancelRequested;

		private final String script;
		private final Map<String, Object> inputs;
		private boolean finished;
		private Thread thread;

		public Task(String uuid, String script, Map<String, Object> inputs) {
			this.uuid = uuid;
			this.script = script;
			this.inputs = inputs;
		}

		@SuppressWarnings("unused")
		public void export(Map<String, Object> vars) {
			exports.putAll(vars);
		}

		@SuppressWarnings("unused")
		public void update(String message) {
			update(message, null, null, null);
		}

		@SuppressWarnings("unused")
		public void update(Long current, Long maximum) {
			update(null, current, maximum, null);
		}

		@SuppressWarnings("unused")
		public void update(Map<String, Object> info) {
			update(null, null, null, info);
		}

		@SuppressWarnings("unused")
		public void update(String message, Long current, Long maximum) {
			update(message, current, maximum, null);
		}

		@SuppressWarnings("unused")
		public void update(String message, Long current, Long maximum, Map<String, Object> info) {
			Map<String, Object> args = new HashMap<>();
			if (message != null) args.put("message", message);
			if (current != null) args.put("current", current);
			if (maximum != null) args.put("maximum", maximum);
			if (info != null) args.put("info", info);
			respond(ResponseType.UPDATE, args);
		}

		@SuppressWarnings("unused")
		public void cancel() {
			respond(ResponseType.CANCELATION, null);
		}

		public void fail(String error) {
			Map<String, Object> args = error == null ? null : //
				Collections.singletonMap("error", error);
			respond(ResponseType.FAILURE, args);
		}

		private void run() {
			try {
				// Populate script bindings.
				Binding binding = new Binding();
				binding.setVariable("task", Task.this);
				initVars.forEach(binding::setVariable);
				exports.forEach(binding::setVariable);
				inputs.forEach(binding::setVariable);

				// Inform the calling process that the script is launching.
				reportLaunch();

				// Execute the script.
				Object result;

				GroovyShell shell = new GroovyShell(binding);
				result = shell.evaluate(script);

				// Report the results to the Appose calling process.
				if (result instanceof Map) {
					// Script produced a map; add all entries to the outputs.
					outputs.putAll((Map<?, ?>) result);
				}
				else if (result != null) {
					// Script produced a non-map; add it alone to the outputs.
					outputs.put("result", result);
				}
				reportCompletion();
			}
			catch (Exception exc) {
				fail(Types.stackTrace(exc));
			}
		}

		private void reportLaunch() {
			respond(ResponseType.LAUNCH, null);
		}

		private void reportCompletion() {
			Map<String, Object> args = Collections.singletonMap("outputs", outputs);
			respond(ResponseType.COMPLETION, args);
		}

		private void respond(ResponseType responseType, Map<String, Object> args) {
			boolean alreadyTerminated = false;
			if (responseType.isTerminal()) {
				if (finished) {
					// This is not the first terminal response. Let's
					// remember, in case an exception is generated below,
					// so that we can avoid infinite recursion loops.
					alreadyTerminated = true;
				}
				finished = true;
			}

			Map<String, Object> response = args == null ? new HashMap<>() : new HashMap<>(args);
			response.put("task", uuid);
			response.put("responseType", responseType.toString());
			try {
				System.out.println(Types.encode(response));
			}
			catch (Exception exc) {
				if (alreadyTerminated) {
					// An exception triggered a failure response which
					// then triggered another exception. Let's stop here
					// to avoid the risk of infinite recursion loops.
					return;
				}
				// Encoding can fail due to unsupported types, when the
				// response or its elements are not supported by JSON encoding.
				// No matter what goes wrong, we want to tell the caller.
				fail(Types.stackTrace(exc));
			}
			// NB: Flush is necessary to ensure service receives the data!
			System.out.flush();
		}
	}
}
