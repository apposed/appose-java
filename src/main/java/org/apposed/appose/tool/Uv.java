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
import org.apposed.appose.util.FilePaths;
import org.apposed.appose.util.Platforms;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * uv-based environment manager.
 * uv is a fast Python package installer and resolver written in Rust.
 *
 * @author Curtis Rueden
 * @author Claude Code
 */
public class Uv extends Tool {

	/** Relative path to the uv executable from the uv {@link #rootdir}. */
	private final static Path UV_RELATIVE_PATH = Platforms.isWindows() ?
			Paths.get(".uv", "bin", "uv.exe") :
			Paths.get(".uv", "bin", "uv");

	/** Path where Appose installs uv by default ({@code .uv} subdirectory thereof). */
	public static final String BASE_PATH = Environments.apposeEnvsDir();

	/** uv version to download. */
	private static final String UV_VERSION = "0.12.24";

	/** Minimum acceptable uv version; older installations get upgraded to it. */
	public static final String MIN_VERSION = UV_VERSION;

	/** Environment variable which, when set to false, disables checking for newer releases. */
	public static final String AUTO_UPDATE_VAR = "APPOSE_UV_AUTO_UPDATE";

	/** URL which redirects to the latest uv release. */
	private static final String LATEST_URL = "https://github.com/astral-sh/uv/releases/latest";

	/** The filename to download for the current platform. */
	private static final String UV_BINARY = uvBinary();

	/** URL from where uv is downloaded to be installed. */
	public final static String UV_URL = uvURL(UV_VERSION);

	private static String uvURL(String version) {
		return UV_BINARY == null ? null :
			"https://github.com/astral-sh/uv/releases/download/" + version + "/" + UV_BINARY;
	}

	private static String uvBinary() {
		switch (Platforms.PLATFORM) {
			case "MACOS|ARM64":   return "uv-aarch64-apple-darwin.tar.gz";           // Apple Silicon macOS
			case "MACOS|X64":     return "uv-x86_64-apple-darwin.tar.gz";            // Intel macOS
			case "WINDOWS|ARM64": return "uv-aarch64-pc-windows-msvc.zip";           // ARM64 Windows
			case "WINDOWS|X32":   return "uv-i686-pc-windows-msvc.zip";              // x86 Windows
			case "WINDOWS|X64":   return "uv-x86_64-pc-windows-msvc.zip";            // x64 Windows
			case "LINUX|ARM64":   return "uv-aarch64-unknown-linux-gnu.tar.gz";      // ARM64 Linux
			case "LINUX|X32":     return "uv-i686-unknown-linux-gnu.tar.gz";         // x86 Linux
			case "LINUX|PPC64LE": return "uv-powerpc64le-unknown-linux-gnu.tar.gz";  // PPC64LE Linux
			case "LINUX|RV64GC":  return "uv-riscv64gc-unknown-linux-gnu.tar.gz";    // RISCV Linux
			case "LINUX|S390X":   return "uv-s390x-unknown-linux-gnu.tar.gz";        // S390x Linux
			case "LINUX|X64":     return "uv-x86_64-unknown-linux-gnu.tar.gz";       // x64 Linux
			case "LINUX|ARMV7":   return "uv-armv7-unknown-linux-gnueabihf.tar.gz";  // ARMv7 Linux
			//case "LINUX|ARM64": return "uv-aarch64-unknown-linux-musl.tar.gz";     // ARM64 MUSL Linux
			//case "LINUX|X32":   return "uv-i686-unknown-linux-musl.tar.gz";        // x86 MUSL Linux
			//case "LINUX|X64":   return "uv-x86_64-unknown-linux-musl.tar.gz";      // x64 MUSL Linux
			case "LINUX|ARMV6":   return "uv-arm-unknown-linux-musleabihf.tar.gz";   // ARMv6 MUSL Linux (Hardfloat)
			//case "LINUX|ARMV7": return "uv-armv7-unknown-linux-musleabihf.tar.gz"; // ARMv7 MUSL Linux
			default:              return null;
		}
	}

	/**
	 * Create a new {@link Uv} object. The root dir for the uv installation
	 * will be the default base path defined at {@link #BASE_PATH}
	 * <p>
	 * It is expected that the uv installation has executable commands as shown below:
	 * </p>
	 * <pre>
	 * UV_ROOT
	 * ├── .uv
	 * │   ├── bin
	 * │   │   ├── uv(.exe)
	 * </pre>
	 */
	public Uv() {
		this(null);
	}

	/**
	 * Create a new Uv object. The root dir for uv installation can be
	 * specified as {@code String}.
	 * <p>
	 * It is expected that the uv installation has executable commands as shown below:
	 * </p>
	 * <pre>
	 * UV_ROOT
	 * ├── .uv
	 * │   ├── bin
	 * │   │   ├── uv(.exe)
	 * </pre>
	 *
	 * @param rootdir
	 *  The root dir for uv installation.
	 */
	public Uv(final String rootdir) {
		super(
			"uv",
			UV_URL,
			Paths.get(rootdir == null ? BASE_PATH : rootdir).resolve(UV_RELATIVE_PATH).toAbsolutePath().toString(),
			rootdir == null ? BASE_PATH : rootdir
		);
	}

