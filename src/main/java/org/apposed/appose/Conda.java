/*******************************************************************************
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
 ******************************************************************************/
package org.apposed.appose;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.lang3.SystemUtils;
import org.apposed.appose.CondaException.EnvironmentExistsException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Conda environment manager, implemented by delegating to micromamba.
 * 
 * @author Ko Sugawara
 * @author Curtis Rueden
 */
public class Conda {
	
	final String pythonCommand = SystemUtils.IS_OS_WINDOWS ? "python.exe" : "bin/python";
	
	final String condaCommand;
	
	private String envName = DEFAULT_ENVIRONMENT_NAME;
	
	private final String rootdir;
	
	public final static String DEFAULT_ENVIRONMENT_NAME = "base";

	private final static int TIMEOUT_MILLIS = 10 * 1000;
	
	private final static String CONDA_RELATIVE_PATH = SystemUtils.IS_OS_WINDOWS ? 
			 File.separator + "Library" + File.separator + "bin" + File.separator + "micromamba.exe" 
			: File.separator + "bin" + File.separator + "micromamba";

	final public static String BASE_PATH = Paths.get(System.getProperty("user.home"), ".local", "share", "appose", "micromamba").toString();
	
	final public static String ENVS_PATH = Paths.get(BASE_PATH, "envs").toString();
	
	public final static String MICROMAMBA_URL =
		"https://micro.mamba.pm/api/micromamba/" + microMambaPlatform() + "/latest";

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
	 * Create a new Conda object. The root dir for Conda installation can be
	 * specified as {@code String}. If there is no directory found at the specified
	 * path, Miniconda will be automatically installed in the path. It is expected
	 * that the Conda installation has executable commands as shown below:
	 * 
	 * <pre>
	 * CONDA_ROOT
	 * ├── condabin
	 * │   ├── conda(.bat)
	 * │   ... 
	 * ├── envs
	 * │   ├── your_env
	 * │   │   ├── python(.exe)
	 * </pre>
	 * 
	 * @param rootdir
	 *            The root dir for Conda installation.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 * @throws ArchiveException 
	 * @throws URISyntaxException 
	 */
	public Conda( final String rootdir ) throws IOException, InterruptedException, ArchiveException, URISyntaxException
	{
		if (rootdir == null)
			this.rootdir = BASE_PATH;
		else
			this.rootdir = rootdir;
		this.condaCommand = this.rootdir + CONDA_RELATIVE_PATH;
		if ( Files.notExists( Paths.get( condaCommand ) ) )
		{

			final File tempFile = File.createTempFile( "miniconda", ".tar.bz2" );
			tempFile.deleteOnExit();
			URL website = MambaInstallerUtils.redirectedURL(new URL(MICROMAMBA_URL));
			ReadableByteChannel rbc = Channels.newChannel(website.openStream());
			try (FileOutputStream fos = new FileOutputStream(tempFile)) {
				fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
			}
			final File tempTarFile = File.createTempFile( "miniconda", ".tar" );
			tempTarFile.deleteOnExit();
			MambaInstallerUtils.unBZip2(tempFile, tempTarFile);
			File mambaBaseDir = new File(BASE_PATH);
			if (!mambaBaseDir.isDirectory() && !mambaBaseDir.mkdirs())
    	        throw new IOException("Failed to create Micromamba default directory " + mambaBaseDir.getParentFile().getAbsolutePath());
			MambaInstallerUtils.unTar(tempTarFile, mambaBaseDir);
			if (!(new File(ENVS_PATH)).isDirectory() && !new File(ENVS_PATH).mkdirs())
    	        throw new IOException("Failed to create Micromamba default envs directory " + ENVS_PATH);
			
		}

		// The following command will throw an exception if Conda does not work as
		// expected.
		getVersion();
	}

