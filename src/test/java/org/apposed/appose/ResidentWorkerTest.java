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

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Tests {@link LazyEnvironment} and {@link ResidentWorker}. */
public class ResidentWorkerTest extends TestBase {

	private static final String PID = "import os\ntask.outputs['pid'] = os.getpid()";

	@Test
	public void testWorkersShareEnvironment() throws Exception {
		LazyEnvironment env = environment();
		AtomicInteger builds = new AtomicInteger();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		env.addBuildListener(new BuildListener() {

			@Override
			public void buildStarted(String envName) {
				builds.incrementAndGet();
			}

			@Override
			public void buildFinished(String envName, Throwable error) {
				failure.set(error);
			}
		});

		try (ResidentWorker a = new ResidentWorker(env, Environment::python);
			ResidentWorker b = new ResidentWorker(env, Environment::python))
		{
			int pidA = pid(a);
			int pidB = pid(b);
			assertEquals(1, builds.get());
			assertNull(failure.get());
			assertNotEquals(pidA, pidB);

			// Each worker stays resident across tasks.
			assertEquals(pidA, pid(a));

			// A killed worker is replaced on its next task.
			a.kill();
			assertNotEquals(pidA, pid(a));
			assertEquals(1, builds.get());
		}
	}

	private static int pid(ResidentWorker worker) throws Exception {
		return ((Number) worker.task(PID).waitFor().outputs.get("pid"))
			.intValue();
	}

	private static LazyEnvironment environment() {
		Builder<?> builder = Appose.uv()
			.python("3.9")
			.include(apposePythonSpec())
			.base("target/envs/test-python");
		return new LazyEnvironment("test-python", builder);
	}
}
