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

package org.apposed.appose.gui;

import org.apposed.appose.builder.Builders;
import org.apposed.appose.util.Environments;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Utility methods for environment management operations.
 * <p>
 * Provides functionality for:
 * </p>
 * <ul>
 *     <li>Checking environment installation status</li>
 *     <li>Calculating environment disk usage</li>
 *     <li>Deleting environments</li>
 *     <li>Formatting sizes in human-readable format</li>
 * </ul>
 * <p>
 * These utilities are used by {@link EnvironmentPanel} and
 * {@link EnvironmentManagerPanel} but can be used standalone.
 * </p>
 *
 * @author Curtis Rueden
 * @author Stefan Hahmann
 */
public class EnvironmentUtils {

	private EnvironmentUtils() {
		// Prevent instantiation
	}

	/**
	 * Checks if an environment is installed.
	 * <p>
	 * An environment is considered installed if its directory exists and can
	 * be wrapped by an Appose builder.
	 * </p>
	 *
	 * @param envDir Environment directory to check
	 * @return true if the environment is installed and valid
	 */
	public static boolean isInstalled(File envDir) {
		return Builders.canWrap(envDir);
	}

	/**
	 * Checks if a named environment is installed in the standard Appose directory.
	 *
	 * @param envName Environment name
	 * @return true if the environment is installed
	 */
	public static boolean isInstalled(String envName) {
		return isInstalled(new File(Environments.apposeEnvsDir(), envName));
	}

	/**
	 * Calculates the disk size of an environment directory.
	 * <p>
	 * On Unix systems (Linux, macOS), this uses actual disk usage (like {@code du -sb}).
	 * On Windows, it uses logical file sizes.
	 * </p>
	 *
	 * @param envDir Environment directory
	 * @return Size in bytes, or 0 if directory doesn't exist
	 */
	public static long calculateSize(File envDir) {
		if (envDir == null || !envDir.exists()) {
			return 0;
		}

		String os = System.getProperty("os.name").toLowerCase();
		boolean isUnix = os.contains("nix") || os.contains("nux") || os.contains("mac");

		if (isUnix) {
			try {
				return calculateDiskUsageUnix(envDir.toPath());
			}
			catch (IOException e) {
				// Fallback to logical size if disk usage calculation fails
				return calculateFileSizeRecursive(envDir);
			}
		}
		else {
			return calculateFileSizeRecursive(envDir);
		}
	}

	/**
	 * Calculates the disk size of a named environment.
	 *
	 * @param envName Environment name
	 * @return Size in bytes, or 0 if environment doesn't exist
	 */
	public static long calculateSize(String envName) {
		return calculateSize(new File(Environments.apposeEnvsDir(), envName));
	}

	/**
	 * Formats a byte count as a human-readable string.
	 * <p>
	 * Examples: "1.5 GB", "256 MB", "4.2 KB"
	 * </p>
	 *
	 * @param bytes Size in bytes
	 * @return Human-readable size string
	 */
	public static String formatSize(long bytes) {
		if (bytes < 0) return "N/A";
		if (bytes < 1024) return bytes + " B";

		int exp = (int) (Math.log(bytes) / Math.log(1024));
		String pre = "KMGTPE".charAt(exp - 1) + "B";
		return String.format("%.1f %s", bytes / Math.pow(1024, exp), pre);
	}

	/**
	 * Deletes an environment directory and all its contents.
	 * <p>
	 * This is a recursive deletion - use with care!
	 * </p>
	 *
	 * @param envDir Environment directory to delete
	 * @throws IOException If deletion fails
	 */
	public static void delete(File envDir) throws IOException {
		if (envDir == null || !envDir.exists()) {
			return;
		}

		Files.walkFileTree(envDir.toPath(), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				if (exc != null) throw exc;
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/**
	 * Deletes a named environment from the standard Appose directory.
	 *
	 * @param envName Environment name
	 * @throws IOException If deletion fails
	 */
	public static void delete(String envName) throws IOException {
		delete(new File(Environments.apposeEnvsDir(), envName));
	}

	// --- Private helper methods (based on Stefan Hahmann's work) ---

	private static long calculateDiskUsageUnix(Path directory) throws IOException {
		final long[] total = {0};
		boolean hasUnixView = java.nio.file.FileSystems.getDefault()
				.supportedFileAttributeViews().contains("unix");

		Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				return Files.isSymbolicLink(dir) ?
						FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				if (!Files.isSymbolicLink(file)) {
					total[0] += getFileDiskUsage(file, hasUnixView);
				}
				return FileVisitResult.CONTINUE;
			}
		});

		return total[0];
	}

	private static long getFileDiskUsage(Path file, boolean hasUnixView) throws IOException {
		if (!hasUnixView) {
			return Files.size(file);
		}

		try {
			Object blocksAttr = Files.getAttribute(file, "unix:blocks", LinkOption.NOFOLLOW_LINKS);
			if (blocksAttr instanceof Number) {
				return ((Number) blocksAttr).longValue() * 512L; // du uses 512-byte blocks
			}
		}
		catch (IllegalArgumentException | UnsupportedOperationException e) {
			// Fall back to logical size
		}
		return Files.size(file);
	}

	private static long calculateFileSizeRecursive(File directory) {
		long size = 0;
		File[] files = directory.listFiles();
		if (files != null) {
			for (File file : files) {
				if (Files.isSymbolicLink(file.toPath())) {
					continue;
				}
				if (file.isFile()) {
					size += file.length();
				}
				else if (file.isDirectory()) {
					size += calculateFileSizeRecursive(file);
				}
			}
		}
		return size;
	}
}
