/*-
 * #%L
 * Appose: multi-language interprocess cooperation with shared memory.
 * %%
 * Copyright (C) 2023 - 2024 Appose developers.
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

package org.apposed.appose.mamba;

import org.apache.commons.lang3.SystemUtils;

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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Conda-based environment manager, implemented by delegating to micromamba.
 * 
 * @author Ko Sugawara
 * @author Carlos Garcia
 */
class Mamba {
	
	/**
	 * String containing the path that points to the micromamba executable
	 */
	final String mambaCommand;
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
	/**
	 * Path to the folder that contains the directories
	 */
	private final String envsdir;
	/**
	 * Progress made on the download from the Internet of the micromamba software. VAlue between 0 and 1.
	 * 
	 */
	private Double mambaDnwldProgress = 0.0;
	/**
	 * Progress made on the decompressing the micromamba files downloaded from the Internet of the micromamba 
	 * software. VAlue between 0 and 1.
	 */
	private Double mambaDecompressProgress = 0.0;
	/**
	 * Consumer that tracks the progress in the download of micromamba, the software used 
	 * by this class to manage Conda environments
	 */
	private final Consumer<Double> mambaDnwldProgressConsumer = this::updateMambaDnwldProgress;
	/**
	 * Consumer that tracks the progress decompressing the downloaded micromamba files.
	 */
	private final Consumer<Double> mambaDecompressProgressConsumer = this::updateMambaDecompressProgress;
	/**
	 * String that contains all the console output produced by micromamba ever since the {@link Mamba} was instantiated
	 */
	private String mambaConsoleOut = "";
	/**
	 * String that contains all the error output produced by micromamba ever since the {@link Mamba} was instantiated
	 */
	private String mambaConsoleErr = "";
	/**
	 * User custom consumer that tracks the console output produced by the micromamba process when it is executed.
	 */
	private Consumer<String> customConsoleConsumer;
	/**
	 * User custom consumer that tracks the error output produced by the micromamba process when it is executed.
	 */
	private Consumer<String> customErrorConsumer;
	/**
	 * Consumer that tracks the console output produced by the micromamba process when it is executed.
	 * This consumer saves all the log of every micromamba execution
	 */
	private final Consumer<String> consoleConsumer = this::updateConsoleConsumer;
	/**
	 * Consumer that tracks the error output produced by the micromamba process when it is executed.
	 * This consumer saves all the log of every micromamba execution
	 */
	private final Consumer<String> errConsumer = this::updateErrorConsumer;
	/**
	 * Relative path to the micromamba executable from the micromamba {@link #rootdir}
	 */
	private final static String MICROMAMBA_RELATIVE_PATH = SystemUtils.IS_OS_WINDOWS ?
			 File.separator + "Library" + File.separator + "bin" + File.separator + "micromamba.exe" 
			: File.separator + "bin" + File.separator + "micromamba";
	/**
	 * Path where Appose installs Micromamba by default
	 */
	final public static String BASE_PATH = Paths.get(System.getProperty("user.home"), ".local", "share", "appose", "micromamba").toString();
	/**
	 * Name of the folder inside the {@link #rootdir} that contains the different Conda environments created by the Appose Micromamba
	 */
	final public static String ENVS_NAME = "envs";
	/**
	 * URL from where Micromamba is downloaded to be installed
	 */
	public final static String MICROMAMBA_URL =
		"https://micro.mamba.pm/api/micromamba/" + microMambaPlatform() + "/latest";
	/**
	 * ID used to identify the text retrieved from the error stream when a consumer is used
	 */
	public final static String ERR_STREAM_UUUID = UUID.randomUUID().toString();

	/**
	 * 
	 * @return a String that identifies the current OS to download the correct Micromamba version
	 */
	private static String microMambaPlatform() {
		String osName = System.getProperty("os.name");
		if (osName.startsWith("Windows")) osName = "Windows";
		String osArch = System.getProperty("os.arch");
		switch (osName + "|" + osArch) {
			case "Linux|amd64":      return "linux-64";
			case "Linux|aarch64":    return "linux-aarch64";
			case "Linux|ppc64le":    return "linux-ppc64le";
			case "Mac OS X|x86_64":  return "osx-64";
			case "Mac OS X|aarch64": return "osx-arm64";
			case "Windows|amd64":    return "win-64";
			default:                 return null;
		}
	}
	
	private void updateMambaDnwldProgress(Double pp) {
	    double progress = pp != null ? pp : 0.0;
	    mambaDnwldProgress = progress * 1.0;
	}
	
	private void updateConsoleConsumer(String str) {
		if (str == null) str = "";
		mambaConsoleOut += str;
		if (customConsoleConsumer != null)
			customConsoleConsumer.accept(str);
	}
	
