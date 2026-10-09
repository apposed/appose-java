/*-
 * #%L
 * Appose: multi-language interprocess cooperation with shared memory.
 * %%
 * Copyright (C) 2023 - 2026 Appose developers.
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

import org.apposed.appose.NDArray.DType;
import org.apposed.appose.NDArray.Shape;
import org.apposed.appose.Service.Task;
import org.junit.jupiter.api.Test;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apposed.appose.NDArray.Shape.Order.C_ORDER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the mechanics of managed shared memory, across processes. */
public class ManagedTest extends TestBase {

	private static Environment numpyEnv() throws BuildException {
		return pythonEnv("ndarray-check-python", "numpy");
	}

	private static NDArray filledFloats(int... shape) {
		NDArray nda = NDArray.managed(DType.FLOAT32, new Shape(C_ORDER, shape));
		FloatBuffer buf = nda.buffer().asFloatBuffer();
		for (int i = 0; i < buf.capacity(); i++) buf.put(i, i);
		return nda;
	}

	/** Whether the service has freed all of its managed memory. */
	private static boolean allFreed() throws InterruptedException {
		return eventually(() -> regionCount() == 0);
	}

	/** Runs the garbage collector in the given (Java) worker. */
	private static void gcWorker(Service worker) {
		try {
			worker.task("System.gc()").waitFor();
		}
		catch (Exception exc) {
			throw new RuntimeException(exc);
		}
	}

	@Test
	public void testJavaToPython() throws Exception {
		try (Service service = numpyEnv().python().init("import numpy")) {
			maybeDebug(service);
			NDArray nda = filledFloats(2, 3);
			String name = nda.shm().name();
			Task task = service.task("type(arr).__name__, arr.dtype.name, arr.tolist()",
				Collections.singletonMap("arr", nda)).waitFor();
			assertComplete(task);
			assertEquals("[ndarray, float32, [[0.0, 1.0, 2.0], [3.0, 4.0, 5.0]]]",
				String.valueOf(task.result()));

			// Once neither process uses the array, it is freed.
			// NB: The task refers to its inputs, so it must go, too.
			nda = null;
			task = null;
			assertTrue(allFreed());
			assertFalse(shmExists(name));
		}
	}

	@Test
	public void testPythonToJava() throws Exception {
		try (Service service = numpyEnv().python().init("import numpy")) {
			maybeDebug(service);
			Task task = service.task("numpy.arange(6, dtype='int32').reshape(2, 3) * 2").waitFor();
			assertComplete(task);
			Object result = task.result();
			assertInstanceOf(NDArray.class, result);
			NDArray nda = (NDArray) result;
			assertEquals(DType.INT32, nda.dType());
			assertEquals(Arrays.asList(2, 3), toList(nda.shape().toIntArray(C_ORDER)));
			assertTrue(((SharedMemoryView) nda.shm()).managed());
			IntBuffer ints = nda.buffer().asIntBuffer();
			for (int i = 0; i < 6; i++) assertEquals(2 * i, ints.get(i));
			String name = nda.shm().name();

			// While a buffer derived from the array is in use, the region stays allocated.
			nda = null;
			result = null;
			task = null;
			System.gc();
			Thread.sleep(200);
			assertEquals(1, regionCount());
			assertEquals(8, ints.get(4));

			// Once the array is garbage collected, the region is freed.
			ints = null;
			assertTrue(allFreed());
			assertFalse(shmExists(name));
		}
	}

	@Test
	public void testExplicitClose() throws Exception {
		try (Service service = numpyEnv().python().init("import numpy")) {
			maybeDebug(service);
			NDArray nda = (NDArray) service.task("numpy.ones(4)").waitFor().result();
			String name = nda.shm().name();
			assertTrue(shmExists(name));
			nda.close();
			assertTrue(allFreed());
			assertFalse(shmExists(name));
		}
	}

