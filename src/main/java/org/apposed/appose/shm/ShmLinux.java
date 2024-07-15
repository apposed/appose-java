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
import org.apposed.appose.SharedMemory;
import org.apposed.appose.ShmFactory;

import static org.apposed.appose.shm.ShmUtils.MAP_SHARED;
import static org.apposed.appose.shm.ShmUtils.O_CREAT;
import static org.apposed.appose.shm.ShmUtils.O_RDONLY;
import static org.apposed.appose.shm.ShmUtils.O_RDWR;
import static org.apposed.appose.shm.ShmUtils.PROT_READ;
import static org.apposed.appose.shm.ShmUtils.PROT_WRITE;
import static org.apposed.appose.shm.ShmUtils.SHM_NAME_PREFIX_POSIX;
import static org.apposed.appose.shm.ShmUtils.SHM_SAFE_NAME_LENGTH;
import static org.apposed.appose.shm.ShmUtils.withLeadingSlash;
import static org.apposed.appose.shm.ShmUtils.withoutLeadingSlash;

/**
 * Linux-specific shared memory implementation.
 * <p>
 * TODO separate unlink and close
 * </p>
 *
 * @author Carlos Garcia Lopez de Haro
 * @author Tobias Pietzsch
 */
public class ShmLinux implements ShmFactory {

	@Override
	public SharedMemory create(final String name, final boolean create, final int size) {
		if (ShmUtils.os != ShmUtils.OS.LINUX) return null; // wrong platform
		return new SharedMemoryLinux(name, create, size);
	}

	private static class SharedMemoryLinux implements SharedMemory {

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

		// name without leading slash
		private SharedMemoryLinux(final String name, final boolean create, final int size) {
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
			ShmUtils.checkSize(shm_name, prevSize, size);

			final int shmFd = LibRtOrC.shm_open(shm_name, O_CREAT | O_RDWR, 0666);
			if (shmFd < 0) {
				throw new RuntimeException("shm_open failed, errno: " + Native.getLastError());
			}
			if (create) {
				if (LibRtOrC.ftruncate(shmFd, (int) size) == -1) {
					LibRtOrC.close(shmFd);
					throw new RuntimeException("ftruncate failed, errno: " + Native.getLastError());
				}
			}
			final int shm_size = (int) getSHMSize(shmFd);

			Pointer pointer = LibRtOrC.mmap(Pointer.NULL, shm_size, PROT_READ | PROT_WRITE, MAP_SHARED, shmFd, 0);
			if (pointer == Pointer.NULL) {
				LibRtOrC.close(shmFd);
				LibRtOrC.shm_unlink(shm_name);
				throw new RuntimeException("mmap failed, errno: " + Native.getLastError());
			}

			this.size = shm_size;
			this.name = withoutLeadingSlash(shm_name);
			this.fd = shmFd;
			this.pointer = pointer;
		}

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

			// Unmap the shared memory
			if (this.pointer != Pointer.NULL && LibRtOrC.munmap(this.pointer, size) == -1) {
				throw new RuntimeException("munmap failed. Errno: " + Native.getLastError());
			}

			// Close the file descriptor
			if (LibRtOrC.close(this.fd) == -1) {
				throw new RuntimeException("close failed. Errno: " + Native.getLastError());
			}

			// Unlink the shared memory object
			LibRtOrC.shm_unlink(this.name);
			unlinked = true;
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
				// TODO remove LibRtOrC.close(shmFd);
				throw new RuntimeException("Failed to get shared memory segment size. Errno: " + Native.getLastError());
			}
			return size;
		}

		@Override
		public String toString() {
			return "ShmLinux{" +
							"fd=" + fd +
							", size=" + size +
							", pointer=" + pointer +
							", name='" + name + '\'' +
							", unlinked=" + unlinked +
							'}';
		}


		private static class LibRtOrC {

			/**
			 * Depending on the computer, some might work with LibRT or LibC to create SHM segments.
			 * Thus if true use librt if false, use libc instance
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

			static int ftruncate(int fd, int length) {
				if (useLibRT) {
					try {
						return LibRt.INSTANCE.ftruncate(fd, length);
					} catch (Exception ex) {
						useLibRT = false;
					}
				}
				return CLibrary.INSTANCE.ftruncate(fd, length);
			}


			static Pointer mmap(Pointer addr, int length, int prot, int flags, int fd, int offset) {
				if (useLibRT) {
					try {
						return LibRt.INSTANCE.mmap(addr, length, prot, flags, fd, offset);
					} catch (Exception ex) {
						useLibRT = false;
					}
				}
				return CLibrary.INSTANCE.mmap(addr, length, prot, flags, fd, offset);
			}

			static int munmap(Pointer addr, int length) {
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
