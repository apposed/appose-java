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

package org.apposed.appose.pixi;

import org.apposed.appose.Platforms;
import org.apposed.appose.util.DownloadUtils;
import org.apposed.appose.util.FileDownloader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Pixi-based environment manager.
 * Pixi is a modern package management tool that provides better environment
 * management than micromamba and supports both conda and PyPI packages.
 *
 * @author Curtis Rueden
 * @author Claude Code
 */
public class Pixi {

	/**
	 * String containing the path that points to the pixi executable
	 */
	public final String pixiCommand;

	/**
	 * Root directory where pixi is installed
	 *
	 * <pre>
	 * rootdir
	 * ├── .pixi
	 * │   ├── bin
	 * │   │   ├── pixi(.exe)
	 * </pre>
	 */
	private final String rootdir;

	/**
	 * Consumer that tracks the progress in the download of pixi
	 */
	private BiConsumer<Long, Long> pixiDownloadProgressConsumer;

	/**
	 * Consumer that tracks the standard output stream produced by the pixi process when it is executed.
	 */
	private Consumer<String> outputConsumer;

	/**
	 * Consumer that tracks the standard error stream produced by the pixi process when it is executed.
	 */
	private Consumer<String> errorConsumer;

	/**
	 * Relative path to the pixi executable from the pixi {@link #rootdir}
	 */
	private final static Path PIXI_RELATIVE_PATH = Platforms.OS == Platforms.OperatingSystem.WINDOWS ?
			Paths.get(".pixi", "bin", "pixi.exe") :
			Paths.get(".pixi", "bin", "pixi");

	/**
	 * Path where Appose installs Pixi by default
	 */
	final public static String BASE_PATH = Paths.get(System.getProperty("user.home"), ".local", "share", "appose").toString();

	/**
	 * Pixi version to download
	 */
	private static final String PIXI_VERSION = "v0.39.5";

	/**
	 * URL from where Pixi is downloaded to be installed
	 */
	public final static String PIXI_URL = "https://github.com/prefix-dev/pixi/releases/download/" +
		PIXI_VERSION + "/pixi-" + pixiPlatform() + ".tar.gz";

	/**
	 * @return a String that identifies the current OS to download the correct Pixi version
	 */
	private static String pixiPlatform() {
		String osName = System.getProperty("os.name");
		if (osName.startsWith("Windows")) osName = "Windows";
		String osArch = System.getProperty("os.arch");
		switch (osName + "|" + osArch) {
			case "Linux|amd64":      return "x86_64-unknown-linux-musl";
			case "Linux|aarch64":    return "aarch64-unknown-linux-musl";
			case "Mac OS X|x86_64":  return "x86_64-apple-darwin";
			case "Mac OS X|aarch64": return "aarch64-apple-darwin";
			case "Windows|amd64":    return "x86_64-pc-windows-msvc";
			default:                 return null;
		}
	}

	private void updatePixiDownloadProgress(long current, long total) {
		if (pixiDownloadProgressConsumer != null)
			pixiDownloadProgressConsumer.accept(current, total);
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
		final ProcessBuilder builder = new ProcessBuilder().directory(new File(rootdir));
		if (isInheritIO)
			builder.inheritIO();
		return builder;
	}

	/**
	 * Create a new {@link Pixi} object. The root dir for the Pixi installation
	 * will be the default base path defined at {@link #BASE_PATH}
	 * <p>
	 * It is expected that the Pixi installation has executable commands as shown below:
	 * </p>
	 * <pre>
	 * PIXI_ROOT
	 * ├── .pixi
	 * │   ├── bin
	 * │   │   ├── pixi(.exe)
	 * </pre>
	 */
	public Pixi() {
		this(BASE_PATH);
	}

	/**
	 * Create a new Pixi object. The root dir for Pixi installation can be
	 * specified as {@code String}.
	 * <p>
	 * It is expected that the Pixi installation has executable commands as shown below:
	 * </p>
	 * <pre>
	 * PIXI_ROOT
	 * ├── .pixi
	 * │   ├── bin
	 * │   │   ├── pixi(.exe)
	 * </pre>
	 *
	 * @param rootdir
	 *  The root dir for Pixi installation.
	 */
	public Pixi(final String rootdir) {
		this.rootdir = rootdir == null ? BASE_PATH : rootdir;
		this.pixiCommand = Paths.get(this.rootdir).resolve(PIXI_RELATIVE_PATH).toAbsolutePath().toString();
	}

