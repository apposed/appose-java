package org.apposed.appose;

import java.io.File;

/**
 * Appose is a library for interprocess cooperation with shared memory. The
 * guiding principles are <em>simplicity</em> and <em>efficiency</em>.
 * <p>
 * Appose was written to enable <strong>easy execution of Python-based deep
 * learning from Java without copying tensors</strong>, but its utility extends
 * beyond that. The steps for using Appose are:
 * </p>
 * <ol>
 * <li>Build an {@link Environment} with the dependencies you need.</li>
 * <li>Create a {@link Service} linked to a <em>worker</em>, which runs in its
 * own process.</li>
 * <li>Execute scripts on the worker by launching {@link Service.Task
 * Tasks}.</li>
 * <li>Receive status updates from the task asynchronously
 * {@link Service.Task#listen via callbacks}.</li>
 * </ol>
 * <h2>Examples</h2>
 * <ul>
 * <li>TODO - move the below code somewhere linkable, for succinctness
 * here.</li>
 * </ul>
 * <p>
 * Here is a very simple example written in Java:
 * </p>
 * 
 * <pre>{@code
 * Environment env = Appose.conda("/path/to/environment.yml").build();
 * Service python = env.python();
 * Task task = python.task("""
 *     5 + 6
 *     """);
 * task.start().waitFor();
 * Object result = task.outputs.get("result");
 * assertEquals(11, result);
 * }</pre>
 * <p>
 * And here is an example using a few more of Appose's features:
 * </p>
 * 
 * <pre>{@code
 * Environment env = Appose.conda("/path/to/environment.yml").build();
 * Service python = env.python();
 * Task golden_ratio = python.task("""
 *     # Approximate the golden ratio using the Fibonacci sequence.
 *     previous = 0
 *     current = 1
 *     for i in range(iterations):
 *         if task.cancel_requested:
 *             task.cancel()
 *             break
 *         task.status(current=i, maximum=iterations)
 *         v = current
 *         current += previous
 *         previous = v
 *     task.outputs["numer"] = current
 *     task.outputs["denom"] = previous
 *     """);
 * task.listen(event -> {
 *     switch (event.responseType) {
 *         case UPDATE:
 *             System.out.println("Progress: " + task.current + "/" + task.maximum);
 *             break;
 *         case COMPLETION:
 *             long numer = (Long) task.outputs["numer"];
 *             long denom = (Long) task.outputs["denom"];
 *             double ratio = (double) numer / denom;
 *             System.out.println("Task complete. Result: " + numer + "/" + denom + " =~ " + ratio);
 *             break;
 *         case CANCELATION:
 *             System.out.println("Task canceled");
 *             break;
 *         case FAILURE:
 *             System.out.println("Task failed: " + task.error);
 *             break;
 *     }
 * });
 * task.start();
 * Thread.sleep(1000);
 * if (!task.status.isFinished()) {
 *     // Task is taking too long; request a cancelation.
 *     task.cancel();
 * }
 * task.waitFor();
 * }</pre>
 * <p>
 * Of course, the above examples could have been done all in Java. But hopefully
 * they hint at the possibilities of easy cross-language integration.
 * </p>
 * <h2>Workers</h2>
 * <p>
 * A <em>worker</em> is a separate process created by Appose to do asynchronous
 * computation on behalf of the calling process. The calling process interacts
 * with a worker via its associated {@link Service}.
 * </p>
 * <p>
 * Appose comes with built-in support for two worker implementations:
 * {@code python-worker} to run Python scripts, and {@link GroovyWorker} to run
 * Groovy scripts. These workers can be created easily by invoking the
 * {@link Environment#python} and {@link Environment#groovy} methods
 * respectively. But Appose is compatible with any program that abides by the
 * <em>Appose worker process contract</em>:
 * </p>
 * <ol>
 * <li>The worker must accept requests in Appose's request format on its
 * standard input (stdin) stream.</li>
 * <li>The worker must issue responses in Appose's response format on its
 * standard output (stdout) stream.</li>
 * </ol>
 * <p>
 * TODO - write up the request and response formats in detail here!
 * JSON, one line per request/response.
 * </p>
 * 
 * @author Curtis Rueden
 */
public class Appose {

	public static Builder base(File directory) {
		return new Builder().base(directory);
	}

	public static Builder java(String vendor, String version) {
		return new Builder().java(vendor, version);
	}

	public static Builder conda(File environmentYaml) {
		return new Builder().conda(environmentYaml);
	}
}