	private void updateErrorConsumer(String str) {
		if (str == null) str = "";
		mambaConsoleErr += str;
		if (customErrorConsumer != null)
			customErrorConsumer.accept(str);
	}
	
	private void updateMambaDecompressProgress(Double pp) {
	    double progress = pp != null ? pp : 0.0;
	    this.mambaDecompressProgress = progress * 1.0;
	}

	/**
	 * Returns a {@link ProcessBuilder} with the working directory specified in the
	 * constructor.
	 * 
	 * @param isInheritIO
	 *            Sets the source and destination for subprocess standard I/O to be
	 *            the same as those of the current Java process.
	 * @return The {@link ProcessBuilder} with the working directory specified in
	 *         the constructor.
	 */
	private ProcessBuilder getBuilder( final boolean isInheritIO )
	{
		final ProcessBuilder builder = new ProcessBuilder().directory( new File( rootdir ) );
		if ( isInheritIO )
			builder.inheritIO();
		return builder;
	}

	/**
	 * Create a new {@link Mamba} object. The root dir for the Micromamba installation
	 * will be the default base path defined at {@link #BASE_PATH}
	 * If there is no Micromamba found at the base path {@link #BASE_PATH}, a {@link MambaInstallException} will be thrown
	 * <p></p>
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
		this(BASE_PATH);
	}

	/**
	 * Create a new Conda object. The root dir for Conda installation can be
	 * specified as {@code String}. 
	 * If there is no Micromamba found at the specified path, it will be installed automatically 
	 * if the parameter 'installIfNeeded' is true. If not a {@link MambaInstallException} will be thrown.
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
		if (rootdir == null)
			this.rootdir = BASE_PATH;
		else
			this.rootdir = rootdir;
		this.mambaCommand = new File(this.rootdir + MICROMAMBA_RELATIVE_PATH).getAbsolutePath();
		this.envsdir = Paths.get(rootdir, ENVS_NAME).toAbsolutePath().toString();
		boolean filesExist = Files.notExists( Paths.get( mambaCommand ) );
		if (!filesExist)
			return;
		try {
			getVersion();
		} catch (Exception ex) {
			return;
		}
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
	 * @throws MambaInstallException if micromamba is not installed
	 */
	private void checkMambaInstalled() throws MambaInstallException {
		if (!isMambaInstalled()) throw new MambaInstallException("Micromamba is not installed");
	}

	/**
	 * 
	 * @return the progress made on the download from the Internet of the micromamba software. VAlue between 0 and 1.
	 */
	public double getMicromambaDownloadProgress(){
		return this.mambaDnwldProgress;
	}
	
	/**
	 * 
	 * @return the the progress made on the decompressing the micromamba files downloaded from the Internet of the micromamba 
	 * 	software. VAlue between 0 and 1.
	 */
	public double getMicromambaDecompressProgress(){
		return this.mambaDecompressProgress;
	}
	
	/**
	 * 
	 * @return all the console output produced by micromamba ever since the {@link Mamba} was instantiated
	 */
	public String getMicromambaConsoleStream(){
		return this.mambaConsoleOut;
	}
	
	/**
	 * 
	 * @return all the error output produced by micromamba ever since the {@link Mamba} was instantiated
	 */
	public String getMicromambaErrStream(){
		return mambaConsoleErr;
	}
	
	/**
	 * Set a custom consumer for the console output of every micromamba call
	 * @param custom
	 * 	custom consumer that receives every console line outputed by ecery micromamba call
	 */
	public void setConsoleOutputConsumer(Consumer<String> custom) {
		this.customConsoleConsumer = custom;
	}
	
	/**
	 * Set a custom consumer for the error output of every micromamba call
	 * @param custom
	 * 	custom consumer that receives every error line outputed by ecery micromamba call
	 */
	public void setErrorOutputConsumer(Consumer<String> custom) {
		this.customErrorConsumer = custom;
	}
	
