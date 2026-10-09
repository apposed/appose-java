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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Tests shutting down {@link Service}s: closing, killing, and exiting the JVM. */
public class ServiceShutdownTest extends TestBase {

	/** Launches the worker as a child of an intermediate process, as {@code pixi run} does. */
	private static final String WRAPPED_WORKER =
		"import subprocess, sys; sys.exit(subprocess.call([sys.executable, '-c', " +
		"'import appose.python_worker; appose.python_worker.main()']))";

	private static final String WORKER_PID = "import os\ntask.outputs['pid'] = os.getpid()";

	private static final String SLEEP = "import time\ntime.sleep(60)";

	@Test
	public void testKillWrappedWorker() throws Exception {
		assumeFalse(isJava8(), "Java 8 cannot kill a worker behind a launcher");
		Service service = pythonEnv().python("-c", WRAPPED_WORKER);
		long pid = workerPid(service);

		Task task = startSleeping(service);
		long start = System.nanoTime();
		service.kill();
		int exitCode = service.waitFor(10, TimeUnit.SECONDS);

		assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(10));
		assertNotEquals(0, exitCode);
		assertEquals(exitCode, service.exitValue());
		assertSame(TaskStatus.CRASHED, task.status);
		assertDead(pid);
	}

	@Test
	public void testCloseWithTimeoutGraceful() throws Exception {
		Service service = pythonEnv().python();
		service.task("1 + 1").waitFor();

		assertEquals(0, service.close(10, TimeUnit.SECONDS));
		assertEquals(0, service.exitValue());
		assertFalse(service.isAlive());
	}

	@Test
	public void testCloseWithTimeoutKillsBusyWorker() throws Exception {
		assumeFalse(isJava8(), "Java 8 cannot kill a worker behind a launcher");
		Service service = pythonEnv().python("-c", WRAPPED_WORKER);
		long pid = workerPid(service);
		Task task = startSleeping(service);

		long start = System.nanoTime();
		int exitCode = service.close(500, TimeUnit.MILLISECONDS);

		assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(10));
		assertNotEquals(0, exitCode);
		assertSame(TaskStatus.CRASHED, task.status);
		assertDead(pid);
	}

	@Test
	public void testWaitForTimeout() throws Exception {
		Service service = pythonEnv().python();
		startSleeping(service);

		assertThrows(TimeoutException.class, () -> service.waitFor(100, TimeUnit.MILLISECONDS));
		assertThrows(IllegalStateException.class, service::exitValue);

		service.kill();
		assertNotEquals(0, service.waitFor(10, TimeUnit.SECONDS));
	}

	@Test
	public void testNotStarted() throws Exception {
		Service service = pythonEnv().python();

		assertFalse(service.isAlive());
		assertThrows(IllegalStateException.class, service::close);
		assertThrows(IllegalStateException.class, service::kill);
		assertThrows(IllegalStateException.class, service::waitFor);
		assertThrows(IllegalStateException.class, service::exitValue);
	}

	/**
	 * A program that leaves a busy service running, relying on a shutdown hook
	 * to clean it up, must still exit: see apposed/appose#37.
	 */
	@Test
	public void testShutdownHookRuns() throws Exception {
		List<String> output = runProgram("hook");
		assertEquals(3, output.size(), output.toString());
		assertEquals("cleanup ran", output.get(1));
		assertNotEquals("0", output.get(2));
		assertDead(Long.parseLong(output.get(0)));
	}

	/**
	 * A program that leaves a busy service running must exit,
	 * killing the worker once its exit timeout elapses.
	 */
	@Test
	public void testExitShutsDownServices() throws Exception {
		List<String> output = runProgram("exit");
		assertEquals(1, output.size(), output.toString());
		assertDead(Long.parseLong(output.get(0)));
	}

	/** Entry point of the program run by {@link #runProgram}. */
	public static void main(String... args) throws Exception {
		Service service = pythonEnv().python("-c", WRAPPED_WORKER);
		System.out.println(workerPid(service));
		service.task(SLEEP).start();
		if (args[0].equals("hook")) {
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				System.out.println("cleanup ran");
				try {
					System.out.println(service.close(0, TimeUnit.SECONDS));
				}
				catch (InterruptedException exc) {
					throw new RuntimeException(exc);
				}
			}));
		}
		else service.exitTimeout(500, TimeUnit.MILLISECONDS);
	}

	/** Runs {@link #main} in a JVM of its own, which must exit by itself. */
	private static List<String> runProgram(String mode) throws Exception {
		String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
		Process process = new ProcessBuilder(java, "-cp",
			System.getProperty("java.class.path"), ServiceShutdownTest.class.getName(), mode)
			.redirectError(ProcessBuilder.Redirect.INHERIT).start();
		process.getOutputStream().close();
		if (!process.waitFor(60, TimeUnit.SECONDS)) {
			process.destroyForcibly();
			fail("Program did not exit");
		}
		assertEquals(0, process.exitValue());
		return Arrays.asList(readAll(process.getInputStream()).trim().split("\\R"));
	}

	private static long workerPid(Service service) throws Exception {
		Object pid = service.task(WORKER_PID).waitFor().outputs.get("pid");
		return ((Number) pid).longValue();
	}

	private static Task startSleeping(Service service) throws InterruptedException {
		Task task = service.task(SLEEP).start();
		while (task.status != TaskStatus.RUNNING) Thread.sleep(10);
		return task;
	}

	private static void assertDead(long pid) throws Exception {
		if (System.getProperty("os.name").startsWith("Windows")) return;
		// NB: On Java 8, Processes.killTree cannot find the worker process
		// behind a launcher, so the worker outlives a kill until its task ends.
		if (isJava8()) return;
		// Give the orphaned worker's new parent a moment to reap it.
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (System.nanoTime() < deadline) {
			Process check = new ProcessBuilder("kill", "-0", Long.toString(pid)).start();
			if (check.waitFor() != 0) return;
			Thread.sleep(50);
		}
		fail("Worker process " + pid + " is still alive");
	}

	private static boolean isJava8() {
		return System.getProperty("java.specification.version").startsWith("1.");
	}

	private static String readAll(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int r;
		while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
		return new String(out.toByteArray(), StandardCharsets.UTF_8);
	}
}
