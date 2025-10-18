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

import java.util.Random;

/**
 * Utilities used in platform-specific {@code ShmBase} implementations.
 */
class Shms {

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
	static String makeFilename(int maxLength, String prefix) {
		// number of random bytes to use for name
		final int nbytes = (maxLength - prefix.length()) / 2;
		if (nbytes < 2) {
			throw new IllegalArgumentException("prefix too long");
		}
		final String name = prefix + tokenHex(nbytes);
		assert name.length() <= maxLength;
		return name;
	}

	static void checkSize(String shmName, long prevSize, long size) {
		final boolean alreadyExists = prevSize >= 0;
		if (alreadyExists && prevSize < size) {
			throw new RuntimeException("Shared memory segment '" + shmName + "' already exists with smaller size. "
				+ "Size of the existing shared memory segment (" + prevSize
				+ ") cannot be smaller than the size of the proposed object (" + size + ".");
		}
	}

	private static String tokenHex(int nbytes) {
		final byte[] bytes = new byte[nbytes];
		new Random().nextBytes(bytes);
		StringBuilder sb = new StringBuilder(nbytes * 2);
		for (byte b : bytes) {
			sb.append(String.format("%02x", b & 0xff));
		}
		return sb.toString();
	}
}
