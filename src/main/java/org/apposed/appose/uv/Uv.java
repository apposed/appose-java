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

package org.apposed.appose.uv;

import org.apposed.appose.util.Downloads;
import org.apposed.appose.util.Environments;
import org.apposed.appose.util.FileDownloader;
import org.apposed.appose.util.Platforms;
import org.apposed.appose.util.Processes;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * UV-based environment manager.
 * UV is a fast Python package installer and resolver written in Rust.
 *
 * @author Curtis Rueden
 * @author Claude Code
 */
public class Uv {

	/**
	 * String containing the path that points to the uv executable
	 */
	public final String uvCommand;

	/**
	 * Root directory where uv is installed
	 *
	 * <pre>
	 * rootdir
	 * ├── .uv
	 * │   ├── bin
	 * │   │   ├── uv(.exe)
	 * </pre>
	 */
	private final String rootdir;

	/**
	 * Consumer that tracks the progress in the download of uv
	 */
	private BiConsumer<Long, Long> uvDownloadProgressConsumer;

	/**
	 * Consumer that tracks the standard output stream produced by the uv process when it is executed.
	 */
	private Consumer<String> outputConsumer;

	/**
	 * Consumer that tracks the standard error stream produced by the uv process when it is executed.
	 */
	private Consumer<String> errorConsumer;

	/**
	 * Environment variables to set when running uv commands.
	 */
	private Map<String, String> envVars = new HashMap<>();

	/**
	 * Relative path to the uv executable from the uv {@link #rootdir}
	 */
	private final static Path UV_RELATIVE_PATH = Platforms.OS == Platforms.OperatingSystem.WINDOWS ?
			Paths.get(".uv", "bin", "uv.exe") :
			Paths.get(".uv", "bin", "uv");

	/**
	 * Path where Appose installs uv by default ({@code .uv} subdirectory thereof).
	 */
	public static final String BASE_PATH = Environments.apposeEnvsDir();

	/**
	 * UV version to download
	 */
	private static final String UV_VERSION = "0.5.25";

	/**
	 * URL from where UV is downloaded to be installed
	 */
	public final static String UV_URL = "https://github.com/astral-sh/uv/releases/download/" +
		UV_VERSION + "/uv-" + uvPlatform() + ".tar.gz";

	/**
	 * @return a String that identifies the current OS to download the correct UV version
	 */
	private static String uvPlatform() {
		String osName = System.getProperty("os.name");
		if (osName.startsWith("Windows")) osName = "Windows";
		String osArch = System.getProperty("os.arch");
		switch (osName + "|" + osArch) {
			case "Linux|amd64":      return "x86_64-unknown-linux-gnu";
			case "Linux|aarch64":    return "aarch64-unknown-linux-gnu";
			case "Mac OS X|x86_64":  return "x86_64-apple-darwin";
			case "Mac OS X|aarch64": return "aarch64-apple-darwin";
			case "Windows|amd64":    return "x86_64-pc-windows-msvc";
			default:                 return null;
		}
	}

	private void updateUvDownloadProgress(long current, long total) {
		if (uvDownloadProgressConsumer != null)
			uvDownloadProgressConsumer.accept(current, total);
	}

	private void updateOutputConsumer(String str) {
		if (outputConsumer != null)
			outputConsumer.accept(str == null ? "" : str);
	}

	private void updateErrorConsumer(String str) {
		if (errorConsumer != null)
			errorConsumer.accept(str == null ? "" : str);
	}

	/**
	 * Returns a {@link ProcessBuilder} with the working directory specified in the constructor.
	 *
	 * @param isInheritIO
	 *            Sets the source and destination for subprocess standard I/O to be
	 *            the same as those of the current Java process.
	 * @return The {@link ProcessBuilder} with the working directory specified in the constructor.
	 */
	private ProcessBuilder getBuilder(final boolean isInheritIO) {
		return Processes.builder(new File(rootdir), envVars, isInheritIO);
	}

	/**
	 * Create a new {@link Uv} object. The root dir for the UV installation
	 * will be the default base path defined at {@link #BASE_PATH}
	 * <p>
	 * It is expected that the UV installation has executable commands as shown below:
	 * </p>
	 * <pre>
	 * UV_ROOT
	 * ├── .uv
	 * │   ├── bin
	 * │   │   ├── uv(.exe)
	 * </pre>
	 */
	public Uv() {
		this(BASE_PATH);
	}

