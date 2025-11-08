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

package org.apposed.appose.builder;

import org.apposed.appose.util.Downloads;
import org.apposed.appose.util.Platforms;
import org.apposed.appose.util.Processes;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Base class for external tool helpers (Mamba, Pixi, uv, etc.).
 * Provides common functionality for process execution, stream handling,
 * and progress tracking.
 *
 * @author Curtis Rueden
 * @author Carlos Garcia Lopez de Haro
 * @author Claude Code
 */
public abstract class Tool {

	/** The name of the external tool (e.g. uv, pixi, micromamba). */
	protected final String name;

	/** Remote URL to use when downloading the tool. */
	protected final String url;

	/** Path to the tool's executable command. */
	protected final String command;

	/** Root directory where the tool is installed. */
	protected final String rootdir;

	/** Consumer that tracks the standard output stream produced by the tool process. */
	protected Consumer<String> outputConsumer;

	/** Consumer that tracks the standard error stream produced by the tool process. */
	protected Consumer<String> errorConsumer;

	/** Consumer that tracks download progress when installing the tool. */
	protected BiConsumer<Long, Long> downloadProgressConsumer;

	/** Environment variables to set when running tool commands. */
	protected Map<String, String> envVars = new HashMap<>();

	/** Additional command-line flags to pass to tool commands. */
	protected List<String> flags = new ArrayList<>();

	/** Captured stdout from the last command execution. */
	protected final StringBuilder capturedOutput = new StringBuilder();

	/** Captured stderr from the last command execution. */
	protected final StringBuilder capturedError = new StringBuilder();

	public Tool(String name, String url, String command, String rootdir) {
		this.name = name;
		this.url = url;
		this.command = command;
		this.rootdir = rootdir;
	}

	/**
	 * Sets a consumer to receive standard output from the tool process.
	 * @param consumer Consumer that processes output strings
	 */
	public void setOutputConsumer(Consumer<String> consumer) {
		this.outputConsumer = consumer;
	}

	/**
	 * Sets a consumer to receive standard error from the tool process.
	 * @param consumer Consumer that processes error strings
	 */
	public void setErrorConsumer(Consumer<String> consumer) {
		this.errorConsumer = consumer;
	}

	/**
	 * Sets a consumer to track download progress during tool installation.
	 * @param consumer Consumer that receives (current, total) progress updates
	 */
	public void setDownloadProgressConsumer(BiConsumer<Long, Long> consumer) {
		this.downloadProgressConsumer = consumer;
	}

	/**
	 * Sets environment variables to be passed to tool processes.
	 * @param envVars Map of environment variable names to values
	 */
	public void setEnvVars(Map<String, String> envVars) {
		if (envVars != null) {
			this.envVars = new HashMap<>(envVars);
		}
	}

	/**
	 * Sets additional command-line flags to pass to tool commands.
	 * @param flags List of command-line flags
	 */
	public void setFlags(List<String> flags) {
		if (flags != null) {
			this.flags = new ArrayList<>(flags);
		}
	}

	/**
	 * Get the version of the installed tool.
	 * <p>
	 * This default implementation calls the tool with {@code --version} and
	 * extracts the first whitespace-delimited token that starts with a digit.
	 * Subclasses can override this method if their tool uses a different
	 * version reporting format.
	 * </p>
	 *
	 * @return The version string.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 */
	public String version() throws IOException, InterruptedException {
		// Example output of supported tools with --version flag:
		// - 2.3.3
		// - pixi 0.58.0
		// - uv 0.5.25 (9c07c3fc5 2025-01-28)
		execDirect("--version");
		String output = capturedOutput.toString();
		for (String token : output.split(" ")) {
			char c = token.isEmpty() ? '\0' : token.charAt(0);
			if (c >= '0' && c <= '9') return token; // starts with a digit
		}
		return output;
	}

	/**
	 * Downloads and installs the external tool.
	 *
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void install() throws IOException, InterruptedException {
		if (isInstalled()) return;
		decompress(download());
	}

	/**
	 * Gets whether the tool is installed or not
	 * @return whether the tool is installed or not
	 */
	public boolean isInstalled() {
		try {
			version();
			return true;
		} catch (IOException | InterruptedException e) {
			return false;
		}
	}

	protected File download() throws IOException, InterruptedException {
		try {
			return Downloads.download(name, url, this::updateDownloadProgress);
		}
		catch (URISyntaxException e) {
			// If this happens, it's a bug in the Tool implementation: the
			// URL being used internally to download the tool is malformed.
			// Let's not propagate that URISyntaxException downstream.
			throw new RuntimeException(e);
		}
	}

