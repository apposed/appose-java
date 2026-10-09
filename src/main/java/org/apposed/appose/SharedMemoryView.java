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

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

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
 * An attached view keeps its block mapped until it is {@link #close()
 * closed}, or until neither it nor any {@link ByteBuffer} obtained from it
 * (including slices and duplicates of such buffers) is reachable.
 * </p>
 */
public class SharedMemoryView implements SharedMemory {

	private final SharedMemory block;
	private final long offset;
	private final long length;

	/** For a region attached by this process: the record of the attachment. */
	private final @Nullable Attachment attachment;

	SharedMemoryView(SharedMemory block, long offset, long length) {
		this(block, offset, length, null);
	}

	private SharedMemoryView(SharedMemory block, long offset, long length, @Nullable Mapping mapping) {
		checkRegion(block.name(), block.rsize(), offset, length);
		this.block = block;
		this.offset = offset;
		this.length = length;
		this.attachment = mapping == null ? null : new Attachment(this, mapping);
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
		checkRegion(name, rsize, offset, length);
		Mapping mapping = Mapping.acquire(name, rsize);
		try {
			return new SharedMemoryView(mapping.block, offset, length, mapping);
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
	 * @return Whether this view of an attached region has been closed.
	 */
	public boolean isClosed() {
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
	 * Closes this view: for an attached region, unmaps its block if no other
	 * region of it is in use. Otherwise, does nothing.
	 * <p>
	 * After closing a view, do not use it, nor any buffer obtained from it.
	 * </p>
	 */
	@Override
	public void close() {
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
	 * The record of an attached region, which releases the
	 * region once its view is closed or garbage collected.
	 */
	private static class Attachment extends PhantomReference<SharedMemoryView> {

		/** Pending attachments, kept reachable until released. */
		private static final Set<Attachment> PENDING = ConcurrentHashMap.newKeySet();

		private final Mapping mapping;

		private final AtomicBoolean done = new AtomicBoolean();

		Attachment(SharedMemoryView view, Mapping mapping) {
			super(view, Cleaner.QUEUE);
			this.mapping = mapping;
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
	 * of buffers, and releases the regions of attached views.
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
