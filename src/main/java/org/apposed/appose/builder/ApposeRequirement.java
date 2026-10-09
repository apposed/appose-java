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

import org.apposed.appose.Appose;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A pip requirement for appose-python, which builders install into the
 * environments they build.
 * <p>
 * The worker must implement the same major.minor version of Appose as the
 * service (see the HELLO handshake), so when a builder's caller does not
 * specify appose explicitly, the builder adds a compatible requirement:
 * </p>
 * <ol>
 *   <li>The {@value #PROPERTY} system property, or else the {@value #ENV_VAR}
 *     environment variable, if set: a local directory (installed in editable
 *     mode), or a pip requirement.</li>
 *   <li>For a release of Appose, e.g. 1.1.2: {@code appose>=1.1,<1.2} from
 *     PyPI.</li>
 *   <li>For a development version, e.g. 1.1.0-SNAPSHOT: the main branch of
 *     appose-python on GitHub.</li>
 * </ol>
 */
public final class ApposeRequirement {

	/** System property overriding which appose-python to install. */
	public static final String PROPERTY = "appose.python.requirement";

	/** Environment variable overriding which appose-python to install. */
	public static final String ENV_VAR = "APPOSE_PYTHON_REQUIREMENT";

	/** The main branch of appose-python on GitHub. */
	public static final ApposeRequirement MAIN_BRANCH =
		new ApposeRequirement("appose @ git+https://github.com/apposed/appose-python", false);

	private final String spec;
	private final boolean editable;

	public ApposeRequirement(String spec, boolean editable) {
		this.spec = spec;
		this.editable = editable;
	}

	/** The pip requirement, e.g. {@code appose>=1.1,<1.2}. */
	public String spec() {
		return spec;
	}

	/** Whether to install in editable mode, as for a local directory. */
	public boolean editable() {
		return editable;
	}

	/** The requirement as arguments to pip install. */
	public List<String> pipArgs() {
		return editable ? Arrays.asList("-e", spec) : Collections.singletonList(spec);
	}

	/**
	 * Determines which appose-python to install into a built environment, as
	 * described in this class's documentation.
	 */
	public static ApposeRequirement get() {
		return forVersion(Appose.version());
	}

	/**
	 * Determines which appose-python to install into a built environment, as
	 * described in this class's documentation.
	 *
	 * @param version The version of Appose in use.
	 */
	public static ApposeRequirement forVersion(String version) {
		String override = System.getProperty(PROPERTY);
		if (override == null || override.trim().isEmpty()) override = System.getenv(ENV_VAR);
		if (override != null && !override.trim().isEmpty()) {
			File dir = new File(override.trim());
			return dir.isDirectory() ? local(dir) : new ApposeRequirement(override.trim(), false);
		}
		Matcher m = Pattern.compile("(\\d+)\\.(\\d+)(\\.\\d+)*").matcher(version);
		if (m.matches()) {
			int major = Integer.parseInt(m.group(1));
			int minor = Integer.parseInt(m.group(2));
			String range = major + "." + minor + ",<" + major + "." + (minor + 1);
			return new ApposeRequirement("appose>=" + range, false);
		}
		return MAIN_BRANCH;
	}

	/** A local appose-python directory, installed in editable mode. */
	public static ApposeRequirement local(File dir) {
		String uri = dir.toPath().toAbsolutePath().normalize().toUri().toString();
		return new ApposeRequirement("appose @ " + uri, true);
	}

	/**
	 * Whether the given package specs include appose explicitly, in which case
	 * the caller's choice takes precedence over {@link #get()}.
	 */
	public static boolean mentionsAppose(List<String> packages) {
		return packages.stream().anyMatch(pkg -> pkg.trim().matches("^appose\\b.*"));
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ApposeRequirement)) return false;
		ApposeRequirement that = (ApposeRequirement) o;
		return editable == that.editable && spec.equals(that.spec);
	}

	@Override
	public int hashCode() {
		return Objects.hash(spec, editable);
	}

	@Override
	public String toString() {
		return String.join(" ", pipArgs());
	}
}