	/**
	 * Returns {@code \{"cmd.exe", "/c"\}} for Windows and an empty list for
	 * Mac/Linux.
	 * 
	 * @return {@code \{"cmd.exe", "/c"\}} for Windows and an empty list for
	 *         Mac/Linux.
	 * @throws IOException
	 */
	private List< String > getBaseCommand()
	{
		final List< String > cmd = new ArrayList<>();
		if ( SystemUtils.IS_OS_WINDOWS )
			cmd.addAll( Arrays.asList( "cmd.exe", "/c" ) );
		return cmd;
	}

	/**
	 * Run {@code conda update} in the activated environment. A list of packages to
	 * be updated and extra parameters can be specified as {@code args}.
	 * 
	 * @param args
	 *            The list of packages to be updated and extra parameters as
	 *            {@code String...}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void update( final String... args ) throws IOException, InterruptedException
	{
		updateIn( envName, args );
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
	 */
	public void updateIn( final String envName, final String... args ) throws IOException, InterruptedException
	{
		final List< String > cmd = new ArrayList<>( Arrays.asList( "update", "-y", "-n", envName ) );
		cmd.addAll( Arrays.asList( args ) );
		runConda( cmd.stream().toArray( String[]::new ) );
	}

	/**
	 * Run {@code conda create} to create a conda environment defined by the input environment yaml file.
	 * 
	 * @param envName
	 *            The environment name to be created.
	 * @param envYaml
	 *            The environment yaml file containing the information required to build it 
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void createWithYaml( final String envName, final String envYaml ) throws IOException, InterruptedException
	{
		createWithYaml(envName, envYaml, false);
	}

	/**
	 * Run {@code conda create} to create a conda environment defined by the input environment yaml file.
	 * 
	 * @param envName
	 *            The environment name to be created.
	 * @param envYaml
	 *            The environment yaml file containing the information required to build it 
	 * @param consumer
	 *            String consumer that keeps track of the environment creation
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void createWithYaml( final String envName, final String envYaml, Consumer<String> consumer ) throws IOException, InterruptedException
	{
		createWithYaml(envName, envYaml, false, consumer);
	}

	/**
	 * Run {@code conda create} to create a conda environment defined by the input environment yaml file.
	 * 
	 * @param envName
	 *            The environment name to be created.
	 * @param envYaml
	 *            The environment yaml file containing the information required to build it  
	 * @param envName
	 *            The environment name to be created.
	 * @param isForceCreation
	 *            Force creation of the environment if {@code true}. If this value
	 *            is {@code false} and an environment with the specified name
	 *            already exists, throw an {@link EnvironmentExistsException}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void createWithYaml( final String envName, final String envYaml, final boolean isForceCreation ) throws IOException, InterruptedException
	{
		if ( !isForceCreation && getEnvironmentNames().contains( envName ) )
			throw new EnvironmentExistsException();
		runConda( "env", "create", "--prefix",
				ENVS_PATH + File.separator + envName, "-f", envYaml, "-y" );
	}

	/**
	 * Run {@code conda create} to create a conda environment defined by the input environment yaml file.
	 * 
	 * @param envName
	 *            The environment name to be created.
	 * @param envYaml
	 *            The environment yaml file containing the information required to build it  
	 * @param envName
	 *            The environment name to be created.
	 * @param isForceCreation
	 *            Force creation of the environment if {@code true}. If this value
	 *            is {@code false} and an environment with the specified name
	 *            already exists, throw an {@link EnvironmentExistsException}.
	 * @param consumer
	 *            String consumer that keeps track of the environment creation
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void createWithYaml( final String envName, final String envYaml, final boolean isForceCreation, Consumer<String> consumer) throws IOException, InterruptedException
	{
		if ( !isForceCreation && getEnvironmentNames().contains( envName ) )
			throw new EnvironmentExistsException();
		runConda(consumer, "env", "create", "--prefix",
				ENVS_PATH + File.separator + envName, "-f", envYaml, "-y" );
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
	 */
	public void create( final String envName ) throws IOException, InterruptedException
	{
		create( envName, false );
	}

