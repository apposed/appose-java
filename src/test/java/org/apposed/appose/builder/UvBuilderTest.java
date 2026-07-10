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
import org.apposed.appose.util.Json;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end tests for {@link UvBuilder}. */
public class UvBuilderTest extends TestBase {

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

	@Test
	public void testUvPyprojectWithGroup() throws Exception {
		Environment env = Appose
			.uv("src/test/resources/envs/cowsay-pyproject-groups.toml")
			.group("cowsay")
			.base("target/envs/uv-cowsay-groups")
			.logDebug()
			.build();
		cowsayAndAssert(env, "groups");
	}

	@Test
	public void testUvGroupRejectsWithoutPyproject() {
		assertThrows(IllegalArgumentException.class, () ->
			Appose.uv()
				.content("appose\n")
				.group("cowsay")
				.base("target/envs/uv-group-no-pyproject")
				.build());
	}

	@Test
	public void testUvStateNoGroupField() throws Exception {
		Environment env = Appose
			.uv("src/test/resources/envs/cowsay-pyproject.toml")
			.base("target/envs/uv-state-no-groups")
			.logDebug()
			.build();
		File apposeJson = new File(env.base(), "appose.json");
		assertTrue(apposeJson.isFile(), "appose.json should exist");
		String json = new String(Files.readAllBytes(apposeJson.toPath()), StandardCharsets.UTF_8);
		@SuppressWarnings("unchecked")
		java.util.Map<String, Object> state = (java.util.Map<String, Object>) Json.parseJson(json);
		assertFalse(state.containsKey("groups"), "appose.json should not contain 'groups' when none specified");
		assertNotNull(state.get("packages"), "appose.json should contain 'packages'");
	}

	@Test
	public void testUvStateGroupFieldPresent() throws Exception {
		Environment env = Appose
			.uv("src/test/resources/envs/cowsay-pyproject-groups.toml")
			.group("cowsay")
			.base("target/envs/uv-state-with-groups")
			.logDebug()
			.build();
		File apposeJson = new File(env.base(), "appose.json");
		assertTrue(apposeJson.isFile(), "appose.json should exist");
		String json = new String(Files.readAllBytes(apposeJson.toPath()), StandardCharsets.UTF_8);
		@SuppressWarnings("unchecked")
		java.util.Map<String, Object> state = (java.util.Map<String, Object>) Json.parseJson(json);
		assertTrue(state.containsKey("groups"), "appose.json should contain 'groups' when specified");
	}
}
