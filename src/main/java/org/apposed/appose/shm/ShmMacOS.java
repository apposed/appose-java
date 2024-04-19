/*-
 * #%L
 * Use deep learning frameworks from Java in an agnostic and isolated way.
 * %%
 * Copyright (C) 2022 - 2023 Institut Pasteur and BioImage.IO developers.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package org.apposed.appose.shm;

import com.sun.jna.Native;
import com.sun.jna.Pointer;

import static org.apposed.appose.shm.ShmUtils.MAP_SHARED;
import static org.apposed.appose.shm.ShmUtils.O_RDONLY;
import static org.apposed.appose.shm.ShmUtils.PROT_READ;
import static org.apposed.appose.shm.ShmUtils.PROT_WRITE;
import static org.apposed.appose.shm.ShmUtils.SHM_NAME_PREFIX_POSIX;
import static org.apposed.appose.shm.ShmUtils.SHM_SAFE_NAME_LENGTH;
import static org.apposed.appose.shm.ShmUtils.withLeadingSlash;
import static org.apposed.appose.shm.ShmUtils.withoutLeadingSlash;

/**
 * TODO separate unlink and close
 *
 * @author Carlos Garcia Lopez de Haro
 * @author Tobias Pietzsch
 */
class ShmMacOS implements ShmImpl {

	/**
	 * File descriptor
	 */
	private final int fd;

	/**
	 * Size in bytes
	 */
	private final int size;

	/**
	 * Pointer referencing the shared memory
	 */
	private final Pointer pointer;

	/**
	 * Unique name that identifies the shared memory segment.
	 */
	private final String name;

	/**
	 * Whether the memory block has been closed and unlinked
	 */
	private boolean unlinked = false;

	@Override
	public String name() {
		return name;
	}

	@Override
	public Pointer pointer() {
		return pointer;
	}

	@Override
	public int size() {
		return size;
	}

	/**
	 * Unmap and close the shared memory. Necessary to eliminate the shared memory block
	 */
	@Override
	public synchronized void close() {
		if (unlinked) {
			return;
		}

		// Unmap the shared memory
		if (this.pointer != Pointer.NULL && CLibrary.INSTANCE.munmap(this.pointer, size) == -1) {
			throw new RuntimeException("munmap failed. Errno: " + Native.getLastError());
		}

		// Close the file descriptor
		if (CLibrary.INSTANCE.close(this.fd) == -1) {
			throw new RuntimeException("close failed. Errno: " + Native.getLastError());
		}

		// Unlink the shared memory object
		CLibrary.INSTANCE.shm_unlink(this.name);
		unlinked = true;
	}

	// name without leading slash
	ShmMacOS(final String name, final boolean create, final int size) {
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

		final boolean alreadyExists = prevSize >= 0;
		if (alreadyExists && prevSize < size) {
			throw new RuntimeException("Shared memory segment already exists with smaller dimensions, data type or format. "
				+ "Size of the existing shared memory segment cannot be smaller than the size of the proposed object. "
				+ "Size of existing shared memory segment: " + prevSize + ", size of proposed object: " + size);
		}
		//		shmFd = INSTANCE.shm_open(this.memoryName, O_RDWR, 0666);
		final int shmFd = MacosHelpers.INSTANCE.create_shared_memory(shm_name, size);
		if (shmFd < 0) {
			throw new RuntimeException("shm_open failed, errno: " + Native.getLastError());
		}
		final int shm_size = (int) getSHMSize(shmFd);

		Pointer pointer = CLibrary.INSTANCE.mmap(Pointer.NULL, shm_size, PROT_READ | PROT_WRITE, MAP_SHARED, shmFd, 0);
		if (pointer == Pointer.NULL) {
			CLibrary.INSTANCE.close(shmFd);
			CLibrary.INSTANCE.shm_unlink(shm_name);
			throw new RuntimeException("mmap failed, errno: " + Native.getLastError());
		}

		this.size = shm_size;
		this.name = withoutLeadingSlash(shm_name);
		this.fd = shmFd;
		this.pointer = pointer;
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
	 * @param shmFd
	 * 	the shared memory segment identifier
	 *
	 * @return the size in bytes of the shared memory segment
	 */
	private static long getSHMSize(final int shmFd) {
		if (shmFd < 0) {
			throw new RuntimeException("Invalid shmFd. It should be bigger than 0.");
		}

		final long size = MacosHelpers.INSTANCE.get_shared_memory_size(shmFd);
		if (size == -1) {
			// TODO remove macosInstance.unlink_shared_memory(null);;
			throw new RuntimeException("Failed to get shared memory segment size. Errno: " + Native.getLastError());
		}
		return size;
	}

	@Override
	public String toString() {
		return "ShmMacOS{" +
				"fd=" + fd +
				", size=" + size +
				", pointer=" + pointer +
				", name='" + name + '\'' +
				", unlinked=" + unlinked +
				'}';
	}
}