	/**
	 * Create a new Uv object. The root dir for UV installation can be
	 * specified as {@code String}.
	 * <p>
	 * It is expected that the UV installation has executable commands as shown below:
	 * </p>
	 * <pre>
	 * UV_ROOT
	 * ├── .uv
	 * │   ├── bin
	 * │   │   ├── uv(.exe)
	 * </pre>
	 *
	 * @param rootdir
	 *  The root dir for UV installation.
	 */
	public Uv(final String rootdir) {
		this.rootdir = rootdir == null ? BASE_PATH : rootdir;
		this.uvCommand = Paths.get(this.rootdir).resolve(UV_RELATIVE_PATH).toAbsolutePath().toString();
	}

	/**
	 * Gets whether uv is installed or not
	 * @return whether uv is installed or not
	 */
	public boolean isUvInstalled() {
		try {
			getVersion();
			return true;
		} catch (IOException | InterruptedException e) {
			return false;
		}
	}

	/**
	 * Check whether uv is installed or not
	 * @throws IllegalStateException if uv is not installed
	 */
	private void checkUvInstalled() {
		if (!isUvInstalled()) throw new IllegalStateException("UV is not installed");
	}

	/**
	 * Registers the consumer for the download progress of uv.
	 * @param consumer callback function invoked with (current, total) bytes
	 */
	public void setUvDownloadProgressConsumer(BiConsumer<Long, Long> consumer) {
		this.uvDownloadProgressConsumer = consumer;
	}

	/**
	 * Registers the consumer for the standard output stream of every uv call.
	 * @param consumer callback function invoked for each stdout line
	 */
	public void setOutputConsumer(Consumer<String> consumer) {
		this.outputConsumer = consumer;
	}

	/**
	 * Registers the consumer for the standard error stream of every uv call.
	 * @param consumer callback function invoked for each stderr line
	 */
	public void setErrorConsumer(Consumer<String> consumer) {
		this.errorConsumer = consumer;
	}

	/**
	 * Sets environment variables to be passed to uv processes.
	 * @param envVars Map of environment variable names to values
	 */
	public void setEnvVars(Map<String, String> envVars) {
		if (envVars != null) {
			this.envVars = new HashMap<>(envVars);
		}
	}

	private File downloadUv() throws IOException, InterruptedException, URISyntaxException {
		final File tempFile = File.createTempFile("uv-", ".tar.gz");
		tempFile.deleteOnExit();
		URL website = Downloads.redirectedURL(new URL(UV_URL));
		long size = Downloads.getFileSize(website);
		Thread currentThread = Thread.currentThread();
		IOException[] ioe = {null};
		InterruptedException[] ie = {null};
		Thread dwnldThread = new Thread(() -> {
			try (
				ReadableByteChannel rbc = Channels.newChannel(website.openStream());
				FileOutputStream fos = new FileOutputStream(tempFile)
			) {
				new FileDownloader(rbc, fos).call(currentThread);
			}
			catch (IOException e) { ioe[0] = e; }
			catch (InterruptedException e) { ie[0] = e; }
		});
		dwnldThread.start();
		while (dwnldThread.isAlive()) {
			Thread.sleep(20); // 50 FPS update rate
			updateUvDownloadProgress(tempFile.length(), size);
		}
		if (ioe[0] != null) throw ioe[0];
		if (ie[0] != null) throw ie[0];
		if (tempFile.length() < size)
			throw new IOException("Error downloading uv from: " + UV_URL);
		return tempFile;
	}

	private void decompressUv(final File tempFile) throws IOException, InterruptedException {
		File uvBaseDir = new File(rootdir);
		if (!uvBaseDir.isDirectory() && !uvBaseDir.mkdirs())
			throw new IOException("Failed to create UV default directory " +
				uvBaseDir.getParentFile().getAbsolutePath() +
				". Please try installing it in another directory.");

		File uvBinDir = Paths.get(rootdir).resolve(".uv").resolve("bin").toFile();
		if (!uvBinDir.exists() && !uvBinDir.mkdirs())
			throw new IOException("Failed to create UV bin directory: " + uvBinDir);

		// Extract tarball which contains uv-<platform>/uv structure
		Downloads.unTarGz(tempFile, uvBinDir);

		// Move the uv binary from uv-<platform>/ subdirectory to bin/
		File platformDir = uvBinDir.listFiles(f -> f.isDirectory() && f.getName().startsWith("uv-"))[0];
		if (platformDir == null || !platformDir.exists()) {
			throw new IOException("Expected uv platform directory not found in: " + uvBinDir);
		}

		File uvSource = new File(platformDir, Platforms.OS == Platforms.OperatingSystem.WINDOWS ? "uv.exe" : "uv");
		if (!uvSource.exists()) {
			throw new IOException("Expected uv binary not found in: " + platformDir);
		}

		File uvDest = new File(uvCommand);
		if (!uvSource.renameTo(uvDest)) {
			throw new IOException("Failed to move uv binary from " + uvSource + " to " + uvDest);
		}

		// Clean up the platform directory
		platformDir.delete();

		if (!uvDest.exists()) throw new IOException("Expected uv binary is missing: " + uvCommand);
		if (!uvDest.canExecute()) {
			boolean executableSet = uvDest.setExecutable(true);
			if (!executableSet)
				throw new IOException("Cannot set file as executable due to missing permissions, "
					+ "please do it manually: " + uvCommand);
		}
	}

