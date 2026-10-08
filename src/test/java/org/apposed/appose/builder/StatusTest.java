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
import org.apposed.appose.BuildException;
import org.apposed.appose.EnvStatus;
import org.apposed.appose.util.FilePaths;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link org.apposed.appose.Builder#status()}.
 * Uses hand-made marker files, so no tools or network are needed.
 */
public class StatusTest {

	private static File freshDir(String name) throws Exception {
		File dir = new File("target/test-status/" + name);
		if (dir.exists()) FilePaths.deleteRecursively(dir);
		return dir;
	}

	private static void touch(File dir, String relPath) throws Exception {
		Path p = new File(dir, relPath).toPath();
		Files.createDirectories(p.getParent());
		Files.write(p, new byte[0]);
	}

	@Test
	public void testMissing() throws Exception {
		File dir = freshDir("missing");
		assertEquals(EnvStatus.MISSING, Appose.mamba().base(dir).status());
		assertEquals(EnvStatus.MISSING, Appose.pixi().base(dir).status());
		assertEquals(EnvStatus.MISSING, Appose.uv().base(dir).status());
	}

	@Test
	public void testEmptyDirIsMissing() throws Exception {
		File dir = freshDir("empty");
		assertEquals(true, dir.mkdirs());
		assertEquals(EnvStatus.MISSING, Appose.mamba().base(dir).status());
		assertEquals(EnvStatus.MISSING, Appose.pixi().base(dir).status());
		assertEquals(EnvStatus.MISSING, Appose.uv().base(dir).status());
	}

	@Test
	public void testExternalConda() throws Exception {
		File dir = freshDir("external-conda");
		touch(dir, "conda-meta/history");
		assertEquals(EnvStatus.EXTERNAL, Appose.mamba().base(dir).status());
	}

	@Test
	public void testExternalVenv() throws Exception {
		File dir = freshDir("external-venv");
		touch(dir, "pyvenv.cfg");
		assertEquals(EnvStatus.EXTERNAL, Appose.uv().base(dir).status());
	}

	@Test
	public void testIncompatible() throws Exception {
		File conda = freshDir("incompatible-conda");
		touch(conda, "conda-meta/history");
		assertEquals(EnvStatus.INCOMPATIBLE, Appose.pixi().base(conda).status());
		assertEquals(EnvStatus.INCOMPATIBLE, Appose.uv().base(conda).status());
		// Note: build() must agree, failing before any tools are downloaded.
		assertThrows(BuildException.class, () -> Appose.pixi().base(conda).build());

		File venv = freshDir("incompatible-venv");
		touch(venv, "pyvenv.cfg");
		assertEquals(EnvStatus.INCOMPATIBLE, Appose.mamba().base(venv).status());
		assertEquals(EnvStatus.INCOMPATIBLE, Appose.pixi().base(venv).status());

		File pixi = freshDir("incompatible-pixi");
		touch(pixi, ".pixi/envs/default/conda-meta/history");
		assertEquals(EnvStatus.INCOMPATIBLE, Appose.mamba().base(pixi).status());
		assertEquals(EnvStatus.INCOMPATIBLE, Appose.uv().base(pixi).status());
		assertEquals(EnvStatus.EXTERNAL, Appose.pixi().base(pixi).status());
	}

	@Test
	public void testStaleAndCurrent() throws Exception {
		File dir = freshDir("managed");
		touch(dir, "conda-meta/history");
		MambaBuilder builder = Appose.mamba().base(dir).content("name: foo\n");
		// Simulate a successful build by recording the builder's state.
		builder.writeApposeStateFile(dir);
		assertEquals(EnvStatus.CURRENT, builder.status());
		builder.channels("conda-forge");
		assertEquals(EnvStatus.STALE, builder.status());
	}

	@Test
	public void testInferredSchemeIsCurrent() throws Exception {
		File dir = freshDir("inferred-scheme");
		touch(dir, "conda-meta/history");
		String yml = "name: foo\n";
		// Note: build() infers and stores the scheme before writing appose.json.
		Appose.mamba().base(dir).content(yml).scheme("environment.yml").writeApposeStateFile(dir);
		// A fresh builder with the same content must still report CURRENT.
		assertEquals(EnvStatus.CURRENT, Appose.mamba().base(dir).content(yml).status());
	}

	@Test
	public void testStateFileWithoutEnvironmentIsMissing() throws Exception {
		File dir = freshDir("orphan-state");
		MambaBuilder builder = Appose.mamba().base(dir).content("name: foo\n");
		dir.mkdirs();
		builder.writeApposeStateFile(dir);
		assertEquals(EnvStatus.MISSING, builder.status());
	}

	@Test
	public void testDeleteResetsToMissing() throws Exception {
		File dir = freshDir("delete");
		touch(dir, "conda-meta/history");
		MambaBuilder builder = Appose.mamba().base(dir).content("name: foo\n");
		builder.writeApposeStateFile(dir);
		assertEquals(EnvStatus.CURRENT, builder.status());
		builder.delete();
		assertEquals(EnvStatus.MISSING, builder.status());
	}

	@Test
	public void testDynamicDelegates() throws Exception {
		File dir = freshDir("dynamic");
		touch(dir, ".pixi/envs/default/conda-meta/history");
		assertEquals(EnvStatus.EXTERNAL,
			Appose.content("name: foo\ndependencies:\n  - python\n").base(dir).status());
	}

	@Test
	public void testSimple() throws Exception {
		File dir = freshDir("simple");
		assertEquals(EnvStatus.MISSING, Appose.custom().base(dir).status());
		dir.mkdirs();
		assertEquals(EnvStatus.EXTERNAL, Appose.custom().base(dir).status());
	}
}
