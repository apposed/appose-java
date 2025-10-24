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

import org.apposed.appose.util.Processes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Base class for external tool helpers (Mamba, Pixi, UV, etc.).
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

	public Tool(String name) {
		this.name = name;
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
	 *
	 * @return The version string.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 */
	abstract String version() throws IOException, InterruptedException;

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

	/**
	 * Check whether the tool is installed or not
	 * @throws IllegalStateException if the tool is not installed
	 */
	protected void checkInstalled() {
		if (!isInstalled()) throw new IllegalStateException(name + " is not installed");
	}

	/**
	 * Creates a ProcessBuilder configured with environment variables.
	 * @param cwd Path to the working directory
	 * @param isInheritIO Whether to inherit I/O streams from parent process
	 * @return Configured ProcessBuilder
	 */
	protected ProcessBuilder processBuilder(String cwd, boolean isInheritIO) {
		return Processes.builder(new File(cwd), envVars, isInheritIO);
	}

	/**
	 * Updates the output consumer with a message, if one is registered.
	 * @param message The message to send to the output consumer
	 */
	protected void updateOutputConsumer(String message) {
		if (outputConsumer != null && message != null && !message.isEmpty()) {
			outputConsumer.accept(message);
		}
	}

	/**
	 * Updates the error consumer with a message, if one is registered.
	 * @param message The message to send to the error consumer
	 */
	protected void updateErrorConsumer(String message) {
		if (errorConsumer != null && message != null && !message.isEmpty()) {
			errorConsumer.accept(message);
		}
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

	/**
	 * Reads stdout and stderr streams from a process, reporting each line to consumers.
	 * This method blocks until the process completes.
	 * 
	 * @param process The process to read streams from
	 * @param mainThread The main thread to monitor for interruption
	 * @throws IOException If an I/O error occurs
	 * @throws InterruptedException If interrupted while reading
	 */
	protected void readStreams(Process process, Thread mainThread) throws IOException, InterruptedException {
		try (
			InputStream inputStream = process.getInputStream();
			InputStream errStream = process.getErrorStream()
		) {
			byte[] buffer = new byte[1024];
			StringBuilder processBuff = new StringBuilder();
			StringBuilder errBuff = new StringBuilder();
			int newLineIndex;

			while (process.isAlive() || inputStream.available() > 0 || errStream.available() > 0) {
				if (!mainThread.isAlive()) {
					process.destroyForcibly();
					return;
				}

				// Read stdout
				if (inputStream.available() > 0) {
					processBuff.append(new String(buffer, 0, inputStream.read(buffer)));
					while ((newLineIndex = processBuff.indexOf(System.lineSeparator())) != -1) {
						String line = processBuff.substring(0, newLineIndex);
						updateOutputConsumer(line + System.lineSeparator());
						processBuff.delete(0, newLineIndex + System.lineSeparator().length());
					}
				}

				// Read stderr
				if (errStream.available() > 0) {
					errBuff.append(new String(buffer, 0, errStream.read(buffer)));
					while ((newLineIndex = errBuff.indexOf(System.lineSeparator())) != -1) {
						String line = errBuff.substring(0, newLineIndex);
						updateErrorConsumer(line + System.lineSeparator());
						errBuff.delete(0, newLineIndex + System.lineSeparator().length());
					}
				}

				// Sleep to avoid busy waiting
				Thread.sleep(60);
			}

			// Flush remaining output
			if (inputStream.available() > 0) {
				processBuff.append(new String(buffer, 0, inputStream.read(buffer)));
				String remaining = processBuff.toString();
				if (!remaining.isEmpty()) {
					updateOutputConsumer(remaining + System.lineSeparator());
				}
			}

			// Flush remaining errors
			if (errStream.available() > 0) {
				errBuff.append(new String(buffer, 0, errStream.read(buffer)));
				String remaining = errBuff.toString();
				if (!remaining.isEmpty()) {
					updateErrorConsumer(remaining + System.lineSeparator());
				}
			}
		}
	}
}
