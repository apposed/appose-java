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
 * The state of the environment targeted by a {@link Builder}, as reported by
 * {@link Builder#status()}. Useful for deciding whether {@link Builder#build()}
 * is necessary, or merely a refresh.
 *
 * @author Curtis Rueden
 */
public enum EnvStatus {

	/**
	 * No environment exists at the target location. This includes the case of
	 * a directory that exists but contains no environment (e.g., one created
	 * by {@link Builder#wrap} of an empty folder).
	 * {@link Builder#build()} will create the environment.
	 */
	MISSING,

	/**
	 * An environment of a different type exists at the target location: e.g.,
	 * a conda environment targeted by a pixi builder.
	 * {@link Builder#build()} will fail; {@link Builder#rebuild()} would
	 * replace it.
	 */
	INCOMPATIBLE,

	/**
	 * An environment exists, but was not built by Appose (no recorded build
	 * state): e.g., made directly with conda, pixi or venv, or by an older
	 * Appose. {@link Builder#build()} will use it as-is if the builder has no
	 * configuration to build from; {@link Builder#rebuild()} would replace it.
	 */
	EXTERNAL,

	/**
	 * An environment built by Appose exists, but its recorded build state
	 * differs from the builder's current configuration.
	 * {@link Builder#build()} will update it.
	 */
	STALE,

	/**
	 * An environment built by Appose exists, and matches the builder's current
	 * configuration. {@link Builder#build()} will do no package management.
	 */
	CURRENT
}
