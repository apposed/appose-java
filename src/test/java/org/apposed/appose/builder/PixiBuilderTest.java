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
import org.apposed.appose.TestBase;
import org.apposed.appose.util.FilePaths;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** End-to-end tests for {@link PixiBuilder}. */
public class PixiBuilderTest extends TestBase {

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

	/** Tests building from a file:// URL to exercise URL support. */
	@Test
	public void testURLSupport() throws Exception {
		// Get absolute path and convert to file:// URL
		File configFile = new File("src/test/resources/envs/cowsay.yml").getAbsoluteFile();
		URL fileURL = configFile.toURI().toURL();

		Environment env = Appose
			.pixi(fileURL)
			.base("target/envs/pixi-url-test")
			.logDebug()
			.build();

		assertInstanceOf(PixiBuilder.class, env.builder());
		cowsayAndAssert(env, "url!");
	}
}
