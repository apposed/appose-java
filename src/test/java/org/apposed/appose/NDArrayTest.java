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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apposed.appose.NDArray.Shape.Order.C_ORDER;
import static org.apposed.appose.NDArray.Shape.Order.F_ORDER;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link NDArray} shapes from 2D through 5D, validating that the shape
 * is preserved correctly on both the Java (service) side and the Python /
 * Groovy (worker/subprocess) side.
 */
public class NDArrayTest extends TestBase {

	private static Service python;

	@BeforeAll
	public static void setUp() throws Exception {
		python = Appose.uv()
			.include("numpy")
			.base("target/envs/ndarray-check-python")
			.build()
			.python()
			.init("import numpy");
	}

	@AfterAll
	public static void tearDown() {
		if (python != null && python.isAlive()) python.close();
	}

	// 2-D through 5-D test cases, one method per (ndim, order) combination.

	@Test public void testPython2dFOrder() throws Exception { checkPython(F_ORDER, 3, 5); }
	@Test public void testPython2dCOrder() throws Exception { checkPython(C_ORDER, 3, 5); }

	@Test public void testPython3dFOrder() throws Exception { checkPython(F_ORDER, 4, 3, 2); }
	@Test public void testPython3dCOrder() throws Exception { checkPython(C_ORDER, 4, 3, 2); }

	@Test public void testPython4dFOrder() throws Exception { checkPython(F_ORDER, 2, 3, 4, 5); }
	@Test public void testPython4dCOrder() throws Exception { checkPython(C_ORDER, 2, 3, 4, 5); }

	@Test public void testPython5dFOrder() throws Exception { checkPython(F_ORDER, 2, 3, 2, 3, 2); }
	@Test public void testPython5dCOrder() throws Exception { checkPython(C_ORDER, 2, 3, 2, 3, 2); }

	@Test public void testGroovy2dFOrder() throws Exception { checkGroovy(F_ORDER, 3, 5); }
	@Test public void testGroovy2dCOrder() throws Exception { checkGroovy(C_ORDER, 3, 5); }

	@Test public void testGroovy3dFOrder() throws Exception { checkGroovy(F_ORDER, 4, 3, 2); }
	@Test public void testGroovy3dCOrder() throws Exception { checkGroovy(C_ORDER, 4, 3, 2); }

	@Test public void testGroovy4dFOrder() throws Exception { checkGroovy(F_ORDER, 2, 3, 4, 5); }
	@Test public void testGroovy4dCOrder() throws Exception { checkGroovy(C_ORDER, 2, 3, 4, 5); }

	@Test public void testGroovy5dFOrder() throws Exception { checkGroovy(F_ORDER, 2, 3, 2, 3, 2); }
	@Test public void testGroovy5dCOrder() throws Exception { checkGroovy(C_ORDER, 2, 3, 2, 3, 2); }

	// -----------------------------------------------------------------------
	// Helpers
	// -----------------------------------------------------------------------

	private void checkPython(NDArray.Shape.Order order, int... dims) throws Exception {
		NDArray.Shape shape = new NDArray.Shape(order, dims);
		try (NDArray ndArray = filledArray(shape)) {
			// Java-side validation
			assertShape(shape, ndArray);

			// Python-side validation: the script returns the numpy shape and order.
			Map<String, Object> inputs = new HashMap<>();
			inputs.put("arr", ndArray);
			Service.Task task = python.task(PYTHON_SHAPE_SCRIPT, inputs);
			task.waitFor();
			assertComplete(task);

			@SuppressWarnings("unchecked")
			List<Number> workerShape = (List<Number>) task.outputs.get("shape");
			// Wire format normalizes to C_ORDER, so numpy always receives a C_ORDER array.
			int[] expectedCShape = shape.toIntArray(C_ORDER);
			int[] actualCShape = workerShape.stream().mapToInt(Number::intValue).toArray();
			assertArrayEquals(expectedCShape, actualCShape,
				"Python shape mismatch for order=" + order + " dims=" + Arrays.toString(dims));

			// Regardless of the original order, the worker always receives C_ORDER.
			assertEquals("C", task.outputs.get("order"),
				"Python order mismatch for order=" + order + " dims=" + Arrays.toString(dims));
		}
	}

