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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The service side of the builtin memory backend: slabs of fixed-size slots,
 * and the number of references to each slot's region.
 * <p>
 * A region is freed once no view of it remains in this process, and no
 * peer (e.g. a worker) holds a reference to it. Only this process creates
 * and unlinks the slabs; workers ask it for regions, by calling the
 * {@value #ALLOCATE} function it exports.
 * </p>
 */
final class SlabMemory implements MemoryBackend {

	/** The service function by which workers allocate managed memory. */
	static final String ALLOCATE = "_appose_allocate";

	/** Slots are aligned to (and sized in multiples of) this many bytes. */
	static final long ALIGN = 64;

	/** The size beyond which a slab gets no more slots. */
	static final long MAX_SLAB_BYTES = 64L * 1024 * 1024;

	private final Map<Long, List<Slab>> slabs = new HashMap<>();
	private final Map<String, Region> regions = new HashMap<>();

	@Override
	public synchronized SharedMemoryView allocate(long length) {
		long slotSize = Math.max((length + ALIGN - 1) / ALIGN * ALIGN, ALIGN);
		List<Slab> sized = slabs.computeIfAbsent(slotSize, k -> new ArrayList<>());
		Slab slab = null;
		for (Slab s : sized) {
			if (!s.free.isEmpty()) {
				slab = s;
				break;
			}
		}
		if (slab == null) {
			// NB: Each new slab of a slot size holds twice as many slots as
			// the last, up to MAX_SLAB_BYTES: one-off arrays take a block
			// each, while many arrays of one size share few blocks.
			long most = Math.max(1, MAX_SLAB_BYTES / slotSize);
			int slots = (int) Math.min(1L << Math.min(sized.size(), 30), most);
			slab = new Slab(slotSize, slots);
			sized.add(slab);
		}
		Region region = new Region(slab, slab.free.pop());
		regions.put(key(slab.block.name(), region.offset), region);
		return view(region, length);
	}

	@Override
	public MemoryLink link(Peer peer) {
		return new ServiceLink(peer);
	}

	/** Returns a new view of the given managed region of this process. */
	synchronized SharedMemoryView resolve(String name, long offset, long length) {
		Region region = regions.get(key(name, offset));
		if (region == null || length > region.slab.slotSize) {
			throw new IllegalArgumentException("No such managed shared memory region: " +
				name + " at " + offset);
		}
		return view(region, length);
	}

	/** Counts a reference held by the given peer to each given region. */
	synchronized void lend(List<SharedMemoryView> views, Object peer) {
		for (SharedMemoryView view : views) {
			if (view.isClosed()) throw new IllegalStateException(view + " was already closed");
			Region region = regions.get(key(view.name(), view.offset()));
			if (region == null) throw new IllegalStateException("No such managed region: " + view);
			region.peers.merge(peer, 1, Integer::sum);
		}
	}

	/** Counts the given references as returned by the given peer. */
	synchronized void release(Object peer, Object released) {
		if (!(released instanceof List)) return;
		for (Object item : (List<?>) released) {
			if (!(item instanceof Map)) continue;
			Map<?, ?> r = (Map<?, ?>) item;
			Object offset = r.get("offset");
			if (!(offset instanceof Number)) continue;
			Region region = regions.get(key(String.valueOf(r.get("name")), ((Number) offset).longValue()));
			Integer count = region == null ? null : region.peers.get(peer);
			if (count == null) continue;
			if (count > 1) region.peers.put(peer, count - 1);
			else {
				region.peers.remove(peer);
				freeIfUnused(region);
			}
		}
	}

	/** Forgets all references held by the given peer, e.g. once it is gone. */
	synchronized void drop(Object peer) {
		for (Region region : new ArrayList<>(regions.values())) {
			if (region.peers.remove(peer) != null) freeIfUnused(region);
		}
	}

	/** Counts a view of the given region as gone. */
	synchronized void unview(Region region) {
		region.views--;
		freeIfUnused(region);
	}

	/** Gets the number of regions currently allocated. */
	synchronized int regionCount() {
		return regions.size();
	}

	private SharedMemoryView view(Region region, long length) {
		region.views++;
		return SharedMemoryView.held(region.slab.block, region.offset, length, () -> unview(region));
	}

	private void freeIfUnused(Region region) {
		if (region.views > 0 || !region.peers.isEmpty()) return;
		Slab slab = region.slab;
		regions.remove(key(slab.block.name(), region.offset));
		slab.free.push(region.slot);
		if (slab.free.size() == slab.slots) {
			// The slab is empty: retire it.
			slabs.get(slab.slotSize).remove(slab);
			slab.block.close();
		}
	}

	private static String key(String name, long offset) {
		return name + "@" + offset;
	}

	/** A shared memory block divided into fixed-size slots. */
	static final class Slab {
		final SharedMemory block;
		final long slotSize;
		final int slots;
		final Deque<Integer> free = new ArrayDeque<>();

		Slab(long slotSize, int slots) {
			this.block = SharedMemory.create(slotSize * slots);
			this.slotSize = slotSize;
			this.slots = slots;
			for (int i = slots - 1; i >= 0; i--) free.push(i);
		}
	}

	/** A managed region: a slot of a slab, and who holds it. */
	static final class Region {
		final Slab slab;
		final int slot;
		final long offset;

		/** Number of views of the region in this process. */
		int views;

		/** Number of references held by each peer, e.g. each worker. */
		final Map<Object, Integer> peers = new HashMap<>();

		Region(Slab slab, int slot) {
			this.slab = slab;
			this.slot = slot;
			this.offset = slot * slab.slotSize;
		}
	}

	/** The builtin backend's link from the service to one worker. */
	private class ServiceLink implements MemoryLink {

		ServiceLink(Peer peer) {
			// NB: Workers allocate managed memory by calling this function.
			peer.export(ALLOCATE, (Function<Number, SharedMemoryView>) length ->
				allocate(length.longValue()));
		}

		@Override
		public Map<String, Object> describe(SharedMemoryView view) {
			return fields(view);
		}

		@Override
		public void sent(List<SharedMemoryView> views) {
			lend(views, this);
		}

		@Override
		public SharedMemoryView resolve(Map<String, Object> ref) {
			// NB: A worker refers to a region of the service's own memory.
			long[] region = region(ref);
			return SlabMemory.this.resolve((String) ref.get("name"), region[1], region[2]);
		}

		@Override
		public void released(Object regions) {
			release(this, regions);
		}

		@Override
		public void close() {
			drop(this);
		}
	}

	/** Gets the fields of a reference to the given region, in the builtin backend. */
	static Map<String, Object> fields(SharedMemoryView view) {
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("name", view.name());
		fields.put("rsize", view.rsize());
		fields.put("offset", view.offset());
		fields.put("length", view.size());
		return fields;
	}

	/** Gets the requested size, offset and length of a referenced region. */
	static long[] region(Map<String, Object> ref) {
		long rsize = ((Number) ref.get("rsize")).longValue();
		Object offsetValue = ref.get("offset");
		long offset = offsetValue == null ? 0 : ((Number) offsetValue).longValue();
		Object lengthValue = ref.get("length");
		long length = lengthValue == null ? rsize - offset : ((Number) lengthValue).longValue();
		return new long[] { rsize, offset, length };
	}
}
