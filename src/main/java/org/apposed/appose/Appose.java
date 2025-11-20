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

import org.apposed.appose.builder.BuildException;
import org.apposed.appose.builder.Builders;
import org.apposed.appose.builder.DynamicBuilder;
import org.apposed.appose.builder.MambaBuilder;
import org.apposed.appose.builder.PixiBuilder;
import org.apposed.appose.builder.SimpleBuilder;
import org.apposed.appose.builder.UvBuilder;
import org.apposed.appose.util.Versions;

import java.io.File;
import java.net.URL;

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
 * Environment env = Appose.mamba("/path/to/environment.yml").build();
 * Service python = env.python();
 * Task task = python.task("""
 *     5 + 6
 *     """);
 * task.waitFor();
 * assertEquals(11, task.result());
 * }</pre>
 * <p>
 * And here is an example using a few more of Appose's features:
 * </p>
 * 
 * <pre>{@code
 * Environment env = Appose.mamba("/path/to/environment.yml").build();
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
 *    "script" : "task.outputs[\"answer\"] = computeResult(gamma)\n",
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

	// -- Fluid Builder API access --

	/**
	 * Creates a Pixi-based environment builder.
	 * Pixi supports both conda and PyPI packages natively.
	 *
	 * @return A new {@link PixiBuilder} instance.
	 */
	public static PixiBuilder pixi() {
		return new PixiBuilder();
	}

	/**
	 * Creates a Pixi-based environment builder from a source (file path or URL).
	 * Auto-detects whether the source is a local file or remote URL.
	 *
	 * @param source Path to configuration file (e.g., {@code "pixi.toml"},
	 *               {@code "/path/to/environment.yml"}) or URL
	 *               (e.g., {@code "https://example.com/pixi.toml"})
	 * @return A new builder configured with the source content.
	 * @throws BuildException If the source cannot be read or is invalid.
	 */
	public static PixiBuilder pixi(String source) throws BuildException {
		return isURL(source) ? pixi().url(source) : pixi().file(source);
	}

	/**
	 * Creates a Pixi-based environment builder from a file.
	 *
	 * @param source File path of environment.yml file
	 * @return A new builder configured with the file content.
	 * @throws BuildException If the file cannot be read.
	 */
	public static PixiBuilder pixi(File source) throws BuildException {
		return pixi().file(source);
	}

	/**
	 * Creates a Pixi-based environment builder from a URL.
	 *
	 * @param source URL to configuration file (e.g., pixi.toml or environment.yml)
	 * @return A new builder configured with the URL content.
	 * @throws BuildException If the URL cannot be read.
	 */
	public static PixiBuilder pixi(URL source) throws BuildException {
		return pixi().url(source);
	}

	/**
	 * Creates a Micromamba-based environment builder.
	 * Micromamba uses traditional conda environments.
	 *
	 * @return A new {@link MambaBuilder} instance.
	 */
	public static MambaBuilder mamba() {
		return new MambaBuilder();
	}

	/**
	 * Creates a Micromamba-based environment builder from a source (file path or URL).
	 * Auto-detects whether the source is a local file or remote URL.
	 *
	 * @param source Path to environment.yml file or URL to environment.yml
	 * @return A new builder configured with the source content.
	 * @throws BuildException If the source cannot be read or is invalid.
	 */
	public static MambaBuilder mamba(String source) throws BuildException {
		return isURL(source) ? mamba().url(source) : mamba().file(source);
	}

	/**
	 * Creates a Micromamba-based environment builder from a file.
	 *
	 * @param source File path of environment.yml file
	 * @return A new builder configured with the file content.
	 * @throws BuildException If the file cannot be read.
	 */
	public static MambaBuilder mamba(File source) throws BuildException {
		return mamba().file(source);
	}

	/**
	 * Creates a Micromamba-based environment builder from a URL.
	 *
	 * @param source URL to environment.yml file
	 * @return A new builder configured with the URL content.
	 * @throws BuildException If the URL cannot be read.
	 */
	public static MambaBuilder mamba(URL source) throws BuildException {
		return mamba().url(source);
	}

	/**
	 * Creates a uv-based virtual environment builder.
	 * uv is a fast Python package installer and resolver.
	 *
	 * @return A new {@link UvBuilder} instance.
	 */
	public static UvBuilder uv() {
		return new UvBuilder();
	}

	/**
	 * Creates a uv-based virtual environment builder from a source (file path or URL).
	 * Auto-detects whether the source is a local file or remote URL.
	 *
	 * @param source Path to environment configuration (e.g. requirements.txt or pyproject.toml)
	 * @return A new builder configured with the source content.
	 * @throws BuildException If the source cannot be read or is invalid.
	 */
	public static UvBuilder uv(String source) throws BuildException {
		return isURL(source) ? uv().url(source) : uv().file(source);
	}

	/**
	 * Creates a uv-based virtual environment builder from a file.
	 *
	 * @param source File path of requirements.txt file
	 * @return A new builder configured with the file content.
	 * @throws BuildException If the file cannot be read.
	 */
	public static UvBuilder uv(File source) throws BuildException {
		return uv().file(source);
	}

	/**
	 * Creates a uv-based virtual environment builder from a URL.
	 *
	 * @param source URL to requirements.txt file
	 * @return A new builder configured with the URL content.
	 * @throws BuildException If the URL cannot be read.
	 */
	public static UvBuilder uv(URL source) throws BuildException {
		return uv().url(source);
	}

	/**
	 * Creates a builder that auto-detects the appropriate handler
	 * based on the file content.
	 *
	 * @param source Path to environment configuration file
	 * @return A new builder configured with the file content.
	 * @throws BuildException If the file cannot be read or is invalid.
	 */
	public static DynamicBuilder file(String source) throws BuildException {
		return new DynamicBuilder().file(source);
	}

	/**
	 * Creates a builder that auto-detects the appropriate handler
	 * based on the file content.
	 *
	 * @param source Path to environment configuration file
	 * @return A new builder configured with the file content.
	 * @throws BuildException If the file cannot be read.
	 */
	public static DynamicBuilder file(File source) throws BuildException {
		return new DynamicBuilder().file(source);
	}

	/**
	 * Creates a builder that auto-detects the appropriate handler
	 * based on the URL content.
	 *
	 * @param source URL to environment configuration file
	 * @return A new builder configured with the URL content.
	 * @throws BuildException If the URL cannot be read or is invalid.
	 */
	public static DynamicBuilder url(String source) throws BuildException {
		return new DynamicBuilder().url(source);
	}

	/**
	 * Creates a builder that auto-detects the appropriate handler
	 * based on the URL content.
	 *
	 * @param source URL to environment configuration file
	 * @return A new builder configured with the URL content.
	 * @throws BuildException If the URL cannot be read.
	 */
	public static DynamicBuilder url(URL source) throws BuildException {
		return new DynamicBuilder().url(source);
	}

	/**
	 * Creates a builder that auto-detects the appropriate handler
	 * based on configuration file content. The scheme is inferred
	 * by analyzing the content structure.
	 *
	 * @param content Configuration file content (e.g., environment.yml or pixi.toml content).
	 * @return A new DynamicBuilder instance pre-configured with the detected scheme.
	 */
	public static DynamicBuilder content(String content) {
		return new DynamicBuilder().content(content);
	}

	// -- Direct Environment shortcuts --

	/**
	 * Wraps an existing environment directory, automatically detecting the environment type.
	 * This method intelligently detects whether the directory contains a pixi, mamba/conda,
	 * or other environment and sets up the appropriate activation.
	 *
	 * @param envDir The directory containing the environment.
	 * @return An Environment configured for the detected type.
	 * @throws BuildException If the directory doesn't exist or type cannot be determined.
	 */
	public static Environment wrap(File envDir) throws BuildException {
		if (!envDir.exists()) {
			throw new BuildException(null, "Environment directory does not exist: " + envDir);
		}

		// Find a builder factory that can wrap this directory.
		BuilderFactory factory = Builders.findFactoryForWrapping(envDir);

		if (factory != null) {
			return factory.createBuilder().wrap(envDir);
		}

		// Default to simple builder (no special activation, just use binaries in directory).
		return custom().wrap(envDir);
	}

	/**
	 * Wraps an existing environment directory, automatically detecting the environment type.
	 *
	 * @param envDir The path to the directory containing the environment.
	 * @return An Environment configured for the detected type.
	 * @throws BuildException If the directory doesn't exist or type cannot be determined.
	 */
	public static Environment wrap(String envDir) throws BuildException {
		return wrap(new File(envDir));
	}

	/**
	 * Creates a system environment with sensible defaults.
	 * <p>
	 * This is a convenience method equivalent to:
	 * <pre>
	 * Appose.custom().inheritRunningJava().appendSystemPath().build()
	 * </pre>
	 * The resulting environment:
	 * <ul>
	 * <li>Uses the parent process's Java installation</li>
	 * <li>Includes the system PATH for finding executables</li>
	 * <li>Uses the current directory as the working directory</li>
	 * </ul>
	 *
	 * @return A system environment ready to use.
	 */
	public static Environment system() {
		try {
			return custom()
				.inheritRunningJava()
				.appendSystemPath()
				.build();
		}
		catch (BuildException exc) {
			// Note: The only way SimpleBuilder can throw BuildException during the
			// build is if the base directory does not exist. But the above invocation
			// will use "." for the base directory, which certainly already exists.
			throw new RuntimeException("Guru meditation");
		}
	}

	/**
	 * Creates a custom simple environment builder with no defaults. Use this
	 * when you need explicit control over binary paths and configuration.
	 *
	 * @return A new {@link SimpleBuilder} instance.
	 * @see #wrap(File)
	 * @see #wrap(String)
	 */
	public static SimpleBuilder custom() {
		return new SimpleBuilder();
	}

	/**
	 * Gets the version of Appose in use.
	 *
	 * @return The version string extracted from the JAR manifest,
	 *   or {@code "<dev>"} if there is no enclosing JAR with
	 *   appropriate manifest entries.
	 */
	public static String version() {
		String v = Versions.version(Appose.class);
		return v == null ? "(unknown)" : v;
	}

	// -- Helper methods --

	/**
	 * Checks if a string appears to be a URL.
	 * Currently detects http:// and https:// prefixes (case-insensitive).
	 * Does not treat single-letter schemes like "C:" as URLs.
	 *
	 * @param source The source string to check
	 * @return true if the string looks like a URL
	 */
	private static boolean isURL(String source) {
		if (source == null || source.isEmpty()) return false;
		String lower = source.toLowerCase();
		return lower.startsWith("http://") || lower.startsWith("https://");
	}
}
