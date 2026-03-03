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

package org.apposed.appose.builder;

import org.apposed.appose.Appose;
import org.apposed.appose.Environment;
import org.apposed.appose.TestBase;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for the builder {@code .content()} API across all builder/scheme combinations.
 * <p>
 * Verifies that each builder:
 * <ul>
 *   <li>correctly accepts its supported schemes and builds a working environment</li>
 *   <li>fails fast (before any tool installation) with a clear error for unsupported schemes</li>
 * </ul>
 * Matrix: 4 builders × 5 content types = 20 tests:
 * <ul>
 *   <li>uv: accepts requirements.txt, pyproject.toml; rejects environment.yml, pixi.toml, unrecognized</li>
 *   <li>pixi: accepts pixi.toml, pyproject.toml, environment.yml; rejects requirements.txt, unrecognized</li>
 *   <li>mamba: accepts environment.yml only; rejects all others</li>
 *   <li>content (dynamic): auto-detects requirements.txt→uv, environment.yml→pixi, pixi.toml→pixi, pyproject.toml→pixi; rejects unrecognized</li>
 * </ul>
 */
public class BuilderContentTest extends TestBase {

	// Minimal content stubs for "fail fast" tests — just enough to trigger correct
	// scheme detection, but deliberately incompatible with the builder under test.

	private static final String PIXI_TOML_STUB =
		"[workspace]\n" +
		"name = \"stub\"\n" +
		"channels = [\"conda-forge\"]\n" +
		"platforms = [\"linux-64\"]\n" +
		"\n" +
		"[dependencies]\n" +
		"python = \"*\"\n";

	private static final String ENV_YML_STUB =
		"name: stub-env\n" +
		"dependencies:\n" +
		"  - python\n";

	private static final String REQUIREMENTS_TXT_STUB = "appose\n";

	private static final String PYPROJECT_TOML_STUB =
		"[project]\n" +
		"name = \"stub\"\n" +
		"version = \"0.1.0\"\n" +
		"dependencies = []\n";

	// Must not match any scheme (does not start with a letter/digit or TOML/YAML markers).
	private static final String UNRECOGNIZED = "## not a valid config format\n";

	private static String readResource(String path) throws IOException {
		return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
	}

	// ======================== UvBuilder ========================

	@Test
	public void uvWithRequirementsTxt() throws Exception {
		String content = readResource("src/test/resources/envs/cowsay-requirements.txt");
		Environment env = Appose.uv()
			.content(content).base("target/envs/content-uv-requirements").logDebug().build();
		assertInstanceOf(UvBuilder.class, env.builder());
		cowsayAndAssert(env, "uv-req");
	}

	@Test
	public void uvWithPyprojectToml() throws Exception {
		String content = readResource("src/test/resources/envs/cowsay-pyproject.toml");
		Environment env = Appose.uv()
			.content(content).base("target/envs/content-uv-pyproject").logDebug().build();
		assertInstanceOf(UvBuilder.class, env.builder());
		cowsayAndAssert(env, "uv-pyproject");
	}

	@Test
	public void uvWithEnvironmentYml() {
		assertThrows(IllegalArgumentException.class, () ->
			Appose.uv().content(ENV_YML_STUB).base("target/envs/content-uv-envyml").build());
	}

	@Test
	public void uvWithPixiToml() {
		assertThrows(IllegalArgumentException.class, () ->
			Appose.uv().content(PIXI_TOML_STUB).base("target/envs/content-uv-pixi").build());
	}

	@Test
	public void uvWithUnrecognized() {
		assertThrows(IllegalArgumentException.class, () ->
			Appose.uv().content(UNRECOGNIZED).base("target/envs/content-uv-unknown").build());
	}

	// ======================== PixiBuilder ========================

	@Test
	public void pixiWithPixiToml() throws Exception {
		String content = readResource("src/test/resources/envs/cowsay-pixi.toml");
		Environment env = Appose.pixi()
			.content(content).base("target/envs/content-pixi-pixi").logDebug().build();
		assertInstanceOf(PixiBuilder.class, env.builder());
		cowsayAndAssert(env, "pixi-pixi");
	}