	@Override
	protected void decompress(final File archive) throws IOException, InterruptedException {
		Path uvBinDir = Paths.get(command).getParent();
		Files.createDirectories(uvBinDir);

		// Note: Unpack to a staging directory and move the binaries into place,
		// rather than overwriting existing binaries in place, which can break
		// them while in use (and invalidates cached code signatures on macOS).
		File stagingDir = Files.createTempDirectory(uvBinDir.getParent(), "staging").toFile();
		try {
			Downloads.unpack(archive, stagingDir);

			// Windows ZIPs contain the binaries directly;
			// others contain them in a uv-<platform> subdirectory.
			File[] platformDirs = stagingDir.listFiles(f -> f.isDirectory() && f.getName().startsWith("uv-"));
			File sourceDir = platformDirs != null && platformDirs.length > 0 ? platformDirs[0] : stagingDir;

			File[] binaries = sourceDir.listFiles(File::isFile);
			if (binaries != null) {
				for (File binary : binaries) {
					File dest = uvBinDir.resolve(binary.getName()).toFile();
					Files.move(binary.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
					if (!dest.canExecute()) dest.setExecutable(true);
				}
			}
		}
		finally {
			FilePaths.deleteRecursively(stagingDir);
		}

		File uvDest = new File(command);
		if (!uvDest.exists()) throw new IOException("Expected uv binary is missing: " + command);
		if (!uvDest.canExecute()) {
			boolean executableSet = uvDest.setExecutable(true);
			if (!executableSet)
				throw new IOException("Cannot set file as executable due to missing permissions, "
					+ "please do it manually: " + command);
		}
	}

	@Override
	protected String minVersion() {
		return MIN_VERSION;
	}

	@Override
	protected String autoUpdateVar() {
		return AUTO_UPDATE_VAR;
	}

	@Override
	protected String latestVersion() throws IOException {
		String location = Downloads.redirectLocation(LATEST_URL);
		Matcher m = Pattern.compile("/tag/([^/]+)$").matcher(location == null ? "" : location);
		if (!m.find()) throw new IOException("Could not determine latest uv release from " + LATEST_URL);
		return m.group(1);
	}

	@Override
	protected String downloadURL(String version) {
		return uvURL(version);
	}

	/**
	 * Create a virtual environment using uv.
	 *
	 * @param envDir The directory for the virtual environment.
	 * @param pythonVersion Optional Python version (e.g., "3.11"). Can be null for default.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 * @throws IllegalStateException if uv has not been installed
	 */
	public void createVenv(final File envDir, String pythonVersion) throws IOException, InterruptedException {
		List<String> args = new ArrayList<>();
		args.add("venv");
		if (pythonVersion != null && !pythonVersion.isEmpty()) {
			args.add("--python");
			args.add(pythonVersion);
		}
		args.add(envDir.getAbsolutePath());
		exec(args.toArray(new String[0]));
	}

	/**
	 * Install PyPI packages into a virtual environment.
	 *
	 * @param envDir The virtual environment directory.
	 * @param packages The packages to install.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 * @throws IllegalStateException if uv has not been installed
	 */
	public void pipInstall(final File envDir, String... packages) throws IOException, InterruptedException {
		List<String> args = new ArrayList<>();
		args.add("pip");
		args.add("install");
		args.add("--python");
		args.add(envDir.getAbsolutePath());
		args.addAll(Arrays.asList(packages));
		exec(args.toArray(new String[0]));
	}

	/**
	 * Install packages from a requirements.txt file.
	 *
	 * @param envDir The virtual environment directory.
	 * @param requirementsFile Path to requirements.txt file.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 * @throws IllegalStateException if uv has not been installed
	 */
	public void pipInstallFromRequirements(final File envDir, String requirementsFile) throws IOException, InterruptedException {
		exec("pip", "install", "--python", envDir.getAbsolutePath(), "-r", requirementsFile);
	}

	/**
	 * Synchronize a project's dependencies from pyproject.toml.
	 * Creates a virtual environment at projectDir/.venv and installs dependencies.
	 *
	 * @param projectDir The project directory containing pyproject.toml.
	 * @param pythonVersion Optional Python version (e.g., "3.11"). Can be null for default.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 * @throws IllegalStateException if uv has not been installed
	 */
	public void sync(final File projectDir, String pythonVersion) throws IOException, InterruptedException {
		sync(projectDir, pythonVersion, null);
	}

	/**
	 * Synchronize a project's dependencies from pyproject.toml, including the
	 * given PEP 735 dependency groups.
	 * Creates a virtual environment at projectDir/.venv and installs dependencies.
	 *
	 * @param projectDir The project directory containing pyproject.toml.
	 * @param pythonVersion Optional Python version (e.g., "3.11"). Can be null for default.
	 * @param groups Optional dependency groups to include. Can be null for none.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 * @throws IllegalStateException if uv has not been installed
	 */
	public void sync(final File projectDir, String pythonVersion, List<String> groups) throws IOException, InterruptedException {
		List<String> args = new ArrayList<>();
		args.add("sync");
		if (pythonVersion != null && !pythonVersion.isEmpty()) {
			args.add("--python");
			args.add(pythonVersion);
		}
		if (groups != null) {
			for (String group : groups) {
				args.add("--group");
				args.add(group);
			}
		}

		// Run uv sync with working directory set to projectDir.
		exec(projectDir, args.toArray(new String[0]));
	}
}
