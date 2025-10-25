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
 * Scheme for pyproject.toml configuration files (PEP 621 format).
 * <p>
 * Supports both standard Python projects with [project.dependencies]
 * and Pixi-flavored pyproject.toml with [tool.pixi.*] sections.
 * </p>
 *
 * @author Curtis Rueden
 */
public class PyProjectTomlScheme implements Scheme {

	@Override
	public String name() {
		return "pyproject.toml";
	}

	@Override
	public double priority() {
		// Higher priority than pixi.toml since pyproject.toml is more specific
		return 100;
	}

	@Override
	public String envName(String content) {
		if (content == null) return null;

		String[] lines = content.split("\n");
		boolean inProjectSection = false;

		for (String line : lines) {
			String trimmed = line.trim();

			// Track if we're in a [project] section
			if (trimmed.equals("[project]")) {
				inProjectSection = true;
				continue;
			} else if (trimmed.startsWith("[") && !trimmed.startsWith("[project")) {
				inProjectSection = false;
			}

			// Look for name in [project] section
			if (inProjectSection && trimmed.startsWith("name") && trimmed.contains("=")) {
				int equalsIndex = trimmed.indexOf('=');
				String value = trimmed.substring(equalsIndex + 1).trim();
				value = value.replaceAll("^[\"']|[\"']$", "");
				if (!value.isEmpty()) return value;
			}
		}

		return null;
	}

	@Override
	public boolean supportsContent(String content) {
		if (content == null) return false;

		String trimmed = content.trim();

		// Must have TOML structure
		if (!trimmed.matches("(?s).*\\[.*\\].*")) return false;

		// Must have [project] section
		if (!trimmed.contains("[project]")) return false;

		// Must have either:
		// - Pixi-flavored: [tool.pixi.*]
		// - Standard PEP 621: [project.dependencies] or dependencies = in [project] section
		return trimmed.contains("[tool.pixi.") ||
		       trimmed.contains("[project.dependencies]") ||
		       trimmed.matches("(?s).*\\[project\\].*dependencies\\s*=.*");
	}

	@Override
	public boolean supportsFilename(String filename) {
		return filename.endsWith("pyproject.toml");
	}
}
