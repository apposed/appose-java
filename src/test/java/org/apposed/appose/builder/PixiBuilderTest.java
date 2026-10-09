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
import org.apposed.appose.Environment;
import org.apposed.appose.TestBase;
import org.apposed.appose.util.FilePaths;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
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

	/**
	 * Tests that a pixi environment launches correctly when the calling process
	 * is itself running inside an activated pixi environment of another project.
	 */
	@Test
	public void testPixiInheritedShellEnv() throws Exception {
		Environment env = Appose
			.pixi("src/test/resources/envs/cowsay-pixi.toml")
			.base("target/envs/pixi-cowsay-shell")
			.env("PIXI_IN_SHELL", "1")
			.env("PIXI_ENVIRONMENT_NAME", "nonexistent")
			.logDebug()
			.build();
		List<String> launchArgs = env.launchArgs();
		assertEquals(Arrays.asList("--environment", "default"),
			launchArgs.subList(launchArgs.size() - 2, launchArgs.size()));
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
	public void testPixiVacuousKeepsExistingEnv() throws Exception {
		File base = new File("target/envs/pixi-vacuous-existing");
		FilePaths.deleteRecursively(base);
		File marker = new File(base, ".pixi/envs/default/conda-meta/history");
		assertTrue(marker.getParentFile().mkdirs());
		assertTrue(marker.createNewFile());
		// With nothing to build from, the existing env must be used as-is, not wiped.
		Environment env = Appose.pixi().base(base).logDebug().build();
		assertEquals(base.getAbsolutePath(), env.base());
		assertTrue(marker.exists());
	}

	/** Tests that building without appose adds a compatible appose. */
	@Test
	public void testPixiApposeRequirement() throws Exception {
		File base = new File("target/envs/pixi-appose-requirement");
		FilePaths.deleteRecursively(base);
		Environment env = Appose
			.pixi()
			.conda("python")
			.pypi("cowsay==6.1")
			.base(base)
			.logDebug()
			.build();
		String pixiToml = new String(Files.readAllBytes(new File(base, "pixi.toml").toPath()),
			StandardCharsets.UTF_8);
		assertTrue(pixiToml.contains("appose"), pixiToml);
		cowsayAndAssert(env, "auto", true);
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
	 * --locked, yielding a reproducible, working environment. Exercises both the
	 * {@code .lockFile(File)} and {@code .lockUrl(URL)} entry points.
	 */
	@Test
	public void testPixiLocked() throws Exception {
		// Build without a lock first to generate a valid pixi.lock.
		String baseA = "target/envs/pixi-lock-src";
		FilePaths.deleteRecursively(new File(baseA));
		Appose.pixi("src/test/resources/envs/cowsay-pixi.toml")
			.base(baseA).logDebug().build();
		File lockFileA = new File(baseA, "pixi.lock");
		assertTrue(lockFileA.isFile(), "first build should generate a pixi.lock");
		String lockContent = FilePaths.readText(lockFileA);

		// .lockFile(File): lock copied in, install runs --locked.
		String baseB = "target/envs/pixi-lock-file";
		FilePaths.deleteRecursively(new File(baseB));
		Environment envB = Appose.pixi("src/test/resources/envs/cowsay-pixi.toml")
			.base(baseB).lockFile(lockFileA).logDebug().build();
		assertTrue(new File(baseB, "pixi.lock").isFile(), "lock should be copied into the env dir");
		assertTrue(apposeJsonMap(new File(baseB)).containsKey("lockHash"),
			"appose.json should record lockHash when a lock is supplied");
		cowsayAndAssert(envB, "locked");

		// .lockUrl(URL): same outcome via a file:// URL.
		String baseC = "target/envs/pixi-lock-url";
		FilePaths.deleteRecursively(new File(baseC));
		Environment envC = Appose.pixi("src/test/resources/envs/cowsay-pixi.toml")
			.base(baseC).lockUrl(lockFileA.toURI().toURL()).logDebug().build();
		assertTrue(apposeJsonMap(new File(baseC)).containsKey("lockHash"));
		cowsayAndAssert(envC, "url-lock");
	}

	/**
	 * A lock that is out of date with the manifest must be rejected by
	 * --locked. (Without --locked, pixi would update the lock and succeed.)
	 */
	@Test
	public void testPixiLockStaleFails() throws Exception {
		// A valid lock for the cowsay manifest...
		String baseA = "target/envs/pixi-stale-src";
		FilePaths.deleteRecursively(new File(baseA));
		Appose.pixi("src/test/resources/envs/cowsay-pixi.toml")
			.base(baseA).logDebug().build();
		String cowsayLock = FilePaths.readText(new File(baseA, "pixi.lock"));

		// ...is stale for the same workspace additionally requiring `requests`.
		String pixiExtra = FilePaths.readText(new File("src/test/resources/envs/cowsay-pixi.toml")) +
			"requests = \"*\"\n";
		String base = "target/envs/pixi-lock-stale";
		FilePaths.deleteRecursively(new File(base));
		assertThrows(BuildException.class, () ->
			Appose.pixi().content(pixiExtra).base(base).lockContent(cowsayLock).logDebug().build());
	}

	/**
	 * Adding channels would re-resolve the manifest, rewriting the lock,
	 * so programmatic channels cannot be combined with a lock.
	 */
	@Test
	public void testPixiLockWithChannelsUnsupported() {
		assertThrows(IllegalArgumentException.class, () ->
			Appose.pixi("src/test/resources/envs/cowsay-pixi.toml")
				.channels("bioconda")
				.base("target/envs/pixi-lock-channels")
				.lockContent("bogus")
				.build());
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
	 * change the resolved package set, so --locked still succeeds.
	 */
	@Test
	public void testPixiLockChangeTriggersRebuild() throws Exception {
		String baseA = "target/envs/pixi-lock-change-src";
		FilePaths.deleteRecursively(new File(baseA));
		Appose.pixi("src/test/resources/envs/cowsay-pixi.toml").base(baseA).logDebug().build();
		String lock = FilePaths.readText(new File(baseA, "pixi.lock"));

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
		String lock = FilePaths.readText(new File(srcBase, "pixi.lock"));

		String baseA = "target/envs/pixi-wrap-locked";
		FilePaths.deleteRecursively(new File(baseA));
		Appose.pixi("src/test/resources/envs/cowsay-pixi.toml")
			.base(baseA).lockContent(lock).logDebug().build();
		assertTrue(apposeJsonMap(new File(baseA)).containsKey("lockHash"));

		// Wrap the locked env (capturing pixi.toml + pixi.lock), then wipe + rebuild.
		Environment env = Appose.wrap(new File(baseA));
		assertInstanceOf(PixiBuilder.class, env.builder());
		assertEquals(EnvStatus.CURRENT, env.builder().status());
		Environment rebuilt = env.rebuild();

		assertTrue(apposeJsonMap(new File(baseA)).containsKey("lockHash"),
			"rebuild after wrap must reproduce lockHash from the captured lock");
		cowsayAndAssert(rebuilt, "rewrapped");
	}

	/**
	 * pixi install writes a pixi.lock even when no lock is supplied. Wrapping
	 * such an environment must not adopt that lock, or the env would look
	 * stale, and rebuild() would install from a lock never asked for.
	 */
	@Test
	public void testPixiWrapIgnoresUnrequestedLock() throws Exception {
		String base = "target/envs/pixi-wrap-unlocked";
		FilePaths.deleteRecursively(new File(base));
		Appose.pixi("src/test/resources/envs/cowsay-pixi.toml")
			.base(base).logDebug().build();
		assertTrue(new File(base, "pixi.lock").isFile(), "pixi install should generate a pixi.lock");

		Environment env = Appose.wrap(new File(base));
		assertEquals(EnvStatus.CURRENT, env.builder().status());
		Environment rebuilt = env.rebuild();
		assertFalse(apposeJsonMap(new File(base)).containsKey("lockHash"),
			"rebuild after wrap must not lock to the generated pixi.lock");
		cowsayAndAssert(rebuilt, "unlocked");
	}
}
