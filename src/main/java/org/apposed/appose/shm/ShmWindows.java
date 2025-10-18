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
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import org.apposed.appose.Platforms;
import org.apposed.appose.SharedMemory;
import org.apposed.appose.ShmFactory;

import static org.apposed.appose.Platforms.OperatingSystem.WINDOWS;
import static org.apposed.appose.shm.Shms.SHM_NAME_PREFIX_WIN;
import static org.apposed.appose.shm.Shms.SHM_SAFE_NAME_LENGTH;

/**
 * Windows-specific shared memory implementation.
 *
 * @author Carlos Garcia Lopez de Haro
 * @author Tobias Pietzsch
 */
public class ShmWindows implements ShmFactory {

	// name is WITHOUT prefix etc
	@Override
	public SharedMemory create(final String name, final boolean create, final long rsize) {
		if (Platforms.OS != WINDOWS) return null; // wrong platform
		return new SharedMemoryWindows(name, create, rsize);
	}

	private static class SharedMemoryWindows extends ShmBase<WinNT.HANDLE> {
		private SharedMemoryWindows(final String name, final boolean create, final long rsize) {
			super(prepareShm(name, create, rsize));
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

		private static ShmInfo<WinNT.HANDLE> prepareShm(String name, boolean create, long rsize) {
			String shmName;
			long prevSize;
			if (name == null) {
				do {
					shmName = Shms.makeFilename(SHM_SAFE_NAME_LENGTH, SHM_NAME_PREFIX_WIN);
					prevSize = getSHMSize(shmName);
				} while (prevSize >= 0);
			} else {
				shmName = name;
				prevSize = getSHMSize(shmName);
			}
			Shms.checkSize(shmName, prevSize, rsize);

			final WinNT.HANDLE hMapFile = Kernel32.INSTANCE.CreateFileMapping(
				WinBase.INVALID_HANDLE_VALUE,
				null,
				WinNT.PAGE_READWRITE,
				(int) (rsize >>> 32),        // hi 32 bits of size
				(int) (rsize & 0xFFFFFFFFL), // lo 32 bits of size
				"Local\\" + shmName
			);
			if (hMapFile == null) {
				throw new RuntimeException("Error creating shared memory: " + lastError());
			}

			final long shm_size = getSHMSize(hMapFile);

			// Map the shared memory
			Pointer pointer = Kernel32.INSTANCE.MapViewOfFile(
				hMapFile,
				WinNT.FILE_MAP_READ | WinNT.FILE_MAP_WRITE,
				0, // hi 32 bits of offset
				0, // lo 32 bits of offset
				0  // map entire file
			);
			if (isNull(pointer)) {
				Kernel32.INSTANCE.CloseHandle(hMapFile);
				throw new RuntimeException("Error mapping shared memory: " + lastError());
			}

			Pointer writePointer = Kernel32.INSTANCE.VirtualAllocEx(
				Kernel32.INSTANCE.GetCurrentProcess(),
				pointer,
				new BaseTSD.SIZE_T(rsize),
				WinNT.MEM_COMMIT,
				WinNT.PAGE_READWRITE
			);
			if (isNull(writePointer)) {
				cleanup(pointer, writePointer, hMapFile);
				throw new RuntimeException("Error committing to the shared memory pages: " + lastError());
			}

			ShmInfo<WinNT.HANDLE> info = new ShmInfo<>();
			info.name = shmName;
			info.rsize = rsize; // REQUESTED size
			info.size = shm_size; // ALLOCATED size
			info.pointer = pointer;
			info.writePointer = writePointer;
			info.handle = hMapFile;
			info.unlinkOnClose = create;
			return info;
		}

		/**
		 * Try to open {@code name} and get its size in bytes.
		 *
		 * @param name name with leading slash
		 * @return size in bytes, or -1 if the shared memory segment could not be opened
		 */
		// name is WITH prefix etc
		private static long getSHMSize(final String name) {
			WinNT.HANDLE hMapFile = Kernel32.INSTANCE.OpenFileMapping(WinNT.FILE_MAP_READ, false, "Local\\" + name);
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
				throw new RuntimeException("MapViewOfFile failed with error: " + lastError());
			}
			final Kernel32.MEMORY_BASIC_INFORMATION mbi = new Kernel32.MEMORY_BASIC_INFORMATION();

			if (Kernel32.INSTANCE.VirtualQueryEx(
					Kernel32.INSTANCE.GetCurrentProcess(),
					pSharedMemory,
					mbi,
					new BaseTSD.SIZE_T(mbi.size())
				).intValue() == 0)
			{
				throw new RuntimeException("Failed to get shared memory segment size: " + lastError());
			}
			final long size = mbi.regionSize.longValue();

			Kernel32.INSTANCE.UnmapViewOfFile(pSharedMemory);

			return size;
		}
	}

	private static void cleanup(Pointer pointer, Pointer writePointer, WinNT.HANDLE handle) {
		if (!isNull(writePointer)) Kernel32.INSTANCE.UnmapViewOfFile(writePointer);
		if (!isNull(pointer)) Kernel32.INSTANCE.UnmapViewOfFile(pointer);
		if (handle != null) Kernel32.INSTANCE.CloseHandle(handle);
	}

	private static boolean isNull(Pointer p) { return p == null || p == Pointer.NULL; }

	private static String lastError() {
		int code = Kernel32.INSTANCE.GetLastError();
		return new Win32Exception(code).getMessage() + " (" + code + ")";
	}
}
