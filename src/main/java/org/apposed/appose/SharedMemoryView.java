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

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A region of a shared memory block: {@link #size()} bytes, starting
 * {@link #offset()} bytes into the block.
 * <p>
 * Create one with {@link SharedMemory#view(long, long)}, or attach to a
 * region of a block created by another process with
 * {@link #attach(String, long, long, long)}.
 * </p>
 * <p>
 * Like a block, a view is accessed via {@link #buf(long, long)}, with
 * positions relative to the start of the region. And like a block, a view
 * reports the block's name and requested size, via {@link #name()} and
 * {@link #rsize()}, so that other processes can attach to it.
 * </p>
 * <p>
 * A view of a <em>managed</em> region (see {@link NDArray#managed}) holds a
 * reference to the region, which the service counts, and frees the region
 * once no process holds one anymore. The view gives up its reference once
 * {@link #close() closed}, or once neither it nor any {@link ByteBuffer}
 * obtained from it (including slices and duplicates of such buffers) is
 * reachable. An attached view likewise keeps its block mapped until then.
 * </p>
 */
public class SharedMemoryView implements SharedMemory {

	private final SharedMemory block;
	private final long offset;
	private final long length;
	private final boolean managed;

	/** For a region attached by this process: the record of the attachment. */
	private final @Nullable Attachment attachment;

	/** For a managed region of this process's memory: the record of this view's hold on it. */
	private final @Nullable Held held;

	SharedMemoryView(SharedMemory block, long offset, long length) {
		this(block, offset, length, false, null, null);
	}

	private SharedMemoryView(SharedMemory block, long offset, long length,
		boolean managed, @Nullable Mapping mapping, @Nullable Runnable release)
	{
		checkRegion(block.name(), block.rsize(), offset, length);
		this.block = block;
		this.offset = offset;
		this.length = length;
		this.managed = managed;
		this.attachment = mapping == null ? null : new Attachment(this, mapping);
		this.held = release == null ? null : new Held(this, release);
	}

	/**
	 * Creates a view of a managed region of a block this process holds.
	 * For use by {@link MemoryBackend memory backends}.
	 *
	 * @param block The shared memory block.
	 * @param offset The region's starting position within the block, in bytes.
	 * @param length The region's length in bytes.
	 * @param release What to do once the view is closed or garbage collected
	 *          (whichever comes first), e.g. to give up its reference.
	 * @return A view of the region.
	 */
	public static SharedMemoryView held(SharedMemory block, long offset, long length, Runnable release) {
		return new SharedMemoryView(block, offset, length, true, null, release);
	}

	/**
	 * Allocates a managed region from this process's memory backend.
	 *
	 * @param length The region's length in bytes.
	 * @return A view of the newly allocated region.
	 */
	static SharedMemoryView allocate(long length) {
		return MemoryBackends.allocate(length);
	}

	/**
	 * Attaches to a region of the named shared memory block, which may have
	 * been created by another process.
	 * <p>
	 * All regions of a block attached by this process share one mapping of
	 * the block, which stays open until the last of them is closed or
	 * garbage collected.
	 * </p>
	 *
	 * @param name The name of the shared memory block.
	 * @param rsize The requested size of the block in bytes.
	 * @param offset The region's starting position within the block, in bytes.
	 * @param length The region's length in bytes.
	 * @return A view of the region.
	 */
	public static SharedMemoryView attach(String name, long rsize, long offset, long length) {
		return attach(name, rsize, offset, length, null);
	}

	/**
	 * Attaches to a managed region of the named shared memory block, created
	 * by another process. For use by {@link MemoryBackend memory backends}.
	 *
	 * @param name The name of the shared memory block.
	 * @param rsize The requested size of the block in bytes.
	 * @param offset The region's starting position within the block, in bytes.
	 * @param length The region's length in bytes.
	 * @param released What to do once the view is closed or garbage collected
	 *          (whichever comes first), given the region's name and offset;
	 *          e.g. to give up its reference.
	 * @return A view of the region.
	 */
	public static SharedMemoryView attachManaged(String name, long rsize, long offset,
		long length, Consumer<Map<String, Object>> released)
	{
		return attach(name, rsize, offset, length, released);
	}

	private static SharedMemoryView attach(String name, long rsize, long offset,
		long length, @Nullable Consumer<Map<String, Object>> released)
	{
		checkRegion(name, rsize, offset, length);
		Mapping mapping = Mapping.acquire(name, rsize);
		try {
			SharedMemoryView view = new SharedMemoryView(mapping.block, offset, length,
				released != null, mapping, null);
			view.attachment.released = released;
			return view;
		}
		catch (RuntimeException exc) {
			mapping.release();
			throw exc;
		}
	}

	/**
	 * @return The region's starting position within the block, in bytes.
	 */
	public long offset() {
		return offset;
	}

	/**
	 * @return Whether the region is managed: a reference to it is counted,
	 *         which this view gives up once closed or garbage collected.
	 */
	public boolean managed() {
		return managed;
	}

	/**
	 * @return Whether this view has been closed (for a managed region:
	 *         whether it has given up its reference to the region).
	 */
	public boolean isClosed() {
		if (held != null) return held.isReleased();
		return attachment != null && attachment.isReleased();
	}

	/**
	 * @return The shared memory block of which this is a region.
	 */
	public SharedMemory block() {
		return block;
	}

	/** Gets the name of the shared memory block of which this is a region. */
	@Override
	public String name() {
		return block.name();
	}

	/** Gets the requested size of the shared memory block of which this is a region. */
	@Override
	public long rsize() {
		return block.rsize();
	}

	/** Gets the length of this region in bytes. */
	@Override
	public long size() {
		return length;
	}

	@Override
	public ByteBuffer buf() {
		return buf(0, length);
	}

	@Override
	public ByteBuffer buf(long fromIndex, long toIndex) {
		if (fromIndex < 0 || toIndex < fromIndex || toIndex > length) {
			throw new IllegalArgumentException("Invalid range [" + fromIndex +
				", " + toIndex + ") for region of length " + length);
		}
		ByteBuffer buf = block.buf(offset + fromIndex, offset + toIndex);
		// NB: Keep this view reachable as long as the buffer is,
		// so that the region stays in use while the buffer is.
		Owners.register(buf, this);
		return buf;
	}

	/**
	 * Unsupported: a region cannot be unlinked, only its block.
	 *
	 * @throws UnsupportedOperationException always.
	 */
	@Override
	public void unlinkOnClose(boolean unlinkOnClose) {
		throw new UnsupportedOperationException("Cannot unlink a region of a shared memory block");
	}

	/**
	 * Unsupported: a region cannot be unlinked, only its block.
	 *
	 * @throws UnsupportedOperationException always.
	 */
	@Override
	public void unlink() {
		throw new UnsupportedOperationException("Cannot unlink a region of a shared memory block");
	}

	/**
	 * Closes this view: for a managed region, gives up this view's reference
	 * to it; for an attached region, unmaps its block if no other region of
	 * it is in use. Otherwise, does nothing.
	 * <p>
	 * After closing a view, do not use it, nor any buffer obtained from it.
	 * </p>
	 */
	@Override
	public void close() {
		if (held != null) held.release();
		if (attachment != null) attachment.release();
	}

	@Override
	public String toString() {
		return "SharedMemoryView(name='" + name() + "', rsize=" + rsize() +
			", offset=" + offset + ", length=" + length + ")";
	}

	private static void checkRegion(String name, long rsize, long offset, long length) {
		if (offset < 0 || length < 0 || offset + length > Math.max(rsize, 1)) {
			throw new IllegalArgumentException("Region [" + offset + ", " +
				(offset + length) + ") does not fit in shared memory block " +
				name + " of size " + rsize);
		}
	}

	/** A mapping of a shared memory block, shared among all regions of it attached by this process. */
	private static class Mapping {

		private static final Map<String, Mapping> MAPPINGS = new HashMap<>();

		final SharedMemory block;
		private int regions;

		private Mapping(SharedMemory block) {
			this.block = block;
		}

		static Mapping acquire(String name, long rsize) {
			synchronized (MAPPINGS) {
				Mapping mapping = MAPPINGS.get(name);
				if (mapping == null) {
					// NB: The block's creator unlinks it, not this process;
					// an attached block is not unlinked upon closing.
					mapping = new Mapping(SharedMemory.attach(name, rsize));
					MAPPINGS.put(name, mapping);
				}
				mapping.regions++;
				return mapping;
			}
		}

		void release() {
			synchronized (MAPPINGS) {
				if (--regions > 0) return;
				MAPPINGS.remove(block.name());
				block.close();
			}
		}
	}

	/**
	 * The record of a view's hold on a managed region of this process's
	 * memory, given up once the view is closed or garbage collected.
	 */
	private static class Held extends PhantomReference<SharedMemoryView> {

		/** Pending records, kept reachable until released. */
		private static final Set<Held> PENDING = ConcurrentHashMap.newKeySet();

		private final Runnable release;

		// NB: Not a lock, since the memory may check it while holding its own.
		private final AtomicBoolean released = new AtomicBoolean();

		Held(SharedMemoryView view, Runnable release) {
			super(view, Cleaner.QUEUE);
			this.release = release;
			PENDING.add(this);
		}

		boolean isReleased() {
			return released.get();
		}

		void release() {
			if (!released.compareAndSet(false, true)) return;
			PENDING.remove(this);
			clear();
			release.run();
		}
	}

	/**
	 * The record of an attached region, which releases the
	 * region once its view is closed or garbage collected.
	 */
	private static class Attachment extends PhantomReference<SharedMemoryView> {

		/** Pending attachments, kept reachable until released. */
		private static final Set<Attachment> PENDING = ConcurrentHashMap.newKeySet();

		private final Mapping mapping;
		private final String name;
		private final long offset;

		/** For a managed region: what to do once released, given the region. */
		volatile @Nullable Consumer<Map<String, Object>> released;

		private final AtomicBoolean done = new AtomicBoolean();

		Attachment(SharedMemoryView view, Mapping mapping) {
			super(view, Cleaner.QUEUE);
			this.mapping = mapping;
			this.name = view.name();
			this.offset = view.offset;
			PENDING.add(this);
		}

		boolean isReleased() {
			return done.get();
		}

		/** Releases the region, unless already released. */
		void release() {
			if (!done.compareAndSet(false, true)) return;
			PENDING.remove(this);
			clear();
			mapping.release();
			Consumer<Map<String, Object>> callback = released;
			if (callback != null) {
				Map<String, Object> region = new LinkedHashMap<>();
				region.put("name", name);
				region.put("offset", offset);
				callback.accept(region);
			}
		}
	}

	/**
	 * Weakly maps buffers to the views they were obtained from, so that
	 * each view stays reachable as long as any of its buffers is.
	 * <p>
	 * A buffer derived from another (e.g. via {@code slice()},
	 * {@code duplicate()} or {@code asFloatBuffer()}) references it,
	 * so the view stays reachable as long as such derived buffers are, too.
	 * </p>
	 */
	private static class Owners {

		private static final Map<Key, Object> OWNERS = new ConcurrentHashMap<>();

		static void register(ByteBuffer buf, Object owner) {
			OWNERS.put(new Key(buf), owner);
		}

		/** A weak reference to a buffer, compared by identity. */
		private static class Key extends WeakReference<ByteBuffer> {
			private final int hash;

			Key(ByteBuffer buf) {
				super(buf, Cleaner.QUEUE);
				hash = System.identityHashCode(buf);
			}

			@Override
			public int hashCode() {
				return hash;
			}

			@Override
			public boolean equals(Object o) {
				if (this == o) return true;
				if (!(o instanceof Key)) return false;
				Object referent = get();
				return referent != null && referent == ((Key) o).get();
			}
		}
	}

	/**
	 * Cleans up after garbage collected buffers and views: forgets the owners
	 * of buffers, and releases the regions of views.
	 */
	private static class Cleaner {

		static final ReferenceQueue<Object> QUEUE = new ReferenceQueue<>();

		static {
			Thread thread = new Thread(Cleaner::run, "Appose-Shm-Cleaner");
			thread.setDaemon(true);
			thread.start();
		}

		private static void run() {
			while (true) {
				try {
					List<Object> batch = new ArrayList<>();
					batch.add(QUEUE.remove());
					Object more;
					while ((more = QUEUE.poll()) != null) batch.add(more);

					for (Object ref : batch) {
						if (ref instanceof Owners.Key) Owners.OWNERS.remove(ref);
						else if (ref instanceof Held) ((Held) ref).release();
						else ((Attachment) ref).release();
					}
				}
				catch (InterruptedException exc) {
					return;
				}
				catch (RuntimeException exc) {
					// NB: Keep cleaning up, no matter what.
					exc.printStackTrace();
				}
			}
		}
	}
}
