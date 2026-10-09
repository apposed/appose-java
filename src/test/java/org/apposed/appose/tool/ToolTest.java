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

package org.apposed.appose.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests {@link Tool} self-update logic.
 *
 * @author Curtis Rueden
 */
public class ToolTest {

	/** Tool whose version and release downloads are simulated. */
	private static class FakeTool extends Tool {
		String installed;
		String latest;
		String minVersion = "1.0.0";
		final List<String> downloads = new ArrayList<>();
		final List<String> errors = new ArrayList<>();

		FakeTool(Path rootdir, String installed, String latest) throws IOException {
			super("fake", null, rootdir.resolve("bin").resolve("fake").toString(), rootdir.toString());
			Files.createDirectories(Paths.get(command).getParent());
			this.installed = installed;
			this.latest = latest;
			setErrorConsumer(errors::add);
		}

		@Override
		public String version() {
			return installed;
		}

		@Override
		protected String minVersion() {
			return minVersion;
		}

		@Override
		protected String autoUpdateVar() {
			return "APPOSE_FAKE_AUTO_UPDATE";
		}

		@Override
		protected String latestVersion() throws IOException {
			if (latest == null) throw new IOException("offline");
			return latest;
		}

		@Override
		protected String downloadURL(String version) {
			return "https://example.com/fake/" + version;
		}

		@Override
		protected File download(String url) {
			String version = url.substring(url.lastIndexOf('/') + 1);
			downloads.add(version);
			return new File(version);
		}

		@Override
		protected void decompress(File archive) {
			installed = archive.getName();
		}
	}

	@Test
	public void testCompareVersions() {
		assertEquals(0, Tool.compareVersions("v0.81.0", "0.81.0"));
		assertTrue(Tool.compareVersions("0.58.0", "v0.81.0") < 0);
		assertTrue(Tool.compareVersions("0.9.0", "0.10.0") < 0);
		assertTrue(Tool.compareVersions("1.0.0", "0.99.99") > 0);
	}

	@Test
	public void testUpdateToLatest(@TempDir Path tmp) throws Exception {
		assumeAutoUpdateEnabled();
		FakeTool tool = new FakeTool(tmp, "98.0.0", "99.0.0");
		tool.selfUpdate();
		assertEquals(Collections.singletonList("99.0.0"), tool.downloads);
		assertEquals("99.0.0", tool.installed);
	}

	@Test
	public void testUpdateAlreadyLatest(@TempDir Path tmp) throws Exception {
		FakeTool tool = new FakeTool(tmp, "99.0.0", "99.0.0");
		tool.selfUpdate();
		assertEquals(Collections.emptyList(), tool.downloads);
	}

	@Test
	public void testUpdateThrottled(@TempDir Path tmp) throws Exception {
		assumeAutoUpdateEnabled();
		FakeTool tool = new FakeTool(tmp, "98.0.0", "99.0.0");
		tool.selfUpdate();
		tool.latest = "99.1.0";
		tool.selfUpdate();
		assertEquals(Collections.singletonList("99.0.0"), tool.downloads);

		// Once the interval elapses, check again.
		Path stamp = Paths.get(tool.command).resolveSibling("last-update-check");
		long old = System.currentTimeMillis() - Tool.UPDATE_INTERVAL - 1000;
		Files.setLastModifiedTime(stamp, FileTime.fromMillis(old));
		tool.selfUpdate();
		assertEquals(Arrays.asList("99.0.0", "99.1.0"), tool.downloads);
	}

	@Test
	public void testUpdateFailureFallsBackToMinimum(@TempDir Path tmp) throws Exception {
		assumeAutoUpdateEnabled();
		// Simulate failure (e.g. offline) when checking for the latest release.
		FakeTool tool = new FakeTool(tmp, "0.1.0", null);
		tool.selfUpdate();
		assertEquals(Collections.singletonList("1.0.0"), tool.downloads);
		assertTrue(tool.errors.stream().anyMatch(e -> e.contains("could not update fake")));
	}

	@Test
	public void testUpdateWithoutMinimum(@TempDir Path tmp) throws Exception {
		FakeTool tool = new FakeTool(tmp, "0.1.0", null);
		tool.minVersion = null;
		tool.selfUpdate();
		assertEquals(Collections.emptyList(), tool.downloads);
	}

	@Test
	public void testAutoUpdateVars(@TempDir Path tmp) throws Exception {
		FakeTool tool = new FakeTool(tmp, "98.0.0", "99.0.0");
		String blanket = Tool.TOOL_AUTO_UPDATE_VAR, specific = tool.autoUpdateVar();
		assertTrue(tool.autoUpdateEnabled(env(null, null)));
		assertFalse(tool.autoUpdateEnabled(env(blanket, "false")));
		assertTrue(tool.autoUpdateEnabled(env(blanket, "true")));
		assertTrue(tool.autoUpdateEnabled(env(blanket, "false", specific, "true")));
		assertFalse(tool.autoUpdateEnabled(env(blanket, "true", specific, "off")));
		assertFalse(tool.autoUpdateEnabled(env(blanket, "no", specific, "")));
		assertFalse(tool.autoUpdateEnabled(env(specific, "0")));
	}

	@Test
	public void testToolUpdateVars() {
		assertEquals("APPOSE_PIXI_AUTO_UPDATE", new Pixi().autoUpdateVar());
		assertEquals("APPOSE_UV_AUTO_UPDATE", new Uv().autoUpdateVar());
		assertEquals("APPOSE_MAMBA_AUTO_UPDATE", new Mamba().autoUpdateVar());
	}

	@Test
	public void testPixiUpgradeUsesSelfUpdate(@TempDir Path tmp) throws Exception {
		List<List<String>> calls = new ArrayList<>();
		Pixi pixi = new Pixi(tmp.toString()) {
			@Override
			protected void doExec(File cwd, boolean silent, boolean includeFlags, String... args) {
				calls.add(Arrays.asList(args));
			}
		};
		pixi.upgrade(null);
		pixi.upgrade("v0.81.0");
		assertEquals(Arrays.asList(
			Arrays.asList("self-update", "--no-release-note"),
			Arrays.asList("self-update", "--no-release-note", "--version", "0.81.0")
		), calls);
	}

	private static Function<String, String> env(String... keyValues) {
		Map<String, String> map = new HashMap<>();
		for (int i = 0; i + 1 < keyValues.length; i += 2) {
			if (keyValues[i] != null) map.put(keyValues[i], keyValues[i + 1]);
		}
		return map::get;
	}

	private static void assumeAutoUpdateEnabled() {
		assumeTrue(System.getenv("APPOSE_FAKE_AUTO_UPDATE") == null);
		assumeTrue(System.getenv(Tool.TOOL_AUTO_UPDATE_VAR) == null);
	}
}
