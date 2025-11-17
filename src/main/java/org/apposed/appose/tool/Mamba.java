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

// Adapted from JavaConda (https://github.com/javaconda/javaconda),
// which has the following license:

/*-*****************************************************************************
 * Copyright (C) 2021, Ko Sugawara
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ****************************************************************************-*/

package org.apposed.appose.tool;

import org.apposed.appose.util.Downloads;
import org.apposed.appose.util.Environments;
import org.apposed.appose.util.Platforms;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Conda-based environment manager, implemented by delegating to micromamba.
 *
 * @author Ko Sugawara
 * @author Carlos Garcia Lopez de Haro
 * @author Curtis Rueden
 */
public class Mamba extends Tool {

	/** Relative path to the micromamba executable from the micromamba {@link #rootdir}. */
	private final static Path MICROMAMBA_RELATIVE_PATH = Platforms.isWindows() ?
			Paths.get("Library", "bin", "micromamba.exe") :
			Paths.get("bin", "micromamba");

	/** Path where Appose installs Micromamba by default. */
	public static final String BASE_PATH = Paths.get(Environments.apposeEnvsDir(), ".mamba").toString();

	/** The filename to download for the current platform. */
	public final static String MICROMAMBA_PLATFORM = microMambaPlatform();

	/** URL from where Micromamba is downloaded to be installed. */
	public final static String MICROMAMBA_URL = MICROMAMBA_PLATFORM == null ? null :
		"https://micro.mamba.pm/api/micromamba/" + MICROMAMBA_PLATFORM + "/latest";

	private static String microMambaPlatform() {
		switch (Platforms.PLATFORM) {
			case "LINUX|X64":     return "linux-64";
			case "LINUX|ARM64":   return "linux-aarch64";
			case "LINUX|PPC64LE": return "linux-ppc64le";
			case "MACOS|X64":     return "osx-64";
			case "MACOS|ARM64":   return "osx-arm64";
			case "WINDOWS|X64":   return "win-64";
			default:              return null;
		}
	}

	/**
	 * Create a new {@link Mamba} object. The root dir for the Micromamba installation
	 * will be the default base path defined at {@link #BASE_PATH}
	 * If there is no Micromamba found at the base path {@link #BASE_PATH}, an {@link IllegalStateException} will be thrown
	 * <p>
	 * It is expected that the Micromamba installation has executable commands as shown below:
	 * </p>
	 * <pre>
	 * MAMBA_ROOT
	 * ├── bin
	 * │   ├── micromamba(.exe)
	 * │   ...
	 * ├── envs
	 * │   ├── your_env
	 * │   │   ├── python(.exe)
	 * </pre>
	 */
	public Mamba() {
		this(null);
	}

	/**
	 * Create a new Mamba object. The root dir for Mamba installation can be
	 * specified as {@code String}.
	 * If there is no Micromamba found at the specified path, it will be installed automatically
	 * if the parameter 'installIfNeeded' is true. If not an {@link IllegalStateException} will be thrown.
	 * <p>
	 * It is expected that the Mamba installation has executable commands as shown below:
	 * </p>
	 * <pre>
	 * MAMBA_ROOT
	 * ├── bin
	 * │   ├── micromamba(.exe)
	 * │   ...
	 * ├── envs
	 * │   ├── your_env
	 * │   │   ├── python(.exe)
	 * </pre>
	 *
	 * @param rootdir
	 *  The root dir for Mamba installation.
	 */
	public Mamba(final String rootdir) {
		super(
			"micromamba",
			MICROMAMBA_URL,
			Paths.get(rootdir == null ? BASE_PATH : rootdir).resolve(MICROMAMBA_RELATIVE_PATH).toAbsolutePath().toString(),
			rootdir == null ? BASE_PATH : rootdir
		);
	}

	@Override
	protected void decompress(final File archive) throws IOException, InterruptedException {
		File mambaBaseDir = new File(rootdir);
		if (!mambaBaseDir.isDirectory() && !mambaBaseDir.mkdirs())
			throw new IOException("Failed to create Micromamba default directory " +
				mambaBaseDir.getParentFile().getAbsolutePath() +
				". Please try installing it in another directory.");
		Downloads.unpack(archive, mambaBaseDir);
		File mmFile = new File(command);
		if (!mmFile.exists()) throw new IOException("Expected micromamba binary is missing: " + command);
		if (!mmFile.canExecute()) {
			boolean executableSet = new File(command).setExecutable(true);
			if (!executableSet)
				throw new IOException("Cannot set file as executable due to missing permissions, "
					+ "please do it manually: " + command);
		}
	}

	/**
	 * Creates an empty conda environment at the specified directory.
	 * This is useful for two-step builds: create empty, then update with environment.yml.
	 *
	 * @param envDir
	 *            The directory where the environment will be created.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted.
	 * @throws IllegalStateException if Micromamba has not been installed
	 */
	public void create(final File envDir) throws IOException, InterruptedException
	{
		exec("create", "--prefix", envDir.getAbsolutePath(), "-y", "--no-rc");
	}

	/**
	 * Updates an existing conda environment from an environment.yml file.
	 *
	 * @param envDir
	 *            The directory of the existing environment.
	 * @param envYaml
	 *            Path to the environment.yml file.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted.
	 * @throws IllegalStateException if Micromamba has not been installed
	 */
	public void update(final File envDir, final File envYaml) throws IOException, InterruptedException
	{
		exec("env", "update", "-y", "--prefix",
			envDir.getAbsolutePath(), "-f", envYaml.getAbsolutePath());
	}
}
