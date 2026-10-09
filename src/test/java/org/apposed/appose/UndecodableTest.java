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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that messages which cannot be decoded fail whatever awaits them,
 * rather than leaving it hanging, and do not break the connection.
 */
public class UndecodableTest extends TestBase {

	/** A shared memory reference that no Appose implementation can decode. */
	private static final String BOGUS_GROOVY = "[appose_type: 'shm', name: 'psm_bogus', rsize: 'bogus']";

	private static Map<String, Object> bogus() {
		Map<String, Object> bogus = new LinkedHashMap<>();
		bogus.put("appose_type", "shm");
		bogus.put("name", "psm_bogus");
		bogus.put("rsize", "bogus");
		return bogus;
	}

	public static class Source {
		public Object get() {
			return bogus();
		}
	}

	public static class Sink {
		public Object take(Object value) {
			return value;
		}
	}

	/** Runs the task, and waits for it to finish, without hanging forever. */
	private static Task finish(Task task) throws InterruptedException {
		task.start();
		long deadline = System.currentTimeMillis() + 10_000;
		while (!task.status.isFinished()) {
			assertTrue(System.currentTimeMillis() < deadline, "task never finished");
			Thread.sleep(20);
		}
		return task;
	}

	private static void assertAlive(Service service) throws InterruptedException {
		assertEquals(42, ((Number) finish(service.task("6 * 7")).result()).intValue());
	}

	private Service groovy() {
		Service service = Appose.system().groovy();
		maybeDebug(service);
		return service;
	}

	/** The worker fails a task whose request it cannot decode. */
	@Test
	public void testUndecodableTaskRequest() throws Exception {
		try (Service service = groovy()) {
			Task task = finish(service.task("x", Collections.singletonMap("x", bogus())));
			assertSame(TaskStatus.FAILED, task.status);
			assertTrue(task.error.contains("Worker could not decode the task request"), task.error);
			assertAlive(service);
		}
	}

	/** The worker fails a call into a service object whose reply it cannot decode. */
	@Test
	public void testUndecodableReply() throws Exception {
		try (Service service = groovy()) {
			Task task = finish(service.task("source.get()", Collections.singletonMap("source", new Source())));
			assertSame(TaskStatus.FAILED, task.status);
			assertTrue(task.error.contains("Worker could not decode the service's reply"), task.error);
			assertAlive(service);
		}
	}

	/** The service fails a task whose completion it cannot decode. */
	@Test
	public void testUndecodableCompletion() throws Exception {
		try (Service service = groovy()) {
			Task task = finish(service.task("task.outputs['x'] = " + BOGUS_GROOVY));
			assertSame(TaskStatus.FAILED, task.status);
			assertTrue(task.error.contains("Service could not decode the task's COMPLETION response"), task.error);
			assertAlive(service);
		}
	}

	/** The service fails a call from the worker which it cannot decode. */
	@Test
	public void testUndecodableCall() throws Exception {
		try (Service service = groovy()) {
			Task task = finish(service.task("sink.take(" + BOGUS_GROOVY + ")",
				Collections.singletonMap("sink", new Sink())));
			assertSame(TaskStatus.FAILED, task.status);
			assertTrue(task.error.contains("Service could not decode the call"), task.error);
			assertAlive(service);
		}
	}
}
