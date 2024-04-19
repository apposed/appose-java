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

import java.io.Closeable;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.util.UUID;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinNT;

import org.apposed.appose.system.PlatformDetection;

/**
 * TODO separate unlink and close
 * TODO separate unlink and close
 * TODO separate unlink and close
 * TODO separate unlink and close
 * TODO separate unlink and close
 * TODO separate unlink and close
 * Interface to interact with shared memory segments retrieving the underlying information
 *
 * @author Carlos Garcia Lopez de Haro
 */
public interface SharedMemoryArray extends Closeable {

	/**
	 * Constant to specify that the shared memory segment that is going to be
	 * open is only for reading
	 */
	int O_RDONLY = 0;

	/**
	 * Constant to specify that the shared memory segment that is going to be
	 * open is for reading and/or writing
	 */
	int O_RDWR = 2;

	/**
	 * Constant to specify that the shared memory segment that is going to be
	 * open will be created if it does not exist
	 */
	int O_CREAT = 64;

	/**
	 * Constant to specify that the shared memory regions mapped can be read but
	 * not written
	 */
	int PROT_READ = 0x1;

	/**
	 * Constant to specify that the shared memory regions mapped can be written
	 */
	int PROT_WRITE = 0x2;

	/**
	 * Constant to specify that the shared memory regions mapped can be shared
	 * with other processes
	 */
	int MAP_SHARED = 0x01;

	/**
	 * List of special characters that should not be used to name shared memory
	 * segments
	 */
	String[] SPECIAL_CHARS_LIST = new String[] {"/", "\\", "#", "·", "!", "¡", "¿", "?", "@", "|", "$", ">", "<", ";"};










	/**
	 * Create a shared memory segment of the given {@code size} (in bytes).
	 * <p>
	 * TODO: Explain what happens if the memory segment exists, is smaller/larger than the requested size.
	 *
	 * @param size
	 * 	byte size wanted to allocate
	 *
	 * @return a new {@link SharedMemoryArray} instance
	 */
	static SharedMemoryArray create(int size) {
		switch (PlatformDetection.getOs()) {
			case OSX:
				return SharedMemoryArrayMacOS.create(size);
			case WINDOWS:
			case LINUX:
			default:
				throw new UnsupportedOperationException("TODO");
		}
	}

	/**
	 * Create a random unique name for a shared memory segment
	 * @return a random unique name for a shared memory segment
	 */
	static String createShmName() { // TODO: Does this have to be public? move to utility class? only use locally?
		switch (PlatformDetection.getOs()) {
			case OSX:
				return SharedMemoryArrayMacOS.createShmName();
			case WINDOWS:
//				return "Local" + File.separator + UUID.randomUUID().toString();
			case LINUX:
//				return "/shm-" + UUID.randomUUID();
			default:
				throw new UnsupportedOperationException("TODO");
		}
	}

	/**
	 * Wraps an existing shared memory segment to allow the user its manipulation.
	 * The name should be the same as the name of the shared memory segment.
	 * <p>
	 * The shared memory segment has a defined size and characteristics such as how the
	 * nd arrays are saved (with fortran or c order, with Numpy npy format or not, ...).
	 * <p>
	 * The {@link SharedMemoryArray} instance retrieved can be used to modify the underlying shared
	 * memory segment
	 *
	 * @param name
	 * 	name of the shared memory segment to be accessed
	 *
	 * @return a {@link SharedMemoryArray} instance that helps handling the data written to the shared memory region
	 */
	static SharedMemoryArray read(String name) {
		switch (PlatformDetection.getOs()) {
			case OSX:
//				return SharedMemoryArrayMacOS.read(name);
			case WINDOWS:
//				return SharedMemoryArrayWin.read(name);
			case LINUX:
			default:
//				return SharedMemoryArrayLinux.read(name);
				throw new UnsupportedOperationException("TODO");
		}
	}

	/**
	 * Get the unique name for this shared memory segment. When creating a new
	 * {@code SharedMemoryArray} a name can be supplied, and if not it will be
	 * generated automatically. Two shared memory blocks existing at the same
	 * time cannot share the name.
	 * <p>
	 * In Unix based systems, Shared memory segment names start with {@code
	 * "/"}, for example {@code "/shm_block"} In Windows shared memory block
	 * names start either with {@code "Global\\"} or {@code "Local\\"}. Example:
	 * {@code "Local\\shm_block"}
	 *
	 * @return the unique name for the shared memory
	 */
	String getName();

	/**
	 * Get the unique name for this shared memory segment, as the Python package
	 * {@code multiprocessing.shared_memory} requires it.
	 * <p>
	 * For Unix based systems it removes the initial {@code "/"}, for example,
	 * {@code "/shm_block"} becomes {@code "shm_block"}. In Windows shared
	 * memory block names start either with {@code "Global\\"} or {@code
	 * "Local\\"}, this is also removed when providing a shared memory name to
	 * Python. For Example, {@code "Local\\shm_block"} becomes {@code
	 * "shm_block"}.
	 *
	 * @return the unique name for the shared memory, for use in Python.
	 */
	String getNameForPython();

	/**
	 * @return the pointer to the shared memory segment
	 */
	Pointer getPointer();

	/**
	 * @return get number of bytes in the shared memory segment
	 */
	int getSize();

}