	private File downloadMicromamba() throws IOException, URISyntaxException {
		final File tempFile = File.createTempFile( "micromamba", ".tar.bz2" );
		tempFile.deleteOnExit();
		URL website = MambaInstallerUtils.redirectedURL(new URL(MICROMAMBA_URL));
		long size = MambaInstallerUtils.getFileSize(website);
		Thread currentThread = Thread.currentThread();
		Thread dwnldThread = new Thread(() -> {
			try (
					ReadableByteChannel rbc = Channels.newChannel(website.openStream());
					FileOutputStream fos = new FileOutputStream(tempFile)
			) {
				new FileDownloader(rbc, fos).call(currentThread);
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		});
		dwnldThread.start();
		while (dwnldThread.isAlive()) 
			this.mambaDnwldProgressConsumer.accept(((double) tempFile.length()) / ((double) size));
		if ((((double) tempFile.length()) / ((double) size)) < 1)
			throw new IOException("Error downloading micromamba from: " + MICROMAMBA_URL);
		return tempFile;
	}
	
	private void decompressMicromamba(final File tempFile) 
				throws IOException, InterruptedException {
		final File tempTarFile = File.createTempFile( "micromamba", ".tar" );
		tempTarFile.deleteOnExit();
		MambaInstallerUtils.unBZip2(tempFile, tempTarFile);
		File mambaBaseDir = new File(rootdir);
		if (!mambaBaseDir.isDirectory() && !mambaBaseDir.mkdirs())
	        throw new IOException("Failed to create Micromamba default directory " + mambaBaseDir.getParentFile().getAbsolutePath()
	        		+ ". Please try installing it in another directory.");
		MambaInstallerUtils.unTar(tempTarFile, mambaBaseDir);
		if (!(new File(envsdir)).isDirectory() && !new File(envsdir).mkdirs())
	        throw new IOException("Failed to create Micromamba default envs directory " + envsdir);
		boolean executableSet = new File(mambaCommand).setExecutable(true);
		if (!executableSet)
			throw new IOException("Cannot set file as executable due to missing permissions, "
					+ "please do it manually: " + mambaCommand);
	}
	
	/**
	 * Install Micromamba automatically
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
	
	public String getEnvsDir() {
		return this.envsdir;
	}

	public String getEnvDir(String envName) {
		return getEnvsDir() + File.separator + envName;
	}

	/**
	 * Returns {@code \{"cmd.exe", "/c"\}} for Windows and an empty list for
	 * Mac/Linux.
	 * 
	 * @return {@code \{"cmd.exe", "/c"\}} for Windows and an empty list for
	 *         Mac/Linux.
	 */
	private static List< String > getBaseCommand()
	{
		final List< String > cmd = new ArrayList<>();
		if ( SystemUtils.IS_OS_WINDOWS )
			cmd.addAll( Arrays.asList( "cmd.exe", "/c" ) );
		return cmd;
	}

	/**
	 * Run {@code conda update} in the specified environment. A list of packages to
	 * update and extra parameters can be specified as {@code args}.
	 * 
	 * @param envName
	 *            The environment name to be used for the update command.
	 * @param args
	 *            The list of packages to be updated and extra parameters as
	 *            {@code String...}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 * @throws MambaInstallException if Micromamba has not been installed, thus the instance of {@link Mamba} cannot be used
	 */
	public void updateIn( final String envName, final String... args ) throws IOException, InterruptedException, MambaInstallException
	{
		checkMambaInstalled();
		final List< String > cmd = new ArrayList<>( Arrays.asList( "update", "-p", getEnvDir(envName) ) );
		cmd.addAll( Arrays.asList( args ) );
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
	 * @throws MambaInstallException if Micromamba has not been installed, thus the instance of {@link Mamba} cannot be used
	 */
	public void createWithYaml( final File envDir, final String envYaml ) throws IOException, InterruptedException, MambaInstallException
	{
		checkMambaInstalled();
		runMamba("env", "create", "--prefix",
				envDir.getAbsolutePath(), "-f", envYaml, "-y", "-vv" );
	}

	/**
	 * Run {@code conda create} to create an empty conda environment.
	 * 
	 * @param envName
	 *            The environment name to be created.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 * @throws MambaInstallException if Micromamba has not been installed, thus the instance of {@link Mamba} cannot be used
	 */
	public void create( final String envName ) throws IOException, InterruptedException, MambaInstallException
	{
		checkMambaInstalled();
		runMamba( "create", "-y", "-p", getEnvDir(envName) );
	}

	/**
	 * Run {@code conda create} to create a new mamba environment with a list of
	 * specified packages.
	 * 
	 * @param envName
	 *            The environment name to be created.
	 * @param args
	 *            The list of packages to be installed on environment creation and
	 *            extra parameters as {@code String...}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 * @throws MambaInstallException if Micromamba has not been installed, thus the instance of {@link Mamba} cannot be used
	 */
	public void create( final String envName, final String... args ) throws IOException, InterruptedException, MambaInstallException
	{
		checkMambaInstalled();
		final List< String > cmd = new ArrayList<>( Arrays.asList( "create", "-p", getEnvDir(envName) ) );
		cmd.addAll( Arrays.asList( args ) );
		if (!cmd.contains("--yes") && !cmd.contains("-y")) cmd.add("--yes");
		runMamba(cmd.toArray(new String[0]));
	}

	/**
	 * Run {@code conda create} to create a new conda environment with a list of
	 * specified packages.
	 * 
	 * @param envName
	 *            The environment name to be created. CAnnot be null.
	 * @param channels
	 *            the channels from where the packages can be installed. Can be null
	 * @param packages
	 * 			  the packages that want to be installed during env creation. They can contain the version.
	 * 			  For example, "python" or "python=3.10.1", "numpy" or "numpy=1.20.1". CAn be null if no packages want to be installed
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 * @throws RuntimeException
	 *             If there is any error running the commands
	 * @throws MambaInstallException if Micromamba has not been installed, thus the instance of {@link Mamba} cannot be used
	 */
	public void create( final String envName, List<String> channels, List<String> packages ) throws IOException, InterruptedException, RuntimeException, MambaInstallException
	{
		checkMambaInstalled();
		Objects.requireNonNull(envName, "The name of the environment of interest needs to be provided.");
		final List< String > cmd = new ArrayList<>( Arrays.asList( "create", "-p", getEnvDir(envName) ) );
		if (channels == null) channels = new ArrayList<>();
		for (String chan : channels) { cmd.add("-c"); cmd.add(chan);}
		if (packages == null) packages = new ArrayList<>();
		cmd.addAll(packages);
		if (!cmd.contains("--yes") && !cmd.contains("-y")) cmd.add("--yes");
		runMamba(cmd.toArray(new String[0]));
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
		final List< String > cmd = getBaseCommand();
		if (mambaCommand.contains(" ") && SystemUtils.IS_OS_WINDOWS)
			cmd.add( surroundWithQuotes(Arrays.asList( coverArgWithDoubleQuotes(mambaCommand), "--version" )) );
		else
			cmd.addAll( Arrays.asList( coverArgWithDoubleQuotes(mambaCommand), "--version" ) );
		final Process process = getBuilder( false ).command( cmd ).start();
		if ( process.waitFor() != 0 )
			throw new RuntimeException("Error getting Micromamba version");
		return new BufferedReader( new InputStreamReader( process.getInputStream() ) ).readLine();
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
	 * @throws MambaInstallException if Micromamba has not been installed, thus the instance of {@link Mamba} cannot be used
	 */
	public void runMamba(boolean isInheritIO, final String... args ) throws RuntimeException, IOException, InterruptedException, MambaInstallException
	{
		checkMambaInstalled();
		Thread mainThread = Thread.currentThread();
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
		
		final List< String > cmd = getBaseCommand();
		List<String> argsList = new ArrayList<>();
		argsList.add( coverArgWithDoubleQuotes(mambaCommand) );
		argsList.addAll( Arrays.stream( args ).map(aa -> {
			if (aa.contains(" ") && SystemUtils.IS_OS_WINDOWS) return coverArgWithDoubleQuotes(aa);
			else return aa;
		}).collect(Collectors.toList()) );
		boolean containsSpaces = argsList.stream().anyMatch(aa -> aa.contains(" "));
		
		if (!containsSpaces || !SystemUtils.IS_OS_WINDOWS) cmd.addAll(argsList);
		else cmd.add(surroundWithQuotes(argsList));
		
		ProcessBuilder builder = getBuilder(isInheritIO).command(cmd);
		Process process = builder.start();
		// Use separate threads to read each stream to avoid a deadlock.
		this.consoleConsumer.accept(sdf.format(Calendar.getInstance().getTime()) + " -- STARTING INSTALLATION" + System.lineSeparator());
		long updatePeriod = 300;
		Thread outputThread = new Thread(() -> {
			try (
			        InputStream inputStream = process.getInputStream();
			        InputStream errStream = process.getErrorStream()
					){
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
		                	errChunk += ERR_STREAM_UUUID + errBuff.substring(0, newLineIndex + 1).trim() + System.lineSeparator();
		                	errBuff.delete(0, newLineIndex + 1);
		                }
		            }
	                // Sleep for a bit to avoid busy waiting
	                Thread.sleep(60);
	                if (System.currentTimeMillis() - t0 > updatePeriod) {
						this.consoleConsumer.accept(processChunk);
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
	                errChunk += ERR_STREAM_UUUID + errBuff.toString().trim();
	            }
				this.errConsumer.accept(errChunk);
				this.consoleConsumer.accept(processChunk + System.lineSeparator() 
								+ sdf.format(Calendar.getInstance().getTime()) + " -- TERMINATED PROCESS");
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
        	throw new RuntimeException("Error executing the following command: " + builder.command()
        								+ System.lineSeparator() + this.mambaConsoleOut
        								+ System.lineSeparator() + this.mambaConsoleErr);
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
	 * @throws MambaInstallException if Micromamba has not been installed, thus the instance of {@link Mamba} cannot be used
	 */
	public void runMamba(final String... args ) throws RuntimeException, IOException, InterruptedException, MambaInstallException
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
        	if (arg.contains(schar) && SystemUtils.IS_OS_WINDOWS) {
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
