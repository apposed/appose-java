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

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import org.apposed.appose.SharedMemory;
import org.apposed.appose.ShmFactory;

import static org.apposed.appose.shm.ShmUtils.SHM_NAME_PREFIX_WIN;
import static org.apposed.appose.shm.ShmUtils.SHM_SAFE_NAME_LENGTH;

/**
 * Windows-specific shared memory implementation.
 * <p>
 * TODO separate unlink and close
 * </p>
 *
 * @author Carlos Garcia Lopez de Haro
 * @author Tobias Pietzsch
 */
public class ShmWindows implements ShmFactory {

	// name is WITHOUT prefix etc
	public SharedMemory create(final String name, final boolean create, final int size) {
		if (ShmUtils.os != ShmUtils.OS.WINDOWS) return null; // wrong platform
		return new SharedMemoryWindows(name, create, size);
	}

	private static class SharedMemoryWindows implements SharedMemory {
		String shm_name;
		long prevSize;
		private SharedMemoryWindows(final String name, final boolean create, final int size) {
			if(name ==null)

			{
				do {
					shm_name = ShmUtils.make_filename(SHM_SAFE_NAME_LENGTH, SHM_NAME_PREFIX_WIN);
					prevSize = getSHMSize(shm_name);
				} while (prevSize >= 0);
			} else

			{
				shm_name = nameMangle_TODO(name);
				prevSize = getSHMSize(shm_name);
			}
			ShmUtils.checkSize(shm_name, prevSize, size);

			final WinNT.HANDLE shm_hMapFile = Kernel32.INSTANCE.CreateFileMapping(
							WinBase.INVALID_HANDLE_VALUE,
							null,
							WinNT.PAGE_READWRITE,
							0,
							size,
							shm_name
			);
			if(hMapFile ==null)

			{
				throw new RuntimeException("Error creating shared memory array. CreateFileMapping failed: " + Kernel32.INSTANCE.GetLastError());
			}

			final int shm_size = (int) getSHMSize(shm_hMapFile);

			// Map the shared memory
			Pointer pointer = Kernel32.INSTANCE.MapViewOfFile(
							hMapFile,
							WinNT.FILE_MAP_WRITE,
							0,
							0,
							size
			);
			if(pointer ==null)

			{
				Kernel32.INSTANCE.CloseHandle(hMapFile);
				throw new RuntimeException("Error creating shared memory array. " + Kernel32.INSTANCE.GetLastError());
			}

			Pointer writePointer = Kernel32.INSTANCE.VirtualAllocEx(Kernel32.INSTANCE.GetCurrentProcess(),
							pointer, new BaseTSD.SIZE_T(size), WinNT.MEM_COMMIT, WinNT.PAGE_READWRITE);
			if(writePointer ==null)

			{
				close();
				throw new RuntimeException("Error committing to the shared memory pages. Errno: " + Kernel32.INSTANCE.GetLastError());
			}

			this.size =shm_size;
			this.name =

			nameUnmangle_TODO(shm_name);
			this.hMapFile =shm_hMapFile;
			this.pointer =pointer;
			this.writePointer =writePointer;
		}

		/**
		 * reference to the file that covers the shared memory region
		 */
		private WinNT.HANDLE hMapFile;

		/**
		 * Size in bytes
		 */
		private final int size;

		/**
		 * Pointer referencing the shared memory
		 */
		private final Pointer pointer;

		private final Pointer writePointer;

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
		public long size() {
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
			if (writePointer != null) {
				Kernel32.INSTANCE.UnmapViewOfFile(this.writePointer);
			}
			Kernel32.INSTANCE.UnmapViewOfFile(pointer);
			Kernel32.INSTANCE.CloseHandle(hMapFile);
			unlinked = true;
		}

		// TODO equivalent of removing slash
		private String nameUnmangle_TODO (String memoryName){
			return memoryName;
		}

		// TODO equivalent of adding slash
		//      Do we need the "Local\" prefix?
		private String nameMangle_TODO (String memoryName){
			//		if (!memoryName.startsWith("Local" + File.separator) && !memoryName.startsWith("Global" + File.separator))
			//			memoryName = "Local" + File.separator + memoryName;
			return memoryName;
		}

		// name is WITH prefix etc
		private static boolean checkSHMExists ( final String name){
			final WinNT.HANDLE hMapFile = Kernel32.INSTANCE.OpenFileMapping(WinNT.FILE_MAP_READ, false, name);
			if (hMapFile == null) {
				return false;
			} else {
				Kernel32.INSTANCE.CloseHandle(hMapFile);
				return true;
			}
		}

		/**
		 * Try to open {@code name} and get its size in bytes.
		 *
		 * @param name name with leading slash
		 * @return size in bytes, or -1 if the shared memory segment couuld not be opened
		 */
		// name is WITH prefix etc
		private static long getSHMSize(final String name) {
			WinNT.HANDLE hMapFile = Kernel32.INSTANCE.OpenFileMapping(WinNT.FILE_MAP_READ, false, name);
			if (hMapFile == null) {
				return -1;
			} else {
				return getSHMSize(hMapFile);
			}
		}

		/**
		 * Method to find the size of an already created shared memory segment
		 *
		 * @param hMapFile
		 * 	the shared memory segment handle
		 *
		 * @return the size in bytes of the shared memory segment
		 */
		private static long getSHMSize(final WinNT.HANDLE hMapFile) {
			if (hMapFile == null) {
				throw new NullPointerException("hMapFile is null.");
			}

			// Map the shared memory object into the current process's address space
			final Pointer pSharedMemory = Kernel32.INSTANCE.MapViewOfFile(hMapFile, WinNT.FILE_MAP_READ, 0, 0, 0);
			if (pSharedMemory == null) {
				Kernel32.INSTANCE.CloseHandle(hMapFile);
				throw new RuntimeException("MapViewOfFile failed with error: " + Kernel32.INSTANCE.GetLastError());
			}
			final Kernel32.MEMORY_BASIC_INFORMATION mbi = new Kernel32.MEMORY_BASIC_INFORMATION();

			if (
							Kernel32.INSTANCE.VirtualQueryEx(
											Kernel32.INSTANCE.GetCurrentProcess(), pSharedMemory,
											mbi, new BaseTSD.SIZE_T((long) mbi.size())
							).intValue() == 0) {
				throw new RuntimeException("Failed to get shared memory segment size. Errno: " + Kernel32.INSTANCE.GetLastError());
			}
			final int size = mbi.regionSize.intValue();

			Kernel32.INSTANCE.UnmapViewOfFile(pSharedMemory);
			Kernel32.INSTANCE.CloseHandle(hMapFile);

			return size;
		}

		@Override
		public String toString() {
			return "ShmWindows{" +
							"hMapFile=" + hMapFile +
							", size=" + size +
							", pointer=" + pointer +
							", writePointer=" + writePointer +
							", name='" + name + '\'' +
							", unlinked=" + unlinked +
							'}';
		}
	}
}