	@Test
	public void testGroovy() throws Exception {
		try (Service service = Appose.system().groovy()) {
			maybeDebug(service);
			NDArray input = filledFloats(4);
			String script =
				"import org.apposed.appose.NDArray\n" +
				"import org.apposed.appose.NDArray.DType\n" +
				"import org.apposed.appose.NDArray.Shape\n" +
				"def sum = 0\n" +
				"def floats = arr.buffer().asFloatBuffer()\n" +
				"for (int i = 0; i < floats.capacity(); i++) sum += floats.get(i)\n" +
				"def out = NDArray.managed(DType.INT32, new Shape(Shape.Order.C_ORDER, 3))\n" +
				"def ints = out.buffer().asIntBuffer()\n" +
				"for (int i = 0; i < 3; i++) ints.put(i, (int) sum + i)\n" +
				"task.outputs['managed'] = arr.shm().managed()\n" +
				"task.outputs['out'] = out\n";
			Task task = service.task(script, Collections.singletonMap("arr", input)).waitFor();
			assertComplete(task);
			assertEquals(true, task.outputs.get("managed"));
			NDArray out = (NDArray) task.outputs.get("out");
			IntBuffer ints = out.buffer().asIntBuffer();
			assertEquals(Arrays.asList(6, 7, 8), Arrays.asList(ints.get(0), ints.get(1), ints.get(2)));

			// Once no process uses either array anymore, both are freed.
			input = null;
			out = null;
			ints = null;
			task = null;
			assertTrue(eventually(() -> {
				gcWorker(service);
				return regionCount() == 0;
			}));
		}
	}

	@Test
	public void testWorkerCrash() throws Exception {
		Service service = numpyEnv().python().init("import numpy");
		maybeDebug(service);
		NDArray input = filledFloats(4);
		Task task = service.task("task.export(kept=arr)\nimport time\ntime.sleep(30)",
			Collections.singletonMap("arr", input));
		task.start();
		input = null;
		Thread.sleep(500);
		System.gc();
		assertEquals(1, regionCount());

		// If the worker dies, the service drops its references.
		service.kill();
		service.waitFor();
		task = null;
		assertTrue(allFreed());
	}

	@Test
	public void testOutputAfterClose() throws Exception {
		Service service = numpyEnv().python().init("import numpy");
		maybeDebug(service);
		Task task = service.task("import time\ntime.sleep(0.5)\nnumpy.arange(5, dtype='int64') * 3");
		task.start();
		service.close();
		assertThrows(IllegalStateException.class, () -> service.task("1").start());
		service.waitFor();
		task.waitFor();
		NDArray nda = (NDArray) task.result();
		assertEquals(12, nda.buffer().asLongBuffer().get(4));

		// The service holds the result's region, even with the worker gone.
		assertEquals(1, regionCount());
		nda.close();
		assertTrue(allFreed());
	}

	@Test
	public void testRelayByReference() throws Exception {
		try (
			Service python = numpyEnv().python().init("import numpy");
			Service groovy = Appose.system().groovy()
		) {
			maybeDebug(python);
			maybeDebug(groovy);
			NDArray nda = (NDArray) python.task("numpy.arange(6, dtype='float32')").waitFor().result();
			String name = nda.shm().name();

			// Sent on to another worker, the array is not copied.
			Object relayed = groovy.task("task.export(kept: arr)\narr.shm().name()",
				Collections.singletonMap("arr", nda)).waitFor().result();
			assertEquals(name, relayed);

			// It stays allocated as long as the other worker uses it.
			nda = null;
			Thread.sleep(200);
			System.gc();
			Thread.sleep(200);
			assertEquals(1, regionCount());
			groovy.task("task.export(kept: [])").waitFor();
			assertTrue(eventually(() -> {
				gcWorker(groovy);
				return regionCount() == 0;
			}));
		}
	}

	@Test
	public void testClosedViewNotSent() throws Exception {
		NDArray nda = NDArray.managed(DType.INT8, new Shape(C_ORDER, 4));
		nda.close();
		try (Service worker = Appose.system().groovy()) {
			Throwable t = assertThrows(Throwable.class,
				() -> worker.task("data", Collections.singletonMap("data", nda)).start());
			while (t.getCause() != null) t = t.getCause();
			assertInstanceOf(IllegalStateException.class, t);
			assertTrue(t.getMessage().contains("already closed"), t.getMessage());
		}
	}

	private static List<Integer> toList(int[] values) {
		List<Integer> list = new ArrayList<>();
		for (int v : values) list.add(v);
		return list;
	}
}