	private void checkGroovy(NDArray.Shape.Order order, int... dims) throws Exception {
		NDArray.Shape shape = new NDArray.Shape(order, dims);
		try (NDArray ndArray = filledArray(shape)) {
			// Java-side validation
			assertShape(shape, ndArray);

			// Groovy-side validation: the script returns shape dims and order name.
			// Note: the Appose wire format always normalizes shape to C_ORDER
			// (see Messages.java), so the worker always receives a C_ORDER array.
			Environment env = Appose.system();
			try (Service service = env.groovy()) {
				maybeDebug(service);
				Map<String, Object> inputs = new HashMap<>();
				inputs.put("arr", ndArray);
				Service.Task task = service.task(GROOVY_SHAPE_SCRIPT, inputs);
				task.waitFor();
				assertComplete(task);

				@SuppressWarnings("unchecked")
				List<Number> workerShape = (List<Number>) task.outputs.get("shape");
				// Wire format normalizes to C_ORDER, so worker always sees C_ORDER dims.
				int[] expectedCDims = shape.toIntArray(C_ORDER);
				int[] actualDims = workerShape.stream().mapToInt(Number::intValue).toArray();
				assertArrayEquals(expectedCDims, actualDims,
					"Groovy shape mismatch for order=" + order + " dims=" + Arrays.toString(dims));

				assertEquals(C_ORDER.name(), task.outputs.get("order"),
					"Groovy order mismatch for order=" + order + " dims=" + Arrays.toString(dims));
			}
		}
	}

	/** Creates a FLOAT32 NDArray with the given shape, filled 0, 1, 2, … */
	private static NDArray filledArray(NDArray.Shape shape) {
		NDArray ndArray = new NDArray(NDArray.DType.FLOAT32, shape);
		FloatBuffer buf = ndArray.buffer().asFloatBuffer();
		long len = shape.numElements();
		for (int i = 0; i < len; i++) buf.put(i, i);
		return ndArray;
	}

	/** Validates Java-side shape metadata. */
	private static void assertShape(NDArray.Shape expected, NDArray ndArray) {
		assertEquals(expected.length(), ndArray.shape().length(),
			"Java ndim mismatch");
		assertArrayEquals(expected.toIntArray(), ndArray.shape().toIntArray(),
			"Java dims mismatch");
		assertEquals(expected.order(), ndArray.shape().order(),
			"Java order mismatch");
		long minBytes = expected.numElements() * NDArray.DType.FLOAT32.bytesPerElement();
		assertTrue(ndArray.shm().size() >= minBytes,
			"Shared memory too small: " + ndArray.shm().size() + " < " + minBytes);
	}

	// -----------------------------------------------------------------------
	// Worker scripts
	// -----------------------------------------------------------------------

	/**
	 * Python script: receives {@code arr} (appose NDArray, accessible as a
	 * numpy ndarray via {@code arr.ndarray()}), returns its shape and memory order.
	 */
	private static final String PYTHON_SHAPE_SCRIPT =
		"na = arr.ndarray()\n" +
		"task.outputs['shape'] = list(na.shape)\n" +
		"task.outputs['order'] = 'F' if na.flags['F_CONTIGUOUS'] and not na.flags['C_CONTIGUOUS'] else 'C'\n";

	/**
	 * Groovy script: receives {@code arr} (appose NDArray), returns its shape
	 * dims (which always end up in C order) and order name.
	 */
	private static final String GROOVY_SHAPE_SCRIPT =
		"def s = arr.shape()\n" +
		"task.outputs['shape'] = s.toIntArray().toList()\n" +
		"task.outputs['order'] = s.order().name()\n";
}
