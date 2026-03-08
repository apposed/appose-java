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

package org.apposed.appose.builder;

import org.apposed.appose.Builder.ProgressConsumer;
import org.apposed.appose.util.Platforms;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Monitors pixi install progress by parsing stderr output (at {@code -vv}
 * verbosity) and polling the {@code conda-meta/} directory for installed
 * packages. Fires progress events through existing
 * {@link ProgressConsumer} subscribers.
 * <p>
 * This class sits between pixi's stderr stream and the builder's error
 * subscribers, intercepting each line to detect phase transitions and
 * emit structured progress updates.
 * </p>
 *
 * @author Curtis Rueden
 */
class PixiInstallMonitor {

	// -- Stderr patterns (requires -vv) --

	/** Matches: {@code DEBUG solve{...}: ...solve_pixi: fetched N records} */
	private static final Pattern FETCHED_RECORDS = Pattern.compile(
		"solve\\{.*\\}.*fetched (\\d+) records");

	/** Matches: {@code INFO ...::update: resolved conda environment for solve group 'X' 'platform'} */
	private static final Pattern SOLVED = Pattern.compile(
		"resolved conda environment for solve group");

	/** Matches: {@code DEBUG pixi_core::environment::conda_prefix: updating prefix for 'X'} */
	private static final Pattern CONDA_PREFIX_UPDATING = Pattern.compile(
		"conda_prefix: updating prefix for");

	/** Matches: {@code DEBUG pixi_core::environment::conda_metadata: Prefix file updated} */
	private static final Pattern CONDA_METADATA_DONE = Pattern.compile(
		"conda_metadata: Prefix file updated");

	/** Matches: {@code DEBUG pixi_install_pypi: N of M required packages are considered installed} */
	private static final Pattern PYPI_REQUIRED = Pattern.compile(
		"pixi_install_pypi: (\\d+) of (\\d+) required packages are considered installed");

	/** Matches: {@code INFO pixi_install_pypi: Prepared N packages} */
	private static final Pattern PYPI_PREPARED = Pattern.compile(
		"pixi_install_pypi: Prepared (\\d+) packages");

	/** Matches: {@code INFO pixi_install_pypi: Installed N packages} */
	private static final Pattern PYPI_INSTALLED = Pattern.compile(
		"pixi_install_pypi: Installed (\\d+) packages");

	/** Matches: {@code INFO ...::update: Installed environment 'X'} */
	private static final Pattern ENV_INSTALLED = Pattern.compile(
		"Installed environment '");

	private final File envDir;
	private final String envName;
	private final String pixiPlatform;
	private final List<ProgressConsumer> progressSubscribers;
	private final Consumer<String> originalErrorConsumer;

	// -- Solve tracking --
	private final AtomicInteger totalPlatforms = new AtomicInteger(0);
	private final AtomicInteger solved = new AtomicInteger(0);

	// -- Conda install tracking --
	private volatile int totalConda = -1;
	private final AtomicBoolean condaPollingActive = new AtomicBoolean(false);
	private volatile Thread condaPollingThread;

	// -- PyPI tracking --
	private volatile int totalPypi = 0;
	private volatile int alreadyInstalledPypi = 0;

	/**
	 * Creates a new monitor for a pixi install operation.
	 *
	 * @param envDir The pixi project directory (containing pixi.toml).
	 * @param envName The pixi environment name (e.g. "default").
	 * @param progressSubscribers The progress subscribers to fire events to.
	 * @param originalErrorConsumer The original error consumer to pass lines through to.
	 */
	PixiInstallMonitor(
		File envDir,
		String envName,
		List<ProgressConsumer> progressSubscribers,
		Consumer<String> originalErrorConsumer)
	{
		this.envDir = envDir;
		this.envName = envName;
		this.pixiPlatform = pixiPlatformString();
		this.progressSubscribers = progressSubscribers;
		this.originalErrorConsumer = originalErrorConsumer;
	}

	/**
	 * Intercepts a stderr line, parsing it for phase-transition signals
	 * and firing progress events as appropriate. The line is always
	 * forwarded to the original error consumer.
	 *
	 * @param line A line from pixi's stderr.
	 */
	void intercept(String line) {
		if (line != null && !line.isEmpty()) {
			parseLine(line);
		}
		// Always forward to the original consumer.
		if (originalErrorConsumer != null) {
			originalErrorConsumer.accept(line);
		}
	}

