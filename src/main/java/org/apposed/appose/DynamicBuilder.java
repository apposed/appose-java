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

package org.apposed.appose;

import org.apposed.appose.util.Builders;

import java.io.File;
import java.io.IOException;

/**
 * Dynamic builder that auto-detects the appropriate specific builder
 * based on source file and scheme.
 *
 * @author Curtis Rueden
 */
public class DynamicBuilder extends BaseBuilder {

	private final String source;
	private String scheme;
	private String builderName;
	private BaseBuilder delegate;

	// Package-private constructor for Appose class
	DynamicBuilder(String source) {
		this.source = source;
	}

	/**
	 * Specifies the scheme for the source file.
	 * Can use "scheme:builder" syntax to also specify preferred builder.
	 *
	 * @param scheme The scheme (e.g., "environment.yml", "pixi.toml") or "scheme:builder"
	 * @return This builder instance, for fluent-style programming.
	 */
	public DynamicBuilder scheme(String scheme) {
		this.scheme = scheme;
		return this;
	}

	/**
	 * Specifies the preferred builder to use.
	 *
	 * @param builderName The builder name (e.g., "pixi", "mamba", "uv")
	 * @return This builder instance, for fluent-style programming.
	 */
	public DynamicBuilder builder(String builderName) {
		this.builderName = builderName;
		return this;
	}

	@Override
	public Environment build(File envDir) throws IOException {
		if (delegate == null) {
			delegate = resolveDelegate();
			// Copy subscribers from dynamic builder to delegate
			delegate.progressSubscribers.addAll(this.progressSubscribers);
			delegate.outputSubscribers.addAll(this.outputSubscribers);
			delegate.errorSubscribers.addAll(this.errorSubscribers);
		}
		return delegate.build(envDir);
	}

	private BaseBuilder resolveDelegate() {
		// 1. Parse scheme:builder syntax if present
		String actualScheme = scheme;
		String actualBuilder = builderName;

		if (scheme != null && scheme.contains(":")) {
			String[] parts = scheme.split(":", 2);
			actualScheme = parts[0];
			if (actualBuilder == null) actualBuilder = parts[1];
		}

		// 2. Auto-detect scheme if needed
		if (actualScheme == null) {
			actualScheme = detectScheme(source);
		}

		// 3. Pick builder based on preference or scheme
		if (actualBuilder != null) {
			// Explicit builder requested
			return createBuilder(actualBuilder, source, actualScheme);
		}

		// 4. Default builder selection by scheme
		return selectDefaultBuilder(actualScheme, source);
	}

	private String detectScheme(String source) {
		if (source == null) {
			throw new IllegalArgumentException("Cannot auto-detect scheme: no source specified");
		}
		if (source.endsWith(".toml")) return "pixi.toml";
		if (source.endsWith(".yml") || source.endsWith(".yaml")) return "environment.yml";
		if (source.endsWith(".txt")) return "requirements.txt";
		throw new IllegalArgumentException("Cannot auto-detect scheme from: " + source);
	}

	private BaseBuilder createBuilder(String builderName, String source, String scheme) {
		// Find factory by name using ServiceLoader
		BuilderFactory factory = Builders.findFactoryByName(builderName);
		if (factory == null) {
			throw new IllegalArgumentException("Unknown builder: " + builderName);
		}

		// Create builder instance via factory
		Builder builder = factory.createBuilder(source, scheme);
		if (!(builder instanceof BaseBuilder)) {
			throw new IllegalArgumentException("Builder must extend BaseBuilder: " + builderName);
		}

		return (BaseBuilder) builder;
	}

	private BaseBuilder selectDefaultBuilder(String scheme, String source) {
		// Find the highest-priority factory that supports this scheme
		BuilderFactory factory = Builders.findFactoryByScheme(scheme);
		if (factory == null) {
			throw new IllegalArgumentException("No builder supports scheme: " + scheme);
		}

		// Create builder instance via factory
		Builder builder = factory.createBuilder(source, scheme);
		if (!(builder instanceof BaseBuilder)) {
			throw new IllegalArgumentException("Builder must extend BaseBuilder for scheme: " + scheme);
		}

		return (BaseBuilder) builder;
	}

	@Override
	public String suggestEnvName() {
		// Delegate to the resolved builder
		if (delegate == null) {
			delegate = resolveDelegate();
		}
		return delegate.suggestEnvName();
	}
}

