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

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
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

	private static Map<String, Task> tasks = new ConcurrentHashMap<>();

	public static void main(String... args) throws IOException {
		BufferedReader stdin = //
			new BufferedReader(new InputStreamReader(System.in));
		while (true) {
			String line = stdin.readLine();
			if (line == null) break; // broken pipe
			Map<String, Object> request = decode(line);
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
		tasks.put(uuid, task);
		task.start(script, inputs);
	}

	private static void cancel(String uuid) {
		Task task = tasks.get(uuid);
		if (task == null) {
			// TODO: proper logging
			// Maybe should stdout the error back to Appose calling process.
			System.err.println("No such task: " + uuid);
			return;
		}
		task.cancelRequested = true;
	}

	// -- JSON processing --

	private static String encode(Map<?, ?> data) {
		return JsonOutput.toJson(data);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> decode(String json) {
		return (Map<String, Object>) new JsonSlurper().parseText(json);
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

		private void start(String script, Map<String, Object> inputs) {
			// TODO: Consider whether to retain a reference to this Thread,
			// and expose a "force" option for cancelation that uses thread.stop().
			new Thread(() -> {
				// Populate script bindings.
				Binding binding = new Binding();
				binding.setVariable("task", Task.this);
				// TODO: Magically convert shared memory image inputs.
				inputs.forEach((k, v) -> binding.setVariable(k, v));

				// Inform the calling process that the script is launching.
				reportLaunch();

				// Execute the script.
				GroovyShell shell = new GroovyShell(binding);

				// Report the results to the Appose calling process.
				Object result = shell.evaluate(script);
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

		private void reportLaunch() {
			respond(ResponseType.LAUNCH, null);
		}

		private void reportCompletion() {
			Map<String, Object> args = outputs == null ? null : //
				Collections.singletonMap("outputs", outputs);
			respond(ResponseType.COMPLETION, args);
		}

		private void respond(ResponseType responseType, Map<String, Object> args) {
			Map<String, Object> response = new HashMap<>();
			response.put("task", uuid);
			response.put("responseType", responseType.toString());
			if (args != null) response.putAll(args);
			System.out.println(encode(response));
			System.out.flush();
		}
	}
}
