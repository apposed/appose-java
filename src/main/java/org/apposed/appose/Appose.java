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

import java.io.File;
import java.io.IOException;

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
 * task.waitFor();
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
 * {@code python_worker} to run Python scripts, and {@link GroovyWorker}
 * to run Groovy scripts. These workers can be created easily by invoking
 * the {@link Environment#python} and {@link Environment#groovy} methods
 * respectively.
 * </p>
 * <p>
 * But Appose is compatible with any program that abides by the
 * <em>Appose worker process contract</em>:
 * </p>
 * <ol>
 * <li>The worker must accept requests in Appose's request format on its
 * standard input (stdin) stream.</li>
 * <li>The worker must issue responses in Appose's response format on its
 * standard output (stdout) stream.</li>
 * </ol>
 * <h3>Requests to worker from service</h3>
 * <p>
 * A <em>request</em> is a single line of JSON sent to the worker process via
 * its standard input stream. It has a {@code task} key taking the form of a
 * <a href="https://en.wikipedia.org/wiki/Universally_unique_identifier">UUID</a>,
 * and a {@code requestType} key with one of the following values:
 * </p>
 * <h4>EXECUTE</h4>
 * <p>
 * Asynchronously execute a script within the worker process. E.g.:
 * </p>
 * <pre><code>
 * {
 *    "task" : "87427f91-d193-4b25-8d35-e1292a34b5c4",
 *    "requestType" : "EXECUTE",
 *    "script" : "task.outputs[\"result\"] = computeResult(gamma)\n",
 *    "inputs" : {"gamma": 2.2}
 * }
 * </code></pre>
 * <h4>CANCEL</h4>
 * <p>
 * Cancel a running script. E.g.:
 * </p>
 * <pre><code>
 * {
 *    "task" : "87427f91-d193-4b25-8d35-e1292a34b5c4",
 *    "requestType" : "CANCEL"
 * }
 * </code></pre>
 * 
 * <h3>Responses from worker to service</h3>
 * <p>
 * A <em>response</em> is a single line of JSON with a {@code task} key
 * taking the form of a
 * <a href="https://en.wikipedia.org/wiki/Universally_unique_identifier">UUID</a>,
 * and a {@code responseType} key with one of the following values:
 * </p>
 * <h4>LAUNCH</h4>
 * <p>
 * A LAUNCH response is issued to confirm the success of an EXECUTE request.
 * </p>
 * <pre><code>
 * {
 *    "task" : "87427f91-d193-4b25-8d35-e1292a34b5c4",
 *    "responseType" : "LAUNCH"
 * }
 * </code></pre>
 * <h4>UPDATE</h4>
 * <p>
 * An UPDATE response is issued to convey that a task has somehow made
 * progress. The UPDATE response typically comes bundled with a {@code message}
 * string indicating what has changed, {@code current} and/or {@code maximum}
 * progress indicators conveying the step the task has reached, or both.
 * </p>
 * <pre><code>
 * {
 *    "task" : "87427f91-d193-4b25-8d35-e1292a34b5c4",
 *    "responseType" : "UPDATE",
 *    "message" : "Processing step 0 of 91",
 *    "current" : 0,
 *    "maximum" : 91
 * }
 * </code></pre>
 * <h4>COMPLETION</h4>
 * <p>
 * A COMPLETION response is issued to convey that a task has successfully
 * completed execution, as well as report the values of any task outputs.
 * </p>
 * <pre><code>
 * {
 *    "task" : "87427f91-d193-4b25-8d35-e1292a34b5c4",
 *    "responseType" : "COMPLETION",
 *    "outputs" : {"result" : 91}
 * }
 * </code></pre>
 * <h4>CANCELATION</h4>
 * <p>
 * A CANCELATION response is issued to confirm the success of a CANCEL request.
 * </p>
 * <pre><code>
 * {
 *    "task" : "87427f91-d193-4b25-8d35-e1292a34b5c4",
 *    "responseType" : "CANCELATION"
 * }
 * </code></pre>
 * <h4>FAILURE</h4>
 * <p>
 * A FAILURE response is issued to convey that a task did not completely
 * and successfully execute, such as an exception being raised.
 * </p>
 * <pre><code>
 * {
 *    "task" : "87427f91-d193-4b25-8d35-e1292a34b5c4",
 *    "responseType" : "FAILURE",
 *    "error", "Invalid gamma value"
 * }
 * </code></pre>
 *
 * @author Curtis Rueden
 */
public class Appose {

	public static Builder scheme(String scheme) {
		return new Builder().scheme(scheme);
	}

	public static Builder file(String filePath) throws IOException {
		return new Builder().file(filePath);
	}

	public static Builder file(String filePath, String scheme) throws IOException {
		return new Builder().file(filePath, scheme);
	}

	public static Builder file(File file) throws IOException {
		return new Builder().file(file);
	}

	public static Builder file(File file, String scheme) throws IOException {
		return new Builder().file(file, scheme);
	}

	public static Builder channel(String name) {
		return new Builder().channel(name);
	}

	public static Builder channel(String name, String location) {
		return new Builder().channel(name, location);
	}

	public static Builder include(String content) {
		return new Builder().include(content);
	}

	public static Builder include(String content, String scheme) {
		return new Builder().include(content, scheme);
	}

	@Deprecated
	public static Builder conda(File environmentYaml) throws IOException {
		return file(environmentYaml, "environment.yml");
	}

	public static Environment build(File directory) throws IOException {
		return new Builder().build(directory);
	}

	public static Environment build(String directory) throws IOException {
		return build(new File(directory));
	}

	public static Environment system() throws IOException {
		return system(new File("."));
	}

	public static Environment system(File directory) throws IOException {
		return new Builder().useSystemPath().build(directory);
	}

	public static Environment system(String directory) throws IOException {
		return system(new File(directory));
	}
}
