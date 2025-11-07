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

import org.apposed.appose.builder.MambaBuilder;
import org.apposed.appose.builder.PixiBuilder;
import org.apposed.appose.builder.SimpleBuilder;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Tests the {@link Appose#wrap} feature. */
public class WrapTest extends TestBase {

	/** Tests wrapping a pixi environment. */
	@Test
	public void testWrapPixi() throws IOException {
		File pixiDir = new File("target/test-wrap-pixi");
		pixiDir.mkdirs();
		File pixiToml = new File(pixiDir, "pixi.toml");
		pixiToml.createNewFile();

		try {
			Environment pixiEnv = Appose.wrap(pixiDir);
			assertNotNull(pixiEnv);
			assertInstanceOf(PixiBuilder.class, pixiEnv.builder());
			assertEquals(pixiDir.getAbsolutePath(), pixiEnv.base());
			assertNotNull(pixiEnv.launchArgs());
			assertFalse(pixiEnv.launchArgs().isEmpty());
			assertTrue(pixiEnv.launchArgs().get(0).contains("pixi"),
				"Pixi environment should use pixi launcher");
		} finally {
			pixiToml.delete();
			pixiDir.delete();
		}
	}

	/** Tests wrapping a conda/mamba environment. */
	@Test
	public void testWrapMamba() throws IOException {
		// Test wrapping a conda/mamba environment.
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
			condaDir.delete();
		}
	}

	/** Tests wrapping a uv environment. */
	@Test
	public void testWrapUv() throws IOException {
		File uvDir = new File("target/test-wrap-uv");
		uvDir.mkdirs();
		File pyvenvCfg = new File(uvDir, "pyvenv.cfg");
		pyvenvCfg.createNewFile();

		try {
			Environment uvEnv = Appose.wrap(uvDir);
			assertNotNull(uvEnv);
			assertEquals(uvDir.getAbsolutePath(), uvEnv.base());
			// uv environments use standard venv structure with no special launch args.
			assertTrue(uvEnv.launchArgs().isEmpty(),
				"uv environment should have no special launcher");
		} finally {
			pyvenvCfg.delete();
			uvDir.delete();
		}
	}

	/** Tests wrapping a plain directory (should fall back to SimpleBuilder). */
	@Test
	public void testWrapCustom() throws IOException {
		File customDir = new File("target/test-wrap-simple");
		customDir.mkdirs();

		try {
			Environment customEnv = Appose.wrap(customDir);
			assertNotNull(customEnv);
			assertInstanceOf(SimpleBuilder.class, customEnv.builder());
			assertEquals(customDir.getAbsolutePath(), customEnv.base());
			// SimpleBuilder uses empty launch args by default.
			assertTrue(customEnv.launchArgs().isEmpty(),
				"Custom environment should have no special launcher");
		} finally {
			customDir.delete();
		}
	}

	/** Tests that wrapping non-existent directory throws exception. */
	@Test
	public void testWrapNonExistent() {
		File nonExistent = new File("target/does-not-exist");
		try {
			Appose.wrap(nonExistent);
			fail("Should have thrown IOException for non-existent directory");
		} catch (IOException e) {
			assertTrue(e.getMessage().contains("does not exist"));
		}
	}

	/** Tests that preexisting (wrapped) environments can be rebuilt properly. */
	@Test
	public void testWrapAndRebuild() throws IOException, InterruptedException {
		// Build an environment from a config file.
		File envDir = new File("target/envs/mamba-wrap-rebuild-test");
		Environment env1 = Appose
			.mamba("src/test/resources/envs/cowsay.yml")
			.base(envDir)
			.logDebug()
			.build();
		assertNotNull(env1);

		// Wrap the environment (simulating restarting the application).
		Environment env2 = Appose.wrap(envDir);
		assertNotNull(env2);
		assertEquals(envDir.getAbsolutePath(), env2.base());
		assertNotNull(env2.builder(), "Wrapped environment should have a builder");

		// Verify that the builder detected the config file.
		assertInstanceOf(MambaBuilder.class, env2.builder());
		assertEquals("mamba", env2.type());

		// Rebuild the wrapped environment.
		Environment env3 = env2.builder().rebuild();
		assertNotNull(env3);
		assertEquals(envDir.getAbsolutePath(), env3.base());

		// Verify the rebuilt environment works.
		cowsayAndAssert(env3, "rebuilt");
	}
}
