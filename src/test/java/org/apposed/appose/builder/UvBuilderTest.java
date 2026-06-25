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
import org.apposed.appose.Environment;
import org.apposed.appose.TestBase;
import org.apposed.appose.util.FilePaths;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

	// -- Lock-file reproducible builds --

	/**
	 * A user-supplied lock is copied into the env dir and the install runs with
	 * --frozen, yielding a reproducible, working environment. Exercises both the
	 * {@code .lockFile(File)} and {@code .lockUrl(URL)} entry points.
	 */
	@Test
	public void testUvLockFrozen() throws Exception {
		// First, build without a lock to generate a valid uv.lock for the manifest.
		String baseA = "target/envs/uv-lock-src";
		FilePaths.deleteRecursively(new File(baseA));
		Appose.uv("src/test/resources/envs/cowsay-pyproject.toml")
			.base(baseA).logDebug().build();
		File lockFileA = new File(baseA, "uv.lock");
		assertTrue(lockFileA.isFile(), "first build should generate a uv.lock");
		String lockContent = new String(Files.readAllBytes(lockFileA.toPath()), StandardCharsets.UTF_8);

		// .lockFile(File): lock copied in, install runs --frozen.
		String baseB = "target/envs/uv-lock-file";
		FilePaths.deleteRecursively(new File(baseB));
		Environment envB = Appose.uv("src/test/resources/envs/cowsay-pyproject.toml")
			.base(baseB).lockFile(lockFileA).logDebug().build();
		assertTrue(new File(baseB, "uv.lock").isFile(), "lock should be copied into the env dir");
		assertTrue(apposeJsonMap(new File(baseB)).containsKey("lockHash"),
			"appose.json should record lockHash when a lock is supplied");
		cowsayAndAssert(envB, "frozen");

		// .lockUrl(URL): same outcome via a file:// URL.
		String baseC = "target/envs/uv-lock-url";
		FilePaths.deleteRecursively(new File(baseC));
		Environment envC = Appose.uv("src/test/resources/envs/cowsay-pyproject.toml")
			.base(baseC).lockUrl(lockFileA.toURI().toURL()).logDebug().build();
		assertTrue(apposeJsonMap(new File(baseC)).containsKey("lockHash"));
		cowsayAndAssert(envC, "url-lock");
	}

	/**
	 * An empty/stale lock cannot satisfy the manifest, so --frozen must reject
	 * it. (Without --frozen, uv would regenerate the lock and succeed — so this
	 * failure proves the --frozen flag is actually wired in.)
	 */
	@Test
	public void testUvLockStaleFails() throws Exception {
		// A valid lock for the cowsay manifest...
		String baseA = "target/envs/uv-stale-src";
		FilePaths.deleteRecursively(new File(baseA));
		Appose.uv("src/test/resources/envs/cowsay-pyproject.toml")
			.base(baseA).logDebug().build();
		String cowsayLock = new String(Files.readAllBytes(
			new File(baseA, "uv.lock").toPath()), StandardCharsets.UTF_8);

		// ...does not satisfy a manifest that additionally requires `requests`.
		// With --frozen, uv must reject the stale lock. (Without --frozen, uv
		// would simply add requests and succeed — so this failure proves the
		// --frozen flag is actually wired in, not just that a corrupt lock fails.)
		String pyprojectExtra =
			"[project]\n" +
			"name = \"cowsay-extra\"\n" +
			"version = \"0.1.0\"\n" +
			"requires-python = \">=3.10\"\n" +
			"dependencies = [\"cowsay>=6.0\", \"appose>=0.1.0\", \"requests\"]\n";
		assertThrows(BuildException.class, () ->
			Appose.uv().content(pyprojectExtra)
				.base("target/envs/uv-lock-stale").lockContent(cowsayLock).logDebug().build());
	}

	/**
	 * Lock files only apply to the pyproject.toml / uv sync path; the
	 * requirements.txt path uses pip install and has no lockfile.
	 */
	@Test
	public void testUvLockUnsupportedScheme() {
		assertThrows(IllegalArgumentException.class, () ->
			Appose.uv("src/test/resources/envs/cowsay-requirements.txt")
				.base("target/envs/uv-lock-reqs").lockContent("bogus").build());
	}

	/**
	 * When no lock is supplied, appose.json must NOT contain a lockHash key, so
	 * the snapshot stays byte-identical to pre-lock-file builds (backward
	 * compatibility) and existing environments are never spuriously rebuilt.
	 */
	@Test
	public void testUvNoLockBackwardCompat() throws Exception {
		String base = "target/envs/uv-no-lock";
		FilePaths.deleteRecursively(new File(base));
		Environment env = Appose.uv("src/test/resources/envs/cowsay-pyproject.toml")
			.base(base).logDebug().build();
		assertFalse(apposeJsonMap(new File(base)).containsKey("lockHash"),
			"appose.json must NOT contain lockHash when no lock is supplied");
		cowsayAndAssert(env, "nolock");
	}

	/**
	 * Changing the lock content must change the lockHash in appose.json and thus
	 * force a rebuild. The lock is edited with a trailing TOML comment, which
	 * doesn't change the resolved package set, so --frozen still succeeds.
	 */
	@Test
	public void testUvLockChangeTriggersRebuild() throws Exception {
		String baseA = "target/envs/uv-lock-change-src";
		FilePaths.deleteRecursively(new File(baseA));
		Appose.uv("src/test/resources/envs/cowsay-pyproject.toml").base(baseA).logDebug().build();
		String lock = new String(Files.readAllBytes(new File(baseA, "uv.lock").toPath()), StandardCharsets.UTF_8);

		String base = "target/envs/uv-lock-change";
		FilePaths.deleteRecursively(new File(base));
		Appose.uv("src/test/resources/envs/cowsay-pyproject.toml")
			.base(base).lockContent(lock).logDebug().build();
		String hashBefore = (String) apposeJsonMap(new File(base)).get("lockHash");

		Appose.uv("src/test/resources/envs/cowsay-pyproject.toml")
			.base(base).lockContent(lock + "# trailing comment\n").logDebug().build();
		String hashAfter = (String) apposeJsonMap(new File(base)).get("lockHash");

		assertNotNull(hashBefore, "lockHash should be present after a locked build");
		assertNotNull(hashAfter, "lockHash should be present after rebuild");
		assertNotEquals(hashBefore, hashAfter,
			"a lock change must produce a different lockHash and force a rebuild");
	}

	/**
	 * wrap() captures the lock file into builder state, so rebuild() reproduces
	 * the locked environment even after its directory has been deleted.
	 */
	@Test
	public void testUvWrapLockSurvivesRebuild() throws Exception {
		// Build a locked environment (generate a valid lock first).
		String srcBase = "target/envs/uv-wrap-src";
		FilePaths.deleteRecursively(new File(srcBase));
		Appose.uv("src/test/resources/envs/cowsay-pyproject.toml").base(srcBase).logDebug().build();
		String lock = new String(Files.readAllBytes(new File(srcBase, "uv.lock").toPath()), StandardCharsets.UTF_8);

		String baseA = "target/envs/uv-wrap-locked";
		FilePaths.deleteRecursively(new File(baseA));
		Appose.uv("src/test/resources/envs/cowsay-pyproject.toml")
			.base(baseA).lockContent(lock).logDebug().build();
		assertTrue(apposeJsonMap(new File(baseA)).containsKey("lockHash"));

		// Wrap the locked env via the typed builder (captures pyproject.toml + uv.lock),
		// then wipe + rebuild. (Appose.wrap() auto-detection does not recognize uv envs,
		// so the typed UvBuilder.wrap() is the path that captures the lock.)
		Environment env = Appose.uv().wrap(new File(baseA));
		Environment rebuilt = env.rebuild();

		assertTrue(apposeJsonMap(new File(baseA)).containsKey("lockHash"),
			"rebuild after wrap must reproduce lockHash from the captured lock");
		cowsayAndAssert(rebuilt, "rewrapped");
	}
}
