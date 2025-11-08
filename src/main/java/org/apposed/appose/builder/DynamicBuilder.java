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
import org.apposed.appose.Environment;

/**
 * Dynamic builder that auto-detects the appropriate specific builder
 * based on source file and scheme.
 *
 * @author Curtis Rueden
 */
public final class DynamicBuilder extends BaseBuilder<DynamicBuilder> {

	private final String source;
	private String builderName;

	public DynamicBuilder() {
		this.source = null;
	}

	public DynamicBuilder(String source) {
		this.source = source;
	}

	// -- DynamicBuilder methods --

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
	public String name() {
		return "dynamic";
	}

	@Override
	public Environment build() throws BuildException {
		Builder<?> delegate = createBuilder(builderName, source, scheme);
		copyConfigToDelegate(delegate);
		return delegate.build();
	}

	@Override
	public Environment rebuild() throws BuildException {
		Builder<?> delegate = createBuilder(builderName, source, scheme);
		copyConfigToDelegate(delegate);
		return delegate.rebuild();
	}

	// -- Helper methods --

	private void copyConfigToDelegate(Builder<?> delegate) {
		// Copy configuration from dynamic builder to delegate.
		delegate.env(envVars);
		if (envName != null) delegate.name(envName);
		if (envDir != null) delegate.base(envDir);
		if (sourceContent != null) delegate.content(sourceContent);
		if (scheme != null) delegate.scheme(scheme);
		delegate.channels(channels);
		progressSubscribers.forEach(delegate::subscribeProgress);
		outputSubscribers.forEach(delegate::subscribeOutput);
		errorSubscribers.forEach(delegate::subscribeError);
	}

	private Builder<?> createBuilder(String name, String source, String scheme) throws BuildException {
		// Find the builder matching the specified name, if any.
		if (name != null) {
			BuilderFactory factory = Builders.findFactoryByName(name);
			if (factory == null) throw new IllegalArgumentException("Unknown builder: " + name);
			if (source != null) {
				return factory.createBuilder(source, scheme);
			} else {
				return factory.createBuilder();
			}
		}

		// Detect scheme from content if content is provided but scheme is not.
		String effectiveScheme = scheme;
		if (effectiveScheme == null && sourceContent != null) {
			effectiveScheme = scheme().name();
		}

		// Find the highest-priority builder that supports this scheme.
		if (effectiveScheme != null) {
			BuilderFactory factory = Builders.findFactoryByScheme(effectiveScheme);
			if (factory == null) throw new IllegalArgumentException("No builder supports scheme: " + effectiveScheme);
			if (source != null) {
				return factory.createBuilder(source, effectiveScheme);
			} else {
				// Only content and scheme provided - create builder and configure via fluent API.
				return factory.createBuilder();
			}
		}

		// Find the highest-priority builder that supports this source.
		if (source != null) {
			BuilderFactory factory = Builders.findFactoryBySource(source);
			if (factory == null) throw new IllegalArgumentException("No builder supports source: " + source);
			return factory.createBuilder(source);
		}

		throw new IllegalArgumentException("At least one of builder name, source, content, and scheme must be non-null");
	}
}
