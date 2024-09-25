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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apposed.appose.Service.ResponseType;
import org.apposed.appose.Service.Task;
import org.apposed.appose.Service.TaskStatus;
import org.junit.jupiter.api.Test;

public class ApposeTest {

	private static final String COLLATZ_GROOVY =
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

	private static final String COLLATZ_PYTHON =
		"# Computes the stopping time of a given value\n" +
		"# according to the Collatz conjecture sequence.\n" +
		"time = 0\n" +
		"v = 9999\n" +
		"while v != 1:\n" +
		"    v = v//2 if v%2==0 else 3*v+1\n" +
		"    task.update(f\"[{time}] -> {v}\", current=time)\n" +
		"    time += 1\n" +
		"task.outputs[\"result\"] = time\n";

	@Test
	public void testGroovy() throws IOException, InterruptedException {
		Environment env = Appose.system();
		try (Service service = env.groovy()) {
			maybeDebug(service);
			executeAndAssert(service, COLLATZ_GROOVY);
		}
	}

	@Test
	public void testPython() throws IOException, InterruptedException {
		Environment env = Appose.system();
		try (Service service = env.python()) {
			maybeDebug(service);
			executeAndAssert(service, COLLATZ_PYTHON);
		}
	}

	@Test
	public void testConda() throws IOException, InterruptedException {
		Environment env = Appose
			.conda(new File("src/test/resources/envs/cowsay.yml"))
			.logDebug()
			.build();
		try (Service service = env.python()) {
			maybeDebug(service);
			Task task = service.task(
				"import cowsay\n" +
				"task.outputs['moo'] = cowsay.get_output_string('cow', 'moo')\n"
			);
			task.waitFor();
			assertComplete(task);
			String expectedMoo =
				"  ___\n" +
				"| moo |\n" +
				"  ===\n" +
				"   \\\n" +
				"    \\\n" +
				"      ^__^\n" +
				"      (oo)\\_______\n" +
				"      (__)\\       )\\/\\\n" +
				"          ||----w |\n" +
				"          ||     ||";
			String actualMoo = (String) task.outputs.get("moo");
			assertEquals(expectedMoo, actualMoo);
		}
	}

	@Test
	public void testServiceStartupFailure() throws IOException, InterruptedException {
		String tempNonExistingDir = "no-pythons-to-be-found-here";
		new File(tempNonExistingDir).deleteOnExit();
		Environment env = Appose.build(tempNonExistingDir);
		try (Service service = env.python()) {
			String info = "";
			try {
				Task task = service.task(
					"import sys\n" +
						"task.outputs['executable'] = sys.executable\n" +
						"task.outputs['version'] = sys.version"
				);
				task.waitFor();
				info += "\n- sys.executable = " + task.outputs.get("executable");
				info += "\n- sys.version = " + task.outputs.get("version");
			}
			finally {
				fail("Python worker process started successfully!?" + info);
			}
		}
		catch (IllegalArgumentException exc) {
			assertEquals(
				"No executables found amongst candidates: " +
				"[python, python3, python.exe]",
				exc.getMessage()
			);
		}
	}

	@Test
	public void testTaskFailurePython() throws InterruptedException, IOException {
		Environment env = Appose.system();
		try (Service service = env.python()) {
			service.debug(System.out::println);
			String script = "whee\n";
			Task task = service.task(script);
			task.waitFor();
			assertSame(TaskStatus.FAILED, task.status);
			String nl = "(\r\n|\n|\r)";
			String expectedError =
				"Traceback \\(most recent call last\\):" + nl +
				"  File \"[^ ]*python_worker.py\", line \\d+, in execute_script" + nl +
				"    result = eval\\(" + nl +
				"  File \"<string>\", line 1, in <module>" + nl +
				"NameError: name 'whee' is not defined" + nl;
			assertTrue(task.error.matches(expectedError));
		}
	}

	public void executeAndAssert(Service service, String script)
		throws IOException, InterruptedException
	{
		Task task = service.task(script);

		// Record the state of the task for each event that occurs.
		class TaskState {
			final ResponseType responseType;
			final TaskStatus status;
			final String message;
			final Long current;
			final Long maximum;
			final String error;
			TaskState(TaskEvent event) {
				responseType = event.responseType;
				status = event.task.status;
				message = event.task.message;
				current = event.task.current;
				maximum = event.task.maximum;
				error = event.task.error;
			}
		}
		List<TaskState> events = new ArrayList<>();
		task.listen(event -> events.add(new TaskState(event)));

		// Wait for task to finish.
		task.waitFor();
		assertComplete(task);

		// Validate the execution result.
		assertComplete(task);
		Number result = (Number) task.outputs.get("result");
		assertEquals(91, result.intValue());

		// Validate the events received.
		assertEquals(93, events.size());
		TaskState launch = events.get(0);
		assertSame(ResponseType.LAUNCH, launch.responseType);
		assertSame(TaskStatus.RUNNING, launch.status);
		assertNull(launch.message);
		assertEquals(0, launch.current);
		assertEquals(1, launch.maximum);
		assertNull(launch.error);
		int v = 9999;
		for (int i=0; i<91; i++) {
			v = v%2==0 ? v/2 : 3*v+1;
			TaskState update = events.get(i + 1);
			assertSame(ResponseType.UPDATE, update.responseType);
			assertSame(TaskStatus.RUNNING, update.status);
			assertEquals("[" + i + "] -> " + v, update.message);
			assertEquals(i, update.current);
			assertEquals(1, update.maximum);
			assertNull(update.error);
		}
		TaskState completion = events.get(92);
		assertSame(ResponseType.COMPLETION, completion.responseType);
		assertEquals("[90] -> 1", completion.message);
		assertEquals(90, completion.current);
		assertEquals(1, completion.maximum);
		assertNull(completion.error);
	}

	private void maybeDebug(Service service) {
		String debug1 = System.getenv("DEBUG");
		String debug2 = System.getProperty("appose.debug");
		if (falsy(debug1) && falsy(debug2)) return;
		service.debug(System.err::println);
	}

	private boolean falsy(String value) {
		if (value == null) return true;
		String tValue = value.trim();
		return tValue.isEmpty() ||
			tValue.equalsIgnoreCase("false") ||
			tValue.equals("0");
	}

	private void assertComplete(Task task) {
		String errorMessage = "";
		if (task.status != TaskStatus.COMPLETE) {
			String caller = new RuntimeException().getStackTrace()[1].getMethodName();
			errorMessage = "TASK ERROR in method " + caller + ":\n" + task.error;
			System.err.println();
		}
		assertEquals(TaskStatus.COMPLETE, task.status, errorMessage);
	}
}
