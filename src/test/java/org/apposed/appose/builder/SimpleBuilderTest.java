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

package org.apposed.appose.builder;

import org.apposed.appose.Appose;
import org.apposed.appose.Environment;
import org.apposed.appose.Service;
import org.apposed.appose.Service.Task;
import org.apposed.appose.TestBase;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end tests for {@link SimpleBuilder}. */
public class SimpleBuilderTest extends TestBase {

	/**
	 * Tests fluent chaining from base Builder methods to SimpleBuilder methods.
	 * This verifies that the recursive generics enable natural method chaining.
	 */
	@Test
	public void testCustom() throws Exception {
		Environment env = Appose.custom()
			.env("CUSTOM_VAR", "test_value")  // Base Builder method
			.inheritRunningJava()             // SimpleBuilder method
			.appendSystemPath()               // SimpleBuilder method
			.build();

		assertNotNull(env);
		assertNotNull(env.binPaths());
		assertFalse(env.binPaths().isEmpty(),
			"Custom environment should have binary paths configured");
		assertTrue(env.launchArgs().isEmpty(),
			"Custom environment should have no special launcher");

		// Verify environment variables are propagated.
		assertNotNull(env.envVars());
		assertEquals("test_value", env.envVars().get("CUSTOM_VAR"));

		// Verify inheritRunningJava() sets JAVA_HOME.
		String javaHome = System.getProperty("java.home");
		if (javaHome != null) {
			assertEquals(javaHome, env.envVars().get("JAVA_HOME"));
			// Verify Java bin directory is in binPaths.
			String javaBin = new File(javaHome, "bin").getAbsolutePath();
			assertTrue(env.binPaths().contains(javaBin),
				"Java bin directory should be in binPaths");
		}

		// Verify that the custom environment can execute Python tasks.
		try (Service service = env.python()) {
			maybeDebug(service);
			Task task = service.task("2 + 2");
			task.waitFor();
			assertComplete(task);
			Number result = (Number) task.result();
			assertEquals(4, result.intValue());
		}

		// Test custom environment with specific base directory.
		File customDir = new File("target/test-custom");
		customDir.mkdirs();
		try {
			Environment customEnv = Appose.custom()
				.base(customDir)
				.appendSystemPath()
				.build();

			assertEquals(customDir.getAbsolutePath(), customEnv.base());
			assertNotNull(customEnv.binPaths());
		}
		finally {
			customDir.delete();
		}

		// Test custom environment with specific binary paths.
		Environment pathEnv = Appose.custom()
			.binPaths("/usr/bin", "/usr/local/bin")
			.build();

		List<String> binPaths = pathEnv.binPaths();
		assertTrue(binPaths.contains("/usr/bin"),
			"Custom binPaths should include /usr/bin");
		assertTrue(binPaths.contains("/usr/local/bin"),
			"Custom binPaths should include /usr/local/bin");
	}
}
