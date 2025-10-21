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

	// Package-private constructor for Appose class
	DynamicBuilder(String source) {
		this.source = source;
	}

	// -- DynamicBuilder methods --

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

	// -- Builder methods --

	@Override
	public Environment build() throws IOException {
		Builder delegate = createBuilder(builderName, source, scheme);

		// Copy configuration from dynamic builder to delegate.
		delegate.env(envVars);
		if (envName != null) delegate.name(envName);
		if (envDir != null) delegate.base(envDir);
		delegate.channels(channels);
		progressSubscribers.forEach(delegate::subscribeProgress);
		outputSubscribers.forEach(delegate::subscribeOutput);
		errorSubscribers.forEach(delegate::subscribeError);

		return delegate.build();
	}

	// -- Internal methods --

	@Override
	protected String suggestEnvName() {
		throw new UnsupportedOperationException();
	}

	// -- Helper methods --

	private Builder createBuilder(String name, String source, String scheme) {
		// Find the builder matching the specified name, if any.
		if (name != null) {
			BuilderFactory factory = Builders.findFactoryByName(name);
			if (factory == null) throw new IllegalArgumentException("Unknown builder: " + name);
			return factory.createBuilder(source, scheme);
		}

		// Find the highest-priority builder that supports this scheme.
		if (scheme != null) {
			BuilderFactory factory = Builders.findFactoryByScheme(scheme);
			if (factory == null) throw new IllegalArgumentException("No builder supports scheme: " + scheme);
			return factory.createBuilder(source, scheme);
		}

		// Find the highest-priority builder that supports this source.
		if (source != null) {
			BuilderFactory factory = Builders.findFactoryBySource(source);
			if (factory == null) throw new IllegalArgumentException("No builder supports source: " + source);
			return factory.createBuilder(source, scheme);
		}

		throw new IllegalArgumentException("At least one of builder name, source, and scheme must be non-null");
	}
}