	/**
	 * Run {@code conda create} to create an empty conda environment.
	 * 
	 * @param envName
	 *            The environment name to be created.
	 * @param isForceCreation
	 *            Force creation of the environment if {@code true}. If this value
	 *            is {@code false} and an environment with the specified name
	 *            already exists, throw an {@link EnvironmentExistsException}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void create( final String envName, final boolean isForceCreation ) throws IOException, InterruptedException
	{
		if ( !isForceCreation && getEnvironmentNames().contains( envName ) )
			throw new EnvironmentExistsException();
		runConda( "create", "-y", "-p", ENVS_PATH + File.separator + envName );
	}

	/**
	 * Run {@code conda create} to create a new conda environment with a list of
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
	 */
	public void create( final String envName, final String... args ) throws IOException, InterruptedException
	{
		create( envName, false, args );
	}

	/**
	 * Run {@code conda create} to create a new conda environment with a list of
	 * specified packages.
	 * 
	 * @param envName
	 *            The environment name to be created.
	 * @param isForceCreation
	 *            Force creation of the environment if {@code true}. If this value
	 *            is {@code false} and an environment with the specified name
	 *            already exists, throw an {@link EnvironmentExistsException}.
	 * @param args
	 *            The list of packages to be installed on environment creation and
	 *            extra parameters as {@code String...}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void create( final String envName, final boolean isForceCreation, final String... args ) throws IOException, InterruptedException
	{
		if ( !isForceCreation && getEnvironmentNames().contains( envName ) )
			throw new EnvironmentExistsException();
		final List< String > cmd = new ArrayList<>( Arrays.asList( "env", "create", "--force", "-p", ENVS_PATH + File.separator + envName ) );
		cmd.addAll( Arrays.asList( args ) );
		runConda( cmd.stream().toArray( String[]::new ) );
	}

	/**
	 * This method works as if the user runs {@code conda activate envName}. This
	 * method internally calls {@link Conda#setEnvName(String)}.
	 * 
	 * @param envName
	 *            The environment name to be activated.
	 * @throws IOException
	 *             If an I/O error occurs.
	 */
	public void activate( final String envName ) throws IOException
	{
		if ( getEnvironmentNames().contains( envName ) )
			setEnvName( envName );
		else
			throw new IllegalArgumentException( "environment: " + envName + " not found." );
	}

	/**
	 * This method works as if the user runs {@code conda deactivate}. This method
	 * internally sets the {@code envName} to {@code base}.
	 */
	public void deactivate()
	{
		setEnvName( DEFAULT_ENVIRONMENT_NAME );
	}

	/**
	 * This method is used by {@code Conda#activate(String)} and
	 * {@code Conda#deactivate()}. This method is kept private since it is not
	 * expected to call this method directory.
	 * 
	 * @param envName
	 *            The environment name to be set.
	 */
	private void setEnvName( final String envName )
	{
		this.envName = envName;
	}

	/**
	 * Returns the active environment name.
	 * 
	 * @return The active environment name.
	 * 
	 */
	public String getEnvName()
	{
		return envName;
	}

