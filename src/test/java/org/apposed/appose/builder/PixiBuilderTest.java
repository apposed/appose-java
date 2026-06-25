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
import org.apposed.appose.util.FilePaths;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
			"[workspace]\n" +
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
			"[workspace]\n" +
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

	/** Tests that {@code env.activate()} launches a service in a non-default pixi environment. */
	@Test
	public void testPixiActivate() throws Exception {
		Environment env = Appose
			.pixi("src/test/resources/envs/cowsay-multi-env.toml")
			.base("target/envs/pixi-multi-env")
			.logDebug()
			.build();
		assertInstanceOf(PixiBuilder.class, env.builder());
		Environment altEnv = env.activate("alt");
		// Verify launch args include --environment alt.
		List<String> launchArgs = altEnv.launchArgs();
		int idx = launchArgs.indexOf("--environment");
		assertTrue(idx >= 0, "launchArgs should contain --environment");
		assertEquals("alt", launchArgs.get(idx + 1));
		// Verify bin path resolves to the alt environment directory.
		assertTrue(altEnv.binPaths().get(0).contains(File.separator + "alt" + File.separator),
			"binPaths should reference the alt environment");
		cowsayAndAssert(altEnv, "multi-env");
	}

	/**
	 * Tests that {@link PixiBuilder#build()} fully installs the pixi environment,
	 * i.e. that {@code .pixi/envs/default} exists after {@code build()} returns,
	 * not only after the first {@code pixi run} invocation.
	 */
	@Test
	public void testBuildInstallsEnv() throws Exception {
		Environment env = Appose
			.pixi("src/test/resources/envs/cowsay-pixi.toml")
			.base("target/envs/pixi-build-installs-env")
			.logDebug()
			.rebuild();
		// The default pixi environment directory must exist right after build(),
		// before any service is launched. This was the bug: build() used to only
		// write pixi.toml, leaving installation to the first pixi run invocation.
		File envDir = new File(env.base(), ".pixi/envs/default");
		assertTrue(envDir.isDirectory(),
			".pixi/envs/default should exist after build(), but was missing: " + envDir);
		cowsayAndAssert(env, "installed");
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

	// -- Lock-file reproducible builds --

	/**
	 * A user-supplied lock is copied into the env dir and the install runs with
	 * --frozen, yielding a reproducible, working environment. Exercises both the
	 * {@code .lockFile(File)} and {@code .lockUrl(URL)} entry points.
	 */
	@Test
	public void testPixiLockFrozen() throws Exception {
		// Build without a lock first to generate a valid pixi.lock.
		String baseA = "target/envs/pixi-lock-src";
		FilePaths.deleteRecursively(new File(baseA));
		Appose.pixi("src/test/resources/envs/cowsay-pixi.toml")
			.base(baseA).logDebug().build();
		File lockFileA = new File(baseA, "pixi.lock");
		assertTrue(lockFileA.isFile(), "first build should generate a pixi.lock");
		String lockContent = new String(Files.readAllBytes(lockFileA.toPath()), StandardCharsets.UTF_8);

		// .lockFile(File): lock copied in, install runs --frozen.
		String baseB = "target/envs/pixi-lock-file";
		FilePaths.deleteRecursively(new File(baseB));
		Environment envB = Appose.pixi("src/test/resources/envs/cowsay-pixi.toml")
			.base(baseB).lockFile(lockFileA).logDebug().build();
		assertTrue(new File(baseB, "pixi.lock").isFile(), "lock should be copied into the env dir");
		assertTrue(apposeJsonMap(new File(baseB)).containsKey("lockHash"),
			"appose.json should record lockHash when a lock is supplied");
		cowsayAndAssert(envB, "frozen");

		// .lockUrl(URL): same outcome via a file:// URL.
		String baseC = "target/envs/pixi-lock-url";
		FilePaths.deleteRecursively(new File(baseC));
		Environment envC = Appose.pixi("src/test/resources/envs/cowsay-pixi.toml")
			.base(baseC).lockUrl(lockFileA.toURI().toURL()).logDebug().build();
		assertTrue(apposeJsonMap(new File(baseC)).containsKey("lockHash"));
		cowsayAndAssert(envC, "url-lock");
	}

	/**
	 * With --frozen, pixi installs the environment exactly as defined in
	 * pixi.lock and does NOT re-resolve or update it. Building a manifest that
	 * drifted from the lock (it additionally requires `requests`) must therefore
	 * leave the lock unchanged (no `requests` resolved in). Without --frozen,
	 * pixi would update the lock to include `requests` -- so an unchanged lock
	 * proves the --frozen flag is wired in and the build is reproducible.
	 */
	@Test
	public void testPixiLockFrozenUsesLockAsIs() throws Exception {
		// A valid lock for the cowsay manifest (contains no `requests`).
		String baseA = "target/envs/pixi-frozen-src";
		FilePaths.deleteRecursively(new File(baseA));
		Appose.pixi("src/test/resources/envs/cowsay-pixi.toml")
			.base(baseA).logDebug().build();
		String cowsayLock = new String(Files.readAllBytes(
			new File(baseA, "pixi.lock").toPath()), StandardCharsets.UTF_8);
		assertFalse(cowsayLock.contains("requests"), "baseline lock should not contain requests");

		// A manifest that additionally requires `requests`, built with the cowsay lock.
		String pixiExtra =
			"[workspace]\n" +
			"name = \"cowsay-extra\"\n" +
			"channels = [\"conda-forge\"]\n" +
			"platforms = [\"linux-64\", \"osx-64\", \"osx-arm64\", \"win-64\"]\n" +
			"\n" +
			"[dependencies]\n" +
			"python = \">=3.8\"\n" +
			"pip = \"*\"\n" +
			"\n" +
			"[pypi-dependencies]\n" +
			"cowsay = \"==6.1\"\n" +
			"appose = \"*\"\n" +
			"requests = \"*\"\n";
		String base = "target/envs/pixi-frozen";
		FilePaths.deleteRecursively(new File(base));
		Appose.pixi().content(pixiExtra).base(base).lockContent(cowsayLock).logDebug().build();

		// --frozen must use the lock AS-IS: `requests` must NOT have been resolved in.
		String lockAfter = new String(Files.readAllBytes(
			new File(base, "pixi.lock").toPath()), StandardCharsets.UTF_8);
		assertFalse(lockAfter.contains("requests"),
			"--frozen must use the lock as-is, not re-resolve `requests` into it");
	}

	/**
	 * Programmatic builds (no manifest) cannot be locked.
	 */
	@Test
	public void testPixiLockProgrammaticUnsupported() {
		assertThrows(IllegalArgumentException.class, () ->
			Appose.pixi()
				.conda("python>=3.8")
				.pypi("cowsay==6.1")
				.base("target/envs/pixi-lock-prog")
				.lockContent("bogus")
				.build());
	}

	/**
	 * When no lock is supplied, appose.json must NOT contain a lockHash key, so
	 * the snapshot stays byte-identical to pre-lock-file builds (backward
	 * compatibility) and existing environments are never spuriously rebuilt.
	 */
	@Test
	public void testPixiNoLockBackwardCompat() throws Exception {
		String base = "target/envs/pixi-no-lock";
		FilePaths.deleteRecursively(new File(base));
		Environment env = Appose.pixi("src/test/resources/envs/cowsay-pixi.toml")
			.base(base).logDebug().build();
		assertFalse(apposeJsonMap(new File(base)).containsKey("lockHash"),
			"appose.json must NOT contain lockHash when no lock is supplied");
		cowsayAndAssert(env, "nolock");
	}

	/**
	 * Changing the lock content must change the lockHash in appose.json and thus
	 * force a rebuild. The lock is edited with a trailing comment, which doesn't
	 * change the resolved package set, so --frozen still succeeds.
	 */
	@Test
	public void testPixiLockChangeTriggersRebuild() throws Exception {
		String baseA = "target/envs/pixi-lock-change-src";
		FilePaths.deleteRecursively(new File(baseA));
		Appose.pixi("src/test/resources/envs/cowsay-pixi.toml").base(baseA).logDebug().build();
		String lock = new String(Files.readAllBytes(new File(baseA, "pixi.lock").toPath()), StandardCharsets.UTF_8);

		String base = "target/envs/pixi-lock-change";
		FilePaths.deleteRecursively(new File(base));
		Appose.pixi("src/test/resources/envs/cowsay-pixi.toml")
			.base(base).lockContent(lock).logDebug().build();
		String hashBefore = (String) apposeJsonMap(new File(base)).get("lockHash");

		Appose.pixi("src/test/resources/envs/cowsay-pixi.toml")
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
	public void testPixiWrapLockSurvivesRebuild() throws Exception {
		// Build a locked environment (generate a valid lock first).
		String srcBase = "target/envs/pixi-wrap-src";
		FilePaths.deleteRecursively(new File(srcBase));
		Appose.pixi("src/test/resources/envs/cowsay-pixi.toml").base(srcBase).logDebug().build();
		String lock = new String(Files.readAllBytes(new File(srcBase, "pixi.lock").toPath()), StandardCharsets.UTF_8);

		String baseA = "target/envs/pixi-wrap-locked";
		FilePaths.deleteRecursively(new File(baseA));
		Appose.pixi("src/test/resources/envs/cowsay-pixi.toml")
			.base(baseA).lockContent(lock).logDebug().build();
		assertTrue(apposeJsonMap(new File(baseA)).containsKey("lockHash"));

		// Wrap the locked env via the typed builder (captures pixi.toml + pixi.lock),
		// then wipe + rebuild.
		Environment env = Appose.pixi().wrap(new File(baseA));
		Environment rebuilt = env.rebuild();

		assertTrue(apposeJsonMap(new File(baseA)).containsKey("lockHash"),
			"rebuild after wrap must reproduce lockHash from the captured lock");
		cowsayAndAssert(rebuilt, "rewrapped");
	}
}
