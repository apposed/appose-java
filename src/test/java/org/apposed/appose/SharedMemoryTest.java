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

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests {@link SharedMemory}.
 *
 * @author Curtis Rueden
 */
public class SharedMemoryTest {

	@Test
	public void testShmCreate() throws IOException {
		int size = 456;
		try (SharedMemory shm = SharedMemory.create(null, size)) {
			assertNotNull(shm.name());
			assertEquals(size, shm.size());
			assertNotNull(shm.pointer());

			// Modify the memory contents.
			ByteBuffer buffer = shm.pointer().getByteBuffer(0, size);
			for (int i = 0; i < size; i++) {
				buffer.put(i, (byte) (size - i));
			}

			// Assert that values have been modified as expected.
			byte[] b = new byte[size];
			buffer.get(b);
			for (int i = 0; i < size; i++) {
				assertEquals((byte) (size - i), b[i]);
			}

			// Assert that another process is able to read the values.
			String output = runPython(
				"from multiprocessing.shared_memory import SharedMemory\n" +
					"from sys import stdout\n" +
					"shm = SharedMemory(name='" + shm.name() + "', size=" + shm.size() + ")\n" +
					"matches = sum(1 for i in range(shm.size) if shm.buf[i] == (shm.size - i) % 256)\n" +
					"stdout.write(f'{matches}\\n')\n" +
					"stdout.flush()\n" +
					"shm.unlink()\n" // HACK: to satisfy Python's overly aggressive resource tracker
			);
			assertEquals("" + size, output);
		}
	}

	@Test
	public void testShmAttach() throws IOException {
		// Create a named shared memory block in a separate process.
		// NB: I originally tried passing the Python script as an argument to `-c`,
		// but it gets truncated (don't know why) and the program fails to execute.
		// So instead, the program is passed in via stdin. But that means the
		// program itself cannot read from stdin as a means of waiting for Java to
		// signal its completion of the test asserts; we use a hacky sleep instead.
		String output = runPython(
			"from multiprocessing.shared_memory import SharedMemory\n" +
			"from sys import stdout\n" +
			"shm = SharedMemory(create=True, size=345)\n" +
			"shm.buf[0] = 12\n" +
			"shm.buf[100] = 234\n" +
			"shm.buf[344] = 7\n" +
			"stdout.write(f'{shm.name}|{shm.size}\\n')\n" +
			"stdout.flush()\n" +
			"import time; time.sleep(0.2)\n" + // HACK: horrible, but keeps things simple
			"shm.unlink()\n"
		);

		// Parse the output into the name and size of the shared memory block.
		String[] shmInfo = output.split("\\|");
		assertEquals(2, shmInfo.length);
		String shmName = shmInfo[0];
		assertNotNull(shmName);
		assertFalse(shmName.isEmpty());
		int shmSize = Integer.parseInt(shmInfo[1]);
		assertEquals(345, shmSize);

		// Attach to the shared memory and verify it matches expectations.
		try (SharedMemory shm = SharedMemory.attach(shmName, shmSize)) {
			assertNotNull(shm);
			assertEquals(shmName, shm.name());
			assertEquals(shmSize, shm.size());
			ByteBuffer buf = shm.pointer().getByteBuffer(0, shmSize);
			assertEquals(12, buf.get(0));
			assertEquals((byte) 234, buf.get(100));
			assertEquals(7, buf.get(344));
		}

		// NB: No need to clean up the shared memory explicitly,
		// since the Python program will unlink it and terminate
		// upon completion of the sleep instruction.
	}

	private static String runPython(String script) throws IOException {
		boolean isWindows = System.getProperty("os.name").startsWith("Win");
		String pythonCommand = isWindows ? "python.exe" : "python";
		ProcessBuilder pb = new ProcessBuilder().command(pythonCommand);
		Process p = pb.start();
		try (BufferedWriter os = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()))) {
			os.write(script);
			os.flush();
		}
		BufferedReader is = new BufferedReader(new InputStreamReader(p.getInputStream()));
		String line = is.readLine();
		assertNotNull(line, "Python program returned no output");
		return line;
	}
}