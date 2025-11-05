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

package org.apposed.appose.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Utility class for working with processes.
 *
 * @author Curtis Rueden
 */
public final class Processes {

	private Processes() {
		// Prevent instantiation of utility class.
	}

	/**
	 * Creates a ProcessBuilder with environment variables applied.
	 *
	 * @param workingDir Working directory for the process (can be null).
	 * @param envVars Environment variables to set (can be null or empty).
	 * @param command Command and arguments to execute.
	 * @return Configured ProcessBuilder ready to start.
	 */
	public static ProcessBuilder builder(File workingDir, Map<String, String> envVars,
		String... command)
	{
		ProcessBuilder pb = new ProcessBuilder(command);
		if (workingDir != null) {
			pb.directory(workingDir);
		}
		if (envVars != null && !envVars.isEmpty()) {
			pb.environment().putAll(envVars);
		}
		return pb;
	}

	public static int run(ProcessBuilder processBuilder, Consumer<String> output, Consumer<String> error)
		throws IOException, InterruptedException
	{
		Process process = processBuilder.start();
		Thread mainThread = Thread.currentThread();
		IOException[] ioExc = {null};
		InterruptedException[] interExc = {null};
		Thread outputThread = new Thread(() -> {
			try {
				readStreams(process, mainThread, output, error);
			}
			catch (IOException e) {
				ioExc[0] = e;
			}
			catch (InterruptedException e) {
				interExc[0] = e;
			}
		});

		outputThread.start();
		int exitCode = process.waitFor();
		outputThread.join();
		if (ioExc[0] != null) throw ioExc[0];
		if (interExc[0] != null) throw interExc[0];
		return exitCode;
	}

	/**
	 * Reads stdout and stderr streams from a process, reporting output to consumers.
	 * Uses separate threads to read each stream concurrently to avoid blocking.
	 * This method blocks until the process completes and all output has been read.
	 *
	 * @param process The process to read streams from.
	 * @param mainThread The main thread to monitor for interruption.
	 * @param output Consumer of stdout content.
	 * @param error Consumer of stderr content.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If interrupted while reading.
	 */
	private static void readStreams(Process process, Thread mainThread,
		Consumer<String> output, Consumer<String> error)
		throws IOException, InterruptedException
	{
		List<IOException> ioExceptions = new CopyOnWriteArrayList<>();

		// Start threads to read stdout and stderr concurrently.
		Thread stdoutThread = new Thread(() -> {
			try {
				readStream(process.getInputStream(), output);
			}
			catch (IOException e) {
				ioExceptions.add(e);
			}
		});
		Thread stderrThread = new Thread(() -> {
			try {
				readStream(process.getErrorStream(), error);
			}
			catch (IOException e) {
				ioExceptions.add(e);
			}
		});
		stdoutThread.start();
		stderrThread.start();

		// Monitor for main thread death while process runs.
		while (process.isAlive() && mainThread.isAlive()) {
			Thread.sleep(50);
		}

		// If main thread died, forcibly terminate the process.
		if (process.isAlive()) process.destroyForcibly();

		// Wait for stream reading threads to finish draining output.
		stdoutThread.join();
		stderrThread.join();

		// Propagate any IOExceptions from stream reading.
		if (!ioExceptions.isEmpty()) {
			IOException primary = ioExceptions.get(0);
			for (int i = 1; i < ioExceptions.size(); i++) {
				primary.addSuppressed(ioExceptions.get(i));
			}
			throw primary;
		}
	}

	/**
	 * Reads a single stream until EOF, reporting complete lines and any final partial line.
	 *
	 * @param stream The input stream to read.
	 * @param consumer Consumer to receive the output.
	 * @throws IOException If an I/O error occurs.
	 */
	private static void readStream(InputStream stream, Consumer<String> consumer) throws IOException {
		byte[] buffer = new byte[8192];
		StringBuilder lineBuffer = new StringBuilder();
		int bytesRead;

		while ((bytesRead = stream.read(buffer)) != -1) {
			String chunk = new String(buffer, 0, bytesRead);
			int start = 0;
			int newlineIndex;

			// Process all complete lines in this chunk.
			while ((newlineIndex = chunk.indexOf(System.lineSeparator(), start)) != -1) {
				// Append everything up to and including the newline.
				lineBuffer.append(chunk, start, newlineIndex + System.lineSeparator().length());
				consumer.accept(lineBuffer.toString());
				lineBuffer.setLength(0);
				start = newlineIndex + System.lineSeparator().length();
			}

			// Buffer any remaining partial line.
			if (start < chunk.length()) {
				lineBuffer.append(chunk.substring(start));
			}
		}

		// Output any remaining partial line (no trailing newline).
		if (lineBuffer.length() > 0) {
			consumer.accept(lineBuffer.toString());
		}
	}
}
