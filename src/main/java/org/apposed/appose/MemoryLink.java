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
 * Managed shared memory as used over one connection: from a service to one
 * of its workers, or from a worker to its service.
 * <p>
 * Through its link, a {@link MemoryBackend} decides what a reference to a
 * managed region consists of on the wire, and what sending or receiving one
 * entails.
 * </p>
 */
public interface MemoryLink {

	/**
	 * Gets the fields of a reference to the given managed region, to send
	 * over this link (besides {@code appose_type} and {@code managed}, which
	 * Appose adds). Called while encoding a message, which may yet fail.
	 *
	 * @param view A view of the managed region.
	 * @return The fields of the reference.
	 */
	Map<String, Object> describe(SharedMemoryView view);

	/**
	 * Records that references to the given managed regions are about to be
	 * sent over this link: the message is encoded, but not yet written.
	 *
	 * @param views Views of the regions.
	 */
	default void sent(List<SharedMemoryView> views) { }

	/**
	 * Gets a view of the managed region that the given reference, received
	 * over this link, refers to.
	 *
	 * @param ref The fields of the reference.
	 * @return A view of the region.
	 */
	SharedMemoryView resolve(Map<String, Object> ref);

	/**
	 * Handles a RELEASE message received over this link.
	 *
	 * @param regions The released regions, as listed by the message.
	 */
	default void released(Object regions) { }

	/** Called once the connection is gone, e.g. the worker terminated. */
	default void close() { }
}
