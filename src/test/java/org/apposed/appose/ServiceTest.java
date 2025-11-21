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
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Tests creation of {@link Service}s and execution of {@link Task}s. */
public class ServiceTest extends TestBase {

	@Test
	public void testGroovy() throws Exception {
		Environment env = Appose.system();
		try (Service service = env.groovy()) {
			maybeDebug(service);
			executeAndAssert(service, COLLATZ_GROOVY);
		}
	}

	@Test
	public void testPython() throws Exception {
		Environment env = Appose.system();
		try (Service service = env.python()) {
			maybeDebug(service);
			executeAndAssert(service, COLLATZ_PYTHON);
		}
	}

	@Test
	public void testScopeGroovy() throws Exception {
		Environment env = Appose.system();
		try (Service service = env.groovy()) {
			maybeDebug(service);

			Map<String, Object> inputs = new HashMap<>();
			inputs.put("age", 100);
			Task task = service.task(CALC_SQRT_GROOVY, inputs).waitFor();
			assertComplete(task);
			Number result = (Number) task.result();
			assertEquals(10, result.intValue());

			inputs.put("age", 81);
			task = service.task("sqrt_age(age)", inputs).waitFor();
			assertComplete(task);
			result = (Number) task.result();
			assertEquals(9, result.intValue());
		}
	}

