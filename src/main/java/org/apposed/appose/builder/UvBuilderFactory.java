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

package org.apposed.appose.builder;

import org.apposed.appose.Builder;
import org.apposed.appose.BuilderFactory;

import java.io.IOException;

/**
 * Factory for creating UvBuilder instances.
 *
 * @author Curtis Rueden
 */
public class UvBuilderFactory implements BuilderFactory {
	@Override
	public Builder<?> createBuilder() {
		return new UvBuilder();
	}

	@Override
	public Builder<?> createBuilder(String source) throws IOException {
		return new UvBuilder(source);
	}

	@Override
	public Builder<?> createBuilder(String source, String scheme) throws IOException {
		return new UvBuilder(source, scheme);
	}

	@Override
	public String name() {
		return "uv";
	}

	@Override
	public boolean supportsScheme(String scheme) {
		switch (scheme) {
			case "requirements.txt":
			case "pypi":
				return true;
			default:
				return false;
		}
	}

	@Override
	public boolean supportsSource(String source) {
		// Support requirements.txt files.
		return source.endsWith("requirements.txt") || source.endsWith(".txt");
	}

	@Override
	public double priority() {
		return 75.0; // Between pixi (100) and mamba (50)
	}

	@Override
	public boolean canWrap(java.io.File envDir) {
		// Check for uv/venv environment markers.
		// uv creates standard Python venv, so look for pyvenv.cfg,
		// but exclude conda and pixi environments.
		boolean hasPyvenvCfg = new java.io.File(envDir, "pyvenv.cfg").isFile();
		boolean isNotPixi = !new java.io.File(envDir, ".pixi").isDirectory() &&
		                    !new java.io.File(envDir, "pixi.toml").isFile();
		boolean isNotConda = !new java.io.File(envDir, "conda-meta").isDirectory();

		return hasPyvenvCfg && isNotPixi && isNotConda;
	}
}
