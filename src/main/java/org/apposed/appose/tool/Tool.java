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
import org.apposed.appose.util.Platforms;
import org.apposed.appose.util.Processes;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class for external tool helpers (Mamba, Pixi, uv, etc.).
 * Provides common functionality for process execution, stream handling,
 * and progress tracking.
 *
 * @author Curtis Rueden
 * @author Carlos Garcia Lopez de Haro
 * @author Claude Code
 */
public abstract class Tool {

	/**
	 * Environment variable which, when set to false, disables checking for
	 * newer releases of all tools. Tool-specific variables (see
	 * {@link #autoUpdateVar()}) take precedence over it.
	 */
	public static final String TOOL_AUTO_UPDATE_VAR = "APPOSE_TOOL_AUTO_UPDATE";

	/** Minimum number of milliseconds between checks for a newer release. */
	public static final long UPDATE_INTERVAL = TimeUnit.DAYS.toMillis(1);

	/** The name of the external tool (e.g. uv, pixi, micromamba). */
	public final String name;

	/** Remote URL to use when downloading the tool. */
	public final String url;

	/** Path to the tool's executable command. */
	public final String command;

	/** Root directory where the tool is installed. */
	public final String rootdir;

	/** Consumer that tracks the standard output stream produced by the tool process. */
	protected Consumer<String> outputConsumer;

	/** Consumer that tracks the standard error stream produced by the tool process. */
	protected Consumer<String> errorConsumer;

	/** Consumer that tracks download progress when installing the tool. */
	protected BiConsumer<Long, Long> downloadProgressConsumer;

	/** Environment variables to set when running tool commands. */
	protected Map<String, String> envVars = new HashMap<>();

	/** Additional command-line flags to pass to tool commands. */
	protected List<String> flags = new ArrayList<>();

	/** Captured stdout from the last command execution. */
	protected final StringBuilder capturedOutput = new StringBuilder();

	/** Captured stderr from the last command execution. */
	protected final StringBuilder capturedError = new StringBuilder();

	public Tool(String name, String url, String command, String rootdir) {
		this.name = name;
		this.url = url;
		this.command = command;
		this.rootdir = rootdir;
	}

	/**
	 * Sets a consumer to receive standard output from the tool process.
	 * @param consumer Consumer that processes output strings
	 */
	public void setOutputConsumer(Consumer<String> consumer) {
		this.outputConsumer = consumer;
	}

	/**
	 * Sets a consumer to receive standard error from the tool process.
	 * @param consumer Consumer that processes error strings
	 */
	public void setErrorConsumer(Consumer<String> consumer) {
		this.errorConsumer = consumer;
	}

	/**
	 * Sets a consumer to track download progress during tool installation.
	 * @param consumer Consumer that receives (current, total) progress updates
	 */
	public void setDownloadProgressConsumer(BiConsumer<Long, Long> consumer) {
		this.downloadProgressConsumer = consumer;
	}

	/**
	 * Sets environment variables to be passed to tool processes.
	 * @param envVars Map of environment variable names to values
	 */
	public void setEnvVars(Map<String, String> envVars) {
		if (envVars != null) {
			this.envVars = new HashMap<>(envVars);
		}
	}

	/**
	 * Sets additional command-line flags to pass to tool commands.
	 * @param flags List of command-line flags
	 */
	public void setFlags(List<String> flags) {
		if (flags != null) {
			this.flags = new ArrayList<>(flags);
		}
	}

	/**
	 * Get the version of the installed tool.
	 * <p>
	 * This default implementation calls the tool with {@code --version} and
	 * extracts the first whitespace-delimited token that starts with a digit.
	 * Subclasses can override this method if their tool uses a different
	 * version reporting format.
	 * </p>
	 *
	 * @return The version string.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 */
	public String version() throws IOException, InterruptedException {
		// Example output of supported tools with --version flag:
		// - 2.3.3
		// - pixi 0.58.0
		// - uv 0.5.25 (9c07c3fc5 2025-01-28)
		execDirect("--version");
		String output = capturedOutput.toString();
		for (String token : output.split(" ")) {
			char c = token.isEmpty() ? '\0' : token.charAt(0);
			if (c >= '0' && c <= '9') return token; // starts with a digit
		}
		return output;
	}

