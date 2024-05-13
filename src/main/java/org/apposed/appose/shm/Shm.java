package org.apposed.appose.shm;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.linux.ErrNo;

import static org.apposed.appose.shm.Shm.OS.OSX;

public class Shm {

	enum OS {
		WINDOWS(false), //
		OSX(true), //
		LINUX(true);

		private final boolean posix;

		OS(boolean posix)
		{
			this.posix = posix;
		}

		public boolean isPosix() {
			return posix;
		}

		public static OS detect()
		{
			final String os_name = System.getProperty("os.name").toLowerCase();

			if (os_name.startsWith("windows")) {
				return WINDOWS;
			} else if (os_name.startsWith("mac")) {
				return OSX;
			} else if (os_name.contains("linux") || os_name.endsWith("ix")) {
				return LINUX;
			}

			throw new RuntimeException("OS detection failed. System.getProperty(\"os.name\") = " + System.getProperty("os.name"));
		}
	}

	private static final OS os = OS.detect();

	private static final boolean prepend_leading_slash = os.isPosix();

	private static final int O_RDWR = 2;
	private static final int O_CREAT = 512;
	private static final int O_EXCL = 2048;
	private static final int O_CREX = O_CREAT | O_EXCL;


	/**
	 * Constant to specify that the shared memory regions mapped can be read but
	 * not written
	 */
	private static final int PROT_READ = 0x1;

	/**
	 * Constant to specify that the shared memory regions mapped can be written
	 */
	private static final int PROT_WRITE = 0x2;

	/**
	 * Constant to specify that the shared memory regions mapped can be shared
	 * with other processes
	 */
	private static final int MAP_SHARED = 0x01;



	/** FreeBSD (and perhaps other BSDs) limit names to 14 characters. */
	private static final int SHM_SAFE_NAME_LENGTH = 14;

	/** Shared memory block name prefix. */
	private static final String SHM_NAME_PREFIX = os.isPosix() ? "/psm_" : "wnsm_";

	/** Creates a random filename for the shared memory object. */
	private static String make_filename() {
		// number of random bytes to use for name
		long nbytes = (SHM_SAFE_NAME_LENGTH - SHM_NAME_PREFIX.length()) / 2;
		assert nbytes >= 2; // 'SHM_NAME_PREFIX too long'
		String name = SHM_NAME_PREFIX + token_hex(nbytes);
		assert name.length() <= SHM_SAFE_NAME_LENGTH;
		return name;
	}

	// TODO revise
	/*
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[length / 2];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(length);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
	 */
	private static String token_hex(long nbytes) {
		StringBuilder sb = new StringBuilder();
		for (int b=0; b<nbytes; b++) {
			String s = Long.toHexString(Double.doubleToLongBits(Math.random()) & 0xff);
			assert s.length() >= 1 && s.length() <= 2;
			if (s.length() == 1) sb.append("0");
			sb.append(s);
		}
		return sb.toString();
	}

	private static long sizeFromFileDescriptor(int fd) {
		LibC.Stat stats = new LibC.Stat();
		int result = LibC.INSTANCE.fstat(fd, stats);
		return stats.st_size;
	}

	// TODO start by taking structure from SharedMemory, actual implementation from SharedMemoryArrayMacOS


	private String name;
	private long size;
	private int fd;
	private Pointer mmap;



	public Shm(String name, boolean create, long size) {

		if (size < 0) {
			throw new IllegalArgumentException("'size' must be a positive integer");
		}
		if (create && size == 0) {
			throw new IllegalArgumentException("'size' must be a positive number different from zero");
		}
		if (!create && name == null) {
			throw new IllegalArgumentException("'name' can only be null if create=true");
		}

		final int flags = create ? O_CREX | O_RDWR : O_RDWR;
		final int mode = 0600;

		if ( os == OSX ) {
			if (name == null) {
				while (true) {
					name = make_filename();
					fd = LibC.INSTANCE.shm_open(
							name,
							flags,
							mode
					);
					if (fd == -1) {
						final int errno = Native.getLastError();
						if (errno != ErrNo.EEXIST) {
							throw new RuntimeException("failed to create SharedMemory. errno=" + errno);
						}
					} else {
						this.name = name;
						break;
					}
				}
			} else {
				if (prepend_leading_slash) {
					name = "/" + name;
				}
				fd = LibC.INSTANCE.shm_open(
						name,
						flags,
						mode
				);
				if (fd == -1) {
					final int errno = Native.getLastError();
					throw new RuntimeException("failed to create SharedMemory. errno=" + errno);
				} else {
					this.name = name;
				}
			}

			if (create) {
				LibC.INSTANCE.ftruncate(fd, (int) size);
			}
			size = sizeFromFileDescriptor(fd);
			this.size = (int) size;
			mmap = LibC.INSTANCE.mmap(Pointer.NULL, (int) size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
			// TODO failures in ftruncate, fstat, and mmap should be caught, then unlink and throw some exception





		} else {
			throw new UnsupportedOperationException("only macos implemented so far ...");
		}
	}

	/**
	 * Unique name that identifies the shared memory block.
	 *
	 * @return The name of the shared memory.
	 */
	public String name() {
		if (prepend_leading_slash && name.startsWith("/")) {
			return name.substring(1);
		} else {
			return name;
		}
	}

	/**
	 * Size in bytes.
	 *
	 * @return The length in bytes of the shared memory.
	 */
	public long size() {
		return this.size;
	}

	public Pointer getPointer() {
		return mmap;
	}


	/**
	 * Closes access to the shared memory from this instance but does
	 * not destroy the shared memory block.
	 */
	public void close() {
		if ( os == OSX ) {
			// Unmap the shared memory
			if (mmap != Pointer.NULL ) {
				if ( LibC.INSTANCE.munmap(mmap, (int) size) == -1 )
					throw new RuntimeException("munmap failed. Errno: " + Native.getLastError());
				mmap = Pointer.NULL;
			}

			// Close the file descriptor
			if (fd != -1) {
				if (LibC.INSTANCE.close(fd) == -1) {
					throw new RuntimeException("close failed. Errno: " + Native.getLastError());
				}
				fd = -1;
			}


		} else {
			throw new UnsupportedOperationException("only macos implemented so far ...");
		}
	}

	/**
	 * Requests that the underlying shared memory block be destroyed.
	 * In order to ensure proper cleanup of resources, unlink should be
	 * called once (and only once) across all processes which have access
	 * to the shared memory block.
	 */
	public void unlink() {
		if ( os == OSX ) {
			if (name != null) {
				LibC.INSTANCE.shm_unlink(name); // TODO error handling
				name = null;
			}


		} else {
			throw new UnsupportedOperationException("only macos implemented so far ...");
		}
	}
}
