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

import org.apposed.appose.util.Messages;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The registry of {@link MemoryBackend}s, and the one this process uses.
 * <p>
 * Each backend is registered by name, with a service side and a worker side.
 * A service uses the {@code builtin} backend unless another is chosen; it
 * tells its workers which, via the {@link #ENV_VAR} environment variable.
 * </p>
 */
public final class MemoryBackends {

	/**
	 * The environment variable by which a service tells its workers which
	 * backend provides managed memory.
	 */
	public static final String ENV_VAR = "APPOSE_SHM";

	private static final Map<String, Supplier<MemoryBackend>[]> BACKENDS = new ConcurrentHashMap<>();

	private static MemoryBackend backend;
	private static String backendName;

	static {
		register("builtin", SlabMemory::new, SlabClient::new);
	}

	private MemoryBackends() {
		// Prevent instantiation of utility class.
	}

	/**
	 * Registers a memory backend.
	 *
	 * @param name The backend's name, by which a service tells its workers to use it.
	 * @param serviceSide Function creating the backend for a service process.
	 * @param workerSide Function creating the backend for a worker process.
	 */
	@SuppressWarnings("unchecked")
	public static void register(String name, Supplier<MemoryBackend> serviceSide,
		Supplier<MemoryBackend> workerSide)
	{
		BACKENDS.put(name, new Supplier[] { serviceSide, workerSide });
	}

	/**
	 * Makes this process use the named memory backend, before it allocates
	 * or exchanges any managed memory.
	 *
	 * @param name The backend's name.
	 */
	public static synchronized void use(String name) {
		Supplier<MemoryBackend>[] sides = BACKENDS.get(name);
		if (sides == null) throw new IllegalArgumentException("No such memory backend: " + name);
		if (backend != null && !name.equals(backendName)) {
			throw new IllegalStateException("This process already uses memory backend " + backendName);
		}
		if (backend != null) return;
		backend = sides[Messages.workerMode ? 1 : 0].get();
		backendName = name;
	}

	/**
	 * Gets this process's memory backend: in a service, the builtin one unless
	 * another was chosen; in a worker, the one its service uses, if supported.
	 *
	 * @return The backend, or null if this process has none.
	 */
	public static synchronized MemoryBackend backend() {
		if (backend == null && !Messages.workerMode) use("builtin");
		return backend;
	}

	/**
	 * Gets the name of this process's memory backend.
	 *
	 * @return The backend's name, or null if this process has none.
	 */
	public static synchronized String backendName() {
		backend();
		return backendName;
	}

	/** In a worker, uses the memory backend its service announced, if known. */
	public static void useFromEnvironment() {
		String name = System.getenv(ENV_VAR);
		if (name != null && BACKENDS.containsKey(name)) use(name);
	}

	/**
	 * Allocates a managed region from this process's memory backend.
	 *
	 * @param length The region's length in bytes.
	 * @return A view of the newly allocated region.
	 */
	public static SharedMemoryView allocate(long length) {
		MemoryBackend memory = backend();
		if (memory == null) {
			throw new IllegalStateException("No managed shared memory is available in this process");
		}
		return memory.allocate(length);
	}
}
