/*-
 * #%L
 * Appose: multi-language interprocess cooperation with shared memory.
 * %%
 * Copyright (C) 2023 - 2024 Appose developers.
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
	@Override
	public SharedMemory create(final String name, final boolean create, final int size) {
		if (ShmUtils.os != ShmUtils.OS.WINDOWS) return null; // wrong platform
		return new SharedMemoryWindows(name, create, size);
	}

	private static class SharedMemoryWindows extends ShmBase<WinNT.HANDLE> {

		private SharedMemoryWindows(final String name, final boolean create, final int size) {
			super(prepareShm(name, create, size));
		}

		@Override
		protected void doClose() {
			cleanup(info.pointer, info.writePointer, info.handle);
		}

		@Override
		protected void doUnlink() {
			// Note: The shared memory object will be deleted when all processes
			// have closed their handles to it. There's no direct "unlink"
			// equivalent in Windows like there is in POSIX systems.
		}

		private static ShmInfo<WinNT.HANDLE> prepareShm(String name, boolean create, int size) {
			String shm_name;
			long prevSize;
			if (name == null) {
				do {
					shm_name = ShmUtils.make_filename(SHM_SAFE_NAME_LENGTH, SHM_NAME_PREFIX_WIN);
					prevSize = getSHMSize(shm_name);
				} while (prevSize >= 0);
			} else {
				shm_name = nameMangle_TODO(name);
				prevSize = getSHMSize(shm_name);
			}
			ShmUtils.checkSize(shm_name, prevSize, size);

			final WinNT.HANDLE hMapFile = Kernel32.INSTANCE.CreateFileMapping(
				WinBase.INVALID_HANDLE_VALUE,
				null,
				WinNT.PAGE_READWRITE,
				0,
				size,
				shm_name
			);
			if (hMapFile == null) {
				throw new RuntimeException("Error creating shared memory array. CreateFileMapping failed: " + Kernel32.INSTANCE.GetLastError());
			}

			final int shm_size = (int) getSHMSize(hMapFile);

			// Map the shared memory
			Pointer pointer = Kernel32.INSTANCE.MapViewOfFile(
				hMapFile,
				WinNT.FILE_MAP_WRITE,
				0,
				0,
				size
			);
			if (isNull(pointer)) {
				Kernel32.INSTANCE.CloseHandle(hMapFile);
				throw new RuntimeException("Error creating shared memory array. " + Kernel32.INSTANCE.GetLastError());
			}

			Pointer writePointer = Kernel32.INSTANCE.VirtualAllocEx(Kernel32.INSTANCE.GetCurrentProcess(),
				pointer, new BaseTSD.SIZE_T(size), WinNT.MEM_COMMIT, WinNT.PAGE_READWRITE);
			if (isNull(writePointer)) {
				cleanup(pointer, writePointer, hMapFile);
				throw new RuntimeException("Error committing to the shared memory pages. Errno: " + Kernel32.INSTANCE.GetLastError());
			}

			ShmInfo<WinNT.HANDLE> info = new ShmInfo<>();
			info.size = shm_size;
			info.name = nameUnmangle_TODO(shm_name);
			info.pointer = pointer;
			info.writePointer = writePointer;
			info.handle = hMapFile;
			info.unlinkOnClose = create;
			return info;
		}

		// TODO equivalent of removing slash
		private static String nameUnmangle_TODO (String memoryName){
			return memoryName;
		}

		// TODO equivalent of adding slash
		//      Do we need the "Local\" prefix?
		private static String nameMangle_TODO (String memoryName){
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
	}

	private static void cleanup(Pointer pointer, Pointer writePointer, WinNT.HANDLE handle) {
		if (!isNull(writePointer)) Kernel32.INSTANCE.UnmapViewOfFile(writePointer);
		if (!isNull(pointer)) Kernel32.INSTANCE.UnmapViewOfFile(pointer);
		if (handle != null) Kernel32.INSTANCE.CloseHandle(handle);
	}

	private static boolean isNull(Pointer p) { return p == null || p == Pointer.NULL; }
}
