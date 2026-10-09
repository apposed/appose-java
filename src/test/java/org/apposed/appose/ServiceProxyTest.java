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

import org.apposed.appose.Service.Task;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests worker-side proxies of service objects (worker → service calls). */
public class ServiceProxyTest extends TestBase {

	public static class Counter {
		public int count = 0;
		public String label = "clicks";

		public int increment() {
			return increment(1);
		}

		public int increment(int amount) {
			count += amount;
			return count;
		}

		public void fail() {
			throw new IllegalStateException("Counter malfunction");
		}
	}

	public static class Weather {
		public Forecast forecast() {
			return new Forecast("sunny", 23);
		}
	}

	public static class Forecast {
		public final String sky;
		public final int temp;

		public Forecast(String sky, int temp) {
			this.sky = sky;
			this.temp = temp;
		}
	}

	public static class Doubler {
		public int apply(WorkerObject workerObj) throws Exception {
			// workerObj is a (forward) proxy to an object in the worker,
			// which is blocked waiting for this very call to return.
			return ((Number) workerObj.getAttribute("value")).intValue() * 2;
		}
	}

	public static class Source {
		public int reads = 0;

		public void read(int index, NDArray buffer) {
			IntBuffer ints = buffer.buffer().order(ByteOrder.nativeOrder()).asIntBuffer();
			for (int i = 0; i < ints.limit(); i++) ints.put(i, index);
			reads++;
		}
	}

	/** Tests property access and method calls on a service object. */
	@Test
	public void testServiceProxyPython() throws Exception {
		try (Service service = pythonEnv().python()) {
			assertServiceProxy(service,
				"counter.increment()\n" +
				"counter.increment(5)\n" +
				"task.outputs['label'] = counter.label\n" +
				"task.outputs['count'] = counter.count\n");
		}
	}

	/** Tests property access and method calls on a service object. */
	@Test
	public void testServiceProxyGroovy() throws Exception {
		try (Service service = Appose.system().groovy()) {
			assertServiceProxy(service,
				"counter.increment()\n" +
				"counter.increment(5)\n" +
				"task.outputs['label'] = counter.label\n" +
				"task.outputs['count'] = counter.count\n" +
				"return null\n");
		}
	}

	/** Tests that the worker can list a service object's attributes. */
	@Test
	public void testServiceProxyDirPython() throws Exception {
		try (Service service = pythonEnv().python()) {
			assertServiceProxyDir(service, "dir(counter)");
		}
	}

	/** Tests that the worker can list a service object's attributes. */
	@Test
	public void testServiceProxyDirGroovy() throws Exception {
		try (Service service = Appose.system().groovy()) {
			assertServiceProxyDir(service, "counter.dir()");
		}
	}

	/** Tests that non-serializable results come back as further proxies. */
	@Test
	public void testServiceProxyChainingPython() throws Exception {
		try (Service service = pythonEnv().python()) {
			assertServiceProxyChaining(service,
				"forecast = weather.forecast()\n" +
				"f'{forecast.sky}, {forecast.temp} degrees'\n");
		}
	}

	/** Tests that non-serializable results come back as further proxies. */
	@Test
	public void testServiceProxyChainingGroovy() throws Exception {
		try (Service service = Appose.system().groovy()) {
			assertServiceProxyChaining(service,
				"forecast = weather.forecast()\n" +
				"\"${forecast.sky}, ${forecast.temp} degrees\".toString()\n");
		}
	}

	/** Tests that a service proxy sent back to the service is the original object. */
	@Test
	public void testServiceProxyRoundTripPython() throws Exception {
		try (Service service = pythonEnv().python()) {
			assertServiceProxyRoundTrip(service);
		}
	}

	/** Tests that a service proxy sent back to the service is the original object. */
	@Test
	public void testServiceProxyRoundTripGroovy() throws Exception {
		try (Service service = Appose.system().groovy()) {
			assertServiceProxyRoundTrip(service);
		}
	}

	/** Tests that service-side exceptions propagate to the worker. */
	@Test
	public void testServiceProxyErrorPython() throws Exception {
		try (Service service = pythonEnv().python()) {
			assertServiceProxyError(service);
		}
	}

	/** Tests that service-side exceptions propagate to the worker. */
	@Test
	public void testServiceProxyErrorGroovy() throws Exception {
		try (Service service = Appose.system().groovy()) {
			assertServiceProxyError(service);
		}
	}

