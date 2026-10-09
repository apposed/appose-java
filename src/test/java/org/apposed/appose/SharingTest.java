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
import java.nio.ShortBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.apposed.appose.NDArray.Shape.Order.C_ORDER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Showcase of the ways to share array data between processes.
 * <p>
 * Each test is a small, realistic scenario. Read them as examples:
 * </p>
 * <ul>
 * <li><em>Managed memory, handed off</em>: send an array and stop using it,
 * and the receiver has it to itself. See {@link NDArray#managed}.</li>
 * <li><em>Managed memory, shared</em>: keep using an array after sending it,
 * and every holder views the same memory. Use it for cached image cells, or
 * output images that workers write into.</li>
 * <li><em>Unmanaged memory</em>: a block the application creates and frees
 * itself, e.g. a buffer reused across many calls, or a worker's own ring
 * buffer.</li>
 * </ul>
 * <p>
 * The service owns all managed memory, and frees each region once no
 * process uses it anymore. See the "Sharing Arrays Between Processes" page
 * of the Appose docs.
 * </p>
 */
public class SharingTest extends TestBase {

	private Service python() throws BuildException {
		Service service = pythonEnv("ndarray-check-python", "numpy").python().init("import numpy");
		maybeDebug(service);
		return service;
	}

	private Service groovy() {
		Service service = Appose.system().groovy();
		maybeDebug(service);
		return service;
	}

	/** Whether the service has freed all of its managed memory. */
	private static boolean allFreed(Service... javaWorkers) throws InterruptedException {
		return eventually(() -> {
			for (Service worker : javaWorkers) gcWorker(worker);
			return regionCount() == 0;
		});
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

	// -- Managed memory, handed off --

	/**
	 * A worker thresholds an image, and hands the resulting mask back.
	 * <p>
	 * The service allocates the image in managed memory, sends it, and stops
	 * using it, so the worker has it to itself. Likewise, the mask comes back
	 * as an array the service has to itself. Each region is freed once its
	 * last holder is done with it.
	 * </p>
	 */
	@Test
	public void testHandoffResult() throws Exception {
		NDArray image = NDArray.managed(DType.FLOAT32, new Shape(C_ORDER, 4, 4));
		FloatBuffer pixels = image.buffer().asFloatBuffer();
		for (int i = 0; i < 16; i++) pixels.put(i, i / 15f);
		pixels = null;
		try (Service worker = python()) {
			Task task = worker.task("image > 0.5", Collections.singletonMap("image", image)).waitFor();
			image = null; // Handed off: the service no longer uses the image.
			assertComplete(task);
			NDArray mask = (NDArray) task.result();
			assertEquals(DType.BOOL, mask.dType());
			for (int i = 0; i < 16; i++) assertEquals(i > 7 ? 1 : 0, mask.buffer().get(i));
			mask.close(); // Done with the mask.
			task = null;
			assertTrue(allFreed());
		}
	}

	/** A reader in the service, which decodes each chunk straight into managed memory. */
	public static class ChunkReader {
		public NDArray read(int index) {
			NDArray chunk = NDArray.managed(DType.UINT16, new Shape(C_ORDER, 64, 64));
			ShortBuffer buf = chunk.buffer().asShortBuffer();
			for (int i = 0; i < buf.capacity(); i++) buf.put(i, (short) index); // E.g. decompress.
			return chunk;
		}
	}

	/**
	 * A worker pulls chunks of a large image from a reader in the service.
	 * <p>
	 * The reader decodes each chunk directly into a managed array, so the
	 * chunk is never copied: the worker receives it, as a NumPy array, and
	 * the chunk is freed once the worker is done with it.
	 * </p>
	 */
	@Test
	public void testHandoffChunksFromReader() throws Exception {
		try (Service worker = python()) {
			Task task = worker.task("[int(reader.read(i).sum()) for i in range(3)]",
				Collections.singletonMap("reader", new ChunkReader())).waitFor();
			assertComplete(task);
			assertEquals("[0, 4096, 8192]", String.valueOf(task.result()));
			assertTrue(allFreed());
		}
	}

	// -- Managed memory, shared --

	/**
	 * A service-side cache of image cells, each loaded once into managed
	 * memory, so that any number of workers can view it in place.
	 */
	public static class CellCache {
		public final Map<Integer, NDArray> cells = new HashMap<>();
		public int loads;

		public synchronized NDArray cell(int index) {
			return cells.computeIfAbsent(index, i -> {
				loads++;
				NDArray cell = NDArray.managed(DType.FLOAT32, new Shape(C_ORDER, 32, 32));
				FloatBuffer buf = cell.buffer().asFloatBuffer();
				for (int j = 0; j < buf.capacity(); j++) buf.put(j, i); // E.g. load from disk.
				return cell;
			});
		}

		public synchronized void evict(int index) {
			cells.remove(index).close();
		}
	}

	/**
	 * Two workers, one in Python and one in Groovy, process the same cell of
	 * a large image, from a cache in the service.
	 * <p>
	 * The cache loads the cell into managed memory once, and sends it to each
	 * worker: both view the very same memory, with no copying. The cache may
	 * evict the cell while workers still use it; it is freed only once no
	 * worker uses it anymore.
	 * </p>
	 */
	@Test
	public void testShareCellCache() throws Exception {
		CellCache cache = new CellCache();
		Map<String, Object> inputs = Collections.singletonMap("cache", cache);
		try (Service python = python(); Service groovy = groovy()) {
			// Each worker keeps using the cell after its first task.
			python.task("cell = cache.cell(5)\ntask.export(cell=cell)", inputs).waitFor();
			groovy.task("task.export(cell: cache.cell(5))", inputs).waitFor();
			assertEquals(1, cache.loads);

			// Both workers view the same memory: one sees what the other writes.
			python.task("cell[0, 0] = 42").waitFor();
			Object seen = groovy.task("cell.buffer().asFloatBuffer().get(0)").waitFor().result();
			assertEquals(42, ((Number) seen).intValue());

			// The cache evicts the cell, but the workers still use it.
			cache.evict(5);
			Thread.sleep(200);
			System.gc();
			assertEquals(1, regionCount());

			// Once both workers are done with it, the cell is freed.
			python.task("task.export(cell=None)").waitFor();
			groovy.task("task.export(cell: [])").waitFor();
			assertTrue(allFreed(groovy));
		}
	}

	/**
	 * A worker segments an image into a label image owned by the service.
	 * <p>
	 * The service sends the worker a managed label image, which the worker
	 * writes into in place; the service sees the labels, with no copying.
	 * </p>
	 */
	@Test
	public void testShareOutputImage() throws Exception {
		try (NDArray labels = NDArray.managed(DType.UINT16, new Shape(C_ORDER, 8, 8));
			Service worker = python())
		{
			assertComplete(worker.task("labels[:4] = 1\nlabels[4:] = 2",
				Collections.singletonMap("labels", labels)).waitFor());
			ShortBuffer view = labels.buffer().asShortBuffer();
			for (int i = 0; i < 64; i++) assertEquals(i < 32 ? 1 : 2, view.get(i));
		}
		assertTrue(allFreed());
	}

	/**
	 * The service passes an array from one worker (in Python) on to another
	 * (in Groovy).
	 * <p>
	 * The array lives in managed memory, so it is passed on by reference,
	 * with no copying: the second worker writes into the very array the
	 * service holds. It stays allocated as long as either of them uses it.
	 * </p>
	 */
	@Test
	public void testShareRelay() throws Exception {
		try (Service source = python(); Service consumer = groovy()) {
			NDArray data = (NDArray) source.task("numpy.full(5, 3, dtype='int32')").waitFor().result();

			assertComplete(consumer.task(
				"def ints = data.buffer().asIntBuffer()\n" +
				"(0..<5).each { ints.put(it, ints.get(it) + 1) }\n" +
				"task.export(kept: data)",
				Collections.singletonMap("data", data)).waitFor());
			IntBuffer ints = data.buffer().asIntBuffer();
			for (int i = 0; i < 5; i++) assertEquals(4, ints.get(i)); // The consumer's write.

			// The service drops the array; the consumer still uses it.
			data = null;
			ints = null;
			System.gc();
			Thread.sleep(200);
			assertEquals(1, regionCount());

			// Once the consumer is done with it, it is freed.
			consumer.task("task.export(kept: [])").waitFor();
			assertTrue(allFreed(consumer));
		}
	}

	// -- Unmanaged memory --

	/**
	 * The service reuses one buffer across many calls into a worker.
	 * <p>
	 * An {@link NDArray} created with a plain constructor is unmanaged:
	 * shared in place, it lives as long as the application wants, here until
	 * it closes the buffer.
	 * </p>
	 */
	@Test
	public void testUnmanagedBuffer() throws Exception {
		String name;
		try (Service worker = python();
			NDArray buffer = new NDArray(DType.INT32, new Shape(C_ORDER, 16)))
		{
			for (int i = 0; i < 3; i++) {
				Map<String, Object> inputs = new HashMap<>();
				inputs.put("buf", buffer);
				inputs.put("i", i);
				assertComplete(worker.task("numpy.asarray(buf)[:] = i", inputs).waitFor());
				IntBuffer ints = buffer.buffer().asIntBuffer();
				for (int j = 0; j < 16; j++) assertEquals(i, ints.get(j));
			}
			name = buffer.shm().name();
		}
		assertFalse(shmExists(name));
	}

	/**
	 * A worker streams frames to the service through a ring buffer it owns.
	 * <p>
	 * The worker writes each frame into the next slot of one block it
	 * created, and sends the service a view of that slot. Reuse of the slots
	 * is a matter of timing, and the block lives until the worker frees it.
	 * </p>
	 */
	@Test
	public void testUnmanagedRingBuffer() throws Exception {
		String grab =
			"import org.apposed.appose.NDArray\n" +
			"import org.apposed.appose.NDArray.DType\n" +
			"import org.apposed.appose.NDArray.Shape\n" +
			"def slot = frame % 4\n" +
			"def view = new NDArray(DType.UINT8, new Shape(Shape.Order.C_ORDER, 4, 4), ring.view(slot * 16, 16))\n" +
			"def buf = view.buffer()\n" +
			"(0..<16).each { buf.put(it, (byte) frame) } // E.g. copy in a camera frame.\n" +
			"view\n";
		String name = null;
		try (Service worker = groovy()) {
			worker.task("task.export(ring: org.apposed.appose.SharedMemory.create(4 * 16))").waitFor();
			for (int frame = 0; frame < 6; frame++) {
				NDArray view = (NDArray) worker.task(grab, Collections.singletonMap("frame", frame)).waitFor().result();
				assertFalse(((SharedMemoryView) view.shm()).managed());
				for (int i = 0; i < 16; i++) assertEquals(frame, view.buffer().get(i));
				name = view.shm().name();
			}
			worker.task("ring.close()").waitFor();
		}
		assertFalse(shmExists(name));
		assertEquals(0, regionCount());
	}
}
