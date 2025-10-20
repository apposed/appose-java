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

package org.apposed.appose.pixi;

import org.apposed.appose.Builder;
import org.apposed.appose.BuilderFactory;

/**
 * Factory for creating PixiBuilder instances.
 *
 * @author Curtis Rueden
 */
public class PixiBuilderFactory implements BuilderFactory {
	@Override
	public Builder createBuilder() {
		return new PixiBuilder();
	}

	@Override
	public Builder createBuilder(String source) {
		return new PixiBuilder(source);
	}

	@Override
	public Builder createBuilder(String source, String scheme) {
		return new PixiBuilder(source, scheme);
	}

	@Override
	public String name() {
		return "pixi";
	}

	@Override
	public boolean supports(String scheme) {
		switch (scheme) {
			case "pixi.toml":
			case "environment.yml":
			case "conda":
			case "pypi":
				return true;
			default:
				return false;
		}
	}

	@Override
	public double priority() {
		return 100.0; // Preferred for environment.yml and conda/pypi packages
	}

	@Override
	public boolean canWrap(java.io.File envDir) {
		// Check for pixi environment markers
		return new java.io.File(envDir, ".pixi").isDirectory() ||
		       new java.io.File(envDir, "pixi.toml").isFile();
	}
}