	/**
	 * Gets whether pixi is installed or not
	 * @return whether pixi is installed or not
	 */
	public boolean isPixiInstalled() {
		try {
			getVersion();
			return true;
		} catch (IOException | InterruptedException e) {
			return false;
		}
	}

	/**
	 * Check whether pixi is installed or not
	 * @throws IllegalStateException if pixi is not installed
	 */
	private void checkPixiInstalled() {
		if (!isPixiInstalled()) throw new IllegalStateException("Pixi is not installed");
	}

	/**
	 * Registers the consumer for the download progress of pixi.
	 * @param consumer callback function invoked with (current, total) bytes
	 */
	public void setPixiDownloadProgressConsumer(BiConsumer<Long, Long> consumer) {
		this.pixiDownloadProgressConsumer = consumer;
	}

	/**
	 * Registers the consumer for the standard output stream of every pixi call.
	 * @param consumer callback function invoked for each stdout line
	 */
	public void setOutputConsumer(Consumer<String> consumer) {
		this.outputConsumer = consumer;
	}

	/**
	 * Registers the consumer for the standard error stream of every pixi call.
	 * @param consumer callback function invoked for each stderr line
	 */
	public void setErrorConsumer(Consumer<String> consumer) {
		this.errorConsumer = consumer;
	}