	/**
	 * Downloads and installs the external tool.
	 *
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void install() throws IOException, InterruptedException {
		if (isInstalled()) return;
		decompress(download());
	}

	/**
	 * Upgrades the installed tool, if warranted.
	 * <p>
	 * The tool is upgraded to the latest release, at most once per
	 * {@link #UPDATE_INTERVAL}, unless auto-updating is disabled: the tool's
	 * own {@link #autoUpdateVar()} environment variable (e.g.
	 * {@code APPOSE_PIXI_AUTO_UPDATE}) or else the blanket
	 * {@code APPOSE_TOOL_AUTO_UPDATE} variable is set to false. Regardless, the
	 * tool is upgraded to at least {@link #minVersion()}, if any, so that it
	 * understands state written by newer installations of the tool elsewhere
	 * on the system.
	 * </p>
	 * <p>
	 * Failures (e.g. due to no network connection) are reported to the error
	 * consumer, but not thrown, so that builds can proceed with the existing tool.
	 * </p>
	 *
	 * @throws IOException If the tool is not installed.
	 * @throws InterruptedException If the current thread is interrupted.
	 */
	public void selfUpdate() throws IOException, InterruptedException {
		if (autoUpdateEnabled(System::getenv) && updateCheckDue()) tryUpgrade(null);

		String minVersion = minVersion();
		if (minVersion != null && compareVersions(version(), minVersion) < 0) {
			tryUpgrade(minVersion);
		}
	}

	/**
	 * Gets the minimum acceptable version of the tool; older installations
	 * get upgraded to it by {@link #selfUpdate()}.
	 *
	 * @return The minimum version, or null if there is none.
	 */
	protected String minVersion() {
		return null;
	}

	/**
	 * Gets the environment variable which, when set to false, disables
	 * checking for newer releases of this tool. It takes precedence over
	 * {@link #TOOL_AUTO_UPDATE_VAR}.
	 *
	 * @return The variable name, or null if there is none.
	 */
	protected String autoUpdateVar() {
		return null;
	}

	/**
	 * Upgrades the installed tool to the given version.
	 * <p>
	 * This default implementation downloads the requested release from
	 * {@link #downloadURL} and installs it over the existing one via
	 * {@link #decompress}. Subclasses whose tool can upgrade itself may
	 * override it.
	 * </p>
	 *
	 * @param version The version to upgrade to, or null for the latest release.
	 * @throws IOException If the upgrade fails.
	 * @throws InterruptedException If the current thread is interrupted.
	 */
	protected void upgrade(String version) throws IOException, InterruptedException {
		String target = version;
		if (target == null) {
			target = latestVersion();
			if (target == null || compareVersions(version(), target) >= 0) return;
		}
		String downloadURL = downloadURL(target);
		if (downloadURL == null) return;
		output("Updating " + name + " to " + target + System.lineSeparator());
		decompress(download(downloadURL));
	}

	/**
	 * Gets the version of the tool's latest release.
	 *
	 * @return The latest version, or null if this tool cannot check for newer releases.
	 * @throws IOException If the check fails.
	 */
	protected String latestVersion() throws IOException {
		return null;
	}

	/**
	 * Gets the URL from which the given release of the tool can be downloaded.
	 *
	 * @param version The release version.
	 * @return The download URL, or null if unavailable.
	 */
	protected String downloadURL(String version) {
		return null;
	}

	private void tryUpgrade(String version) throws InterruptedException {
		try {
			upgrade(version);
		}
		catch (IOException e) {
			// Note: The tool's own error output has already gone to the error consumer.
			error("Warning: could not update " + name +
				"; continuing with the installed version." + System.lineSeparator());
		}
	}

