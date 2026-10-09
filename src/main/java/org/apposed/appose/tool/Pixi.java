/*-
 * #%L
 * Appose: multi-language interprocess cooperation with shared memory.
 * %%
 * Copyright (C) 2023 - 2026 Appose developers.
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

package org.apposed.appose.tool;

import org.apposed.appose.util.Downloads;
import org.apposed.appose.util.Environments;
import org.apposed.appose.util.Platforms;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pixi-based environment manager.
 * Pixi is a modern package management tool that provides better environment
 * management than micromamba and supports both conda and PyPI packages.
 *
 * @author Curtis Rueden
 * @author Claude Code
 */
public class Pixi extends Tool {

	/** Relative path to the pixi executable from the pixi {@link #rootdir}. */
	private final static Path PIXI_RELATIVE_PATH = Platforms.isWindows() ?
			Paths.get(".pixi", "bin", "pixi.exe") :
			Paths.get(".pixi", "bin", "pixi");

	/** Path where Appose installs Pixi by default ({@code .pixi} subdirectory thereof). */
	public static final String BASE_PATH = Environments.apposeEnvsDir();

	/** Pixi version to download. */
	private static final String PIXI_VERSION = "v0.81.0";

	/** Minimum acceptable Pixi version; older installations get upgraded to it. */
	public static final String MIN_VERSION = PIXI_VERSION;

	/** Minimum number of milliseconds between checks for a newer Pixi release. */
	public static final long UPDATE_INTERVAL = TimeUnit.DAYS.toMillis(1);

