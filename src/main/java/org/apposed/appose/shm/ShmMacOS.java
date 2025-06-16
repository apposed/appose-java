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

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.apposed.appose.Platforms;
import org.apposed.appose.SharedMemory;
import org.apposed.appose.ShmFactory;

import static org.apposed.appose.Platforms.OperatingSystem.MACOS;
import static org.apposed.appose.shm.ShmUtils.MAP_SHARED;
import static org.apposed.appose.shm.ShmUtils.O_RDONLY;
import static org.apposed.appose.shm.ShmUtils.PROT_READ;
import static org.apposed.appose.shm.ShmUtils.PROT_WRITE;
import static org.apposed.appose.shm.ShmUtils.SHM_NAME_PREFIX_POSIX;
import static org.apposed.appose.shm.ShmUtils.SHM_SAFE_NAME_LENGTH;
import static org.apposed.appose.shm.ShmUtils.withLeadingSlash;
import static org.apposed.appose.shm.ShmUtils.withoutLeadingSlash;

/**
 * MacOS-specific shared memory implementation.
 *
 * @author Carlos Garcia Lopez de Haro
 * @author Tobias Pietzsch
 * @author Curtis Rueden
 */
public class ShmMacOS implements ShmFactory {

	@Override
	public SharedMemory create(final String name, final boolean create, final long rsize) {
		if (Platforms.OS != MACOS) return null; // wrong platform
		return new SharedMemoryMacOS(name, create, rsize);
	}

	private static class SharedMemoryMacOS extends ShmBase<Integer> {
		private SharedMemoryMacOS(final String name, final boolean create, final long rsize) {
			super(prepareShm(name, create, rsize));
		}

		@Override
		protected void doUnlink() {
			CLibrary.INSTANCE.shm_unlink(name());
		}

		@Override
		protected void doClose() {
			// Unmap the shared memory
			if (info.pointer != Pointer.NULL && CLibrary.INSTANCE.munmap(info.pointer, size()) == -1) {
				throw new RuntimeException("munmap failed. Errno: " + Native.getLastError());
			}

			// Close the file descriptor
			if (CLibrary.INSTANCE.close(info.handle) == -1) {
				throw new RuntimeException("close failed. Errno: " + Native.getLastError());
			}
		}

		private static ShmInfo<Integer> prepareShm(String name, boolean create, long rsize) {
			String shm_name;
			long prevSize;
			if (name == null) {
				do {
					shm_name = ShmUtils.make_filename(SHM_SAFE_NAME_LENGTH, SHM_NAME_PREFIX_POSIX);
					prevSize = getSHMSize(shm_name);
				} while (prevSize >= 0);
			} else {
				shm_name = withLeadingSlash(name);
				prevSize = getSHMSize(shm_name);
			}
			ShmUtils.checkSize(shm_name, prevSize, rsize);

			// shmFd = INSTANCE.shm_open(this.memoryName, O_RDWR, 0666);
			final int shmFd = MacosHelpers.INSTANCE.create_shared_memory(shm_name, rsize);
			if (shmFd < 0) {
				throw new RuntimeException("shm_open failed, errno: " + Native.getLastError());
			}
			final long shm_size = getSHMSize(shmFd);

			Pointer pointer = CLibrary.INSTANCE.mmap(Pointer.NULL, shm_size, PROT_READ | PROT_WRITE, MAP_SHARED, shmFd, 0);
			if (pointer == Pointer.NULL) {
				CLibrary.INSTANCE.close(shmFd);
				CLibrary.INSTANCE.shm_unlink(shm_name);
				throw new RuntimeException("mmap failed, errno: " + Native.getLastError());
			}

			ShmInfo<Integer> info = new ShmInfo<>();
			info.name = withoutLeadingSlash(shm_name);
			info.rsize = rsize; // REQUESTED size
			info.size = shm_size; // ALLOCATED size
			info.pointer = pointer;
			info.handle = shmFd;
			info.unlinkOnClose = create;
			return info;
		}

		/**
		 * Try to open {@code name} and get its size in bytes.
		 *
		 * @param name name with leading slash
		 * @return size in bytes, or -1 if the shared memory segment couuld not be opened
		 */
		private static long getSHMSize(final String name) {
			final int shmFd = CLibrary.INSTANCE.shm_open(name, O_RDONLY, 0700);
			if (shmFd < 0) {
				return -1;
			} else {
				return getSHMSize(shmFd);
			}
		}

		/**
		 * Method to find the size of an already created shared memory segment
		 *
		 * @param shmFd the shared memory segment identifier
		 * @return the size in bytes of the shared memory segment
		 */
		private static long getSHMSize(final int shmFd) {
			if (shmFd < 0) {
				throw new RuntimeException("Invalid shmFd. It should be bigger than 0.");
			}

			final long size = MacosHelpers.INSTANCE.get_shared_memory_size(shmFd);
			if (size == -1) {
				throw new RuntimeException("Failed to get shared memory segment size. Errno: " + Native.getLastError());
			}
			return size;
		}
	}
}
