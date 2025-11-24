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

import org.apposed.appose.BuildException;
import org.apposed.appose.Builder;
import org.apposed.appose.BuilderFactory;
import org.apposed.appose.Environment;
import org.apposed.appose.Scheme;

/**
 * Dynamic builder that auto-detects the appropriate specific builder
 * based on source file and scheme.
 *
 * @author Curtis Rueden
 */
public final class DynamicBuilder extends BaseBuilder<DynamicBuilder> {

	private String envType;

	// -- DynamicBuilder methods --

	/**
	 * Specifies the preferred builder to use.
	 *
	 * @param envType The builder's environment type (e.g., "pixi", "mamba", "uv")
	 * @return This builder instance, for fluent-style programming.
	 */
	public DynamicBuilder builder(String envType) {
		this.envType = envType;
		return this;
	}

	// -- Builder methods --

	@Override
	public String envType() {
		return envType != null ? envType : "dynamic";
	}

	@Override
	public Environment build() throws BuildException {
		Builder<?> delegate = createBuilder();
		copyConfigToDelegate(delegate);
		return delegate.build();
	}

	@Override
	public Environment rebuild() throws BuildException {
		Builder<?> delegate = createBuilder();
		copyConfigToDelegate(delegate);
		return delegate.rebuild();
	}

	// -- Helper methods --

	private void copyConfigToDelegate(Builder<?> delegate) {
		// Copy configuration from dynamic builder to delegate.
		delegate.env(envVars);
		if (envName != null) delegate.name(envName);
		if (envDir != null) delegate.base(envDir);
		if (content != null) delegate.content(content);
		if (scheme != null) delegate.scheme(scheme.name());
		delegate.channels(channels);
		progressSubscribers.forEach(delegate::subscribeProgress);
		outputSubscribers.forEach(delegate::subscribeOutput);
		errorSubscribers.forEach(delegate::subscribeError);
	}

	private Builder<?> createBuilder() {
		// Find the builder matching the specified name, if any.
		if (envType != null) {
			BuilderFactory factory = Builders.findFactoryByEnvType(envType);
			if (factory == null) throw new IllegalArgumentException("Unknown builder: " + envType);
			return factory.createBuilder();
		}

		// Detect scheme from content if content is provided but scheme is not.
		Scheme actualScheme = scheme;
		if (actualScheme == null && content != null) {
			actualScheme = resolveScheme();
		}

		// Find the highest-priority builder that supports this scheme.
		if (actualScheme != null) {
			BuilderFactory factory = Builders.findFactoryByScheme(actualScheme.name());
			if (factory == null) throw new IllegalArgumentException("No builder supports scheme: " + actualScheme.name());
			return factory.createBuilder();
		}

		throw new IllegalArgumentException("Content and/or scheme must be provided for dynamic builder");
	}
}
