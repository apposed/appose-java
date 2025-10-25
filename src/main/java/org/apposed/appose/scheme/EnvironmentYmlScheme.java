/*-
 * #%L
 * Appose: multi-language interprocess cooperation with shared memory.
 * %%
 * Copyright (C) 2023 - 2025 Appose developers.
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

package org.apposed.appose.scheme;

import org.apposed.appose.Scheme;

/**
 * Scheme for environment.yml (conda/mamba) configuration files.
 *
 * @author Curtis Rueden
 */
public class EnvironmentYmlScheme implements Scheme {

	@Override
	public String name() {
		return "environment.yml";
	}

	@Override
	public double priority() {
		// TODO
		return 0;
	}

	@Override
	public String envName(String content) {
		if (content == null) return null;

		String[] lines = content.split("\n");

		for (String line : lines) {
			String trimmed = line.trim();

			if (trimmed.startsWith("name:")) {
				String value = trimmed.substring(5).trim().replace("\"", "");
				if (!value.isEmpty()) return value;
			}
		}

		return null;
	}

	@Override
	public boolean supportsContent(String content) {
		if (content == null) return false;

		String trimmed = content.trim();

		// YAML format detection: starts with common conda keys or has key: value pattern
		return trimmed.startsWith("name:") ||
			trimmed.startsWith("channels:") ||
			trimmed.startsWith("dependencies:") ||
			trimmed.matches("(?s)^[a-z_]+:\\s*.*");
	}

	@Override
	public boolean supportsFilename(String filename) {
		// TODO
		return false;
	}
}