	/**
	 * Run {@code conda install} in the activated environment. A list of packages to
	 * install and extra parameters can be specified as {@code args}.
	 * 
	 * @param args
	 *            The list of packages to be installed and extra parameters as
	 *            {@code String...}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void install( final String... args ) throws IOException, InterruptedException
	{
		installIn( envName, args );
	}

	/**
	 * Run {@code conda install} in the specified environment. A list of packages to
	 * install and extra parameters can be specified as {@code args}.
	 * 
	 * @param envName
	 *            The environment name to be used for the install command.
	 * @param args
	 *            The list of packages to be installed and extra parameters as
	 *            {@code String...}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void installIn( final String envName, final String... args ) throws IOException, InterruptedException
	{
		final List< String > cmd = new ArrayList<>( Arrays.asList( "install", "-y", "-n", envName ) );
		cmd.addAll( Arrays.asList( args ) );
		runConda( cmd.stream().toArray( String[]::new ) );
	}

	/**
	 * Run {@code pip install} in the activated environment. A list of packages to
	 * install and extra parameters can be specified as {@code args}.
	 * 
	 * @param args
	 *            The list of packages to be installed and extra parameters as
	 *            {@code String...}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void pipInstall( final String... args ) throws IOException, InterruptedException
	{
		pipInstallIn( envName, args );
	}

	/**
	 * Run {@code pip install} in the specified environment. A list of packages to
	 * install and extra parameters can be specified as {@code args}.
	 * 
	 * @param envName
	 *            The environment name to be used for the install command.
	 * @param args
	 *            The list of packages to be installed and extra parameters as
	 *            {@code String...}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void pipInstallIn( final String envName, final String... args ) throws IOException, InterruptedException
	{
		final List< String > cmd = new ArrayList<>( Arrays.asList( "-m", "pip", "install" ) );
		cmd.addAll( Arrays.asList( args ) );
		runPythonIn( envName, cmd.stream().toArray( String[]::new ) );
	}

	/**
	 * Run a Python command in the activated environment. This method automatically
	 * sets environment variables associated with the activated environment. In
	 * Windows, this method also sets the {@code PATH} environment variable so that
	 * the specified environment runs as expected.
	 * 
	 * @param args
	 *            One or more arguments for the Python command.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void runPython( final String... args ) throws IOException, InterruptedException
	{
		runPythonIn( envName, args );
	}

	/**
	 * Run a Python command in the specified environment. This method automatically
	 * sets environment variables associated with the specified environment. In
	 * Windows, this method also sets the {@code PATH} environment variable so that
	 * the specified environment runs as expected.
	 * 
	 * @param envName
	 *            The environment name used to run the Python command.
	 * @param args
	 *            One or more arguments for the Python command.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void runPythonIn( final String envName, final String... args ) throws IOException, InterruptedException
	{
		final List< String > cmd = getBaseCommand();
		if ( envName.equals( DEFAULT_ENVIRONMENT_NAME ) )
			cmd.add( pythonCommand );
		else
			cmd.add( Paths.get( "envs", envName, pythonCommand ).toString() );
		cmd.addAll( Arrays.asList( args ) );
		final ProcessBuilder builder = getBuilder( true );
		if ( SystemUtils.IS_OS_WINDOWS )
		{
			final Map< String, String > envs = builder.environment();
			final String envDir = Paths.get( rootdir, "envs", envName ).toString();
			envs.put( "Path", envDir + ";" + envs.get( "Path" ) );
			envs.put( "Path", Paths.get( envDir, "Scripts" ).toString() + ";" + envs.get( "Path" ) );
			envs.put( "Path", Paths.get( envDir, "Library" ).toString() + ";" + envs.get( "Path" ) );
			envs.put( "Path", Paths.get( envDir, "Library", "Bin" ).toString() + ";" + envs.get( "Path" ) );
		}
		// TODO find way to get env vars in micromamba builder.environment().putAll( getEnvironmentVariables( envName ) );
		if ( builder.command( cmd ).start().waitFor() != 0 )
			throw new RuntimeException();
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
	public String getVersion() throws IOException, InterruptedException
	{
		final List< String > cmd = getBaseCommand();
		cmd.addAll( Arrays.asList( condaCommand, "--version" ) );
		final Process process = getBuilder( false ).command( cmd ).start();
		if ( process.waitFor() != 0 )
			throw new RuntimeException();
		return new BufferedReader( new InputStreamReader( process.getInputStream() ) ).readLine();
	}

	/**
	 * Run a Conda command with one or more arguments.
	 * 
	 * @param consumer
	 * 			  String consumer that receives the Strings that the process prints to the console
	 * @param args
	 *            One or more arguments for the Conda command.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void runConda(Consumer<String> consumer, final String... args ) throws RuntimeException, IOException, InterruptedException
	{
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
		
		final List< String > cmd = getBaseCommand();
		cmd.add( condaCommand );
		cmd.addAll( Arrays.asList( args ) );

		ProcessBuilder builder = getBuilder(true).command(cmd);
		Process process = builder.start();
		try (
				BufferedReader outReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
				BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
				) {
	        String line = outReader.readLine();
	        String errLine = errReader.readLine();
	        while (line != null || errLine != null) {
	        	if (line != null)
	        		consumer.accept(sdf.format(cal.getTime()) + " -- PROGRESS -> " + line);
	        	if (errLine != null)
	        		consumer.accept(sdf.format(cal.getTime()) + " -- ERROR -> " + errLine);
	            line = outReader.readLine();
	            errLine = errReader.readLine();
	        }
	        if (process.waitFor() != 0)
	        	throw new RuntimeException("Error executing the following command: " + builder.command());
		}
	}

	/**
	 * Run a Conda command with one or more arguments.
	 * 
	 * @param args
	 *            One or more arguments for the Conda command.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void runConda(final String... args ) throws RuntimeException, IOException, InterruptedException
	{
		final List< String > cmd = getBaseCommand();
		cmd.add( condaCommand );
		cmd.addAll( Arrays.asList( args ) );
		if ( getBuilder( true ).command( cmd ).start().waitFor() != 0 )
			throw new RuntimeException();
	}

	/**
	 * Returns environment variables associated with the activated environment as
	 * {@code Map< String, String >}.
	 * 
	 * @return The environment variables as {@code Map< String, String >}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	/* TODO find equivalent in mamba
	public Map< String, String > getEnvironmentVariables() throws IOException, InterruptedException
	{
		return getEnvironmentVariables( envName );
	}
	*/

