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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for the {@link Builders} utility class. */
public class BuildersTest {

	@TempDir
	File tempDir;

	@Test
	public void testCanWrapPixi() throws IOException {
		// Create a directory with pixi.toml marker.
		File pixiDir = new File(tempDir, "pixi-env");
		pixiDir.mkdirs();
		File pixiToml = new File(pixiDir, "pixi.toml");
		pixiToml.createNewFile();

		assertTrue(Builders.canWrap(pixiDir),
			"Should detect pixi environment");
	}

	@Test
	public void testCanWrapMamba() throws IOException {
		// Create a directory with conda-meta marker.
		File condaDir = new File(tempDir, "conda-env");
		condaDir.mkdirs();
		File condaMeta = new File(condaDir, "conda-meta");
		condaMeta.mkdirs();

		assertTrue(Builders.canWrap(condaDir),
			"Should detect conda/mamba environment");
	}

	@Test
	public void testCanWrapUv() throws IOException {
		// Create a directory with pyvenv.cfg marker.
		File uvDir = new File(tempDir, "uv-env");
		uvDir.mkdirs();
		File pyvenvCfg = new File(uvDir, "pyvenv.cfg");
		pyvenvCfg.createNewFile();

		assertTrue(Builders.canWrap(uvDir),
			"Should detect uv/venv environment");
	}

	@Test
	public void testCannotWrapPlainDirectory() {
		// A plain directory without any markers is not recognized by any builder factory.
		// Note: Appose.wrap() will still work by defaulting to SimpleBuilder,
		// but Builders.canWrap() returns false since no factory claims it.
		File plainDir = new File(tempDir, "plain-dir");
		plainDir.mkdirs();

		assertFalse(Builders.canWrap(plainDir),
			"Plain directory should not be recognized by any builder factory");
	}

	@Test
	public void testCannotWrapNonExistentDirectory() {
		File nonExistent = new File(tempDir, "does-not-exist");

		assertFalse(Builders.canWrap(nonExistent),
			"Should not wrap non-existent directory");
	}

	@Test
	public void testEnvTypePixi() throws IOException {
		File pixiDir = new File(tempDir, "pixi-env");
		pixiDir.mkdirs();
		File pixiToml = new File(pixiDir, "pixi.toml");
		pixiToml.createNewFile();

		assertEquals("pixi", Builders.envType(pixiDir),
			"Should detect pixi as environment type");
	}

	@Test
	public void testEnvTypeMamba() throws IOException {
		File condaDir = new File(tempDir, "conda-env");
		condaDir.mkdirs();
		File condaMeta = new File(condaDir, "conda-meta");
		condaMeta.mkdirs();

		assertEquals("mamba", Builders.envType(condaDir),
			"Should detect mamba as environment type");
	}

	@Test
	public void testEnvTypeUv() throws IOException {
		File uvDir = new File(tempDir, "uv-env");
		uvDir.mkdirs();
		File pyvenvCfg = new File(uvDir, "pyvenv.cfg");
		pyvenvCfg.createNewFile();

		assertEquals("uv", Builders.envType(uvDir),
			"Should detect uv as environment type");
	}

	@Test
	public void testEnvTypePlainDirectory() {
		// A plain directory returns null since no factory recognizes it.
		// Note: Appose.wrap() will still wrap it using SimpleBuilder as fallback.
		File plainDir = new File(tempDir, "plain-dir");
		plainDir.mkdirs();

		assertNull(Builders.envType(plainDir),
			"Should return null for plain directory not recognized by any factory");
	}

	@Test
	public void testEnvTypeNonExistent() {
		File nonExistent = new File(tempDir, "does-not-exist");

		assertNull(Builders.envType(nonExistent),
			"Should return null for non-existent directory");
	}

	@Test
	public void testEnvTypePriority() throws IOException {
		// When multiple markers exist, should pick highest priority builder.
		// Pixi has priority 100, mamba has priority 50.
		File multiDir = new File(tempDir, "multi-env");
		multiDir.mkdirs();
		File pixiToml = new File(multiDir, "pixi.toml");
		pixiToml.createNewFile();
		File condaMeta = new File(multiDir, "conda-meta");
		condaMeta.mkdirs();

		assertEquals("pixi", Builders.envType(multiDir),
			"Should prefer pixi over mamba when both markers present");
	}
}
