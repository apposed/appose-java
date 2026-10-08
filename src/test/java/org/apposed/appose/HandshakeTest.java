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
import org.apposed.appose.Service.TaskStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Tests the HELLO handshake, and worker robustness to bad requests. */
public class HandshakeTest extends TestBase {

	/**
	 * A fake worker, which completes every task immediately with result 42.
	 * It first sends the HELLO message given as its first argument, if any.
	 */
	private static final String FAKE_WORKER =
		"import json, sys\n" +
		"hello = %s\n" +
		"if hello is not None:\n" +
		"    print(json.dumps(hello), flush=True)\n" +
		"for line in sys.stdin:\n" +
		"    request = json.loads(line)\n" +
		"    if request.get('requestType') != 'EXECUTE':\n" +
		"        continue\n" +
		"    uuid = request['task']\n" +
		"    print(json.dumps({'task': uuid, 'responseType': 'LAUNCH'}), flush=True)\n" +
		"    print(json.dumps({'task': uuid, 'responseType': 'COMPLETION',\n" +
		"        'outputs': {'result': 42}}), flush=True)\n";

	private static Service fakeWorker(String version) throws Exception {
		String hello = version == null ? "None" :
			"{'responseType': 'HELLO', 'implementation': 'fake', 'version': '" + version + "'}";
		return pythonEnv().python("-c", String.format(FAKE_WORKER, hello));
	}

	@Test
	public void testHelloGroovy() throws Exception {
		try (Service service = Appose.system().groovy()) {
			maybeDebug(service);
			service.task("1").waitFor();
			Map<String, Object> info = service.workerInfo();
			assertNotNull(info);
			assertEquals("appose-java", info.get("implementation"));
			assertEquals(Appose.version(), info.get("version"));
		}
	}

	@Test
	public void testHelloPython() throws Exception {
		try (Service service = pythonEnv().python()) {
			maybeDebug(service);
			service.task("1").waitFor();
			Map<String, Object> info = service.workerInfo();
			assertNotNull(info);
			assertEquals("appose-python", info.get("implementation"));
			assertEquals(Service.minor(Appose.version()),
				Service.minor(info.get("version").toString()));
		}
	}

	@Test
	public void testCompatibleFakeWorker() throws Exception {
		try (Service service = fakeWorker(Appose.version())) {
			maybeDebug(service);
			assertEquals(42, service.task("x").waitFor().result());
		}
	}

	@Test
	public void testIncompatibleWorker() throws Exception {
		try (Service service = fakeWorker("0.0.1")) {
			maybeDebug(service);
			TaskException e = assertThrows(TaskException.class, () -> service.task("x").waitFor());
			assertTrue(e.task.error.contains("fake 0.0.1 is incompatible"), e.task.error);
			// Subsequent tasks fail fast, with the same reason.
			e = assertThrows(TaskException.class, () -> service.task("y").waitFor());
			assertTrue(e.task.error.contains("fake 0.0.1 is incompatible"), e.task.error);
		}
	}

	@Test
	public void testWorkerWithoutHello() throws Exception {
		try (Service service = fakeWorker(null)) {
			maybeDebug(service);
			TaskException e = assertThrows(TaskException.class, () -> service.task("x").waitFor());
			assertTrue(e.task.error.contains("Worker did not identify itself"), e.task.error);
		}
	}

	@Test
	public void testMinor() {
		assertEquals("1.1", Service.minor("1.1.2"));
		assertEquals("1.1", Service.minor("1.1.0.dev0"));
		assertEquals("0.12", Service.minor("0.12.1-SNAPSHOT"));
		assertNull(Service.minor("(unknown)"));
	}

	/** Tests that a bad request fails its task, and the worker carries on. */
	@Test
	public void testBadRequest() throws Exception {
		try (Service service = Appose.system().groovy()) {
			maybeDebug(service);
			service.start();
			Method send = Service.class.getDeclaredMethod("send", Map.class);
			send.setAccessible(true);

			// A request of unknown type, for a new task: the task fails.
			Task task = service.task("1");
			task.status = TaskStatus.QUEUED;
			Map<String, Object> request = new HashMap<>();
			request.put("task", task.uuid);
			request.put("requestType", "BOGUS");
			send.invoke(service, request);
			long deadline = System.currentTimeMillis() + 10_000;
			while (task.status == TaskStatus.QUEUED && System.currentTimeMillis() < deadline) {
				Thread.sleep(10);
			}
			assertEquals(TaskStatus.FAILED, task.status);
			assertTrue(task.error.contains("BOGUS"), task.error);

			// Either way, the worker is still alive and well.
			assertEquals(42, service.task("6 * 7").waitFor().result());
		}
	}
}
