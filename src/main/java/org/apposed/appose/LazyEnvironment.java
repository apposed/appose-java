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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An Appose environment that is built on first use, once, and then shared by
 * any number of {@link ResidentWorker}s.
 * <p>
 * Builds of environments with the same name are serialized, even across
 * instances, because they typically share a directory on disk: e.g. when the
 * environment's configuration changes while an older build is still running.
 * So instances whose builders target the same directory should be given the
 * same name.
 * </p>
 *
 * @author Curtis Rueden
 */
public class LazyEnvironment {

	/** Locks serializing builds, keyed by environment name. */
	private static final Map<String, Object> BUILD_LOCKS =
		new ConcurrentHashMap<>();

	private final String name;
	private final Builder<?> builder;
	private final String activate;
	private final List<BuildListener> listeners = new CopyOnWriteArrayList<>();

	private volatile Environment env;

	/**
	 * @param name Name of the environment, as reported to listeners, and
	 *          identifying builds to serialize. It is not given to the builder,
	 *          which must already be configured with where to build, e.g. via
	 *          {@link Builder#name} or {@link Builder#base(java.io.File)}.
	 * @param builder Builder for the environment; built on first use.
	 */
	public LazyEnvironment(String name, Builder<?> builder) {
		this(name, builder, null);
	}

	/**
	 * @param name Name of the environment, as reported to listeners, and
	 *          identifying builds to serialize. It is not given to the builder,
	 *          which must already be configured with where to build, e.g. via
	 *          {@link Builder#name} or {@link Builder#base(java.io.File)}.
	 * @param builder Builder for the environment; built on first use.
	 * @param activate Name of the sub-environment to activate once built (e.g.
	 *          a pixi environment), or null to use the environment as built.
	 */
	public LazyEnvironment(String name, Builder<?> builder,
		@Nullable String activate)
	{
		this.name = name;
		this.activate = activate;
		// Note: Subscribe once, here, and dispatch to the current listeners,
		// because builders accumulate subscribers with every call.
		this.builder = builder //
			.subscribeProgress((title, current, maximum) -> listeners.forEach(
				l -> l.buildProgress(name, title, current, maximum))) //
			.subscribeOutput(text -> listeners.forEach(l -> l.buildOutput(name,
				text))) //
			.subscribeError(text -> listeners.forEach(l -> l.buildError(name,
				text)));
	}

	public String name() {
		return name;
	}

	public LazyEnvironment addBuildListener(BuildListener listener) {
		listeners.add(listener);
		return this;
	}

	/** Whether the environment has been built by this instance. */
	public boolean isBuilt() {
		return env != null;
	}

	/** Gets the environment, building it if this is its first use. */
	public Environment get() throws BuildException {
		if (env != null) return env;
		synchronized (BUILD_LOCKS.computeIfAbsent(name, k -> new Object())) {
			if (env != null) return env;
			listeners.forEach(l -> l.buildStarted(name));
			try {
				Environment built = builder.build();
				env = activate == null ? built : built.activate(activate);
			}
			catch (BuildException | RuntimeException exc) {
				listeners.forEach(l -> l.buildFinished(name, exc));
				throw exc;
			}
			listeners.forEach(l -> l.buildFinished(name, null));
			return env;
		}
	}
}
