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
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.UUID;

public class SharedMemoryArrayMacOS implements SharedMemoryArray {

	/**
	 * Instance of the CLibrary JNI containing the methods to interact with the Shared memory segments
	 */
	private static final CLibrary INSTANCE = CLibrary.INSTANCE;

	/**
	 * Instance of the  JNI containing the methods that help ineracting with shared memory segments
	 * and are not contained in the {@link CLibrary}.
	 */
	private static final MacosHelpers MACOS_INSTANCE = MacosHelpers.INSTANCE;

	/**
	 * Maximum length of the name that can be given to a shared memory region
	 */
	private static final int MACOS_MAX_LENGTH = 30;



	/**
	 * Pointer referencing the shared memory byte array
	 */
	private final Pointer pSharedMemory;

	/**
	 * File descriptor value of the shared memory segment
	 */
	private final int shmFd;

	/**
	 * Name of the file containing the shared memory segment. In Unix based systems consits of "/" + file_name.
	 * In Linux the shared memory segments can be inspected at /dev/shm.
	 * For MacOS the name can only have a certain length, {@value #MACOS_MAX_LENGTH}
	 */
	private final String memoryName;

	/**
	 * Size of the shared memory block
	 */
	private final int size;

	/**
	 * Whether the memory block has been closed and unlinked
	 */
	private boolean unlinked = false;



	@Override
	public String getName() {
		return memoryName;
	}

	@Override
	public String getNameForPython() {
		return memoryName.substring("/".length());
	}

	@Override
	public String name() {
		return getNameForPython(); // TODO fix name generation and reporting
	}

	@Override
	public Pointer getPointer() {
		return pSharedMemory;
	}

	@Override
	public int getSize() {
		return size;
	}

	@Override
	public long size() {
		return size;
	}

	/**
	 * Unmap and close the shared memory. Necessary to eliminate the shared memory block
	 */
	@Override
	public void close() throws IOException {
		if (this.unlinked) {
			return;
		}

		// Unmap the shared memory
		if (this.pSharedMemory != Pointer.NULL && INSTANCE.munmap(this.pSharedMemory, size) == -1) {
			throw new RuntimeException("munmap failed. Errno: " + Native.getLastError());
		}

		// Close the file descriptor
		if (INSTANCE.close(this.shmFd) == -1) {
			throw new RuntimeException("close failed. Errno: " + Native.getLastError());
		}

		// Unlink the shared memory object
		INSTANCE.shm_unlink(this.memoryName);
		unlinked = true;

	}



	private SharedMemoryArrayMacOS(int size) throws FileAlreadyExistsException
	{
		this(createShmName(), size);
	}

	private SharedMemoryArrayMacOS(String name, int size) throws FileAlreadyExistsException
	{
		this.size = size;
		this.memoryName = name;

		boolean alreadyExists = false;
		int shmFd = INSTANCE.shm_open(memoryName, O_RDONLY, 0700);
		long prevSize = 0;
		if (shmFd != -1) {
			prevSize = getSHMSize(shmFd);
			alreadyExists = true;
		}

		if (alreadyExists && prevSize < size) {
			throw new FileAlreadyExistsException("Shared memory segment already exists with smaller dimensions, data type or format. "
				+ "Size of the existing shared memory segment cannot be smaller than the size of the proposed object. "
				+ "Size of existing shared memory segment: " + prevSize + ", size of proposed object: " + size);
		}
		//		shmFd = INSTANCE.shm_open(this.memoryName, O_RDWR, 0666);
		shmFd = MACOS_INSTANCE.create_shared_memory(memoryName, size);
		if (shmFd < 0) {
			throw new RuntimeException("shm_open failed, errno: " + Native.getLastError());
		}

		pSharedMemory = INSTANCE.mmap(Pointer.NULL, this.size, PROT_READ | PROT_WRITE, MAP_SHARED, shmFd, 0);
		if (pSharedMemory == Pointer.NULL) {
			INSTANCE.close(shmFd);
			throw new RuntimeException("mmap failed, errno: " + Native.getLastError());
		}

		this.shmFd = shmFd;
	}











	static SharedMemoryArrayMacOS create(int size) {
		try {
			return new SharedMemoryArrayMacOS(size);
		} catch (FileAlreadyExistsException e) {
			throw new RuntimeException("Unexpected error.", e);
		}
	}

	/**
	 * Create a random unique name for a shared memory segment
	 * @return a random unique name for a shared memory segment
	 */
	private static String createShmName() {
		return ("/shm-" + UUID.randomUUID()).substring(0, SharedMemoryArrayMacOS.MACOS_MAX_LENGTH);
	}

	/**
	 * Method to find the size of an already created shared memory segment
	 *
	 * @param shmFd
	 * 	the shared memory segment identifier
	 *
	 * @return the size in bytes of the shared memory segment
	 */
	private static long getSHMSize(int shmFd) {
		if (shmFd < 0) {
			throw new RuntimeException("Invalid shmFd. It should be bigger than 0.");
		}

		final long size = MACOS_INSTANCE.get_shared_memory_size(shmFd);
		if (size == -1) {
			// TODO remove macosInstance.unlink_shared_memory(null);;
			throw new RuntimeException("Failed to get shared memory segment size. Errno: " + Native.getLastError());
		}
		return size;
	}


}