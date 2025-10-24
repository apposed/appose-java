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
import org.apposed.appose.SharedMemory;
import org.apposed.appose.ShmFactory;
import org.apposed.appose.util.Platforms;

import static org.apposed.appose.shm.Shms.MAP_SHARED;
import static org.apposed.appose.shm.Shms.O_CREAT;
import static org.apposed.appose.shm.Shms.O_RDONLY;
import static org.apposed.appose.shm.Shms.O_RDWR;
import static org.apposed.appose.shm.Shms.PROT_READ;
import static org.apposed.appose.shm.Shms.PROT_WRITE;
import static org.apposed.appose.shm.Shms.SHM_NAME_PREFIX_POSIX;
import static org.apposed.appose.shm.Shms.SHM_SAFE_NAME_LENGTH;
import static org.apposed.appose.shm.Shms.withLeadingSlash;
import static org.apposed.appose.shm.Shms.withoutLeadingSlash;

/**
 * Linux-specific shared memory implementation.
 *
 * @author Carlos Garcia Lopez de Haro
 * @author Tobias Pietzsch
 * @author Curtis Rueden
 */
public class ShmLinux implements ShmFactory {

	@Override
	public SharedMemory create(final String name, final boolean create, final long rsize) {
		if (!Platforms.isLinux()) return null; // wrong platform
		return new SharedMemoryLinux(name, create, rsize);
	}

	private static class SharedMemoryLinux extends ShmBase<Integer> {

		// name without leading slash
		private SharedMemoryLinux(final String name, final boolean create, final long rsize) {
			super(prepareShm(name, create, rsize));
		}

		@Override
		protected void doUnlink() {
			LibRtOrC.shm_unlink(name());
		}

		@Override
		protected void doClose() {
			// Unmap the shared memory
			if (info.pointer != Pointer.NULL && LibRtOrC.munmap(info.pointer, size()) == -1) {
				throw new RuntimeException("munmap failed. Errno: " + Native.getLastError());
			}

			// Close the file descriptor
			if (LibRtOrC.close(info.handle) == -1) {
				throw new RuntimeException("close failed. Errno: " + Native.getLastError());
			}
		}

		private static ShmInfo<Integer> prepareShm(String name, boolean create, long rsize) {
			String shmName;
			long prevSize;
			if (name == null) {
				do {
					shmName = Shms.makeFilename(SHM_SAFE_NAME_LENGTH, SHM_NAME_PREFIX_POSIX);
					prevSize = getSHMSize(shmName);
				} while (prevSize >= 0);
			} else {
				shmName = withLeadingSlash(name);
				prevSize = getSHMSize(shmName);
			}
			Shms.checkSize(shmName, prevSize, rsize);

			final int shmFd = LibRtOrC.shm_open(shmName, O_CREAT | O_RDWR, 0666);
			if (shmFd < 0) {
				throw new RuntimeException("shm_open failed, errno: " + Native.getLastError());
			}
			if (create) {
				if (LibRtOrC.ftruncate(shmFd, rsize) == -1) {
					LibRtOrC.close(shmFd);
					throw new RuntimeException("ftruncate failed, errno: " + Native.getLastError());
				}
			}
			final long shmSize = getSHMSize(shmFd);

			Pointer pointer = LibRtOrC.mmap(Pointer.NULL, shmSize, PROT_READ | PROT_WRITE, MAP_SHARED, shmFd, 0);
			if (pointer == Pointer.NULL) {
				LibRtOrC.close(shmFd);
				LibRtOrC.shm_unlink(shmName);
				throw new RuntimeException("mmap failed, errno: " + Native.getLastError());
			}

			ShmInfo<Integer> info = new ShmInfo<>();
			info.name = withoutLeadingSlash(shmName);
			info.rsize = rsize; // REQUESTED size
			info.size = shmSize; // ALLOCATED size
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
			final int shmFd = LibRtOrC.shm_open(name, O_RDONLY, 0700);
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

			final long size = LibRtOrC.lseek(shmFd, 0, LibRtOrC.SEEK_END);
			if (size == -1) {
				throw new RuntimeException("Failed to get shared memory segment size. Errno: " + Native.getLastError());
			}
			return size;
		}

		private static class LibRtOrC {

			/**
			 * Depending on the computer, some might work with LibRT or LibC to create SHM segments.
			 * Thus: if true use librt, if false use libc instance.
			 */
			private static boolean useLibRT = true;

			static final int SEEK_SET = 0;
			static final int SEEK_CUR = 1;
			static final int SEEK_END = 2;

			static int shm_open(String name, int oflag, int mode) {
				if (useLibRT) {
					try {
						return LibRt.INSTANCE.shm_open(name, oflag, mode);
					} catch (Exception ex) {
						useLibRT = false;
					}
				}
				return CLibrary.INSTANCE.shm_open(name, oflag, mode);
			}

			static long lseek(int fd, long offset, int whence) {
				if (useLibRT) {
					try {
						return LibRt.INSTANCE.lseek(fd, offset, whence);
					} catch (Exception ex) {
						useLibRT = false;
					}
				}
				return CLibrary.INSTANCE.lseek(fd, offset, whence);
			}

			static int ftruncate(int fd, long length) {
				if (useLibRT) {
					try {
						return LibRt.INSTANCE.ftruncate(fd, length);
					} catch (Exception ex) {
						useLibRT = false;
					}
				}
				return CLibrary.INSTANCE.ftruncate(fd, length);
			}

			static Pointer mmap(Pointer addr, long length, int prot, int flags, int fd, long offset) {
				if (useLibRT) {
					try {
						return LibRt.INSTANCE.mmap(addr, length, prot, flags, fd, offset);
					} catch (Exception ex) {
						useLibRT = false;
					}
				}
				return CLibrary.INSTANCE.mmap(addr, length, prot, flags, fd, offset);
			}

			static int munmap(Pointer addr, long length) {
				if (useLibRT) {
					try {
						return LibRt.INSTANCE.munmap(addr, length);
					} catch (Exception ex) {
						useLibRT = false;
					}
				}
				return CLibrary.INSTANCE.munmap(addr, length);
			}

			static int close(int fd) {
				if (useLibRT) {
					try {
						return LibRt.INSTANCE.close(fd);
					} catch (Exception ex) {
						useLibRT = false;
					}
				}
				return CLibrary.INSTANCE.close(fd);
			}

			static int shm_unlink(String name) {
				if (useLibRT) {
					try {
						return LibRt.INSTANCE.shm_unlink(name);
					} catch (Exception ex) {
						useLibRT = false;
					}
				}
				return CLibrary.INSTANCE.shm_unlink(name);
			}
		}
	}
}
