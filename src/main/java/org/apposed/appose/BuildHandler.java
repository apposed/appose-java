/*-
 * #%L
 * Appose: multi-language interprocess cooperation with shared memory.
 * %%
 * Copyright (C) 2023 - 2024 Appose developers.
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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface BuildHandler {

	/**
	 * Registers a channel from which elements of the environment can be obtained.
	 *
	 * @param name The name of the channel to register.
	 * @param location The location of the channel (e.g. a URI), or {@code null} if the
	 *                  name alone is sufficient to unambiguously identify the channel.
	 * @return true iff the channel is understood by this build handler implementation.
	 * @see Builder#channel
	 */
	boolean channel(String name, String location);

	/**
	 * Registers content to be included within the environment.
	 *
	 * @param content The content to include in the environment, fetching if needed.
	 * @param scheme The type of content, which serves as a hint for
	 *                how to interpret the content in some scenarios.
	 * @see Builder#include
	 */
	boolean include(String content, String scheme);

	/** Suggests a name for the environment currently being built. */
	String envName();

	/**
	 * Executes the environment build, according to the configured channels and includes.
	 *
	 * @param envDir The directory into which the environment will be built.
	 * @param builder The {@link Builder} instance managing the build process.
	 *                Contains event subscribers and output configuration table.
	 * @throws IOException If something goes wrong building the environment.
	 * @see Builder#build(String)
	 */
	void build(File envDir, Builder builder) throws IOException;

	default void progress(Builder builder, String title, long current) {
		progress(builder, title, current, -1);
	}

	default void progress(Builder builder, String title, long current, long maximum) {
		builder.progressSubscribers.forEach(subscriber -> subscriber.accept(title, current, maximum));
	}

	default void output(Builder builder, String message) {
		builder.outputSubscribers.forEach(subscriber -> subscriber.accept(message));
	}

	default void error(Builder builder, String message) {
		builder.errorSubscribers.forEach(subscriber -> subscriber.accept(message));
	}
}