	protected abstract void decompress(final File archive) throws IOException, InterruptedException;

	/**
	 * Executes a tool command with the specified arguments in the tool's root directory.
	 *
	 * @param args Command arguments for the tool.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 * @throws IllegalStateException If the tool has not been installed.
	 */
	protected void exec(String... args) throws IOException, InterruptedException {
		exec(null, args);
	}

	/**
	 * Executes a tool command with the specified arguments.
	 *
	 * @param cwd Working directory for the command (null to use tool's root directory).
	 * @param args Command arguments for the tool.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 * @throws IllegalStateException If the tool has not been installed.
	 */
	protected void exec(File cwd, String... args) throws IOException, InterruptedException {
		if (!isInstalled()) throw new IllegalStateException(name + " is not installed");
		doExec(cwd, false, true, args); // silent=false, includeFlags=true
	}

	/**
	 * Executes a tool command with the specified arguments, without validating the tool installation beforehand,
	 * without passing output to external listeners (see {@link #setOutputConsumer} and {@link #setErrorConsumer}),
	 * and
	 *
	 * @param args Command arguments for the tool.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 */
	protected void execDirect(String... args) throws IOException, InterruptedException {
		doExec(null, true, false, args); // silent=true, includeFlags=false
	}

	/**
	 * Executes a tool command with the specified arguments,
	 * without validating the tool installation beforehand.
	 *
	 * @param cwd Working directory for the command (null to use tool's root directory).
	 * @param silent If false, pass command output along to external listeners
	 *                  (see {@link #setOutputConsumer} and {@link #setErrorConsumer}).
	 * @param includeFlags If true, include {@link #flags} in the command argument list (see {@link #setFlags}).
	 * @param args Command arguments for the tool.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 */
	private void doExec(File cwd, boolean silent, boolean includeFlags, String... args) throws IOException, InterruptedException {
		// Clear captured output from previous command.
		capturedOutput.setLength(0);
		capturedError.setLength(0);

		final List<String> cmd = Platforms.baseCommand();
		cmd.add(command);
		if (includeFlags) cmd.addAll(flags);
		cmd.addAll(Arrays.asList(args));

		final String workingDir = cwd != null ? cwd.getAbsolutePath() : rootdir;
		final ProcessBuilder builder = Processes.builder(new File(workingDir), envVars);
		builder.command(cmd);
		int exitCode = Processes.run(builder,
			silent ? capturedOutput::append : this::output,
			silent ? capturedError::append : this::error);

		if (exitCode != 0) {
			StringBuilder errorMsg = new StringBuilder();
			errorMsg.append(name).append(" command failed with exit code ").append(exitCode);
			errorMsg.append(": ").append(String.join(" ", args));

			// Include stderr if available.
			String stderr = capturedError.toString().trim();
			if (!stderr.isEmpty()) {
				errorMsg.append("\n\nError output:\n").append(stderr);
			}

			// Include stdout if available and stderr was empty.
			String stdout = capturedOutput.toString().trim();
			if (stderr.isEmpty() && !stdout.isEmpty()) {
				errorMsg.append("\n\nOutput:\n").append(stdout);
			}

			throw new IOException(errorMsg.toString());
		}
	}

	/**
	 * Handles a line from the tool's standard output stream.
	 * <ul>
	 * <li>Captures the output for later inclusion in error messages.</li>
	 * <li>Updates the output consumer with a message, if one is registered.</li>
	 * </ul>
	 * @param line The line of stdout to process
	 */
	protected void output(String line) {
		if (line == null || line.isEmpty()) return;
		capturedOutput.append(line);
		if (outputConsumer != null) outputConsumer.accept(line);
	}

	/**
	 * Handles a line from the tool's standard error stream.
	 * <ul>
	 * <li>Captures the error for later inclusion in error messages.</li>
	 * <li>Updates the error consumer with a message, if one is registered.</li>
	 * </ul>
	 * @param line The line of stderr to process
	 */
	protected void error(String line) {
		if (line == null || line.isEmpty()) return;
		capturedError.append(line);
		if (errorConsumer != null) errorConsumer.accept(line);
	}

	/**
	 * Updates the download progress consumer, if one is registered.
	 * @param current Current progress value
	 * @param total Total progress value
	 */
	protected void updateDownloadProgress(long current, long total) {
		if (downloadProgressConsumer != null) {
			downloadProgressConsumer.accept(current, total);
		}
	}
}