	/**
	 * Downloads and installs UV.
	 *
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is thrown.
	 * @throws URISyntaxException if there is any error with the uv url
	 */
	public void installUv() throws IOException, InterruptedException, URISyntaxException {
		if (isUvInstalled()) return;
		decompressUv(downloadUv());
	}

	/**
	 * Returns {@code \{"cmd.exe", "/c"\}} for Windows and an empty list for Mac/Linux.
	 *
	 * @return {@code \{"cmd.exe", "/c"\}} for Windows and an empty list for Mac/Linux.
	 */
	private static List<String> getBaseCommand() {
		final List<String> cmd = new ArrayList<>();
		if (Platforms.OS == Platforms.OperatingSystem.WINDOWS)
			cmd.addAll(Arrays.asList("cmd.exe", "/c"));
		return cmd;
	}

	/**
	 * Create a virtual environment using UV.
	 *
	 * @param envDir The directory for the virtual environment.
	 * @param pythonVersion Optional Python version (e.g., "3.11"). Can be null for default.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 * @throws IllegalStateException if UV has not been installed
	 */
	public void createVenv(final File envDir, String pythonVersion) throws IOException, InterruptedException {
		checkUvInstalled();
		List<String> args = new ArrayList<>();
		args.add("venv");
		if (pythonVersion != null && !pythonVersion.isEmpty()) {
			args.add("--python");
			args.add(pythonVersion);
		}
		args.add(envDir.getAbsolutePath());
		runUv(args.toArray(new String[0]));
	}

	/**
	 * Install PyPI packages into a virtual environment.
	 *
	 * @param envDir The virtual environment directory.
	 * @param packages The packages to install.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 * @throws IllegalStateException if UV has not been installed
	 */
	public void pipInstall(final File envDir, String... packages) throws IOException, InterruptedException {
		checkUvInstalled();
		List<String> args = new ArrayList<>();
		args.add("pip");
		args.add("install");
		args.add("--python");
		args.add(envDir.getAbsolutePath());
		args.addAll(Arrays.asList(packages));
		runUv(args.toArray(new String[0]));
	}

	/**
	 * Install packages from a requirements.txt file.
	 *
	 * @param envDir The virtual environment directory.
	 * @param requirementsFile Path to requirements.txt file.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 * @throws IllegalStateException if UV has not been installed
	 */
	public void pipInstallFromRequirements(final File envDir, String requirementsFile) throws IOException, InterruptedException {
		checkUvInstalled();
		runUv("pip", "install", "--python", envDir.getAbsolutePath(), "-r", requirementsFile);
	}

	/**
	 * Run a UV command with the specified arguments.
	 *
	 * @param args Command arguments for uv.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 * @throws IllegalStateException if UV has not been installed
	 */
	public void runUv(final String... args) throws IOException, InterruptedException {
		checkUvInstalled();
		final List<String> cmd = getBaseCommand();
		cmd.add(uvCommand);
		cmd.addAll(Arrays.asList(args));

		final ProcessBuilder builder = getBuilder(false);
		builder.command(cmd);
		final Process process = builder.start();

		// Read output
		Thread stdoutThread = new Thread(() -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					updateOutputConsumer(line);
				}
			} catch (IOException e) {
				updateErrorConsumer("Error reading stdout: " + e.getMessage());
			}
		});

		// Read errors
		Thread stderrThread = new Thread(() -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					updateErrorConsumer(line);
				}
			} catch (IOException e) {
				updateErrorConsumer("Error reading stderr: " + e.getMessage());
			}
		});

		stdoutThread.start();
		stderrThread.start();

		int exitCode = process.waitFor();
		stdoutThread.join();
		stderrThread.join();

		if (exitCode != 0) {
			throw new IOException("UV command failed with exit code " + exitCode + ": " + String.join(" ", args));
		}
	}

	/**
	 * Get the version of the installed UV.
	 *
	 * @return The UV version string.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 */
	public String getVersion() throws IOException, InterruptedException {
		final List<String> cmd = getBaseCommand();
		cmd.add(uvCommand);
		cmd.add("--version");

		final ProcessBuilder builder = getBuilder(false);
		builder.command(cmd);
		final Process process = builder.start();

		String version;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			version = reader.lines().collect(Collectors.joining("\n"));
		}

		int exitCode = process.waitFor();
		if (exitCode != 0) {
			throw new IOException("Failed to get UV version");
		}

		return version.trim();
	}
}
