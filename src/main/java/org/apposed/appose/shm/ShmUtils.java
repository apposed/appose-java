package org.apposed.appose.shm;

import java.util.Random;

import static org.apposed.appose.shm.ShmUtils.OS.LINUX;
import static org.apposed.appose.shm.ShmUtils.OS.OSX;
import static org.apposed.appose.shm.ShmUtils.OS.WINDOWS;

/**
 * Utilities used in all platform-specific {@code SharedMemory} implementations.
 */
class ShmUtils {

	/**
	 * Constant to specify that the shared memory segment that is going to be
	 * open is only for reading
	 */
	static int O_RDONLY = 0;

	/**
	 * Constant to specify that the shared memory segment that is going to be
	 * open is for reading and/or writing
	 */
	static int O_RDWR = 2;

	/**
	 * Constant to specify that the shared memory segment that is going to be
	 * open will be created if it does not exist
	 */
	static int O_CREAT = 64;

	/**
	 * Constant to specify that the shared memory regions mapped can be read but
	 * not written
	 */
	static int PROT_READ = 0x1;

	/**
	 * Constant to specify that the shared memory regions mapped can be written
	 */
	static int PROT_WRITE = 0x2;

	/**
	 * Constant to specify that the shared memory regions mapped can be shared
	 * with other processes
	 */
	static int MAP_SHARED = 0x01;

	/**
	 * The detected OS.
	 */
	public static final OS os = detect_os();

	enum OS {WINDOWS, OSX, LINUX}

	private static OS detect_os() {
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

	/**
	 * Add leading slash if {@code name} doesn't have one.
	 */
	static String withLeadingSlash(String name) {
		return name.startsWith("/") ? name : ("/" + name);
	}

	/**
	 * Remove leading slash if {@code name} has one.
	 */
	static String withoutLeadingSlash(String name) {
		return name.startsWith("/") ? name.substring(1) : name;
	}

	/**
	 * FreeBSD (and perhaps other BSDs) limit names to 14 characters.
	 */
	static final int SHM_SAFE_NAME_LENGTH = 14;

	/**
	 * Shared memory block name prefix for Mac and Linux
	 */
	static final String SHM_NAME_PREFIX_POSIX = "/psm_";

	/**
	 * Shared memory block name prefix for Windows
	 */
	static final String SHM_NAME_PREFIX_WIN = "wnsm_";

	/**
	 * Creates a random filename for the shared memory object.
	 *
	 * @param maxLength maximum length of the generated name
	 * @param prefix    prefix of the generated filename
	 * @return a random filename with the given {@code prefix}.
	 */
	static String make_filename(int maxLength, String prefix) {
		// number of random bytes to use for name
		final int nbytes = (maxLength - prefix.length()) / 2;
		if (nbytes < 2) {
			throw new IllegalArgumentException("prefix too long");
		}
		final String name = prefix + token_hex(nbytes);
		assert name.length() <= maxLength;
		return name;
	}

	static void checkSize(String shmName, long prevSize, int size) {
		final boolean alreadyExists = prevSize >= 0;
		if (alreadyExists && prevSize < size) {
			throw new RuntimeException("Shared memory segment '" + shmName + "' already exists with smaller size. "
				+ "Size of the existing shared memory segment cannot be smaller than the size of the proposed object. "
				+ "Size of existing shared memory segment: " + prevSize + ", size of proposed object: " + size);
		}
	}

	private static String token_hex(int nbytes) {
		final byte[] bytes = new byte[nbytes];
		new Random().nextBytes(bytes);
		StringBuilder sb = new StringBuilder(nbytes * 2);
		for (byte b : bytes) {
			sb.append(String.format("%02x", b & 0xff));
		}
		return sb.toString();
	}
}
