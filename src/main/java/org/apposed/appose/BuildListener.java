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

package org.apposed.appose;

/**
 * Receives notifications about the build of an Appose environment, from
 * start to finish.
 * <p>
 * Unlike the individual {@code Builder.subscribe*} callbacks, every
 * notification names the environment it concerns, and the build's start and
 * end are events too, so that one listener can follow several builds.
 * </p>
 * <p>
 * Note that {@link #buildError} receives the build tool's stderr stream,
 * which is <em>not</em> a failure report: pixi, for example, writes all of
 * its ordinary status there, including its success message. A failed build
 * is signaled only by a non-null error passed to {@link #buildFinished}.
 * </p>
 *
 * @author Curtis Rueden
 * @see LazyEnvironment#addBuildListener
 */
public interface BuildListener {

	/** Called when a build (or up-to-date check) of the environment begins. */
	default void buildStarted(String envName) {}

	/** Called as the build advances; {@code maximum} is 0 when unknown. */
	default void buildProgress(String envName, String title, long current,
		long maximum)
	{}

	/** Called with the build tool's stdout. */
	default void buildOutput(String envName, String text) {}

	/** Called with the build tool's stderr, which is not a failure signal. */
	default void buildError(String envName, String text) {}

	/** Called when the build ends; {@code error} is null on success. */
	default void buildFinished(String envName, @Nullable Throwable error) {}
}
