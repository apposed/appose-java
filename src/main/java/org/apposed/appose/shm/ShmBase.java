/*-
 * #%L
 * Appose: multi-language interprocess cooperation with shared memory.
 * %%
 * Copyright (C) 2023 - 2025 Appose developers.
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

package org.apposed.appose.shm;

import com.sun.jna.Pointer;
import org.apposed.appose.SharedMemory;

import java.nio.ByteBuffer;

/**
 * Base class for platform-specific shared memory implementations.
 *
 * @author Carlos Garcia Lopez de Haro
 * @author Tobias Pietzsch
 * @author Curtis Rueden
 */
abstract class ShmBase<HANDLE> implements SharedMemory {

	/** Struct containing shm details, including name, size, pointer(s), and handle. */
	protected final ShmInfo<HANDLE> info;

	/** Whether the memory block has been closed. */
	private boolean closed;

	/** Whether the memory block has been unlinked. */
	private boolean unlinked;

	protected ShmBase(final ShmInfo<HANDLE> info) {
		this.info = info;
	}

	protected abstract void doClose();
	protected abstract void doUnlink();

	@Override
	public String name() {
		return info.name;
	}

	@Override
	public long rsize() {
		return info.rsize;
	}

	@Override
	public long size() {
		return info.size;
	}

	@Override
	public ByteBuffer buf(long fromIndex, long toIndex) {
		return info.pointer.getByteBuffer(fromIndex, toIndex);
	}

	@Override
	public void unlinkOnClose(boolean unlinkOnClose) {
		info.unlinkOnClose = unlinkOnClose;
	}

	@Override
	public synchronized void unlink() {
		if (unlinked) throw new IllegalStateException("Shared memory '" + info.name + "' is already unlinked.");
		doClose();
		doUnlink();
		unlinked = true;
	}

	@Override
	public synchronized void close() {
		if (closed) return;
		doClose();
		if (info.unlinkOnClose) doUnlink();
		closed = true;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getClass().getSimpleName()).append("{");
		sb.append("name='").append(name()).append("', size=").append(size());
		if (info.pointer != null) sb.append(", pointer=").append(info.pointer);
		if (info.writePointer != null) sb.append(", writePointer=").append(info.writePointer);
		if (info.handle != null) sb.append("handle=").append(info.handle);
		sb.append(", closed=").append(closed);
		sb.append(", unlinked=").append(unlinked);
		sb.append("}");
		return sb.toString();
	}

	/** Struct containing details about this shared memory block. */
	protected static class ShmInfo<HANDLE> {

		/** Unique name that identifies the shared memory segment. */
		String name;

		/** Nominal/requested size in bytes. */
		long rsize;

		/** Actual size in bytes after "rounding up" for OS requirements. */
		long size;

		/** Pointer referencing the shared memory. */
		Pointer pointer;

		Pointer writePointer;

		/** File handle for the shared memory block's (pseudo-)file. */
		HANDLE handle;

		/** Whether to destroy the shared memory block when {@link #close()} is called. */
		boolean unlinkOnClose;
	}
}
