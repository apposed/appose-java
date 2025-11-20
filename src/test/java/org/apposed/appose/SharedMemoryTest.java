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

import org.apposed.appose.util.Platforms;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link SharedMemory}.
 *
 * @author Curtis Rueden
 */
public class SharedMemoryTest {

	@Test
	public void testShmCreate() throws Exception {
		int rsize = 456;
		try (SharedMemory shm = SharedMemory.create(rsize)) {
			assertNotNull(shm.name());
			assertEquals(rsize, shm.rsize()); // REQUESTED size
			assertTrue(rsize <= shm.size()); // ALLOCATED size

			// Modify the memory contents.
			ByteBuffer buffer = shm.buf();
			assertNotNull(buffer);
			assertEquals(rsize, buffer.limit());
			for (int i = 0; i < rsize; i++) {
				buffer.put(i, (byte) (rsize - i));
			}

			// Assert that values have been modified as expected.
			byte[] b = new byte[rsize];
			buffer.get(b);
			for (int i = 0; i < rsize; i++) {
				assertEquals((byte) (rsize - i), b[i]);
			}

			// Assert that another process is able to read the values.
			String output = runPython(
				"from appose import SharedMemory\n" +
					"from sys import stdout\n" +
					"shm = SharedMemory(name='" + shm.name() + "', rsize=" + rsize + ")\n" +
					"matches = sum(1 for i in range(" + rsize + ") if shm.buf[i] == (" + rsize + " - i) % 256)\n" +
					"stdout.write(f'{matches}\\n')\n" +
					"stdout.flush()\n" +
					"shm.unlink()\n" // HACK: to satisfy Python's overly aggressive resource tracker
			);
			assertEquals("" + rsize, output);
		}
	}

	@Test
	public void testShmAttach() throws Exception {
		// Create a named shared memory block in a separate process.
		// The Python process waits for a signal from stdin before unlinking,
		// ensuring deterministic coordination with the Java test.
		try (PythonProcess py = startPythonAndWait(
			"import sys\n" +
			"from appose import SharedMemory\n" +
			"shm = SharedMemory(create=True, rsize=345)\n" +
			"shm.buf[0] = 12\n" +
			"shm.buf[100] = 234\n" +
			"shm.buf[344] = 7\n" +
			"sys.stdout.write(f'{shm.name}|{shm.rsize}|{shm.size}\\n')\n" +
			"sys.stdout.flush()\n" +
			"input()  # Wait for Java to signal completion\n" +
			"shm.unlink()\n"
		)) {
			// Parse the output into the name and size of the shared memory block.
			String[] shmInfo = py.firstLine.split("\\|");
			assertEquals(3, shmInfo.length);
			String shmName = shmInfo[0];
			assertNotNull(shmName);
			assertFalse(shmName.isEmpty());
			int shmRSize = Integer.parseInt(shmInfo[1]);
			assertEquals(345, shmRSize);
			int shmSize = Integer.parseInt(shmInfo[2]);
			assertTrue(shmSize >= 345);

			// Attach to the shared memory and verify it matches expectations.
			try (SharedMemory shm = SharedMemory.attach(shmName, shmRSize)) {
				assertNotNull(shm);
				assertEquals(shmName, shm.name());
				assertEquals(shmRSize, shm.rsize());
				// Note: We do not test that shmSize and shm.size() match exactly,
				// because Python and appose-java's SharedMemory code will not
				// necessarily behave identically when it comes to block rounding.
				// Notably, on Windows, Python does not round, whereas ShmWindows
				// rounds up to the next block size (4K on GitHub Actions CI).
				assertTrue(shm.size() >= 345);
				ByteBuffer buf = shm.buf();
				assertNotNull(buf);
				assertEquals(shmRSize, buf.limit());
				assertEquals(12, buf.get(0));
				assertEquals((byte) 234, buf.get(100));
				assertEquals(7, buf.get(344));
			}
		}
	}

	private static String pythonCommand() {
		return Platforms.isWindows() ? "python.exe" : "python";
	}

	/**
	 * Runs a Python script, reads the first line of output, and waits for completion.
	 * For scripts that need to coordinate with Java before exiting, use startPythonAndWait.
	 */
	private static String runPython(String script) throws Exception {
		try (PythonProcess py = startPythonAndWait(script)) {
			return py.firstLine;
		}
	}

	/**
	 * Starts a Python process with the given script, waits for first line of output,
	 * but keeps the process alive for later coordination via stdin.
	 * The returned PythonProcess must be closed (signals Python to exit via stdin).
	 */
	private static PythonProcess startPythonAndWait(String script) throws IOException {
		// Write script to a temp file so stdin is available for coordination
		File tempScript = File.createTempFile("appose-test-", ".py");
		tempScript.deleteOnExit();
		try (FileWriter fw = new FileWriter(tempScript)) {
			fw.write(script);
		}

		Process p = new ProcessBuilder(pythonCommand(), "-u", tempScript.getAbsolutePath()).start();
		BufferedWriter stdin = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));
		BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
		String line = stdout.readLine();
		assertNotNull(line, "Python program returned no output");
		return new PythonProcess(p, stdin, line);
	}

	/**
	 * Helper class to hold a running Python process and its first output line.
	 * Implements AutoCloseable to handle cleanup automatically.
	 */
	private static class PythonProcess implements AutoCloseable {
		final Process process;
		final BufferedWriter stdin;
		final String firstLine;

		PythonProcess(Process process, BufferedWriter stdin, String firstLine) {
			this.process = process;
			this.stdin = stdin;
			this.firstLine = firstLine;
		}

		@Override
		public void close() throws Exception {
			// Signal Python to exit by sending a newline and closing stdin
			try {
				stdin.write("\n");
				stdin.flush();
				stdin.close();
			} catch (IOException e) {
				// Ignore - process may have already exited
			}
			boolean exited = process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
			if (!exited) {
				process.destroyForcibly();
			}
		}
	}
}
