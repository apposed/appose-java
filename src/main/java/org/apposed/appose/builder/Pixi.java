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

package org.apposed.appose.builder;

import org.apposed.appose.util.Downloads;
import org.apposed.appose.util.Environments;
import org.apposed.appose.util.Platforms;
import org.apposed.appose.util.Processes;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pixi-based environment manager.
 * Pixi is a modern package management tool that provides better environment
 * management than micromamba and supports both conda and PyPI packages.
 *
 * @author Curtis Rueden
 * @author Claude Code
 */
public class Pixi extends Tool {

	/** String containing the path that points to the pixi executable. */
	public final String pixiCommand;

	/**
	 * Root directory where pixi is installed.
	 *
	 * <pre>
	 * rootdir
	 * ├── .pixi
	 * │   ├── bin
	 * │   │   ├── pixi(.exe)
	 * </pre>
	 */
	private final String rootdir;

	/** Relative path to the pixi executable from the pixi {@link #rootdir}. */
	private final static Path PIXI_RELATIVE_PATH = Platforms.isWindows() ?
			Paths.get(".pixi", "bin", "pixi.exe") :
			Paths.get(".pixi", "bin", "pixi");

	/** Path where Appose installs Pixi by default ({@code .pixi} subdirectory thereof). */
	public static final String BASE_PATH = Environments.apposeEnvsDir();

	/** Pixi version to download. */
	private static final String PIXI_VERSION = "v0.58.0";

	/** The filename to download for the current platform. */
	private static final String PIXI_BINARY = pixiBinary();

	/** URL from where Pixi is downloaded to be installed. */
	public final static String PIXI_URL = PIXI_BINARY == null ? null :
		"https://github.com/prefix-dev/pixi/releases/download/" + PIXI_VERSION + "/" + PIXI_BINARY;

	private static String pixiBinary() {
		switch (Platforms.PLATFORM) {
			case "MACOS|ARM64":      return "pixi-aarch64-apple-darwin.tar.gz";       // Apple Silicon macOS
			case "MACOS|X64":        return "pixi-x86_64-apple-darwin.tar.gz";        // Intel macOS
			case "WINDOWS|ARM64":    return "pixi-aarch64-pc-windows-msvc.zip";       // ARM64 Windows
			case "WINDOWS|X64":      return "pixi-x86_64-pc-windows-msvc.zip";        // x64 Windows
			case "LINUX|ARM64":      return "pixi-aarch64-unknown-linux-musl.tar.gz"; // ARM64 MUSL Linux
			case "LINUX|X64":        return "pixi-x86_64-unknown-linux-musl.tar.gz";  // x64 MUSL Linux
			default:                 return null;
		}
	}

	/**
	 * Returns a {@link ProcessBuilder} with the working directory specified in the constructor.
	 *
	 * @param isInheritIO
	 *            Sets the source and destination for subprocess standard I/O to be
	 *            the same as those of the current Java process.
	 * @return The {@link ProcessBuilder} with the working directory specified in the constructor.
	 */
	@Override
	protected ProcessBuilder getBuilder(final boolean isInheritIO) {
		return Processes.builder(new File(rootdir), envVars, isInheritIO);
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
		this(null);
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

	private File downloadPixi() throws IOException, InterruptedException, URISyntaxException {
		return Downloads.download("pixi", PIXI_URL, this::updateDownloadProgress);
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

		Downloads.unpack(tempFile, pixiBinDir);
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
		if (Platforms.isWindows())
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
		if (pixiCommand.contains(" ") && Platforms.isWindows())
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

		final List<String> cmd = getBaseCommand();
		List<String> argsList = new ArrayList<>();
		argsList.add(coverArgWithDoubleQuotes(pixiCommand));
		// Add user-specified flags first
		argsList.addAll(flags.stream().map(flag -> {
			if (flag.contains(" ") && Platforms.isWindows()) return coverArgWithDoubleQuotes(flag);
			else return flag;
		}).collect(Collectors.toList()));
		// Then add the command-specific args
		argsList.addAll(Arrays.stream(args).map(aa -> {
			if (aa.contains(" ") && Platforms.isWindows()) return coverArgWithDoubleQuotes(aa);
			else return aa;
		}).collect(Collectors.toList()));
		boolean containsSpaces = argsList.stream().anyMatch(aa -> aa.contains(" "));

		if (!containsSpaces || !Platforms.isWindows()) cmd.addAll(argsList);
		else cmd.add(surroundWithQuotes(argsList));

		ProcessBuilder builder = getBuilder(isInheritIO).command(cmd);
		Process process = builder.start();
		// Use separate threads to read each stream to avoid a deadlock.
		Thread outputThread = new Thread(() -> {
			try {
				readStreams(process, mainThread);
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
			if (arg.contains(schar) && Platforms.isWindows()) {
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