	@Test
	public void pixiWithPyprojectToml() throws Exception {
		String content = readResource("src/test/resources/envs/cowsay-pixi-pyproject.toml");
		Environment env = Appose.pixi()
			.content(content).base("target/envs/content-pixi-pyproject").logDebug().build();
		assertInstanceOf(PixiBuilder.class, env.builder());
		cowsayAndAssert(env, "pixi-pyproject");
	}

	@Test
	public void pixiWithEnvironmentYml() throws Exception {
		String content = readResource("src/test/resources/envs/cowsay.yml");
		Environment env = Appose.pixi()
			.content(content).base("target/envs/content-pixi-envyml").logDebug().build();
		assertInstanceOf(PixiBuilder.class, env.builder());
		cowsayAndAssert(env, "pixi-envyml");
	}

	@Test
	public void pixiWithRequirementsTxt() {
		assertThrows(IllegalArgumentException.class, () ->
			Appose.pixi().content(REQUIREMENTS_TXT_STUB).base("target/envs/content-pixi-requirements").build());
	}

	@Test
	public void pixiWithUnrecognized() {
		assertThrows(IllegalArgumentException.class, () ->
			Appose.pixi().content(UNRECOGNIZED).base("target/envs/content-pixi-unknown").build());
	}

	// ======================== MambaBuilder ========================

	@Test
	public void mambaWithEnvironmentYml() throws Exception {
		String content = readResource("src/test/resources/envs/cowsay.yml");
		Environment env = Appose.mamba()
			.content(content).base("target/envs/content-mamba-envyml").logDebug().build();
		assertInstanceOf(MambaBuilder.class, env.builder());
		cowsayAndAssert(env, "mamba-envyml");
	}

	@Test
	public void mambaWithPyprojectToml() {
		assertThrows(IllegalArgumentException.class, () ->
			Appose.mamba().content(PYPROJECT_TOML_STUB).base("target/envs/content-mamba-pyproject").build());
	}

	@Test
	public void mambaWithRequirementsTxt() {
		assertThrows(IllegalArgumentException.class, () ->
			Appose.mamba().content(REQUIREMENTS_TXT_STUB).base("target/envs/content-mamba-requirements").build());
	}

	@Test
	public void mambaWithPixiToml() {
		assertThrows(IllegalArgumentException.class, () ->
			Appose.mamba().content(PIXI_TOML_STUB).base("target/envs/content-mamba-pixi").build());
	}

	@Test
	public void mambaWithUnrecognized() {
		assertThrows(IllegalArgumentException.class, () ->
			Appose.mamba().content(UNRECOGNIZED).base("target/envs/content-mamba-unknown").build());
	}

	// ======================== DynamicBuilder (Appose.content) ========================

	@Test
	public void contentWithRequirementsTxt() throws Exception {
		String content = readResource("src/test/resources/envs/cowsay-requirements.txt");
		Environment env = Appose.content(content)
			.base("target/envs/content-dynamic-requirements").logDebug().build();
		assertInstanceOf(UvBuilder.class, env.builder());
		cowsayAndAssert(env, "dynamic-req");
	}

	@Test
	public void contentWithEnvironmentYml() throws Exception {
		String content = readResource("src/test/resources/envs/cowsay.yml");
		Environment env = Appose.content(content)
			.base("target/envs/content-dynamic-envyml").logDebug().build();
		assertInstanceOf(PixiBuilder.class, env.builder());
		cowsayAndAssert(env, "dynamic-envyml");
	}

	@Test
	public void contentWithPixiToml() throws Exception {
		String content = readResource("src/test/resources/envs/cowsay-pixi.toml");
		Environment env = Appose.content(content)
			.base("target/envs/content-dynamic-pixi").logDebug().build();
		assertInstanceOf(PixiBuilder.class, env.builder());
		cowsayAndAssert(env, "dynamic-pixi");
	}

	@Test
	public void contentWithPyprojectToml() throws Exception {
		String content = readResource("src/test/resources/envs/cowsay-pixi-pyproject.toml");
		Environment env = Appose.content(content)
			.base("target/envs/content-dynamic-pyproject").logDebug().build();
		assertInstanceOf(PixiBuilder.class, env.builder());
		cowsayAndAssert(env, "dynamic-pyproject");
	}

	@Test
	public void contentWithUnrecognized() {
		assertThrows(IllegalArgumentException.class, () ->
			Appose.content(UNRECOGNIZED).base("target/envs/content-dynamic-unknown").build());
	}
}
