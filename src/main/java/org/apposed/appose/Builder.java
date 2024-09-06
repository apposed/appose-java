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

package org.apposed.appose;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * TODO
 *
 * @author Curtis Rueden
 */
public class Builder {

	public final Map<String, List<String>> config = new HashMap<>();

	private final List<BuildHandler> handlers;

	private boolean includeSystemPath;
	private String scheme = "conda";

	Builder() {
		handlers = new ArrayList<>();
		ServiceLoader.load(BuildHandler.class).forEach(handlers::add);
	}

	/**
	 * TODO
	 *
	 * @return This {@code Builder} instance, for fluent-style programming.
	 */
	public Builder useSystemPath() {
		includeSystemPath = true;
		return this;
	}

	/**
	 * Sets the scheme to use with subsequent {@link #channel(String)} and
	 * {@link #include(String)} directives.
	 *
	 * @param scheme TODO
	 * @return This {@code Builder} instance, for fluent-style programming.
	 */
	public Builder scheme(String scheme) {
		this.scheme = scheme;
		return this;
	}

	/**
	 * TODO
	 *
	 * @return This {@code Builder} instance, for fluent-style programming.
	 */
	public Builder file(String filePath) throws IOException {
		return file(new File(filePath));
	}

	/**
	 * TODO
	 *
	 * @return This {@code Builder} instance, for fluent-style programming.
	 */
	public Builder file(String filePath, String scheme) throws IOException {
		return file(new File(filePath), scheme);
	}

	/**
	 * TODO
	 *
	 * @return This {@code Builder} instance, for fluent-style programming.
	 */
	public Builder file(File file) throws IOException {
		return file(file, file.getName());
	}

	/**
	 * TODO
	 *
	 * @return This {@code Builder} instance, for fluent-style programming.
	 */
	public Builder file(File file, String scheme) throws IOException {
		byte[] bytes = Files.readAllBytes(file.toPath());
		return include(new String(bytes), scheme);
	}

	/**
	 * Registers a channel that provides components of the environment,
	 * according to the currently configured scheme ("conda" by default).
	 * <p>
	 * For example, {@code channel("bioconda")} registers the {@code bioconda}
	 * channel as a source for conda packages.
	 * </p>
	 *
	 * @param name The name of the channel to register.
	 * @return This {@code Builder} instance, for fluent-style programming.
	 * @see #channel(String, String)
	 * @see #scheme(String)
	 */
	public Builder channel(String name) {
		return channel(name, scheme);
	}

	/**
	 * Registers a channel that provides components of the environment.
	 * How to specify a channel is implementation-dependent. Examples:
	 *
	 * <ul>
	 *   <li>{@code channel("bioconda")} -
	 *     to register the {@code bioconda} channel as a source for conda packages.</li>
	 *   <li>{@code channel("scijava", "maven:https://maven.scijava.org/content/groups/public")} -
	 *     to register the SciJava Maven repository as a source for Maven artifacts.</li>
	 * </ul>
	 *
	 * @param name The name of the channel to register.
	 * @param location The location of the channel (e.g. a URI), or {@code null} if the
	 *                  name alone is sufficient to unambiguously identify the channel.
	 * @return This {@code Builder} instance, for fluent-style programming.
	 * @throws IllegalArgumentException if the channel is not understood by any of the available build handlers.
	 */
	public Builder channel(String name, String location) {
		// Pass the channel directive to all handlers.
		if (handle(handler -> handler.channel(name, location))) return this;
		// None of the handlers accepted the directive.
		throw new IllegalArgumentException("Unsupported channel: " + name +
			(location == null ? "" : "=" + location));
	}

	/**
	 * TODO
	 *
	 * @param content TODO
	 * @return This {@code Builder} instance, for fluent-style programming.
	 * @see #include(String, String)
	 * @see #scheme(String)
	 */
	public Builder include(String content) {
		return include(content, scheme);
	}