	/**
	 * Returns environment variables associated with the specified environment as
	 * {@code Map< String, String >}.
	 * 
	 * @param envName
	 *            The environment name used to run the Python command.
	 * @return The environment variables as {@code Map< String, String >}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	/**
	 * TODO find equivalent in mamba
	public Map< String, String > getEnvironmentVariables( final String envName ) throws IOException, InterruptedException
	{
		final List< String > cmd = getBaseCommand();
		cmd.addAll( Arrays.asList( condaCommand, "env", "config", "vars", "list", "-n", envName ) );
		final Process process = getBuilder( false ).command( cmd ).start();
		if ( process.waitFor() != 0 )
			throw new RuntimeException();
		final Map< String, String > map = new HashMap<>();
		try (final BufferedReader reader = new BufferedReader( new InputStreamReader( process.getInputStream() ) ))
		{
			String line;

			while ( ( line = reader.readLine() ) != null )
			{
				final String[] keyVal = line.split( " = " );
				map.put( keyVal[ 0 ], keyVal[ 1 ] );
			}
		}
		return map;
	}
	*/

	/**
	 * Returns a list of the Conda environment names as {@code List< String >}.
	 * 
	 * @return The list of the Conda environment names as {@code List< String >}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public List< String > getEnvironmentNames() throws IOException
	{
		final List< String > envs = new ArrayList<>( Arrays.asList( DEFAULT_ENVIRONMENT_NAME ) );
		envs.addAll( Files.list( Paths.get( ENVS_PATH ) )
				.map( p -> p.getFileName().toString() )
				.filter( p -> !p.startsWith( "." ) )
				.collect( Collectors.toList() ) );
		return envs;
	}
	
	public static boolean checkDependenciesInEnv(String envDir, List<String> dependencies) {
		if (!(new File(envDir).isDirectory()))
			return false;
		Builder env = new Builder().conda(new File(envDir));
		// TODO run conda list -p /full/path/to/env
		return false;
	}
	
	public boolean checkEnvFromYamlExists(String envYaml) {
		if (envYaml == null || new File(envYaml).isFile() == false 
				|| (envYaml.endsWith(".yaml") && envYaml.endsWith(".yml"))) {
			return false;
		}
		// TODO parse yaml without adding deps
		return false;
	}

}
