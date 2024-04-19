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

	// Load the native library
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

	// Declare methods corresponding to the native functions
	int create_shared_memory(String name, int size);

	void unlink_shared_memory(String name);

	long get_shared_memory_size(int fd);
}


