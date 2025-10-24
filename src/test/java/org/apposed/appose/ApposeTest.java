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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apposed.appose.Service.ResponseType;
import org.apposed.appose.Service.Task;
import org.apposed.appose.Service.TaskStatus;
import org.apposed.appose.builder.MambaBuilder;
import org.junit.jupiter.api.Test;

/**
 * High-level (integration) tests for Appose functionality including
 * cross-language.
 */
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

	private static final String CALC_SQRT_GROOVY =
		"sqrt_age = age -> {\n" +
		"  return Math.sqrt(age)\n" +
		"}\n" +
		"task.export(sqrt_age: sqrt_age)\n" +
		"return sqrt_age(age)\n";

	private static final String MAIN_THREAD_CHECK_GROOVY =
		"task.outputs[\"thread\"] = Thread.currentThread().getName()\n";

	private static final String MAIN_THREAD_CHECK_PYTHON =
		"import threading\n" +
		"task.outputs[\"thread\"] = threading.current_thread().name\n";

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
	public void testScopeGroovy() throws IOException, InterruptedException {
		Environment env = Appose.system();
		try (Service service = env.groovy()) {
			maybeDebug(service);

			Map<String, Object> inputs = new HashMap<>();
			inputs.put("age", 100);
			Task task = service.task(CALC_SQRT_GROOVY, inputs);
			task.waitFor();
			assertComplete(task);
			Number result = (Number) task.outputs.get("result");
			assertEquals(10, result.intValue());

			inputs.put("age", 81);
			task = service.task("task.outputs['result'] = sqrt_age(age)", inputs);
			task.waitFor();
			assertComplete(task);
			result = (Number) task.outputs.get("result");
			assertEquals(9, result.intValue());
		}
	}

	@Test
	public void testConda() throws IOException, InterruptedException {
		Environment env = Appose
			.file("src/test/resources/envs/cowsay.yml")
			.base("target/envs/conda-cowsay")
			.logDebug()
			.build();
		cowsayAndAssert(env, "moo");
	}

	@Test
	public void testPixi() throws IOException, InterruptedException {
		Environment env = Appose
			.pixi("src/test/resources/envs/cowsay-pixi.toml")
			.base("target/envs/pixi-cowsay")
			.logDebug()
			.build();
		cowsayAndAssert(env, "baa");
	}

	@Test
	public void testPixiBuilderAPI() throws IOException, InterruptedException {
		Environment env = Appose
			.pixi()
			.conda("python>=3.8", "pip")
			.pypi("cowsay==6.1")
			.base("target/envs/pixi-cowsay-builder")
			.logDebug()
			.build();
		cowsayAndAssert(env, "ooh");
	}

	@Test
	public void testPixiPyproject() throws IOException, InterruptedException {
		Environment env = Appose
			.pixi("src/test/resources/envs/cowsay-pixi-pyproject.toml")
			.base("target/envs/pixi-cowsay-pyproject")
			.logDebug()
			.build();
		cowsayAndAssert(env, "pixi-pyproject");
	}

	@Test
	public void testExplicitMambaBuilder() throws IOException, InterruptedException {
		// Test explicit mamba builder selection using .builder() method
		Environment env = Appose
			.file("src/test/resources/envs/cowsay.yml")
			.builder("mamba")
			.base("target/envs/mamba-cowsay")
			.logDebug()
			.build();

		// Verify it's actually using mamba by checking for conda-meta directory
		File envBase = new File(env.base());
		File condaMeta = new File(envBase, "conda-meta");
		assertTrue(condaMeta.exists() && condaMeta.isDirectory(),
			"Environment should have conda-meta directory when using mamba builder");

		cowsayAndAssert(env, "yay");
	}

	@Test
	public void testUv() throws IOException, InterruptedException {
		Environment env = Appose
			.uv("src/test/resources/envs/cowsay-requirements.txt")
			.base("target/envs/uv-cowsay")
			.logDebug()
			.build();
		cowsayAndAssert(env, "uv");
	}

	@Test
	public void testUvBuilderAPI() throws IOException, InterruptedException {
		Environment env = Appose
			.uv()
			.include("cowsay==6.1")
			.base("target/envs/uv-cowsay-builder")
			.logDebug()
			.build();
		cowsayAndAssert(env, "fast");
	}

	@Test
	public void testUvPyproject() throws IOException, InterruptedException {
		Environment env = Appose
			.uv("src/test/resources/envs/cowsay-pyproject.toml")
			.base("target/envs/uv-cowsay-pyproject")
			.logDebug()
			.build();
		cowsayAndAssert(env, "pyproject");
	}

	@Test
	public void testWrap() throws IOException {
		// Test wrapping a pixi environment
		File pixiDir = new File("target/test-wrap-pixi");
		pixiDir.mkdirs();
		File pixiToml = new File(pixiDir, "pixi.toml");
		pixiToml.createNewFile();

		try {
			Environment pixiEnv = Appose.wrap(pixiDir);
			assertNotNull(pixiEnv);
			assertEquals(pixiDir.getAbsolutePath(), pixiEnv.base());
			assertNotNull(pixiEnv.launchArgs());
			assertFalse(pixiEnv.launchArgs().isEmpty());
			assertTrue(pixiEnv.launchArgs().get(0).contains("pixi"),
				"Pixi environment should use pixi launcher");
		} finally {
			pixiToml.delete();
		}

		// Test wrapping a conda/mamba environment
		File condaDir = new File("target/test-wrap-conda");
		condaDir.mkdirs();
		File condaMeta = new File(condaDir, "conda-meta");
		condaMeta.mkdirs();

		try {
			Environment condaEnv = Appose.wrap(condaDir);
			assertNotNull(condaEnv);
			assertEquals(condaDir.getAbsolutePath(), condaEnv.base());
			assertNotNull(condaEnv.launchArgs());
			assertFalse(condaEnv.launchArgs().isEmpty());
			assertTrue(condaEnv.launchArgs().get(0).contains("micromamba"),
				"Conda environment should use micromamba launcher");
		} finally {
			condaMeta.delete();
		}

		// Test wrapping a UV/venv environment
		File uvDir = new File("target/test-wrap-uv");
		uvDir.mkdirs();
		File pyvenvCfg = new File(uvDir, "pyvenv.cfg");
		pyvenvCfg.createNewFile();

		try {
			Environment uvEnv = Appose.wrap(uvDir);
			assertNotNull(uvEnv);
			assertEquals(uvDir.getAbsolutePath(), uvEnv.base());
			// UV environments use standard venv structure with no special launch args
			assertTrue(uvEnv.launchArgs().isEmpty(),
				"UV environment should have no special launcher");
		} finally {
			pyvenvCfg.delete();
		}

		// Test wrapping a plain directory (should fall back to SystemBuilder)
		File systemDir = new File("target/test-wrap-system");
		systemDir.mkdirs();

		try {
			Environment systemEnv = Appose.wrap(systemDir);
			assertNotNull(systemEnv);
			assertEquals(systemDir.getAbsolutePath(), systemEnv.base());
			// SystemBuilder uses empty launch args by default
			assertTrue(systemEnv.launchArgs().isEmpty(),
				"System environment should have no special launcher");
		} finally {
			systemDir.delete();
			pixiDir.delete();
			condaDir.delete();
			uvDir.delete();
		}

		// Test that wrapping non-existent directory throws exception
		File nonExistent = new File("target/does-not-exist");
		try {
			Appose.wrap(nonExistent);
			fail("Should have thrown IOException for non-existent directory");
		} catch (IOException e) {
			assertTrue(e.getMessage().contains("does not exist"));
		}
	}

	@Test
	public void testServiceStartupFailure() throws IOException, InterruptedException {
		// Create an environment with no binPaths to test startup failure
		File tempDir = new File("no-pythons-to-be-found-here");
		tempDir.mkdirs();
		tempDir.deleteOnExit();

		Environment env = new Environment() {
			@Override public String base() { return tempDir.getAbsolutePath(); }
			@Override public List<String> binPaths() { return new ArrayList<>(); }
			@Override public List<String> launchArgs() { return new ArrayList<>(); }
			@Override public Builder<?> builder() { return null; }
		};
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
			maybeDebug(service);
			String script = "whee\n";
			Task task = service.task(script);
			task.waitFor();
			assertSame(TaskStatus.FAILED, task.status);
			String expectedError = "NameError: name 'whee' is not defined";
			assertTrue(task.error.contains(expectedError));
		}
	}

	@Test
	public void testStartupCrash() throws InterruptedException, IOException {
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
	public void testPythonSysExit() throws InterruptedException, IOException {
		Environment env = Appose.system();
		try (Service service = env.python()) {
			maybeDebug(service);

			// Create a task that calls sys.exit. This is a nasty thing to do
			// because Python does not exit the worker process when sys.exit is
			// called within a dedicated threading.Thread; the thread just dies.
			// So in addition to testing the Java code here, we are also testing
			// that Appose's python_worker handles this situation well.
			Task task = service.task("import sys\nsys.exit(123)");

			// Launch the task and wait for it to finish.
			task.waitFor();

			// Is the tag flagged as failed due to thread death?
			assertSame(TaskStatus.FAILED, task.status);
			assertEquals("thread death", task.error);
		}
	}

	@Test
	public void testCrashWithActiveTask() throws InterruptedException, IOException {
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
				"import time; time.sleep(999)\n";
			Task task = service.task(script);

			// Record any crash reported in the task notifications.
			String[] reportedError = {null};
			task.listen(event -> {
				if (event.responseType == ResponseType.CRASH) {
					reportedError[0] = task.error;
				}
			});
			// Launch the task.
			task.start();
			// Simulate a crash after 500ms has gone by.
			Thread.sleep(500);
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
	public void testMainThreadQueueGroovy() throws IOException, InterruptedException {
		Environment env = Appose.system();
		try (Service service = env.groovy()) {
			Task task = service.task(MAIN_THREAD_CHECK_GROOVY, "main");
			task.waitFor();
			String thread = (String) task.outputs.get("thread");
			assertEquals("main", thread);

			task = service.task(MAIN_THREAD_CHECK_GROOVY);
			task.waitFor();
			thread = (String) task.outputs.get("thread");
			assertNotEquals("main", thread);
		}
	}

	@Test
	public void testMainThreadQueuePython() throws IOException, InterruptedException {
		Environment env = Appose.system();
		try (Service service = env.python()) {
			Task task = service.task(MAIN_THREAD_CHECK_PYTHON, "main");
			task.waitFor();
			String thread = (String) task.outputs.get("thread");
			assertEquals("MainThread", thread);

			task = service.task(MAIN_THREAD_CHECK_PYTHON);
			task.waitFor();
			thread = (String) task.outputs.get("thread");
			assertNotEquals("MainThread", thread);
		}
	}

	@Test
	public void testContentAPI() throws IOException, InterruptedException {
		// Test building environment from content string
		String pixiToml =
			"[project]\n" +
			"name = \"content-test\"\n" +
			"channels = [\"conda-forge\"]\n" +
			"platforms = [\"linux-64\", \"osx-64\", \"osx-arm64\", \"win-64\"]\n" +
			"\n" +
			"[dependencies]\n" +
			"python = \">=3.8\"\n" +
			"appose = \"*\"\n" +
			"\n" +
			"[pypi-dependencies]\n" +
			"cowsay = \"==6.1\"\n";

		Environment env = Appose.pixi()
			.content(pixiToml)
			.base("target/envs/pixi-content-test")
			.logDebug()
			.build();

		cowsayAndAssert(env, "content!");
	}

	@Test
	public void testCustom() throws IOException, InterruptedException {
		// Test fluent chaining from base Builder methods to SimpleBuilder methods.
		// This verifies that the recursive generics enable natural method chaining.
		Environment env = Appose.custom()
			.env("CUSTOM_VAR", "test_value")  // Base Builder method
			.inheritRunningJava()             // SimpleBuilder method
			.appendSystemPath()               // SimpleBuilder method
			.build();

		assertNotNull(env);
		assertNotNull(env.binPaths());
		assertFalse(env.binPaths().isEmpty(), "Custom environment should have binary paths configured");
		assertTrue(env.launchArgs().isEmpty(), "Custom environment should have no special launcher");

		// Verify environment variables are propagated
		assertNotNull(env.envVars());
		assertEquals("test_value", env.envVars().get("CUSTOM_VAR"));

		// Verify inheritRunningJava() sets JAVA_HOME
		String javaHome = System.getProperty("java.home");
		if (javaHome != null) {
			assertEquals(javaHome, env.envVars().get("JAVA_HOME"));
			// Verify Java bin directory is in binPaths
			String javaBin = new File(javaHome, "bin").getAbsolutePath();
			assertTrue(env.binPaths().contains(javaBin),
				"Java bin directory should be in binPaths");
		}

		// Verify that the custom environment can execute Python tasks
		try (Service service = env.python()) {
			maybeDebug(service);
			Task task = service.task("task.outputs['result'] = 2 + 2");
			task.waitFor();
			assertComplete(task);
			Number result = (Number) task.outputs.get("result");
			assertEquals(4, result.intValue());
		}

		// Test custom environment with specific base directory
		File customDir = new File("target/test-custom");
		customDir.mkdirs();
		try {
			Environment customEnv = Appose.custom()
				.base(customDir)
				.appendSystemPath()
				.build();

			assertEquals(customDir.getAbsolutePath(), customEnv.base());
			assertNotNull(customEnv.binPaths());
		} finally {
			customDir.delete();
		}

		// Test custom environment with specific binary paths
		Environment pathEnv = Appose.custom()
			.binPaths("/usr/bin", "/usr/local/bin")
			.build();

		List<String> binPaths = pathEnv.binPaths();
		assertTrue(binPaths.contains("/usr/bin"), "Custom binPaths should include /usr/bin");
		assertTrue(binPaths.contains("/usr/local/bin"), "Custom binPaths should include /usr/local/bin");
	}

	@Test
	public void testWrapAndRebuild() throws IOException, InterruptedException {
		// Build a mamba environment from a config file
		File envDir = new File("target/envs/mamba-wrap-rebuild-test");
		Environment env1 = Appose
			.mamba("src/test/resources/envs/cowsay.yml")
			.base(envDir)
			.logDebug()
			.build();

		// Wrap the environment (simulating restarting the application)
		Environment env2 = Appose.wrap(envDir);
		assertNotNull(env2);
		assertEquals(envDir.getAbsolutePath(), env2.base());
		assertNotNull(env2.builder(), "Wrapped environment should have a builder");

		// Verify that the builder detected the config file
		assertInstanceOf(MambaBuilder.class, env2.builder());
		assertEquals("mamba", env2.type());

		// Rebuild the wrapped environment
		Environment env3 = env2.builder().rebuild();
		assertNotNull(env3);
		assertEquals(envDir.getAbsolutePath(), env3.base());

		// Verify the rebuilt environment works
		cowsayAndAssert(env3, "rebuilt");
	}

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
			// (exact spacing can vary between cowsay versions)
			String actual = (String) task.outputs.get("result");
			assertNotNull(actual, "Cowsay output should not be null");
			assertTrue(actual.contains(greeting), "Output should contain the greeting: " + greeting);
			assertTrue(actual.contains("^__^"), "Output should contain cow face");
			assertTrue(actual.contains("(oo)"), "Output should contain cow eyes");
			assertTrue(actual.contains("||----w |"), "Output should contain cow legs");
		}
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
		}
		assertEquals(TaskStatus.COMPLETE, task.status, errorMessage);
	}
}
