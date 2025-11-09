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

package org.apposed.appose.tool;

import org.apposed.appose.util.Downloads;
import org.apposed.appose.util.Environments;
import org.apposed.appose.util.Platforms;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
		if (packages.length == 0) return;
		List<String> cmd = new ArrayList<>();
		cmd.add("add");
		cmd.add("--pypi");
		cmd.add("--manifest-path");
		cmd.add(new File(projectDir, "pixi.toml").getAbsolutePath());
		cmd.addAll(Arrays.asList(packages));
		exec(cmd.toArray(new String[0]));
	}
}
