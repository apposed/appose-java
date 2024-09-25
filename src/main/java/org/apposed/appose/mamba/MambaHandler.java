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
package org.apposed.appose.mamba;

import org.apposed.appose.BuildHandler;
import org.apposed.appose.Builder;
import org.apposed.appose.FilePaths;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

/** A {@link BuildHandler} plugin powered by micromamba. */
public class MambaHandler implements BuildHandler {

	private final List<String> channels = new ArrayList<>();
	private final List<String> condaIncludes = new ArrayList<>();
	private final List<String> yamlIncludes = new ArrayList<>();
	private final List<String> pypiIncludes = new ArrayList<>();

	@Override
	public boolean channel(String name, String location) {
		if (location == null) {
			// Assume it's a conda channel.
			channels.add(name);
			return true;
		}
		return false;
	}

	@Override
	public boolean include(String content, String scheme) {
		if (content == null) throw new NullPointerException("content must not be null");
		if (scheme == null) throw new NullPointerException("scheme must not be null");
		switch (scheme) {
			case "conda":
				// It's a conda package (or newline-separated package list).
				condaIncludes.addAll(lines(content));
				return true;
			case "pypi":
				// It's a PyPI package (or newline-separated package list).
				pypiIncludes.addAll(lines(content));
				return true;
			case "environment.yml":
				yamlIncludes.add(content);
				return true;
		}
		return false;
	}

	@Override
	public String envName() {
		for (String yaml : yamlIncludes) {
			String[] lines = yaml.split("(\r\n|\n|\r)");
			Optional<String> name = Arrays.stream(lines)
				.filter(line -> line.startsWith("name:"))
				.map(line -> line.substring(5).trim().replace("\"", ""))
				.findFirst();
			if (name.isPresent()) return name.get();
		}
		return null;
	}

	@Override
	public void build(File envDir, Builder builder) throws IOException {
		if (!channels.isEmpty() || !condaIncludes.isEmpty() || !pypiIncludes.isEmpty()) {
			throw new UnsupportedOperationException(
				"Sorry, I don't know how to mix in additional packages from Conda or PyPI yet." +
					" Please put them in your environment.yml for now.");
		}

		Mamba conda = new Mamba(Mamba.BASE_PATH);
		boolean isCondaDir = new File(envDir, "conda-meta").isDirectory();

		if (yamlIncludes.isEmpty()) {
			// Nothing for this handler to do.
			if (isCondaDir) {
				// If directory already exists and is a conda environment prefix,
				// inject needed micromamba stuff into the configuration.
				fillConfig(conda, envDir, builder.config);
			}
			return;
		}
		if (yamlIncludes.size() > 1) {
			throw new UnsupportedOperationException(
				"Sorry, I can't synthesize Conda environments from multiple environment.yml files yet." +
					" Please use a single environment.yml for now.");
		}

		// Is this envDir an already-existing conda directory?
		// If so, we can update it.
		if (isCondaDir) {
			// This environment has already been populated.
			// TODO: Should we update it? For now, we just use it.
			fillConfig(conda, envDir, builder.config);
			return;
		}

		// Micromamba refuses to create an environment into an existing non-conda directory:
		//
		//   "Non-conda folder exists at prefix"
		//
		// So if a non-conda directory already exists, we need to perform some
		// contortions to make micromamba integrate with other build handlers:
		//
		// 1. If envDir already exists, rename it temporarily.
		// 2. Run the micromamba command to create the environment.
		// 3. Recursively move any previously existing contents from the
		//    temporary directory into the newly constructed one.
		// 4. If moving an old file would overwrite a new file, put the old
		//    file back with a .old extension, so nothing is permanently lost.
		// 5. As part of the move, remove the temp directories as they empty out.

		// Write out environment.yml from input content.
		// We cannot write it into envDir, because mamba needs the directory to
		// not exist yet in order to create the environment there. So we write it
		// into a temporary work directory with a hopefully unique name.
		Path envPath = envDir.getAbsoluteFile().toPath();
		String antiCollision = "" + (new Random().nextInt(90000000) + 10000000);
		File workDir = envPath.resolveSibling(envPath.getFileName() + "." + antiCollision + ".tmp").toFile();
		if (envDir.exists()) {
			if (envDir.isDirectory()) {
				// Move aside the existing non-conda directory.
				if (!envDir.renameTo(workDir)) {
					throw new IOException("Failed to rename directory: " + envDir + " -> " + workDir);
				}
			}
			else throw new IllegalArgumentException("Non-directory file already exists: " + envDir.getAbsolutePath());
		}
		else if (!workDir.mkdirs()) {
			throw new IOException("Failed to create work directory: " + workDir);
		}

		// At this point, workDir exists and envDir does not.
		// We want to write the environment.yml file into the work dir.
		// But what if there is an existing environment.yml file in the work dir?
		// Let's move it out of the way, rather than stomping on it.
		File environmentYaml = new File(workDir, "environment.yml");
		FilePaths.renameToBackup(environmentYaml);

		// It should be safe to write out the environment.yml file now.
		try (FileWriter fout = new FileWriter(environmentYaml)) {
			fout.write(yamlIncludes.get(0));
		}

		// Finally, we can build the environment from the environment.yml file.
		try {
			conda.setOutputConsumer(msg -> builder.outputSubscribers.forEach(sub -> sub.accept(msg)));
			conda.setErrorConsumer(msg -> builder.errorSubscribers.forEach(sub -> sub.accept(msg)));
			conda.setMambaDownloadProgressConsumer((cur, max) -> {
				builder.progressSubscribers.forEach(subscriber -> subscriber.accept("Downloading micromamba", cur, max));
			});

			conda.installMicromamba();
			conda.createWithYaml(envDir, environmentYaml.getAbsolutePath());
			fillConfig(conda, envDir, builder.config);
		}
		catch (InterruptedException | URISyntaxException e) {
			throw new IOException(e);
		}
		finally {
			// Lastly, we merge the contents of workDir into envDir. This will be
			// at least the environment.yml file, and maybe other files from other handlers.
			FilePaths.moveDirectory(workDir, envDir, false);
		}
	}

	private List<String> lines(String content) {
		return Arrays.stream(content.split("(\r\n|\n|\r)"))
			.map(String::trim)
			.filter(s -> !s.isEmpty() && !s.startsWith("#"))
			.collect(Collectors.toList());
	}

	private void fillConfig(Mamba conda, File envDir, Map<String, List<String>> config) {
		// Use `mamba run -p $envDir ...` to run within this environment.
		config.computeIfAbsent("launchArgs", k -> new ArrayList<>());
		config.get("launchArgs").addAll(Arrays.asList(conda.mambaCommand, "run", "-p", envDir.getAbsolutePath()));
	}
}
