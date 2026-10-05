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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests {@link Pixi} self-update logic.
 *
 * @author Curtis Rueden
 */
public class PixiTest {

	private static final String MIN = Pixi.MIN_VERSION.replaceFirst("^v", "");

	/** Pixi whose version and self-update calls are simulated. */
	private static class FakePixi extends Pixi {
		String installed;
		final String latest;
		final List<List<String>> calls = new ArrayList<>();

		FakePixi(Path rootdir, String installed, String latest) throws IOException {
			super(rootdir.toString());
			Files.createDirectories(Paths.get(command).getParent());
			this.installed = installed;
			this.latest = latest;
		}

		@Override
		public String version() {
			return installed;
		}

		@Override
		protected void selfUpdate(String... args) {
			calls.add(Arrays.asList(args));
			installed = args.length > 0 ? args[1] : latest;
		}
	}

	@Test
	public void testCompareVersions() {
		assertEquals(0, Pixi.compareVersions("v0.81.0", "0.81.0"));
		assertTrue(Pixi.compareVersions("0.58.0", "v0.81.0") < 0);
		assertTrue(Pixi.compareVersions("0.9.0", "0.10.0") < 0);
		assertTrue(Pixi.compareVersions("1.0.0", "0.99.99") > 0);
	}

	@Test
	public void testUpdateToLatest(@TempDir Path tmp) throws Exception {
		assumeAutoUpdateEnabled();
		FakePixi pixi = new FakePixi(tmp, "98.0.0", "99.0.0");
		pixi.update();
		assertEquals(Arrays.asList(Arrays.asList()), pixi.calls);
		assertEquals("99.0.0", pixi.installed);
	}

	@Test
	public void testUpdateThrottled(@TempDir Path tmp) throws Exception {
		assumeAutoUpdateEnabled();
		FakePixi pixi = new FakePixi(tmp, "98.0.0", "99.0.0");
		pixi.update();
		pixi.update();
		assertEquals(1, pixi.calls.size());

		// Once the interval elapses, check again.
		Path stamp = Paths.get(pixi.command).resolveSibling("last-update-check");
		long old = System.currentTimeMillis() - Pixi.UPDATE_INTERVAL - 1000;
		Files.setLastModifiedTime(stamp, FileTime.fromMillis(old));
		pixi.update();
		assertEquals(2, pixi.calls.size());
	}

	@Test
	public void testUpdateFailureFallsBackToMinimum(@TempDir Path tmp) throws Exception {
		assumeAutoUpdateEnabled();
		// Simulate failure (e.g. offline) when checking for the latest release.
		FakePixi pixi = new FakePixi(tmp, "0.1.0", "0.1.0");
		pixi.update();
		assertEquals(Arrays.asList(Arrays.asList(), Arrays.asList("--version", MIN)), pixi.calls);
	}

	private static void assumeAutoUpdateEnabled() {
		assumeTrue(System.getenv(Pixi.AUTO_UPDATE_VAR) == null);
	}
}
