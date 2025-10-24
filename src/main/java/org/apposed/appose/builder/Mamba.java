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

package org.apposed.appose.builder;

import org.apposed.appose.util.Downloads;
import org.apposed.appose.util.Environments;
import org.apposed.appose.util.Platforms;

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
 * Conda-based environment manager, implemented by delegating to micromamba.
 *
 * @author Ko Sugawara
 * @author Carlos Garcia Lopez de Haro
 */
public class Mamba extends Tool {

	/** String containing the path that points to the micromamba executable. */
	public final String mambaCommand;

	/**
	 * Root directory of micromamba that also contains the environments folder
	 *
	 * <pre>
	 * rootdir
	 * ├── bin
	 * │   ├── micromamba(.exe)
	 * │   ...
	 * ├── envs
	 * │   ├── your_env
	 * │   │   ├── python(.exe)
	 * </pre>
	 */
	private final String rootdir;

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
	 * Create a new Conda object. The root dir for Conda installation can be
	 * specified as {@code String}. 
	 * If there is no Micromamba found at the specified path, it will be installed automatically 
	 * if the parameter 'installIfNeeded' is true. If not an {@link IllegalStateException} will be thrown.
	 * <p>
	 * It is expected that the Conda installation has executable commands as shown below:
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
		this.rootdir = rootdir == null ? BASE_PATH : rootdir;
		this.mambaCommand = Paths.get(this.rootdir).resolve(MICROMAMBA_RELATIVE_PATH).toAbsolutePath().toString();
	}

	/**
	 * Gets whether micromamba is installed or not to be able to use the instance of {@link Mamba}
	 * @return whether micromamba is installed or not to be able to use the instance of {@link Mamba}
	 */
	public boolean isMambaInstalled() {
		try {
			getVersion();
			return true;
		} catch (IOException | InterruptedException e) {
			return false;
        }
    }

	/**
	 * Check whether micromamba is installed or not to be able to use the instance of {@link Mamba}
	 * @throws IllegalStateException if micromamba is not installed
	 */
	private void checkMambaInstalled() {
		if (!isMambaInstalled()) throw new IllegalStateException("Micromamba is not installed");
	}

	private File downloadMicromamba() throws IOException, InterruptedException, URISyntaxException {
		return Downloads.download("micromamba", MICROMAMBA_URL, this::updateDownloadProgress);
	}

	private void decompressMicromamba(final File tempFile) throws IOException, InterruptedException {
		final File tempTarFile = File.createTempFile("micromamba", ".tar");
		tempTarFile.deleteOnExit();
		Downloads.unBZip2(tempFile, tempTarFile);
		File mambaBaseDir = new File(rootdir);
		if (!mambaBaseDir.isDirectory() && !mambaBaseDir.mkdirs())
			throw new IOException("Failed to create Micromamba default directory " +
				mambaBaseDir.getParentFile().getAbsolutePath() +
				". Please try installing it in another directory.");
		Downloads.unTar(tempTarFile, mambaBaseDir);
		File mmFile = new File(mambaCommand);
		if (!mmFile.exists()) throw new IOException("Expected micromamba binary is missing: " + mambaCommand);
		if (!mmFile.canExecute()) {
			boolean executableSet = new File(mambaCommand).setExecutable(true);
			if (!executableSet)
				throw new IOException("Cannot set file as executable due to missing permissions, "
					+ "please do it manually: " + mambaCommand);
		}
	}

	/**
	 * Downloads and installs Micromamba.
	 *
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 * @throws URISyntaxException  if there is any error with the micromamba url
	 */
	public void installMicromamba() throws IOException, InterruptedException, URISyntaxException {
		if (isMambaInstalled()) return;
		decompressMicromamba(downloadMicromamba());
	}

	/**
	 * Run {@code conda update} in the specified environment. A list of packages to
	 * update and extra parameters can be specified as {@code args}.
	 *
	 * @param envDir
	 *            The directory within which the environment will be updated.
	 * @param args
	 *            The list of packages to be updated and extra parameters as
	 *            {@code String...}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 * @throws IllegalStateException if Micromamba has not been installed, thus the instance of {@link Mamba} cannot be used
	 */
	public void updateIn(final File envDir, final String... args) throws IOException, InterruptedException
	{
		checkMambaInstalled();
		final List< String > cmd = new ArrayList<>(Arrays.asList("update", "--prefix", envDir.getAbsolutePath()));
		cmd.addAll(Arrays.asList(args));
		if (!cmd.contains("--yes") && !cmd.contains("-y")) cmd.add("--yes");
		runMamba(cmd.toArray(new String[0]));
	}

	/**
	 * Run {@code conda create} to create a Conda environment defined by the input environment yaml file.
	 * 
	 * @param envDir
	 *            The directory within which the environment will be created.
	 * @param envYaml
	 *            The environment yaml file containing the information required to build it 
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 * @throws IllegalStateException if Micromamba has not been installed, thus the instance of {@link Mamba} cannot be used
	 */
	public void createWithYaml(final File envDir, final String envYaml) throws IOException, InterruptedException
	{
		checkMambaInstalled();
		runMamba("env", "create", "--prefix",
				envDir.getAbsolutePath(), "-f", envYaml, "-y", "-vv", "--no-rc");
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
		checkMambaInstalled();
		runMamba("create", "--prefix", envDir.getAbsolutePath(), "-y", "--no-rc");
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
		checkMambaInstalled();
		runMamba("env", "update", "-y", "--prefix",
				envDir.getAbsolutePath(), "-f", envYaml.getAbsolutePath());
	}

	/**
	 * Returns Conda version as a {@code String}.
	 * 
	 * @return The Conda version as a {@code String}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public String getVersion() throws IOException, InterruptedException {
		final List< String > cmd = Platforms.baseCommand();
		if (mambaCommand.contains(" ") && Platforms.isWindows())
			cmd.add(surroundWithQuotes(Arrays.asList(coverArgWithDoubleQuotes(mambaCommand), "--version")));
		else
			cmd.addAll(Arrays.asList(coverArgWithDoubleQuotes(mambaCommand), "--version"));
		final Process process = processBuilder(rootdir, false).command(cmd).start();
		if (process.waitFor() != 0)
			throw new RuntimeException("Error getting Micromamba version");
		return new BufferedReader(new InputStreamReader(process.getInputStream())).readLine();
	}

	/**
	 * Run a Conda command with one or more arguments.
	 * 
	 * @param isInheritIO
	 *            Sets the source and destination for subprocess standard I/O to be
	 *            the same as those of the current Java process.
	 * @param args
	 *            One or more arguments for the Mamba command.
	 * @throws RuntimeException
	 *             If there is any error running the commands
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 * @throws IllegalStateException if Micromamba has not been installed, thus the instance of {@link Mamba} cannot be used
	 */
	public void runMamba(boolean isInheritIO, final String... args) throws RuntimeException, IOException, InterruptedException
	{
		checkMambaInstalled();
		Thread mainThread = Thread.currentThread();

		final List< String > cmd = Platforms.baseCommand();
		List<String> argsList = new ArrayList<>();
		argsList.add(coverArgWithDoubleQuotes(mambaCommand));
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
		
		ProcessBuilder builder = processBuilder(rootdir, isInheritIO).command(cmd);
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
			throw new InterruptedException("Mamba process stopped. The command being executed was: " + cmd);
		}
		// Wait for all output to be read
		outputThread.join();
		if (processResult != 0)
			throw new RuntimeException("Exit code " + processResult + " from command execution: " + builder.command());
	}

	/**
	 * Run a Conda command with one or more arguments.
	 * 
	 * @param args
	 *            One or more arguments for the Conda command.
	 * @throws RuntimeException
	 *             If there is any error running the commands
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 * @throws IllegalStateException if Micromamba has not been installed, thus the instance of {@link Mamba} cannot be used
	 */
	public void runMamba(final String... args) throws RuntimeException, IOException, InterruptedException
	{
		checkMambaInstalled();
		runMamba(false, args);
	}

	/**
	 * In Windows, if a command prompt argument contains and space " " it needs to
	 * start and end with double quotes
	 * @param arg
	 * 	the cmd argument
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
	 * When an argument of a command prompt argument in Windows contains an space, not
	 * only the argument needs to be surrounded by double quotes, but the whole sentence
	 * @param args
	 * 	arguments to be executed by the windows cmd
	 * @return a complete Sting containing all the arguments and surrounded by double quotes
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
