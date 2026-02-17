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

package org.apposed.appose.util;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * Utility methods for working with version strings.
 *
 * @author Curtis Rueden
 */
public final class Versions {

	private Versions() {
		// Prevent instantiation of utility class.
	}

	/** Gets the version of the resource (e.g. JAR file) containing the given class. */
	public static String version(Class<?> c) {
		// Extract version from embedded pom.xml.
		XML xml = null;
		URL pom = c.getResource("META-INF/org.apposed/appose/pom.xml");
		try {
			if (pom != null) xml = new XML(pom);
		}
		catch (IOException e) {
			// No action needed.
		}
		if (xml == null) {
			// Assume development working copy of appose.
			try {
				xml = new XML(new File("pom.xml"));
			}
			catch (IOException e) {
				// No action needed.
			}
		}
		if (xml == null) return null;
		return xml.cdata("//project/version");
	}
}
