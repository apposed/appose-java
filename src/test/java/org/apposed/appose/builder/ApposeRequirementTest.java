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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/** Tests {@link ApposeRequirement}. */
public class ApposeRequirementTest {

	private String savedOverride;

	@BeforeEach
	public void saveOverride() {
		// NB: The test suite may set the override, e.g. to a sibling checkout.
		savedOverride = System.getProperty(ApposeRequirement.PROPERTY);
		System.clearProperty(ApposeRequirement.PROPERTY);
	}

	@AfterEach
	public void restoreOverride() {
		if (savedOverride == null) System.clearProperty(ApposeRequirement.PROPERTY);
		else System.setProperty(ApposeRequirement.PROPERTY, savedOverride);
	}

	@Test
	public void testRelease() {
		// NB: Only meaningful when the environment variable override is unset.
		if (System.getenv(ApposeRequirement.ENV_VAR) != null) return;
		assertEquals(new ApposeRequirement("appose>=1.1,<1.2", false),
			ApposeRequirement.forVersion("1.1.2"));
		assertEquals(new ApposeRequirement("appose>=1.9,<1.10", false),
			ApposeRequirement.forVersion("1.9.0"));
	}

	@Test
	public void testSnapshot() {
		if (System.getenv(ApposeRequirement.ENV_VAR) != null) return;
		assertEquals(ApposeRequirement.MAIN_BRANCH, ApposeRequirement.forVersion("1.1.0-SNAPSHOT"));
		assertEquals(ApposeRequirement.MAIN_BRANCH, ApposeRequirement.forVersion("(unknown)"));
	}

	@Test
	public void testOverrideRequirement() {
		System.setProperty(ApposeRequirement.PROPERTY, "appose==1.1.3");
		assertEquals(new ApposeRequirement("appose==1.1.3", false),
			ApposeRequirement.forVersion("1.1.2"));
	}

	@Test
	public void testOverrideDirectory(@TempDir File dir) {
		System.setProperty(ApposeRequirement.PROPERTY, dir.getAbsolutePath());
		ApposeRequirement requirement = ApposeRequirement.forVersion("1.1.2");
		assertTrue(requirement.editable());
		assertEquals("appose @ " + dir.toPath().toUri(), requirement.spec());
		assertEquals(Arrays.asList("-e", requirement.spec()), requirement.pipArgs());
	}

	@Test
	public void testMentionsAppose() {
		assertTrue(ApposeRequirement.mentionsAppose(Arrays.asList("numpy", "appose")));
		assertTrue(ApposeRequirement.mentionsAppose(Collections.singletonList("appose==1.1.2")));
		assertTrue(ApposeRequirement.mentionsAppose(Collections.singletonList("appose @ file:///x")));
		assertFalse(ApposeRequirement.mentionsAppose(Collections.singletonList("apposeish")));
		assertFalse(ApposeRequirement.mentionsAppose(Collections.emptyList()));
	}
}
