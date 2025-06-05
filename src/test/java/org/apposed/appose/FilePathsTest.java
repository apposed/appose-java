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

package org.apposed.appose;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.apposed.appose.Platforms.OperatingSystem.WINDOWS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link FilePaths}.
 *
 * @author Curtis Rueden
 */
public class FilePathsTest {

	private static final String EXT = Platforms.OS == WINDOWS ? ".exe" : "";
	private static final boolean SET_EXEC_BIT = Platforms.OS != WINDOWS;

	/** Tests {@link FilePaths#findExe}. */
	@Test
	public void testFindExe() throws IOException {
		File tmpDir = Files.createTempDirectory("appose-FilePathsTest-testFindExe-").toFile();
		try {
			// Set up some red herrings.
			createStubFile(tmpDir, "walk");
			createStubFile(tmpDir, "fly");
			File binDir = createDirectory(tmpDir, "bin");
			File binFly = createStubFile(binDir, "fly" + EXT);
			if (SET_EXEC_BIT) {
				// Mark the desired match as executable.
				assertTrue(binFly.setExecutable(true));
				assertTrue(binFly.canExecute());
			}

			// Search for the desired match.
			List<String> dirs = Arrays.asList(tmpDir.getAbsolutePath(), binDir.getAbsolutePath());
			List<String> exes = Arrays.asList("walk" + EXT, "fly" + EXT, "swim" + EXT);
			File exe = FilePaths.findExe(dirs, exes);

			// Check that we found the right file.
			assertEquals(binFly, exe);
		}
		finally {
			FileUtils.deleteDirectory(tmpDir);
		}
	}

	/** Tests {@link FilePaths#location}. */
	@Test
	public void testLocation() {
		// NB: Will fail if this test is run in a weird way (e.g.
		// from inside the tests JAR), but I don't care right now. :-P
		File expected = Paths.get(System.getProperty("user.dir"), "target", "test-classes").toFile();
		File actual = FilePaths.location(getClass());
		assertEquals(expected, actual);
	}

	/** Tests {@link FilePaths#moveDirectory}. */
	@Test
	public void testMoveDirectory() throws IOException {
		File tmpDir = Files.createTempDirectory("appose-FilePathsTest-testMoveDirectory-").toFile();
		try {
			// Set up a decently weighty directory structure.
			File srcDir = createDirectory(tmpDir, "src");
			File breakfast = createStubFile(srcDir, "breakfast");
			File lunchDir = createDirectory(srcDir, "lunch");
			File lunchFile1 = createStubFile(lunchDir, "apples", "fuji");
			File lunchFile2 = createStubFile(lunchDir, "bananas");
			File dinnerDir = createDirectory(srcDir, "dinner");
			File dinnerFile1 = createStubFile(dinnerDir, "bread");
			File dinnerFile2 = createStubFile(dinnerDir, "wine");
			File destDir = createDirectory(tmpDir, "dest");
			File destLunchDir = createDirectory(destDir, "lunch");
			createStubFile(destLunchDir, "apples", "gala");

			// Move the source directory to the destination.
			FilePaths.moveDirectory(srcDir, destDir, false);

			// Check whether everything worked.
			assertFalse(srcDir.exists());
			assertMoved(breakfast, destDir, "<breakfast>");
			assertMoved(lunchFile1, destLunchDir, "gala");
			File backupLunchFile1 = new File(destLunchDir, "apples.old");
			assertContent(backupLunchFile1, "fuji");
			assertMoved(lunchFile2, destLunchDir, "<bananas>");
			File destDinnerDir = new File(destDir, dinnerDir.getName());
			assertMoved(dinnerFile1, destDinnerDir, "<bread>");
			assertMoved(dinnerFile2, destDinnerDir, "<wine>");
		}
		finally {
			FileUtils.deleteDirectory(tmpDir);
		}
	}

	/** Tests {@link FilePaths#moveFile}. */
	@Test
	public void testMoveFile() throws IOException {
		File tmpDir = Files.createTempDirectory("appose-FilePathsTest-testMoveFile-").toFile();
		try {
			File srcDir = createDirectory(tmpDir, "from");
			File srcFile = createStubFile(srcDir, "stuff.txt", "shiny");
			File destDir = createDirectory(tmpDir, "to");
			File destFile = createStubFile(destDir, "stuff.txt", "obsolete");
			boolean overwrite = true;

			FilePaths.moveFile(srcFile, destDir, overwrite);

			assertTrue(srcDir.exists());
			assertFalse(srcFile.exists());
			assertContent(destFile, "shiny");
			File backupFile = new File(destDir, "stuff.txt.old");
			assertContent(backupFile, "obsolete");
		}
		finally {
			FileUtils.deleteDirectory(tmpDir);
		}
	}

	/** Tests {@link FilePaths#renameToBackup}. */
	@Test
	public void testRenameToBackup() throws IOException {
		File tmpFile = Files.createTempFile("appose-FilePathsTest-testRenameToBackup-", "").toFile();
		assertTrue(tmpFile.exists());
		tmpFile.deleteOnExit();
		FilePaths.renameToBackup(tmpFile);
		File backupFile = new File(tmpFile.getParent(), tmpFile.getName() + ".old");
		backupFile.deleteOnExit();
		assertFalse(tmpFile.exists());
		assertTrue(backupFile.exists());
	}

	private File createDirectory(File parent, String name) {
		File dir = new File(parent, name);
		assertTrue(dir.mkdir());
		assertTrue(dir.exists());
		return dir;
	}

	private File createStubFile(File dir, String name) throws IOException {
		return createStubFile(dir, name, "<" + name + ">");
	}

	private File createStubFile(File dir, String name, String content) throws IOException {
		File stubFile = new File(dir, name);
		try (PrintWriter pw = new PrintWriter(new FileWriter(stubFile))) {
			pw.print(content);
		}
		assertTrue(stubFile.exists());
		return stubFile;
	}

	private void assertMoved(File srcFile, File destDir, String expectedContent) throws IOException {
		assertFalse(srcFile.exists());
		File destFile = new File(destDir, srcFile.getName());
		assertContent(destFile, expectedContent);
	}

	private void assertContent(File file, String expectedContent) throws IOException {
		assertTrue(file.exists());
		String actualContent = new String(Files.readAllBytes(file.toPath()));
		assertEquals(expectedContent, actualContent);
	}
}
