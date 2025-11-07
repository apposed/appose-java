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

import com.sun.jna.Library;
import com.sun.jna.Native;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

interface MacosHelpers extends Library {

	String LIBRARY_NAME = "ShmCreate";

	String LIBRARY_NAME_SUF = ".dylib";

	// Load the native library.
	MacosHelpers INSTANCE = loadLibrary();

	static MacosHelpers loadLibrary() {
		try(
			final InputStream in =  MacosHelpers.class.getResourceAsStream(LIBRARY_NAME + LIBRARY_NAME_SUF);) {
			final File tempFile = File.createTempFile(LIBRARY_NAME, LIBRARY_NAME_SUF);
			tempFile.deleteOnExit();
			Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			return Native.load(tempFile.getAbsolutePath(), MacosHelpers.class);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	// Declare methods corresponding to the native functions.
	int create_shared_memory(String name, long rsize);

	void unlink_shared_memory(String name);

	long get_shared_memory_size(int fd);
}