	private File downloadPixi() throws IOException, InterruptedException, URISyntaxException {
		final File tempFile = File.createTempFile("pixi", ".tar.gz");
		tempFile.deleteOnExit();
		URL website = DownloadUtils.redirectedURL(new URL(PIXI_URL));
		long size = DownloadUtils.getFileSize(website);
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
			updatePixiDownloadProgress(tempFile.length(), size);
		}
		if (ioe[0] != null) throw ioe[0];
		if (ie[0] != null) throw ie[0];
		if (tempFile.length() < size)
			throw new IOException("Error downloading pixi from: " + PIXI_URL);
		return tempFile;
	}

	private void decompressPixi(final File tempFile) throws IOException, InterruptedException {
		File pixiBaseDir = new File(rootdir);
		if (!pixiBaseDir.isDirectory() && !pixiBaseDir.mkdirs())
			throw new IOException("Failed to create Pixi default directory " +
				pixiBaseDir.getParentFile().getAbsolutePath() +
				". Please try installing it in another directory.");

		File pixiBinDir = Paths.get(rootdir).resolve(".pixi").resolve("bin").toFile();
		if (!pixiBinDir.exists() && !pixiBinDir.mkdirs())
			throw new IOException("Failed to create Pixi bin directory: " + pixiBinDir);

		DownloadUtils.unTarGz(tempFile, pixiBinDir);
		File pixiFile = new File(pixiCommand);
		if (!pixiFile.exists()) throw new IOException("Expected pixi binary is missing: " + pixiCommand);
		if (!pixiFile.canExecute()) {
			boolean executableSet = pixiFile.setExecutable(true);
			if (!executableSet)
				throw new IOException("Cannot set file as executable due to missing permissions, "
					+ "please do it manually: " + pixiCommand);
		}
	}

	/**
	 * Downloads and installs Pixi.
	 *
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is thrown.
	 * @throws URISyntaxException if there is any error with the pixi url
	 */
	public void installPixi() throws IOException, InterruptedException, URISyntaxException {
		if (isPixiInstalled()) return;
		decompressPixi(downloadPixi());
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
	 * Initialize a pixi project in the specified directory.
	 *
	 * @param projectDir The directory to initialize as a pixi project.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 * @throws IllegalStateException if Pixi has not been installed
	 */
	public void init(final File projectDir) throws IOException, InterruptedException {
		checkPixiInstalled();
		runPixi("init", projectDir.getAbsolutePath());
	}

	/**
	 * Add conda channels to a pixi project.
	 *
	 * @param projectDir The pixi project directory.
	 * @param channels The channels to add.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 * @throws IllegalStateException if Pixi has not been installed
	 */
	public void addChannels(final File projectDir, final String... channels) throws IOException, InterruptedException {
		checkPixiInstalled();
		if (channels.length == 0) return;
		List<String> cmd = new ArrayList<>();
		cmd.add("project");
		cmd.add("channel");
		cmd.add("add");
		cmd.add("--manifest-path");
		cmd.add(new File(projectDir, "pixi.toml").getAbsolutePath());
		cmd.addAll(Arrays.asList(channels));
		runPixi(cmd.toArray(new String[0]));
	}

	/**
	 * Add conda packages to a pixi project.
	 *
	 * @param projectDir The pixi project directory.
	 * @param packages The conda packages to add.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 * @throws IllegalStateException if Pixi has not been installed
	 */
	public void addCondaPackages(final File projectDir, final String... packages) throws IOException, InterruptedException {
		checkPixiInstalled();
		if (packages.length == 0) return;
		List<String> cmd = new ArrayList<>();
		cmd.add("add");
		cmd.add("--manifest-path");
		cmd.add(new File(projectDir, "pixi.toml").getAbsolutePath());
		cmd.addAll(Arrays.asList(packages));
		runPixi(cmd.toArray(new String[0]));
	}

	/**
	 * Add PyPI packages to a pixi project.
	 *
	 * @param projectDir The pixi project directory.
	 * @param packages The PyPI packages to add.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 * @throws IllegalStateException if Pixi has not been installed
	 */
	public void addPypiPackages(final File projectDir, final String... packages) throws IOException, InterruptedException {
		checkPixiInstalled();
		if (packages.length == 0) return;
		List<String> cmd = new ArrayList<>();
		cmd.add("add");
		cmd.add("--pypi");
		cmd.add("--manifest-path");
		cmd.add(new File(projectDir, "pixi.toml").getAbsolutePath());
		cmd.addAll(Arrays.asList(packages));
		runPixi(cmd.toArray(new String[0]));
	}

	/**
	 * Returns Pixi version as a {@code String}.
	 *
	 * @return The Pixi version as a {@code String}.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 */
	public String getVersion() throws IOException, InterruptedException {
		final List<String> cmd = getBaseCommand();
		if (pixiCommand.contains(" ") && Platforms.OS == Platforms.OperatingSystem.WINDOWS)
			cmd.add(surroundWithQuotes(Arrays.asList(coverArgWithDoubleQuotes(pixiCommand), "--version")));
		else
			cmd.addAll(Arrays.asList(coverArgWithDoubleQuotes(pixiCommand), "--version"));
		final Process process = getBuilder(false).command(cmd).start();
		if (process.waitFor() != 0)
			throw new RuntimeException("Error getting Pixi version");
		return new BufferedReader(new InputStreamReader(process.getInputStream())).readLine();
	}

	/**
	 * Run a Pixi command with one or more arguments.
	 *
	 * @param args One or more arguments for the Pixi command.
	 * @throws RuntimeException If there is any error running the commands
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 * @throws IllegalStateException if Pixi has not been installed
	 */
	public void runPixi(final String... args) throws RuntimeException, IOException, InterruptedException {
		checkPixiInstalled();
		runPixi(false, args);
	}

	/**
	 * Run a Pixi command with one or more arguments.
	 *
	 * @param isInheritIO Sets the source and destination for subprocess standard I/O to be
	 *            the same as those of the current Java process.
	 * @param args One or more arguments for the Pixi command.
	 * @throws RuntimeException If there is any error running the commands
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 * @throws IllegalStateException if Pixi has not been installed
	 */
	public void runPixi(boolean isInheritIO, final String... args) throws RuntimeException, IOException, InterruptedException {
		checkPixiInstalled();
		Thread mainThread = Thread.currentThread();
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

		final List<String> cmd = getBaseCommand();
		List<String> argsList = new ArrayList<>();
		argsList.add(coverArgWithDoubleQuotes(pixiCommand));
		argsList.addAll(Arrays.stream(args).map(aa -> {
			if (aa.contains(" ") && Platforms.OS == Platforms.OperatingSystem.WINDOWS) return coverArgWithDoubleQuotes(aa);
			else return aa;
		}).collect(Collectors.toList()));
		boolean containsSpaces = argsList.stream().anyMatch(aa -> aa.contains(" "));

		if (!containsSpaces || Platforms.OS != Platforms.OperatingSystem.WINDOWS) cmd.addAll(argsList);
		else cmd.add(surroundWithQuotes(argsList));

		ProcessBuilder builder = getBuilder(isInheritIO).command(cmd);
		Process process = builder.start();
		// Use separate threads to read each stream to avoid a deadlock.
		updateOutputConsumer(sdf.format(Calendar.getInstance().getTime()) + " -- STARTING PIXI COMMAND" + System.lineSeparator());
		long updatePeriod = 300;
		Thread outputThread = new Thread(() -> {
			try (
				InputStream inputStream = process.getInputStream();
				InputStream errStream = process.getErrorStream()
			) {
				byte[] buffer = new byte[1024]; // Buffer size can be adjusted
				StringBuilder processBuff = new StringBuilder();
				StringBuilder errBuff = new StringBuilder();
				String processChunk = "";
				String errChunk = "";
				int newLineIndex;
				long t0 = System.currentTimeMillis();
				while (process.isAlive() || inputStream.available() > 0) {
					if (!mainThread.isAlive()) {
						process.destroyForcibly();
						return;
					}
					if (inputStream.available() > 0) {
						processBuff.append(new String(buffer, 0, inputStream.read(buffer)));
						while ((newLineIndex = processBuff.indexOf(System.lineSeparator())) != -1) {
							processChunk += sdf.format(Calendar.getInstance().getTime()) + " -- "
								+ processBuff.substring(0, newLineIndex + 1).trim() + System.lineSeparator();
							processBuff.delete(0, newLineIndex + 1);
						}
					}
					if (errStream.available() > 0) {
						errBuff.append(new String(buffer, 0, errStream.read(buffer)));
						while ((newLineIndex = errBuff.indexOf(System.lineSeparator())) != -1) {
							errChunk += errBuff.substring(0, newLineIndex + 1).trim() + System.lineSeparator();
							errBuff.delete(0, newLineIndex + 1);
						}
					}
					// Sleep for a bit to avoid busy waiting
					Thread.sleep(60);
					if (System.currentTimeMillis() - t0 > updatePeriod) {
						updateOutputConsumer(processChunk);
						updateErrorConsumer(errChunk);
						processChunk = "";
						errChunk = "";
						t0 = System.currentTimeMillis();
					}
				}
				if (inputStream.available() > 0) {
					processBuff.append(new String(buffer, 0, inputStream.read(buffer)));
					processChunk += sdf.format(Calendar.getInstance().getTime()) + " -- " + processBuff.toString().trim();
				}
				if (errStream.available() > 0) {
					errBuff.append(new String(buffer, 0, errStream.read(buffer)));
					errChunk += errBuff.toString().trim();
				}
				updateErrorConsumer(errChunk);
				updateOutputConsumer(processChunk + System.lineSeparator()
					+ sdf.format(Calendar.getInstance().getTime()) + " -- TERMINATED PROCESS\n");
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		});
		// Start reading threads
		outputThread.start();
		int processResult;
		try {
			processResult = process.waitFor();
		} catch (InterruptedException ex) {
			throw new InterruptedException("Pixi process stopped. The command being executed was: " + cmd);
		}
		// Wait for all output to be read
		outputThread.join();
		if (processResult != 0)
			throw new RuntimeException("Exit code " + processResult + " from command execution: " + builder.command());
	}

	/**
	 * In Windows, if a command prompt argument contains a space " " it needs to
	 * start and end with double quotes
	 * @param arg the cmd argument
	 * @return a robust argument
	 */
	private static String coverArgWithDoubleQuotes(String arg) {
		String[] specialChars = new String[] {" "};
		for (String schar : specialChars) {
			if (arg.startsWith("\"") && arg.endsWith("\""))
				continue;
			if (arg.contains(schar) && Platforms.OS == Platforms.OperatingSystem.WINDOWS) {
				return "\"" + arg + "\"";
			}
		}
		return arg;
	}

	/**
	 * When an argument of a command prompt argument in Windows contains a space, not
	 * only the argument needs to be surrounded by double quotes, but the whole sentence
	 * @param args arguments to be executed by the windows cmd
	 * @return a complete String containing all the arguments and surrounded by double quotes
	 */
	private static String surroundWithQuotes(List<String> args) {
		String arg = "\"";
		for (String aa : args) {
			arg += aa + " ";
		}
		arg = arg.substring(0, arg.length() - 1);
		arg += "\"";
		return arg;
	}
}
