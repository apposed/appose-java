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
import org.apposed.appose.CheckResult;
import org.apposed.appose.Environment;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the environment staleness checking API:
 * {@link org.apposed.appose.Builder#isUpToDate()},
 * {@link org.apposed.appose.Builder#checkUpToDate()},
 * {@link org.apposed.appose.Environment#isUpToDate()},
 * {@link org.apposed.appose.Environment#checkUpToDate()},
 * and {@link CheckResult}.
 */
public class CheckResultTest {

	// -- CheckResult factory tests --

	@Test
	public void testCheckResultsUpToDateVerified() {
		CheckResult result = upToDateResult(true);
		assertTrue(result.isUpToDate());
		assertTrue(result.verified());
		assertNotNull(result.description());
	}

	@Test
	public void testCheckResultsUpToDateNotVerified() {
		CheckResult result = upToDateResult(false);
		assertTrue(result.isUpToDate());
		assertFalse(result.verified());
	}

	@Test
	public void testCheckResultsStaleVerified() {
		CheckResult result = staleResult(true);
		assertFalse(result.isUpToDate());
		assertTrue(result.verified());
	}

	@Test
	public void testCheckResultsStaleNotVerified() {
		CheckResult result = staleResult(false);
		assertFalse(result.isUpToDate());
		assertFalse(result.verified());
	}

	// -- SimpleBuilder tests --

	@Test
	public void testSimpleBuilderAlwaysUpToDate() throws Exception {
		SimpleBuilder builder = new SimpleBuilder();
		assertTrue(builder.isUpToDate());
	}

	@Test
	public void testSimpleBuilderCheckUpToDate() throws Exception {
		SimpleBuilder builder = new SimpleBuilder();
		CheckResult result = builder.checkUpToDate();
		assertTrue(result.isUpToDate());
		assertFalse(result.verified());
	}

	@Test
	public void testSimpleEnvironmentIsUpToDate() throws Exception {
		Environment env = Appose.custom().build();
		assertTrue(env.isUpToDate());
	}

	@Test
	public void testSimpleEnvironmentCheckUpToDate() throws Exception {
		Environment env = Appose.custom().build();
		CheckResult result = env.checkUpToDate();
		assertTrue(result.isUpToDate());
		assertFalse(result.verified());
	}

	// -- BaseBuilder isUpToDate tests --

	@Test
	public void testUvBuilderNotUpToDateWhenNoEnvDir() throws Exception {
		UvBuilder builder = new UvBuilder();
		builder.name("nonexistent-check-result-test");
		assertFalse(builder.isUpToDate());
	}

	@Test
	public void testPixiBuilderNotUpToDateWhenNoEnvDir() throws Exception {
		PixiBuilder builder = new PixiBuilder();
		builder.name("nonexistent-check-result-test");
		assertFalse(builder.isUpToDate());
	}

	@Test
	public void testUvBuilderNotUpToDateWhenNoApposeJson() throws Exception {
		File tempDir = new File("target/check-result-test-no-json");
		tempDir.mkdirs();
		UvBuilder builder = new UvBuilder();
		builder.base(tempDir);
		// Directory exists but no appose.json.
		assertFalse(builder.isUpToDate());
	}

	@Test
	public void testUvBuilderUpToDateAfterBuild() throws Exception {
		File tempDir = new File("target/check-result-test-up-to-date");
		cleanDir(tempDir);

		String requirements = "appose\n";

		UvBuilder builder = new UvBuilder();
		builder.base(tempDir);
		builder.content(requirements);
		builder.scheme("requirements.txt");

		// Before build, not up to date.
		assertFalse(builder.isUpToDate());

		// Build the environment.
		Environment env = builder.build();

		// After build, the same builder config should be up to date.
		assertTrue(builder.isUpToDate());

		// Environment convenience method should also work.
		assertTrue(env.isUpToDate());

		// Clean up.
		env.delete();
	}

	@Test
	public void testUvBuilderStaleAfterConfigChange() throws Exception {
		File tempDir = new File("target/check-result-test-stale");
		cleanDir(tempDir);

		String requirements = "appose\n";

		UvBuilder builder = new UvBuilder();
		builder.base(tempDir);
		builder.content(requirements);
		builder.scheme("requirements.txt");
		builder.build();

		// Up to date with current config.
		assertTrue(builder.isUpToDate());

		// Change config.
		builder.content("appose\nnumpy\n");

		// Now stale.
		assertFalse(builder.isUpToDate());

		// Clean up.
		builder.delete();
	}

	@Test
	public void testUvBuilderCheckUpToDateVerified() throws Exception {
		File tempDir = new File("target/check-result-test-verified");
		cleanDir(tempDir);

		String pyproject = "[project]\nname = \"check-test\"\nversion = \"0.1.0\"\ndependencies = [\"appose\"]\n";

		UvBuilder builder = new UvBuilder();
		builder.base(tempDir);
		builder.content(pyproject);
		builder.scheme("pyproject.toml");
		Environment env = builder.build();

		CheckResult result = builder.checkUpToDate();
		assertTrue(result.isUpToDate());
		assertTrue(result.verified(), "pyproject.toml environments should have verified=true");

		// Environment convenience method.
		CheckResult envResult = env.checkUpToDate();
		assertTrue(envResult.isUpToDate());

		// Clean up.
		env.delete();
	}

	@Test
	public void testUvBuilderCheckUpToDateNotVerifiedForRequirementsTxt() throws Exception {
		File tempDir = new File("target/check-result-test-not-verified");
		cleanDir(tempDir);

		String requirements = "appose\n";

		UvBuilder builder = new UvBuilder();
		builder.base(tempDir);
		builder.content(requirements);
		builder.scheme("requirements.txt");
		Environment env = builder.build();

		CheckResult result = builder.checkUpToDate();
		assertTrue(result.isUpToDate());
		assertFalse(result.verified(), "requirements.txt environments should have verified=false");

		// Clean up.
		env.delete();
	}

	@Test
	public void testCheckUpToDateStaleWhenNoEnv() throws Exception {
		File tempDir = new File("target/check-result-test-no-env");
		cleanDir(tempDir);

		UvBuilder builder = new UvBuilder();
		builder.base(tempDir);

		CheckResult result = builder.checkUpToDate();
		assertFalse(result.isUpToDate());
		assertFalse(result.verified());
	}

	// -- DynamicBuilder delegation tests --

	@Test
	public void testDynamicBuilderDelegatesIsUpToDate() throws Exception {
		// DynamicBuilder with SimpleBuilder via system.
		Environment env = Appose.system();
		assertTrue(env.isUpToDate());
	}

	// -- Helper methods --

	/** Creates an up-to-date CheckResult for testing. */
	private static CheckResult upToDateResult(final boolean verified) {
		return CheckResult.upToDate("test up-to-date", verified);
	}

	/** Creates a stale CheckResult for testing. */
	private static CheckResult staleResult(final boolean verified) {
		return CheckResult.stale("test stale", verified);
	}

	private static void cleanDir(File dir) throws IOException {
		if (dir.exists()) {
			String[] entries = dir.list();
			if (entries != null) {
				for (String entry : entries) {
					deleteRecursively(new File(dir, entry));
				}
			}
		}
		dir.mkdirs();
	}

	private static void deleteRecursively(File file) throws IOException {
		if (file.isDirectory()) {
			File[] children = file.listFiles();
			if (children != null) {
				for (File child : children) {
					deleteRecursively(child);
				}
			}
		}
		Files.deleteIfExists(file.toPath());
	}
}
