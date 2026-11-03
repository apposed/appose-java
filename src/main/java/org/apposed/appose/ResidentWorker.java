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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * An Appose worker process that stays resident across tasks: it is started
 * on first use, reused afterward, and ended only on request.
 * <p>
 * Keeping the worker alive is what makes repeated runs fast: modules the
 * worker has imported stay imported, and objects a task hands to
 * {@code task.export(...)} stay available to later tasks. The price is that
 * the worker's memory, GPU memory included, stays claimed until
 * {@link #release()} ends the process, which is the only reliable way to
 * free it.
 * </p>
 * <p>
 * A worker runs in a {@link LazyEnvironment}, which it builds if needed; any
 * number of workers may share one environment. If the worker dies (e.g. it
 * crashes, or {@link #kill()} is called), the next task transparently starts
 * a new process.
 * </p>
 *
 * @author Curtis Rueden
 */
public class ResidentWorker implements AutoCloseable {

	private final LazyEnvironment environment;
	private final Function<Environment, Service> launcher;
	private final Lock exclusive = new ReentrantLock();

	private volatile Consumer<String> debug;
	private Service service;

	/**
	 * @param environment Environment the worker runs in.
	 * @param launcher Creates the worker's service from the built environment,
	 *          e.g. {@code env -> env.python().init("import numpy")}.
	 */
	public ResidentWorker(LazyEnvironment environment,
		Function<Environment, Service> launcher)
	{
		this.environment = environment;
		this.launcher = launcher;
	}

	public LazyEnvironment environment() {
		return environment;
	}

	/**
	 * Gets a lock for callers needing exclusive use of the worker, e.g. to
	 * attribute its output to a single task. The worker's other methods do not
	 * take this lock, so it can be released or killed while someone holds it.
	 */
	public Lock exclusive() {
		return exclusive;
	}

	/**
	 * Sets where the worker's debug output goes: stderr lines, non-protocol
	 * stdout lines and protocol traffic, as described by
	 * {@link Service#debug}. May be changed at any time, including while the
	 * worker is running; {@code null} discards the output.
	 */
	public void debug(@Nullable Consumer<String> listener) {
		debug = listener;
	}

	/**
	 * Gets the worker's service, building the environment and starting the
	 * worker process first if needed.
	 *
	 * @throws UncheckedIOException If the worker process fails to launch.
	 */
	public synchronized Service service() throws BuildException {
		if (service != null && service.isAlive()) return service;
		Service s = launcher.apply(environment.get());
		s.debug(msg -> {
			Consumer<String> listener = debug;
			if (listener != null) listener.accept(msg);
		});
		try {
			s.start();
		}
		catch (IOException exc) {
			throw new UncheckedIOException(exc);
		}
		service = s;
		return service;
	}

	/** Creates a task to run on this worker. */
	public Service.Task task(String script) throws BuildException {
		return task(script, null);
	}

	/** Creates a task to run on this worker. */
	public Service.Task task(String script, @Nullable Map<String, Object> inputs)
		throws BuildException
	{
		return service().task(script, inputs);
	}

	/** Whether the worker process is currently running. */
	public synchronized boolean isAlive() {
		return service != null && service.isAlive();
	}

	/**
	 * Ends the worker process gently, letting pending tasks finish, and
	 * releasing everything it holds. The environment stays built.
	 */
	public synchronized void release() {
		if (service == null) return;
		service.close();
		service = null;
	}

	/** Ends the worker process immediately, interrupting any pending tasks. */
	public synchronized void kill() {
		if (service == null) return;
		service.kill();
		service = null;
	}

	@Override
	public void close() {
		release();
	}
}
