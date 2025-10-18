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

package org.apposed.appose.util;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Utility methods for working with files.
 */
public final class FilePaths {

	private FilePaths() {
		// Prevent instantiation of utility class.
	}

	/**
	 * Gets the path to the JAR file containing the given class. Technically
	 * speaking, it might not actually be a JAR file, it might be a raw class
	 * file, or even something weirder... But for our purposes, we'll just
	 * assume it's going to be something you can put onto a classpath.
	 *
	 * @param c The class whose file path should be discerned.
	 * @return File path of the JAR file containing the given class.
	 */
	public static File location(Class<?> c) {
		try {
			return new File(c.getProtectionDomain().getCodeSource().getLocation().toURI());
		}
		catch (URISyntaxException exc) {
			return null;
		}
	}

	public static File findExe(List<String> dirs, List<String> exes) {
		for (String exe : exes) {
			File exeFile = new File(exe);
			if (exeFile.isAbsolute()) {
				// Candidate is an absolute path; check it directly.
				if (Platforms.isExecutable(exeFile)) return exeFile;
			}
			else {
				// Candidate is a relative path; check beneath each given directory.
				for (String dir : dirs) {
					File f = Paths.get(dir, exe).toFile();
					if (Platforms.isExecutable(f) && !f.isDirectory()) return f;
				}
			}
		}
		return null;
	}

	/**
	 * Merges the files of the given source directory into the specified destination directory.
	 * <p>
	 *   For example, {@code moveDirectory(foo, bar)} would move:
	 * </p>
	 * <ul>
	 *   <li>{@code foo/a.txt} &rarr; {@code bar/a.txt}</li>
	 *   <li>{@code foo/b.dat} &rarr; {@code bar/b.dat}</li>
	 *   <li>{@code foo/c.csv} &rarr; {@code bar/c.csv}</li>
	 *   <li>{@code foo/subfoo/d.doc} &rarr; {@code bar/subfoo/d.doc}</li>
	 *   <li>etc.</li>
	 * </ul>
	 *
	 * @param srcDir TODO
	 * @param destDir TODO
	 * @param overwrite TODO
	 */
	public static void moveDirectory(File srcDir, File destDir, boolean overwrite) throws IOException {
		if (!srcDir.isDirectory()) throw new IllegalArgumentException("Not a directory: " + srcDir);
		if (!destDir.isDirectory()) throw new IllegalArgumentException("Not a directory: " + destDir);
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(srcDir.toPath())) {
			for (Path srcPath : stream) moveFile(srcPath.toFile(), destDir, overwrite);
		}
		if (!srcDir.delete()) throw new IOException("Could not remove directory " + destDir);
	}

	/**
	 * Moves the given source file to the destination directory,
	 * creating intermediate destination directories as needed.
	 * <p>
	 *   If the destination {@code file.ext} already exists, one of two things will happen: either
	 *   A) the existing destination file will be renamed as a backup to {@code file.ext.old}&mdash;or
	 *      {@code file.ext.0.old}, {@code file.ext.1.old}, etc., if {@code file.ext.old} also already exists&mdash;or
	 *   B) the <em>source</em> file will be renamed as a backup in this manner.
	 *   Which behavior occurs depends on the value of the {@code overwrite} flag:
	 *   true to back up the destination file, or false to back up the source file.
	 * </p>
	 *
	 * @param srcFile Source file to move.
	 * @param destDir Destination directory into which the file will be moved.
	 * @param overwrite If true, "overwrite" the destination file with the source file,
	 *                   backing up any existing destination file first; if false,
	 *                   leave the original destination file in place, instead moving
	 *                   the source file to a backup destination as a "previous" version.
	 * @throws IOException If something goes wrong with the needed I/O operations.
	 */
	public static void moveFile(File srcFile, File destDir, boolean overwrite) throws IOException {
		File destFile = new File(destDir, srcFile.getName());
		if (srcFile.isDirectory()) {
			// Create matching destination directory as needed.
			if (!destFile.exists() && !destFile.mkdirs())
				throw new IOException("Failed to create destination directory: " + destDir);
			// Recurse over source directory contents.
			moveDirectory(srcFile, destFile, overwrite);
			return;
		}
		// Source file is not a directory; move it into the destination directory.
		if (destDir.exists() && !destDir.isDirectory()) throw new IllegalArgumentException("Non-directory destination path: " + destDir);
		if (!destDir.exists() && !destDir.mkdirs()) throw new IOException("Failed to create destination directory: " + destDir);
		if (destFile.exists() && !overwrite) {
			// Destination already exists, and we aren't allowed to rename it. So we instead
			// rename the source file directly to a backup filename in the destination directory.
			renameToBackup(srcFile, destDir);
			return;
		}

		// Rename the existing destination file (if any) to a
		// backup file, then move the source file into place.
		renameToBackup(destFile);
		if (!srcFile.renameTo(destFile)) throw new IOException("Failed to move file: " + srcFile + " -> " + destFile);
	}

	/**
	 * TODO
	 *
	 * @param srcFile TODO
	 * @throws IOException If something goes wrong with the needed I/O operations.
	 */
	public static void renameToBackup(File srcFile) throws IOException {
		renameToBackup(srcFile, srcFile.getParentFile());
	}

	/**
	 * TODO
	 *
	 * @param srcFile TODO
	 * @param destDir TODO
	 * @throws IOException If something goes wrong with the needed I/O operations.
	 */
	public static void renameToBackup(File srcFile, File destDir) throws IOException {
		if (!srcFile.exists()) return; // Nothing to back up!
		String prefix = srcFile.getName();
		String suffix = "old";
		File backupFile = new File(destDir, prefix + "." + suffix);
		for (int i = 0; i < 1000; i++) {
			if (!backupFile.exists()) break;
			// The .old backup file already exists! Try .0.old, .1.old, and so on.
			backupFile = new File(destDir, prefix + "." + i + "." + suffix);
		}
		if (backupFile.exists()) {
			File failedTarget = new File(destDir, prefix + "." + suffix);
			throw new UnsupportedOperationException("Too many backup files already exist for target: " + failedTarget);
		}
		if (!srcFile.renameTo(backupFile)) {
			throw new IOException("Failed to rename file:" + srcFile + " -> " + backupFile);
		}
	}
}
