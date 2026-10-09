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

/**
 * The other end of a connection, as a {@link MemoryBackend} may use it.
 */
public interface Peer {

	/**
	 * Sends a message (e.g. a RELEASE response) to the peer.
	 *
	 * @param message The message.
	 */
	default void send(Map<String, Object> message) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Calls a function the peer (a service) exports. Only available in a
	 * worker, and never from its receiver thread.
	 *
	 * @param function The name of the exported function.
	 * @param args The arguments.
	 * @return The function's result.
	 */
	default Object call(String function, List<Object> args) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Exports an object (e.g. a function) for the peer (a worker) to call.
	 * Only available in a service.
	 *
	 * @param name The name to export the object under.
	 * @param obj The object.
	 */
	default void export(String name, Object obj) {
		throw new UnsupportedOperationException();
	}
}
