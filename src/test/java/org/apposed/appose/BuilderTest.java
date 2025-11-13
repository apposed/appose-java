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

import org.apposed.appose.Service.Task;
import org.apposed.appose.builder.MambaBuilder;
import org.apposed.appose.builder.PixiBuilder;
import org.apposed.appose.builder.UvBuilder;
import org.apposed.appose.util.FilePaths;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end tests for the Appose builder subsystem and implementations. */
public class BuilderTest extends TestBase {

	/** Tests the builder-agnostic API with an environment.yml file. */
	@Test
	public void testConda() throws Exception {
		Environment env = Appose
			.file("src/test/resources/envs/cowsay.yml")
			.base("target/envs/conda-cowsay")
			.logDebug()
			.build();
		assertInstanceOf(PixiBuilder.class, env.builder());
		cowsayAndAssert(env, "moo");
	}

	@Test
	public void testPixi() throws Exception {
		Environment env = Appose
			.pixi("src/test/resources/envs/cowsay-pixi.toml")
			.base("target/envs/pixi-cowsay")
			.logDebug()
			.build();
		assertInstanceOf(PixiBuilder.class, env.builder());
		cowsayAndAssert(env, "baa");
	}

	@Test
	public void testPixiBuilderAPI() throws Exception {
		Environment env = Appose
			.pixi()
			.conda("python>=3.8", "appose")
			.pypi("cowsay==6.1")
			.base("target/envs/pixi-cowsay-builder")
			.logDebug()
			.build();
		assertInstanceOf(PixiBuilder.class, env.builder());
		cowsayAndAssert(env, "ooh");
	}

	@Test
	public void testPixiVacuous() throws IOException {
		String base = "target/envs/pixi-vacuous";
		FilePaths.deleteRecursively(new File(base));
		assertThrows(IllegalStateException.class, () -> {
			Appose
				.pixi()
				.base(base)
				.logDebug()
				.build();
		});
	}

	@Test
	public void testPixiApposeRequirement() throws IOException {
		String base = "target/envs/pixi-appose-requirement";
		FilePaths.deleteRecursively(new File(base));
		assertThrows(IllegalStateException.class, () -> {
			Appose
				.pixi()
				.conda("python")
				.pypi("cowsay==6.1")
				.base(base)
				.logDebug()
				.build();
		});
	}

	@Test
	public void testPixiPyproject() throws Exception {
		Environment env = Appose
			.pixi("src/test/resources/envs/cowsay-pixi-pyproject.toml")
			.base("target/envs/pixi-cowsay-pyproject")
			.logDebug()
			.build();
		assertInstanceOf(PixiBuilder.class, env.builder());
		cowsayAndAssert(env, "pixi-pyproject");
	}

	/** Tests explicit mamba builder selection using {@code .builder()} method. */
	@Test
	public void testExplicitMambaBuilder() throws Exception {
		Environment env = Appose
			.file("src/test/resources/envs/cowsay.yml")
			.builder("mamba")
			.base("target/envs/mamba-cowsay")
			.logDebug()
			.build();

		assertInstanceOf(MambaBuilder.class, env.builder());

		// Verify it actually used mamba by checking for conda-meta directory.
		File envBase = new File(env.base());
		File condaMeta = new File(envBase, "conda-meta");
		assertTrue(condaMeta.exists() && condaMeta.isDirectory(),
			"Environment should have conda-meta directory when using mamba builder");

		cowsayAndAssert(env, "yay");
	}

	@Test
	public void testUv() throws Exception {
		Environment env = Appose
			.uv("src/test/resources/envs/cowsay-requirements.txt")
			.base("target/envs/uv-cowsay")
			.logDebug()
			.build();
		assertInstanceOf(UvBuilder.class, env.builder());
		cowsayAndAssert(env, "uv");
	}

	@Test
	public void testUvBuilderAPI() throws Exception {
		Environment env = Appose
			.uv()
			.include("cowsay==6.1")
			.base("target/envs/uv-cowsay-builder")
			.logDebug()
			.build();
		assertInstanceOf(UvBuilder.class, env.builder());
		cowsayAndAssert(env, "fast");
	}

	@Test
	public void testUvPyproject() throws Exception {
		Environment env = Appose
			.uv("src/test/resources/envs/cowsay-pyproject.toml")
			.base("target/envs/uv-cowsay-pyproject")
			.logDebug()
			.build();
		cowsayAndAssert(env, "pyproject");
	}

	/** Tests building environment from content string using type-specific builder.*/
	@Test
	public void testContentAPI() throws Exception {
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

	/** Tests auto-detecting builder from environment.yml content string. */
	@Test
	public void testContentEnvironmentYml() throws Exception {
		String envYml =
			"name: content-env-yml\n" +
			"channels:\n" +
			"  - conda-forge\n" +
			"dependencies:\n" +
			"  - python>=3.8\n" +
			"  - appose\n" +
			"  - pip\n" +
			"  - pip:\n" +
			"    - cowsay==6.1\n";

		Environment env = Appose.content(envYml)
			.base("target/envs/content-env-yml")
			.logDebug()
			.build();

		assertInstanceOf(PixiBuilder.class, env.builder());
		cowsayAndAssert(env, "yml!");
	}

	/** Tests auto-detecting builder from pixi.toml content string. */
	@Test
	public void testContentPixiToml() throws Exception {
		String pixiToml =
			"[project]\n" +
			"name = \"content-pixi-toml\"\n" +
			"channels = [\"conda-forge\"]\n" +
			"platforms = [\"linux-64\", \"osx-64\", \"osx-arm64\", \"win-64\"]\n" +
			"\n" +
			"[dependencies]\n" +
			"python = \">=3.8\"\n" +
			"appose = \"*\"\n" +
			"\n" +
			"[pypi-dependencies]\n" +
			"cowsay = \"==6.1\"\n";

		Environment env = Appose.content(pixiToml)
			.base("target/envs/content-pixi-toml")
			.logDebug()
			.build();

		assertInstanceOf(PixiBuilder.class, env.builder());
		cowsayAndAssert(env, "toml!");
	}

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