	/** Environment variable which, when set to false, disables checking for newer releases. */
	public static final String AUTO_UPDATE_VAR = "APPOSE_PIXI_AUTO_UPDATE";

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
		super(
			"pixi",
			PIXI_URL,
			Paths.get(rootdir == null ? BASE_PATH : rootdir).resolve(PIXI_RELATIVE_PATH).toAbsolutePath().toString(),
			rootdir == null ? BASE_PATH : rootdir
		);
	}

	@Override
	protected void decompress(final File archive) throws IOException, InterruptedException {
		File pixiBaseDir = new File(rootdir);
		if (!pixiBaseDir.isDirectory() && !pixiBaseDir.mkdirs())
			throw new IOException("Failed to create Pixi default directory " +
				pixiBaseDir.getParentFile().getAbsolutePath() +
				". Please try installing it in another directory.");

		File pixiBinDir = Paths.get(rootdir).resolve(".pixi").resolve("bin").toFile();
		if (!pixiBinDir.exists() && !pixiBinDir.mkdirs())
			throw new IOException("Failed to create Pixi bin directory: " + pixiBinDir);

		Downloads.unpack(archive, pixiBinDir);
		File pixiFile = new File(command);
		if (!pixiFile.exists()) throw new IOException("Expected pixi binary is missing: " + command);
		if (!pixiFile.canExecute()) {
			boolean executableSet = pixiFile.setExecutable(true);
			if (!executableSet)
				throw new IOException("Cannot set file as executable due to missing permissions, "
					+ "please do it manually: " + command);
		}
	}

	/**
	 * Upgrade the installed Pixi, if warranted.
	 * <p>
	 * Pixi is upgraded to the latest release, at most once per
	 * {@link #UPDATE_INTERVAL}, unless the {@code APPOSE_PIXI_AUTO_UPDATE}
	 * environment variable is set to false. Regardless, Pixi is upgraded to at
	 * least {@link #MIN_VERSION}, so that it understands manifests, lock files
	 * and caches written by newer Pixi installations elsewhere on the system.
	 * </p>
	 * <p>
	 * Failures (e.g. due to no network connection) are reported to the error
	 * consumer, but not thrown, so that builds can proceed with the existing Pixi.
	 * </p>
	 *
	 * @throws IOException If Pixi is not installed.
	 * @throws InterruptedException If the current thread is interrupted.
	 */
	public void update() throws IOException, InterruptedException {
		if (autoUpdateEnabled() && updateCheckDue()) selfUpdate();

		if (compareVersions(version(), MIN_VERSION) < 0) {
			selfUpdate("--version", MIN_VERSION.replaceFirst("^v", ""));
		}
	}

	private static boolean autoUpdateEnabled() {
		String value = System.getenv(AUTO_UPDATE_VAR);
		if (value == null) return true;
		switch (value.trim().toLowerCase()) {
			case "0": case "false": case "no": case "off": return false;
			default: return true;
		}
	}

	/**
	 * Checks whether {@link #UPDATE_INTERVAL} has elapsed since the last update
	 * check, recording the current time as the latest check if so.
	 */
	private boolean updateCheckDue() {
		Path stamp = Paths.get(command).resolveSibling("last-update-check");
		long now = System.currentTimeMillis();
		try {
			long elapsed = now - Files.getLastModifiedTime(stamp).toMillis();
			if (elapsed >= 0 && elapsed < UPDATE_INTERVAL) return false;
		}
		catch (IOException e) {
			// No previous check recorded.
		}
		try {
			// Note: Record the check even if it fails, so that
			// being offline does not cause a failed check every time.
			if (!Files.exists(stamp)) Files.createFile(stamp);
			Files.setLastModifiedTime(stamp, FileTime.fromMillis(now));
		}
		catch (IOException e) {
			// Cannot record the check; proceed anyway.
		}
		return true;
	}

	/**
	 * Runs {@code pixi self-update} with the given arguments,
	 * reporting failure to the error consumer rather than throwing.
	 */
	protected void selfUpdate(String... args) throws InterruptedException {
		List<String> cmd = new ArrayList<>();
		cmd.add("self-update");
		cmd.add("--no-release-note");
		cmd.addAll(Arrays.asList(args));
		try {
			doExec(null, false, false, cmd.toArray(new String[0]));
		}
		catch (IOException e) {
			// Note: Pixi's own error output has already gone to the error consumer.
			error("Warning: could not update pixi; continuing with the installed version." +
				System.lineSeparator());
		}
	}

	/** Compares version strings like {@code v0.81.0} numerically. */
	static int compareVersions(String v1, String v2) {
		int[] a = versionNumbers(v1), b = versionNumbers(v2);
		for (int i = 0; i < Math.max(a.length, b.length); i++) {
			int x = i < a.length ? a[i] : 0, y = i < b.length ? b[i] : 0;
			if (x != y) return Integer.compare(x, y);
		}
		return 0;
	}

	private static int[] versionNumbers(String version) {
		List<Integer> numbers = new ArrayList<>();
		Matcher m = Pattern.compile("\\d+").matcher(version);
		while (m.find() && numbers.size() < 3) numbers.add(Integer.parseInt(m.group()));
		return numbers.stream().mapToInt(Integer::intValue).toArray();
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
		exec("init", projectDir.getAbsolutePath());
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
		if (channels.length == 0) return;
		List<String> cmd = new ArrayList<>();
		cmd.add("project");
		cmd.add("channel");
		cmd.add("add");
		cmd.add("--manifest-path");
		cmd.add(new File(projectDir, "pixi.toml").getAbsolutePath());
		cmd.addAll(Arrays.asList(channels));
		exec(cmd.toArray(new String[0]));
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
		if (packages.length == 0) return;
		List<String> cmd = new ArrayList<>();
		cmd.add("add");
		cmd.add("--manifest-path");
		cmd.add(new File(projectDir, "pixi.toml").getAbsolutePath());
		cmd.addAll(Arrays.asList(packages));
		exec(cmd.toArray(new String[0]));
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
		addPypiPackages(projectDir, false, packages);
	}

	/**
	 * Adds PyPI packages to a Pixi project.
	 *
	 * @param projectDir The Pixi project directory.
	 * @param editable Whether to install the packages in editable mode,
	 *          which applies to packages given as local directories.
	 * @param packages The PyPI packages to add.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 */
	public void addPypiPackages(final File projectDir, final boolean editable,
		final String... packages) throws IOException, InterruptedException
	{
		if (packages.length == 0) return;
		List<String> cmd = new ArrayList<>();
		cmd.add("add");
		cmd.add("--pypi");
		if (editable) cmd.add("--editable");
		cmd.add("--manifest-path");
		cmd.add(new File(projectDir, "pixi.toml").getAbsolutePath());
		cmd.addAll(Arrays.asList(packages));
		exec(cmd.toArray(new String[0]));
	}
}
