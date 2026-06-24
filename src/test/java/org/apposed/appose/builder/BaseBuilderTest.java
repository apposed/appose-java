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

import java.io.File;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BaseBuilder} helpers. These are fast and require no
 * external tools or network access.
 */
public class BaseBuilderTest {

	/** Same lock content must produce an identical hash. */
	@Test
	public void testComputeLockHashDeterministic() {
		String content = "version = 1\npackages = []\n";
		assertEquals(BaseBuilder.computeLockHash(content), BaseBuilder.computeLockHash(content),
			"identical content must hash identically");
	}

	/** Different lock content must produce different hashes. */
	@Test
	public void testComputeLockHashContentSensitive() {
		String a = BaseBuilder.computeLockHash("packages = [\"a\"]\n");
		String b = BaseBuilder.computeLockHash("packages = [\"b\"]\n");
		assertNotEquals(a, b, "different content must hash differently");
	}

	/** Null content must hash to null (so no lockHash key is emitted). */
	@Test
	public void testComputeLockHashNull() {
		assertNull(BaseBuilder.computeLockHash(null));
	}

	/** The hash must be a 64-character lowercase hex string (SHA-256). */
	@Test
	public void testComputeLockHashFormat() {
		String hash = BaseBuilder.computeLockHash("appose");
		assertEquals(64, hash.length(), "SHA-256 hex must be 64 chars");
		assertTrue(hash.matches("[0-9a-f]{64}"), "hash must be lowercase hex: " + hash);
	}

	/** Lock files are opt-in: conda/custom builders that cannot honor them reject them early. */
	@Test
	public void testMambaRejectsLock() {
		org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
			() -> new MambaBuilder().lockContent("anything"));
	}

	/** Lock files are opt-in: conda/custom builders that cannot honor them reject them early. */
	@Test
	public void testSimpleRejectsLock() {
		org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
			() -> new SimpleBuilder().lockContent("anything"));
	}

	/** A missing lock file surfaces as a BuildException (not a raw IOException). */
	@Test
	public void testLockFileMissingThrowsBuildException() {
		org.junit.jupiter.api.Assertions.assertThrows(org.apposed.appose.BuildException.class,
			() -> new UvBuilder().lockFile(new File("this-lock-does-not-exist.lock")));
	}

	/** A malformed lock URL surfaces as a BuildException (not a raw MalformedURLException). */
	@Test
	public void testLockUrlMalformedThrowsBuildException() {
		org.junit.jupiter.api.Assertions.assertThrows(org.apposed.appose.BuildException.class,
			() -> new UvBuilder().lockUrl("ht!tp://not a valid url"));
	}
}