	/**
	 * Stops any background polling threads. Call this after
	 * {@code pixi install} completes.
	 */
	void shutdown() {
		condaPollingActive.set(false);
		Thread t = condaPollingThread;
		if (t != null) {
			t.interrupt();
			try {
				t.join(2000);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			condaPollingThread = null;
		}
	}

	// -- Helper methods --

	private void parseLine(String line) {
		Matcher m;

		// Solve phase: platform fetched records (accumulates denominator).
		if (FETCHED_RECORDS.matcher(line).find()) {
			totalPlatforms.incrementAndGet();
			return;
		}

		// Solve phase: a platform resolved.
		if (SOLVED.matcher(line).find()) {
			int s = solved.incrementAndGet();
			int total = totalPlatforms.get();
			if (total > 0) {
				fireProgress("Solving", s, total);
			}
			return;
		}

		// Conda install begins: parse lock file, start polling.
		if (CONDA_PREFIX_UPDATING.matcher(line).find()) {
			totalConda = parseLockFileCondaCount();
			if (totalConda > 0) {
				startCondaPolling();
			}
			return;
		}

		// Conda install finished.
		if (CONDA_METADATA_DONE.matcher(line).find()) {
			condaPollingActive.set(false);
			if (totalConda > 0) {
				fireProgress("Installing conda packages", totalConda, totalConda);
			}
			return;
		}

		// PyPI: required packages count.
		m = PYPI_REQUIRED.matcher(line);
		if (m.find()) {
			alreadyInstalledPypi = Integer.parseInt(m.group(1));
			totalPypi = Integer.parseInt(m.group(2));
			return;
		}

		// PyPI: packages prepared.
		m = PYPI_PREPARED.matcher(line);
		if (m.find()) {
			int prepared = Integer.parseInt(m.group(1));
			int toInstall = totalPypi - alreadyInstalledPypi;
			if (toInstall > 0) {
				fireProgress("Downloading PyPI packages", prepared, toInstall);
			}
			return;
		}

		// PyPI: packages installed.
		m = PYPI_INSTALLED.matcher(line);
		if (m.find()) {
			int installed = Integer.parseInt(m.group(1));
			int toInstall = totalPypi - alreadyInstalledPypi;
			if (toInstall > 0) {
				fireProgress("Installing PyPI packages", installed, toInstall);
			}
			return;
		}

		// Environment fully installed.
		if (ENV_INSTALLED.matcher(line).find()) {
			fireProgress("Done", 1, 1);
		}
	}

	/**
	 * Parses {@code pixi.lock} to count conda packages for the target
	 * environment and platform. Uses a simple line-by-line state machine.
	 *
	 * @return The number of conda packages, or 0 if the lock file
	 *         cannot be parsed.
	 */
	private int parseLockFileCondaCount() {
		File lockFile = new File(envDir, "pixi.lock");
		if (!lockFile.isFile()) return 0;

		// State machine: track which section we're in.
		// Level 0: top-level
		// Level 1: inside "environments:"
		// Level 2: inside target env (e.g. "  default:")
		// Level 3: inside "    packages:"
		// Level 4: inside target platform (e.g. "      osx-arm64:")
		int level = 0;
		int count = 0;

		try (BufferedReader reader = new BufferedReader(new FileReader(lockFile))) {
			String ln;
			while ((ln = reader.readLine()) != null) {
				// Skip blank lines and comments.
				String trimmed = ln.trim();
				if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

				int indent = leadingSpaces(ln);

				switch (level) {
					case 0:
						// Look for "environments:" at top level.
						if (indent == 0 && trimmed.equals("environments:")) {
							level = 1;
						}
						break;
					case 1:
						// Inside "environments:" — look for our env name.
						if (indent == 0) {
							// Left the environments section entirely.
							return count;
						}
						if (indent == 2 && trimmed.equals(envName + ":")) {
							level = 2;
						}
						break;
					case 2:
						// Inside target environment — look for "packages:".
						if (indent <= 2) {
							// Left the target env section.
							return count;
						}
						if (indent == 4 && trimmed.equals("packages:")) {
							level = 3;
						}
						break;
					case 3:
						// Inside "packages:" — look for target platform.
						if (indent <= 4) {
							// Left the packages section.
							return count;
						}
						if (indent == 6 && trimmed.equals(pixiPlatform + ":")) {
							level = 4;
						}
						break;
					case 4:
						// Inside target platform — count "- conda:" lines.
						if (indent <= 6) {
							// Left the platform section.
							return count;
						}
						if (trimmed.startsWith("- conda:")) {
							count++;
						}
						break;
				}
			}
		}
		catch (IOException e) {
			// Lock file unreadable; return what we have.
		}
		return count;
	}

	/** Counts leading space characters in a string. */
	private static int leadingSpaces(String s) {
		int count = 0;
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) == ' ') count++;
			else break;
		}
		return count;
	}

	/**
	 * Starts a background thread that polls the conda-meta directory
	 * for {@code .json} files, emitting progress events every 500ms.
	 */
	private void startCondaPolling() {
		condaPollingActive.set(true);
		Path condaMetaPath = envDir.toPath()
			.resolve(".pixi").resolve("envs").resolve(envName)
			.resolve("conda-meta");

		condaPollingThread = new Thread(() -> {
			while (condaPollingActive.get() && !Thread.currentThread().isInterrupted()) {
				int installed = countJsonFiles(condaMetaPath.toFile());
				fireProgress("Installing conda packages", installed, totalConda);
				try {
					Thread.sleep(500);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}, "PixiInstallMonitor-conda-poll");
		condaPollingThread.setDaemon(true);
		condaPollingThread.start();
	}

	/** Counts {@code *.json} files in a directory, returning 0 if it doesn't exist. */
	private static int countJsonFiles(File dir) {
		if (!dir.isDirectory()) return 0;
		File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
		return files != null ? files.length : 0;
	}

	private void fireProgress(String title, long current, long maximum) {
		for (ProgressConsumer subscriber : progressSubscribers) {
			subscriber.accept(title, current, maximum);
		}
	}

	/**
	 * Maps the Appose {@link Platforms#PLATFORM} string to
	 * pixi's lock file platform convention.
	 */
	private static String pixiPlatformString() {
		switch (Platforms.PLATFORM) {
			case "MACOS|ARM64":   return "osx-arm64";
			case "MACOS|X64":     return "osx-64";
			case "LINUX|ARM64":   return "linux-aarch64";
			case "LINUX|X64":     return "linux-64";
			case "WINDOWS|ARM64": return "win-arm64";
			case "WINDOWS|X64":   return "win-64";
			default:              return Platforms.PLATFORM.toLowerCase().replace("|", "-");
		}
	}
}