	boolean autoUpdateEnabled(Function<String, String> env) {
		for (String var : new String[] { autoUpdateVar(), TOOL_AUTO_UPDATE_VAR }) {
			String value = var == null ? null : env.apply(var);
			if (value == null || value.trim().isEmpty()) continue;
			switch (value.trim().toLowerCase()) {
				case "0": case "false": case "no": case "off": return false;
				default: return true;
			}
		}
		return true;
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
	 * Gets whether the tool is installed or not
	 * @return whether the tool is installed or not
	 */
	public boolean isInstalled() {
		try {
			version();
			return true;
		} catch (IOException | InterruptedException e) {
			return false;
		}
	}

	/**
	 * Executes a tool command with the specified arguments in the tool's root directory.
	 *
	 * @param args Command arguments for the tool.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 * @throws IllegalStateException If the tool has not been installed.
	 */
	public void exec(String... args) throws IOException, InterruptedException {
		exec(null, args);
	}

	/**
	 * Executes a tool command with the specified arguments.
	 *
	 * @param cwd Working directory for the command (null to use tool's root directory).
	 * @param args Command arguments for the tool.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 * @throws IllegalStateException If the tool has not been installed.
	 */
	protected void exec(File cwd, String... args) throws IOException, InterruptedException {
		if (!isInstalled()) {
			File commandFile = new File(command);
			if (commandFile.isFile()) {
				throw new IllegalStateException(name + " is installed at \"" + command +
					"\" but could not be run -- the path may contain characters" +
					" that are special to the shell (e.g. parentheses on Windows)");
			}
			throw new IllegalStateException(name + " is not installed" +
				" (expected executable at \"" + command + "\")");
		}
		doExec(cwd, false, true, args); // silent=false, includeFlags=true
	}

	/**
	 * Executes a tool command with the specified arguments, without validating
	 * the tool installation beforehand, without passing output to external
	 * listeners (see {@link #setOutputConsumer} and {@link #setErrorConsumer}),
	 * and without including flags.
	 * <p>
	 * This method mainly exists for {@link #version()} checking, and subclasses
	 * of {@code Tool} are unlikely to need it&mdash;they should probably use
	 * {@link #exec} instead.
	 * </p>
	 *
	 * @param args Command arguments for the tool.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 */
	protected void execDirect(String... args) throws IOException, InterruptedException {
		doExec(null, true, false, args); // silent=true, includeFlags=false
	}

	protected File download() throws IOException, InterruptedException {
		if (url == null) {
			throw new IOException(name + " is not available for this platform (" +
				Platforms.PLATFORM + "). Please install it manually.");
		}
		return download(url);
	}

	protected File download(String url) throws IOException, InterruptedException {
		try {
			return Downloads.download(name, url, this::updateDownloadProgress);
		}
		catch (URISyntaxException e) {
			// If this happens, it's a bug in the Tool implementation: the
			// URL being used internally to download the tool is malformed.
			// Let's not propagate that URISyntaxException downstream.
			throw new RuntimeException(e);
		}
	}

	protected abstract void decompress(final File archive) throws IOException, InterruptedException;

	/**
	 * Handles a line from the tool's standard output stream.
	 * <ul>
	 * <li>Captures the output for later inclusion in error messages.</li>
	 * <li>Updates the output consumer with a message, if one is registered.</li>
	 * </ul>
	 * @param line The line of stdout to process
	 */
	protected void output(String line) {
		if (line == null || line.isEmpty()) return;
		capturedOutput.append(line);
		if (outputConsumer != null) outputConsumer.accept(line);
	}

	/**
	 * Handles a line from the tool's standard error stream.
	 * <ul>
	 * <li>Captures the error for later inclusion in error messages.</li>
	 * <li>Updates the error consumer with a message, if one is registered.</li>
	 * </ul>
	 * @param line The line of stderr to process
	 */
	protected void error(String line) {
		if (line == null || line.isEmpty()) return;
		capturedError.append(line);
		if (errorConsumer != null) errorConsumer.accept(line);
	}

	/**
	 * Updates the download progress consumer, if one is registered.
	 * @param current Current progress value
	 * @param total Total progress value
	 */
	protected void updateDownloadProgress(long current, long total) {
		if (downloadProgressConsumer != null) {
			downloadProgressConsumer.accept(current, total);
		}
	}

	/**
	 * Executes a tool command with the specified arguments,
	 * without validating the tool installation beforehand.
	 *
	 * @param cwd Working directory for the command (null to use tool's root directory).
	 * @param silent If false, pass command output along to external listeners
	 *                  (see {@link #setOutputConsumer} and {@link #setErrorConsumer}).
	 * @param includeFlags If true, include {@link #flags} in the command argument list (see {@link #setFlags}).
	 * @param args Command arguments for the tool.
	 * @throws IOException If an I/O error occurs.
	 * @throws InterruptedException If the current thread is interrupted.
	 */
	protected void doExec(File cwd, boolean silent, boolean includeFlags, String... args) throws IOException, InterruptedException {
		// Clear captured output from previous command.
		capturedOutput.setLength(0);
		capturedError.setLength(0);

		final List<String> cmd = Platforms.command(command);
		if (includeFlags) cmd.addAll(flags);
		cmd.addAll(Arrays.asList(args));

		final String workingDir = cwd != null ? cwd.getAbsolutePath() : rootdir;
		final ProcessBuilder builder = Processes.builder(new File(workingDir), envVars);
		builder.command(cmd);
		int exitCode = Processes.run(builder,
			silent ? capturedOutput::append : this::output,
			silent ? capturedError::append : this::error);

		if (exitCode != 0) {
			StringBuilder errorMsg = new StringBuilder();
			errorMsg.append(name).append(" command failed with exit code ").append(exitCode);
			errorMsg.append(": ").append(String.join(" ", args));

			// Include stderr if available.
			String stderr = capturedError.toString().trim();
			if (!stderr.isEmpty()) {
				errorMsg.append("\n\nError output:\n").append(stderr);
			}

			// Include stdout if available and stderr was empty.
			String stdout = capturedOutput.toString().trim();
			if (stderr.isEmpty() && !stdout.isEmpty()) {
				errorMsg.append("\n\nOutput:\n").append(stdout);
			}

			throw new IOException(errorMsg.toString());
		}
	}
}
