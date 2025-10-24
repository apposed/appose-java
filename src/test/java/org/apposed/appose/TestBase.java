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

import org.apposed.appose.Service.ResponseType;
import org.apposed.appose.Service.Task;
import org.apposed.appose.Service.TaskStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Scripts and helper functions for Appose unit testing. */
public abstract class TestBase {

	public static final String COLLATZ_GROOVY =
		"// Computes the stopping time of a given value\n" +
		"// according to the Collatz conjecture sequence.\n" +
		"time = 0\n" +
		"BigInteger v = 9999\n" +
		"while (v != 1) {\n" +
		"  v = v%2==0 ? v/2 : 3*v+1\n" +
		"  task.update(\"[${time}] -> ${v}\", time, null)\n" +
		"  time++\n" +
		"}\n" +
		"return time\n";

	public static final String COLLATZ_PYTHON =
		"# Computes the stopping time of a given value\n" +
		"# according to the Collatz conjecture sequence.\n" +
		"time = 0\n" +
		"v = 9999\n" +
		"while v != 1:\n" +
		"    v = v//2 if v%2==0 else 3*v+1\n" +
		"    task.update(f\"[{time}] -> {v}\", current=time)\n" +
		"    time += 1\n" +
		"task.outputs[\"result\"] = time\n";

	public static final String CALC_SQRT_GROOVY =
		"sqrt_age = age -> {\n" +
		"  return Math.sqrt(age)\n" +
		"}\n" +
		"task.export(sqrt_age: sqrt_age)\n" +
		"return sqrt_age(age)\n";

	public static final String THREAD_CHECK_GROOVY =
		"task.outputs[\"thread\"] = Thread.currentThread().getName()\n";

	public static final String THREAD_CHECK_PYTHON =
		"import threading\n" +
		"task.outputs[\"thread\"] = threading.current_thread().name\n";

	public void executeAndAssert(Service service, String script)
		throws IOException, InterruptedException
	{
		Task task = service.task(script);

		// Record the state of the task for each event that occurs.
		class TaskState {
			final ResponseType responseType;
			final String message;
			final Long current;
			final Long maximum;
			final TaskStatus status;
			final String error;
			TaskState(TaskEvent event) {
				responseType = event.responseType;
				message = event.message;
				current = event.current;
				maximum = event.maximum;
				status = event.task.status;
				error = event.task.error;
			}
		}
		List<TaskState> events = new ArrayList<>();
		task.listen(event -> events.add(new TaskState(event)));

		// Wait for task to finish.
		task.waitFor();

		// Validate the execution result.
		assertComplete(task);
		Number result = (Number) task.outputs.get("result");
		assertEquals(91, result.intValue());

		// Validate the events received.
		assertEquals(93, events.size());
		TaskState launch = events.get(0);
		assertSame(ResponseType.LAUNCH, launch.responseType);
		assertSame(TaskStatus.RUNNING, launch.status);
		assertNull(launch.error);
		int v = 9999;
		for (int i=0; i<91; i++) {
			v = v%2==0 ? v/2 : 3*v+1;
			TaskState update = events.get(i + 1);
			assertSame(ResponseType.UPDATE, update.responseType);
			assertSame(TaskStatus.RUNNING, update.status);
			assertEquals("[" + i + "] -> " + v, update.message);
			assertEquals(i, update.current);
			assertEquals(0, update.maximum);
			assertNull(update.error);
		}
		TaskState completion = events.get(92);
		assertSame(ResponseType.COMPLETION, completion.responseType);
		assertNull(completion.message); // no message from non-UPDATE response
		assertEquals(0, completion.current); // no current from non-UPDATE response
		assertEquals(0, completion.maximum); // no maximum from non-UPDATE response
		assertNull(completion.error);
	}

	public void cowsayAndAssert(Environment env, String greeting)
		throws IOException, InterruptedException
	{
		try (Service service = env.python()) {
			maybeDebug(service);
			Task task = service.task(
				"import cowsay\n" +
				"task.outputs['result'] = cowsay.get_output_string('cow', '" + greeting + "')\n"
			);
			task.waitFor();
			assertComplete(task);
			// Verify cowsay output contains the greeting and key elements
			// (exact spacing can vary between cowsay versions).
			String actual = (String) task.outputs.get("result");
			assertNotNull(actual, "Cowsay output should not be null");
			assertTrue(actual.contains(greeting), "Output should contain the greeting: " + greeting);
			assertTrue(actual.contains("^__^"), "Output should contain cow face");
			assertTrue(actual.contains("(oo)"), "Output should contain cow eyes");
			assertTrue(actual.contains("||----w |"), "Output should contain cow legs");
		}
	}

	public void maybeDebug(Service service) {
		String debug1 = System.getenv("DEBUG");
		String debug2 = System.getProperty("appose.debug");
		if (falsy(debug1) && falsy(debug2)) return;
		service.debug(System.err::println);
	}

	public boolean falsy(String value) {
		if (value == null) return true;
		String tValue = value.trim();
		return tValue.isEmpty() ||
			tValue.equalsIgnoreCase("false") ||
			tValue.equals("0");
	}

	public void assertComplete(Task task) {
		String errorMessage = "";
		if (task.status != TaskStatus.COMPLETE) {
			String caller = new RuntimeException().getStackTrace()[1].getMethodName();
			errorMessage = "TASK ERROR in method " + caller + ":\n" + task.error;
		}
		assertEquals(TaskStatus.COMPLETE, task.status, errorMessage);
	}
}
