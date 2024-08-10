/*-
 * #%L
 * Appose: multi-language interprocess cooperation with shared memory.
 * %%
 * Copyright (C) 2023 - 2024 Appose developers.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apposed.appose.Service.RequestType;
import org.apposed.appose.Service.ResponseType;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;

/**
 * The Appose worker for running Groovy scripts.
 * <p>
 * Like all Appose workers, this program conforms to the {@link Appose Appose
 * worker process contract}, meaning it accepts requests on stdin and produces
 * responses on stdout, both formatted according to Appose's assumptions.
 * </p>
 */
public class GroovyWorker {

	private static final Map<String, Task> TASKS = new ConcurrentHashMap<>();

	public static void main(String... args) throws IOException {
		BufferedReader stdin = //
			new BufferedReader(new InputStreamReader(System.in));
		while (true) {
			String line = stdin.readLine();
			if (line == null) break; // broken pipe
			Map<String, Object> request = Types.decode(line);
			String uuid = (String) request.get("task");
			String requestType = (String) request.get("requestType");
			switch (RequestType.valueOf(requestType)) {
				case EXECUTE:
					String script = (String) request.get("script");
					@SuppressWarnings({ "rawtypes", "unchecked" })
					Map<String, Object> inputs = (Map) request.get("inputs");
					execute(uuid, script, inputs);
					break;
				case CANCEL:
					cancel(uuid);
					break;
				default:
					break;
			}
		}
	}

	/** Executes the script on a separate thread. */
	private static void execute(String uuid, String script,
		Map<String, Object> inputs)
	{
		Task task = new Task(uuid);
		TASKS.put(uuid, task);
		task.start(script, inputs);
	}

	private static void cancel(String uuid) {
		Task task = TASKS.get(uuid);
		if (task == null) {
			// TODO: proper logging
			// Maybe should stdout the error back to Appose calling process.
			System.err.println("No such task: " + uuid);
			return;
		}
		task.cancelRequested = true;
	}

	/**
	 * Task object tracking the execution of a script. Accessible from the Groovy
	 * script.
	 */
	public static class Task {
		public final String uuid;
		public final Map<Object, Object> outputs = new ConcurrentHashMap<>();
		public boolean cancelRequested;

		public Task(String uuid) {
			this.uuid = uuid;
		}

		public void update(String message, Long current, Long maximum) {
			Map<String, Object> args = new HashMap<>();
			if (message != null) args.put("message", message);
			if (current != null) args.put("current", current);
			if (maximum != null) args.put("maximum", maximum);
			respond(ResponseType.UPDATE, args);
		}

		public void cancel() {
			respond(ResponseType.CANCELATION, null);
		}

		public void fail(String error) {
			Map<String, Object> args = error == null ? null : //
				Collections.singletonMap("error", error);
			respond(ResponseType.FAILURE, args);
		}

		private void start(String script, Map<String, Object> inputs) {
			// TODO: Consider whether to retain a reference to this Thread, and
			// expose a "force" option for cancelation that uses thread.stop().
			new Thread(() -> {
				// Populate script bindings.
				Binding binding = new Binding();
				binding.setVariable("task", Task.this);
				// TODO: Magically convert shared memory image inputs.
				inputs.forEach(binding::setVariable);

				// Inform the calling process that the script is launching.
				reportLaunch();

				// Execute the script.
				Object result;
				try {
					GroovyShell shell = new GroovyShell(binding);
					result = shell.evaluate(script);
				}
				catch (Exception exc) {
					fail(Types.stackTrace(exc));
					return;
				}

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
			}, "Appose-" + uuid).start();
		}

		private void reportLaunch() {
			respond(ResponseType.LAUNCH, null);
		}

		private void reportCompletion() {
			Map<String, Object> args = Collections.singletonMap("outputs", outputs);
			respond(ResponseType.COMPLETION, args);
		}

		private void respond(ResponseType responseType, Map<String, Object> args) {
			Map<String, Object> response = new HashMap<>();
			response.put("task", uuid);
			response.put("responseType", responseType.toString());
			if (args != null) response.putAll(args);
			try {
				System.out.println(Types.encode(response));
			}
			catch (Exception exc) {
				// Encoding can fail due to unsupported types, when the response
				// or its elements are not supported by JSON encoding.
				// No matter what goes wrong, we want to tell the caller.
				if (responseType == ResponseType.FAILURE) {
					// TODO: How to address this hypothetical case
					// of a failure message triggering another failure?
					throw new RuntimeException(exc);
				}
				fail(Types.stackTrace(exc));
			}
			// NB: Flush is necessary to ensure service receives the data!
			System.out.flush();
		}
	}
}