	/** Tests a service object calling back into a worker object mid-call. */
	@Test
	public void testServiceProxyReentrantPython() throws Exception {
		try (Service service = pythonEnv().python()) {
			assertServiceProxyReentrant(service,
				"class Box:\n" +
				"    def __init__(self, value):\n" +
				"        self.value = value\n" +
				"\n" +
				"doubler.apply(Box(21))\n");
		}
	}

	/** Tests a service object calling back into a worker object mid-call. */
	@Test
	public void testServiceProxyReentrantGroovy() throws Exception {
		try (Service service = Appose.system().groovy()) {
			assertServiceProxyReentrant(service,
				"class Box {\n" +
				"  int value\n" +
				"}\n" +
				"doubler.apply(new Box(value: 21))\n");
		}
	}

	/** Tests a service object filling shared memory on behalf of the worker. */
	@Test
	public void testServiceProxyShmPython() throws Exception {
		Environment env = pythonEnv("ndarray-check-python", "numpy");
		try (Service service = env.python().init("import numpy")) {
			assertServiceProxyShm(service,
				"import numpy\n" +
				"total = 0\n" +
				"for i in range(10):\n" +
				"    source.read(i, buffer)\n" +
				"    total += int(numpy.asarray(buffer).sum())\n" +
				"total\n");
		}
	}

	/** Tests a service object filling shared memory on behalf of the worker. */
	@Test
	public void testServiceProxyShmGroovy() throws Exception {
		try (Service service = Appose.system().groovy()) {
			assertServiceProxyShm(service,
				"import java.nio.ByteOrder\n" +
				"long total = 0\n" +
				"for (int i = 0; i < 10; i++) {\n" +
				"  source.read(i, buffer)\n" +
				"  def ints = buffer.buffer().order(ByteOrder.nativeOrder()).asIntBuffer()\n" +
				"  for (int j = 0; j < ints.limit(); j++) total += ints.get(j)\n" +
				"}\n" +
				"total\n");
		}
	}

	// -- Helper methods --

	private void assertServiceProxy(Service service, String script) throws Exception {
		maybeDebug(service);
		Counter counter = new Counter();
		Task task = service.task(script, inputs("counter", counter)).waitFor();
		assertComplete(task);
		assertEquals("clicks", task.outputs.get("label"));
		assertEquals(6, ((Number) task.outputs.get("count")).intValue());
		// The calls mutated the actual service-side object.
		assertEquals(6, counter.count);
	}

	private void assertServiceProxyDir(Service service, String script) throws Exception {
		maybeDebug(service);
		Task task = service.task(script, inputs("counter", new Counter())).waitFor();
		assertComplete(task);
		@SuppressWarnings("unchecked")
		List<String> names = (List<String>) task.result();
		assertTrue(names.contains("increment"), "Missing increment: " + names);
		assertTrue(names.contains("label"), "Missing label: " + names);
	}

	private void assertServiceProxyChaining(Service service, String script) throws Exception {
		maybeDebug(service);
		Task task = service.task(script, inputs("weather", new Weather())).waitFor();
		assertComplete(task);
		assertEquals("sunny, 23 degrees", task.result());
	}

	private void assertServiceProxyRoundTrip(Service service) throws Exception {
		maybeDebug(service);
		Counter counter = new Counter();
		Task task = service.task("counter", inputs("counter", counter)).waitFor();
		assertComplete(task);
		assertSame(counter, task.result());
	}

	private void assertServiceProxyError(Service service) {
		maybeDebug(service);
		Task task = service.task("counter.fail()", inputs("counter", new Counter()));
		assertThrows(TaskException.class, task::waitFor);
		assertTrue(task.error.contains("Counter malfunction"), task.error);
	}

	private void assertServiceProxyReentrant(Service service, String script) throws Exception {
		maybeDebug(service);
		Task task = service.task(script, inputs("doubler", new Doubler())).waitFor();
		assertComplete(task);
		assertEquals(42, ((Number) task.result()).intValue());
	}

	private void assertServiceProxyShm(Service service, String script) throws Exception {
		maybeDebug(service);
		Source source = new Source();
		try (NDArray buffer = new NDArray(NDArray.DType.INT32, new NDArray.Shape(NDArray.Shape.Order.C_ORDER, 4, 4))) {
			Map<String, Object> inputs = new HashMap<>();
			inputs.put("source", source);
			inputs.put("buffer", buffer);
			Task task = service.task(script, inputs).waitFor();
			assertComplete(task);
			assertEquals(16 * 45, ((Number) task.result()).intValue());
			assertEquals(10, source.reads);
		}
	}

	private static Map<String, Object> inputs(String name, Object value) {
		return Collections.singletonMap(name, value);
	}
}
