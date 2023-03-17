package org.apposed.appose;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;

/**
 * An Appose *service* provides access to a linked Appose *worker* running in a
 * different process. Using the service, programs create Appose {@link Task}s
 * that run asynchronously in the worker process, which notifies the service of
 * updates via communication over pipes (stdin and stdout).
 */
public class Service {

	private static int serviceCount = 0;

	private final Process process;
	private final PrintWriter stdin;
	private final BufferedReader stdout;
	private final Thread thread;
	private final Map<String, Task> tasks = new ConcurrentHashMap<>();

	public Service(File cwd, String... args) throws IOException {
		ProcessBuilder pb = new ProcessBuilder(args).directory(cwd);
		process = pb.start();
		stdin = new PrintWriter(process.getOutputStream());
		stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
		thread = new Thread(() -> {
			while (true) {
				try {
					String line = stdout.readLine();
					if (line == null) return; // pipe closed
					Map<String, Object> response = decode(line);
					Object uuid = response.get("task");
					if (uuid == null) {
						// TODO: proper logging
						System.err.println("Invalid service message:\n" + line);
						continue;
					}
					Task task = tasks.get(uuid.toString());
					if (task == null) {
						// TODO: proper logging
						System.err.println("No such task: " + uuid);
						continue;
					}
					task.handle(response);
				}
				catch (IOException exc) {
					// TODO: proper logging
					exc.printStackTrace();
					return;
				}
			}
		}, "Appose-Service-" + ++serviceCount);
		thread.start();
	}

	public Task task(String script) {
		return task(script, null);
	}

	public Task task(String script, Map<String, Object> inputs) {
		return new Task(script, inputs);
	}

	public static enum TaskStatus {
		INITIAL, QUEUED, RUNNING, COMPLETE, CANCELED, FAILED;

		public boolean isFinished() {
			return this == COMPLETE || this == CANCELED || this == FAILED;
		}
	}

	public static enum RequestType {
		EXECUTE, CANCEL
	}

	public static enum ResponseType {
		LAUNCH, UPDATE, COMPLETION, CANCELATION, FAILURE
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

		public TaskStatus status = TaskStatus.INITIAL;
		public String message;
		public long current;
		public long maximum = 1;
		public String error;

		private final List<Consumer<TaskEvent>> listeners = new ArrayList<>();

		public Task(String script, Map<String, Object> inputs) {
			this.script = script;
			if (inputs != null) mInputs.putAll(inputs);
			tasks.put(uuid, this);
		}

		public synchronized Task start() {
			if (status != TaskStatus.INITIAL) throw new IllegalStateException();
			status = TaskStatus.QUEUED;

			Map<String, Object> args = new HashMap<>();
			args.put("script", script);
			args.put("inputs", inputs);
			request(RequestType.EXECUTE, args);

			return this;
		}

		/** Registers a callback function to be notified of updates to the task. */
		public synchronized void listen(Consumer<TaskEvent> listener) {
			if (status != TaskStatus.INITIAL) throw new IllegalStateException();
			listeners.add(listener);
		}

		public synchronized void waitFor() throws InterruptedException {
			if (status == TaskStatus.INITIAL) start();
			if (status != TaskStatus.QUEUED && status != TaskStatus.RUNNING) return;
			wait();
		}

		/** Sends a task cancelation request to the service executor. */
		public void cancel() {
			request(RequestType.CANCEL, null);
		}

		/** Sends a request to the service executor. */
		private void request(RequestType requestType, Map<String, Object> args) {
			Map<String, Object> request = new HashMap<>();
			request.put("task", uuid);
			request.put("requestType", requestType.toString());
			if (args != null) request.putAll(args);
			stdin.println(encode(request));
			stdin.flush(); // NB: Necessary to ensure worker receives the data!
		}

		@SuppressWarnings("hiding")
		private void handle(Map<String, Object> response) {
			String responseType = (String) response.get("responseType");
			if (responseType == null) {
				// TODO: proper logging
				System.err.println("Message type not specified");
				return;
			}
			switch (ResponseType.valueOf(responseType)) {
				case LAUNCH:
					status = TaskStatus.RUNNING;
					break;
				case UPDATE:
					message = (String) response.get("message");
					Number current = (Number) response.get("current");
					Number maximum = (Number) response.get("maximum");
					if (current != null) current = current.longValue();
					if (maximum != null) maximum = maximum.longValue();
					break;
				case COMPLETION:
					tasks.remove(uuid);
					status = TaskStatus.COMPLETE;
					@SuppressWarnings({ "rawtypes", "unchecked" })
					Map<String, Object> outputs = (Map) response.get("outputs");
					// TODO: Magically convert shared memory image outputs.
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
					if (error != null) error = error.toString();
					break;
				default:
					// TODO: proper logging
					System.err.println("Invalid service message type: " + responseType);
					return;
			}
			TaskEvent event = new TaskEvent(this, ResponseType.valueOf(responseType));
			listeners.forEach(l -> l.accept(event));

			if (status == TaskStatus.COMPLETE || status == TaskStatus.CANCELED ||
				status == TaskStatus.FAILED)
			{
				synchronized (this) {
					notifyAll();
				}
			}
		}
	}

	// -- JSON processing --

	private static String encode(Map<String, Object> data) {
		return JsonOutput.toJson(data);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> decode(String json) {
		return (Map<String, Object>) new JsonSlurper().parseText(json);
	}
}