	@Test
	public void testServiceStartupFailure() throws Exception {
		// Create an environment with no binPaths to test startup failure.
		File tempDir = new File("target", "no-pythons-to-be-found-here");
		tempDir.mkdirs();
		tempDir.deleteOnExit();

		Environment env = Appose.custom().base(tempDir).build();
		try (Service service = env.python()) {
			String info = "";
			try {
				Task task = service.task(
					"import sys\n" +
						"task.outputs['executable'] = sys.executable\n" +
						"task.outputs['version'] = sys.version"
				).waitFor();
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
	public void testTaskFailurePython() throws Exception {
		Environment env = Appose.system();
		try (Service service = env.python()) {
			maybeDebug(service);
			String script = "whee\n";
			try {
				service.task(script).waitFor();
				fail("Expected TaskException for failed script");
			}
			catch (TaskException e) {
				String expectedError = "NameError: name 'whee' is not defined";
				assertTrue(e.getMessage().contains(expectedError));
			}
		}
	}

	@Test
	public void testStartupCrash() throws Exception {
		Environment env = Appose.system();
		List<String> pythonExes = Arrays.asList("python", "python3", "python.exe");
		@SuppressWarnings("resource")
		Service service = env.service(pythonExes, "-c", "import nonexistentpackage").start();
		// Wait up to 500ms for the crash.
		for (int i = 0; i < 100; i++) {
			if (!service.isAlive()) break;
			Thread.sleep(5);
		}
		assertFalse(service.isAlive());
		// Check that the crash happened and was recorded correctly.
		List<String> errorLines = service.errorLines();
		assertNotNull(errorLines);
		assertFalse(errorLines.isEmpty());
		String error = errorLines.get(errorLines.size() - 1);
		assertEquals("ModuleNotFoundError: No module named 'nonexistentpackage'", error);
	}

	@Test
	public void testPythonSysExit() throws Exception {
		Environment env = Appose.system();
		try (Service service = env.python()) {
			maybeDebug(service);

			// Launch a task that calls sys.exit. This is a nasty thing to do
			// because Python does not exit the worker process when sys.exit is
			// called within a dedicated threading.Thread; the thread just dies.
			// So in addition to testing the Java code here, we are also testing
			// that Appose's python_worker handles this situation well.
			try {
				service.task("import sys\nsys.exit(123)").waitFor();
				fail("Expected TaskException for sys.exit");
			}
			catch (TaskException e) {
				// The failure should be either "thread death" or a "SystemExit" message.
				assertTrue(
					e.getMessage().contains("thread death") ||
					e.getMessage().contains("SystemExit: 123")
				);
			}
		}
	}

	@Test
	public void testCrashWithActiveTask() throws Exception {
		Environment env = Appose.system();
		try (Service service = env.python()) {
			maybeDebug(service);
			// Create a "long-running" task.
			String script =
				"import sys\n" +
				"sys.stderr.write('one\\n')\n" +
				"sys.stderr.flush()\n" +
				"print('two')\n" +
				"sys.stdout.flush()\n" +
				"sys.stderr.write('three\\n')\n" +
				"sys.stderr.flush()\n" +
				"task.update('halfway')\n" +
				"print('four')\n" +
				"sys.stdout.flush()\n" +
				"sys.stderr.write('five\\n')\n" +
				"sys.stderr.flush()\n" +
				"print('six')\n" +
				"sys.stdout.flush()\n" +
				"sys.stderr.write('seven\\n')\n" +
				"task.update(\"crash-me\")\n" +
				"import time; time.sleep(999)\n";
			boolean[] ready = {false};
			Task task = service.task(script).listen(e -> {
				if ("crash-me".equals(e.message)) ready[0] = true;
			});

			// Record any crash reported in the task notifications.
			String[] reportedError = {null};
			task.listen(event -> {
				if (event.responseType == ResponseType.CRASH) {
					reportedError[0] = task.error;
				}
			});
			// Launch the task.
			task.start();

			// Simulate a crash after the script has emitted its output.
			while (!ready[0]) Thread.sleep(5);
			service.kill();

			// Wait for the service to fully shut down after the crash.
			int exitCode = service.waitFor();
			assertTrue(exitCode != 0);

			// Is the tag flagged as crashed?
			assertSame(TaskStatus.CRASHED, task.status);

			// Was the crash error successfully and consistently recorded?
			assertNotNull(reportedError[0]);
			String nl = System.lineSeparator();
			assertEquals(Arrays.asList("two", "four", "six"), service.invalidLines());
			assertEquals(Arrays.asList("one", "three", "five", "seven"), service.errorLines());
			String expected =
				"Worker crashed with exit code ###." + nl +
				nl +
				"[stdout]" + nl +
				"two" + nl +
				"four" + nl +
				"six" + nl +
				nl +
				"[stderr]" + nl +
				"one" + nl +
				"three" + nl +
				"five" + nl +
				"seven" + nl;
			String generalizedError = task.error.replaceFirst("exit code [0-9]+", "exit code ###");
			assertEquals(expected, generalizedError);
		}
	}

	@Test
	public void testMainThreadQueueGroovy() throws Exception {
		Environment env = Appose.system();
		try (Service service = env.groovy()) {
			Task task = service.task(THREAD_CHECK_GROOVY, "main").waitFor();
			String thread = (String) task.outputs.get("thread");
			assertEquals("main", thread);

			task = service.task(THREAD_CHECK_GROOVY).waitFor();
			thread = (String) task.outputs.get("thread");
			assertNotEquals("main", thread);
		}
	}

	@Test
	public void testMainThreadQueuePython() throws Exception {
		Environment env = Appose.system();
		try (Service service = env.python()) {
			Task task = service.task(THREAD_CHECK_PYTHON, "main").waitFor();
			String thread = (String) task.outputs.get("thread");
			assertEquals("MainThread", thread);

			task = service.task(THREAD_CHECK_PYTHON).waitFor();
			thread = (String) task.outputs.get("thread");
			assertNotEquals("MainThread", thread);
		}
	}

	/** Tests that init script is executed before tasks run. */
	@Test
	public void testInit() throws Exception {
		Environment env = Appose.system();
		try (Service service = env.groovy().init("init_value = 'initialized'")) {
			maybeDebug(service);

			// Verify that the init script was executed and the variable is accessible.
			Task task = service.task("init_value").waitFor();
			assertComplete(task);

			String result = (String) task.result();
			assertEquals("initialized", result,
				"Init script should set init_value variable");
		}
	}

	/** Tests {@link Task#result()} convenience method. */
	@Test
	public void testTaskResult() throws Exception {
		Environment env = Appose.system();
		try (Service service = env.python()) {
			maybeDebug(service);

			// Create a task that produces a result.
			Task task = service.task("'success'").waitFor();
			assertComplete(task);

			// Test the result() convenience method.
			Object result = task.result();
			assertEquals("success", result);

			// Verify it's the same as directly accessing outputs.
			assertEquals(task.outputs.get("result"), result);
		}
	}

	/** Tests {@link Task#result()} returns null when no result is set. */
	@Test
	public void testTaskResultNull() throws Exception {
		Environment env = Appose.system();
		try (Service service = env.groovy()) {
			maybeDebug(service);

			// Create a task that doesn't set a result.
			Task task = service.task("println 'no result'").waitFor();
			assertComplete(task);

			// result() should return null.
			assertNull(task.result());
		}
	}

	/** Tests that NumPy works on every platform, even Windows. */
	@Test
	public void testInitNumpy() throws Exception {
		Environment env = Appose.pixi()
			.base("target/envs/test-init-numpy")
			.conda("numpy=2.3.4")
			.pypi("appose==0.7.2")
			.logDebug()
			.build();
		try (Service service = env.python().init("import numpy")) {
			maybeDebug(service);

			Task task = service.task(
				"narr = numpy.random.default_rng(seed=1337).random([3, 5])\n" +
				"[float(v) for v in narr.flatten()]"
			).waitFor();
			assertComplete(task);

			Object result = task.outputs.get("result");
			assertInstanceOf(List.class, result);
			List<?> actual = (List<?>) result;
			double[] expected = {
				0.8781019003, 0.1855279616, 0.9209004548, 0.9465658637, 0.8745080903,
				0.1157427629, 0.1937316623, 0.3417371975, 0.4957909002, 0.8983712328,
				0.0064586191, 0.2274114670, 0.7936549524, 0.4142867178, 0.0838144031
			};
			for (int i = 0; i < expected.length; i++) {
				Object element = actual.get(i);
				assertInstanceOf(Number.class, element);
				double value = ((Number) element).doubleValue();
				assertEquals(expected[i], value, 1e-10);
			}
		}
	}
}