	/**
	 * Registers content to be included within the environment.
	 * How to specify the content is implementation-dependent. Examples:
	 * <ul>
	 *   <li>{@code include("cowsay", "pypi")} -
	 *     Install {@code cowsay} from the Python package index.</li>
	 *   <li>{@code include("openjdk=17")} -
	 *     Install {@code openjdk} version 17 from conda-forge.</li>
	 *   <li>{@code include("bioconda::sourmash")} -
	 *     Specify a conda channel explicitly using environment.yml syntax.</li>
	 *   <li>{@code include("org.scijava:parsington", "maven")} -
	 *     Install the latest version of Parsington from Maven Central.</li>
	 *   <li>{@code include("org.scijava:parsington:2.0.0", "maven")} -
	 *     Install Parsington 2.0.0 from Maven Central.</li>
	 *   <li>{@code include("sc.fiji:fiji", "maven")} -
	 *     Install the latest version of Fiji from registered Maven repositories.</li>
	 *   <li>{@code include("zulu:17", "jdk")} -
	 *     Install version 17 of Azul Zulu OpenJDK.</li>
	 *   <li>{@code include(yamlString, "environment.yml")} -
	 *     Provide the literal contents of a conda {@code environment.yml} file,
	 *     indicating a set of packages to include.
	 * </ul>
	 * <p>
	 * Note that content is not actually fetched or installed until
	 * {@link #build} is called at the end of the builder chain.
	 * </p>
	 *
	 * @param content The content (e.g. a package name, or perhaps the contents of an environment
	 *                 configuration file) to include in the environment, fetching if needed.
	 * @param scheme The type of content, which serves as a hint for how to interpret
	 *                the content in some scenarios; see above for examples.
	 * @return This {@code Builder} instance, for fluent-style programming.
	 * @throws IllegalArgumentException if the include directive is not understood by any of the available build handlers.
	 */
	public Builder include(String content, String scheme) {
		// Pass the include directive to all handlers.
		if (handle(handler -> handler.include(content, scheme))) return this;
		// None of the handlers accepted the directive.
		throw new IllegalArgumentException("Unsupported '" + scheme + "' content: " + content);
	}

	/**
	 * Executes the environment build, according to the configured channels and includes,
	 * with a name inferred from the configuration registered earlier. For example, if
	 * {@code environment.yml} content was registered, the name from that configuration will be used.
	 *
	 * @return The newly constructed Appose {@link Environment},
	 *          from which {@link Service}s can be launched.
	 * @see #build(String)
	 * @throws IllegalStateException if no name can be inferred from included content.
	 * @throws IOException If something goes wrong building the environment.
	 */
	public Environment build() throws IOException {
		// Autodetect the environment name from the available build handlers.
		return build(handlers.stream()
			.map(BuildHandler::envName)
			.filter(Objects::nonNull)
			.findFirst()
			.orElse(null));
	}

	/**
	 * Executes the environment build, according to the configured channels and includes.
	 * with a base directory inferred from the given name.
	 *
	 * @param envName The name of the environment to build.
	 * @return The newly constructed Appose {@link Environment},
	 *          from which {@link Service}s can be launched.
	 * @throws IOException If something goes wrong building the environment.
	 */
	public Environment build(String envName) throws IOException {
		if (envName == null || envName.isEmpty()) {
			throw new IllegalArgumentException("No environment name given.");
		}
		// TODO: Make Appose's root directory configurable.
		Path apposeRoot = Paths.get(System.getProperty("user.home"), ".local", "share", "appose");
		return build(apposeRoot.resolve(envName).toFile());
	}

	/**
	 * Executes the environment build, according to the configured channels and includes.
	 * with the given base directory.
	 *
	 * @param envDir The directory in which to construct the environment.
	 * @return The newly constructed Appose {@link Environment},
	 *          from which {@link Service}s can be launched.
	 * @throws IOException If something goes wrong building the environment.
	 */
	public Environment build(File envDir) throws IOException {
		if (envDir == null) {
			throw new IllegalArgumentException("No environment directory given.");
		}
		if (!envDir.exists()) {
			if (!envDir.mkdirs()) {
				throw new RuntimeException("Failed to create environment directory: " + envDir);
			}
		}
		if (!envDir.isDirectory()) {
			throw new IllegalArgumentException("Not a directory: " + envDir);
		}

		config.clear();
		for (BuildHandler handler : handlers) handler.build(envDir, this);

		String base = envDir.getAbsolutePath();

		List<String> launchArgs = listFromConfig("launchArgs", config);
		List<String> binPaths = listFromConfig("binPaths", config);
		List<String> classpath = listFromConfig("classpath", config);

		// Always add the environment directory itself to the binPaths.
		// Especially important on Windows, where python.exe is not tucked into a bin subdirectory.
		binPaths.add(envDir.getAbsolutePath());

		if (includeSystemPath) {
			List<String> systemPaths = Arrays.asList(System.getenv("PATH").split(File.pathSeparator));
			binPaths.addAll(systemPaths);
		}

		return new Environment() {
			@Override public String base() { return base; }
			@Override public List<String> binPaths() { return binPaths; }
			@Override public List<String> classpath() { return classpath; }
			@Override public List<String> launchArgs() { return launchArgs; }
		};
	}

	private boolean handle(Function<BuildHandler, Boolean> handlerFunction) {
		boolean handled = false;
		for (BuildHandler handler : handlers)
			handled |= handlerFunction.apply(handler);
		return handled;
	}

	private static List<String> listFromConfig(String key, Map<String, List<String>> config) {
		List<?> value = config.getOrDefault(key, Collections.emptyList());
		return value.stream().map(Object::toString).collect(Collectors.toList());
	}
}
