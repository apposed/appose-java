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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The worker side of the builtin memory backend: asks the service for
 * managed memory, and tells it, in batches, once done with the references
 * it received.
 */
final class SlabClient implements MemoryBackend {

	private volatile WorkerLink link;

	@Override
	public SharedMemoryView allocate(long length) {
		WorkerLink l = link;
		if (l == null) throw new IllegalStateException("Not connected to a service");
		Object view = l.peer.call(SlabMemory.ALLOCATE, Collections.singletonList(length));
		if (!(view instanceof SharedMemoryView) || !((SharedMemoryView) view).managed()) {
			throw new IllegalStateException("The service allocated no managed shared memory: " + view);
		}
		return (SharedMemoryView) view;
	}

	@Override
	public MemoryLink link(Peer peer) {
		link = new WorkerLink(peer);
		return link;
	}

	/** The builtin backend's link from a worker to its service. */
	private static final class WorkerLink implements MemoryLink {

		/** Marks the end of the released regions. */
		private static final Map<String, Object> STOP = Collections.emptyMap();

		final Peer peer;
		private final BlockingQueue<Map<String, Object>> released = new LinkedBlockingQueue<>();

		WorkerLink(Peer peer) {
			this.peer = peer;
			Thread thread = new Thread(this::releaseLoop, "Appose-Releaser");
			thread.setDaemon(true);
			thread.start();
		}

		@Override
		public Map<String, Object> describe(SharedMemoryView view) {
			return SlabMemory.fields(view);
		}

		@Override
		public SharedMemoryView resolve(Map<String, Object> ref) {
			long[] region = SlabMemory.region(ref);
			return SharedMemoryView.attachManaged((String) ref.get("name"),
				region[0], region[1], region[2], released::add);
		}

		@Override
		public void close() {
			released.add(STOP);
		}

		/** Tells the service, in batches, which regions this worker is done with. */
		private void releaseLoop() {
			while (true) {
				List<Map<String, Object>> batch = new ArrayList<>();
				try {
					batch.add(released.take());
				}
				catch (InterruptedException exc) {
					return;
				}
				released.drainTo(batch);
				boolean stopping = batch.remove(STOP);
				if (!batch.isEmpty()) {
					Map<String, Object> response = new HashMap<>();
					response.put("responseType", Service.ResponseType.RELEASE.toString());
					response.put("regions", batch);
					try {
						peer.send(response);
					}
					catch (RuntimeException exc) {
						// NB: The service is gone, and so are its counts.
					}
				}
				if (stopping) return;
			}
		}
	}
}
