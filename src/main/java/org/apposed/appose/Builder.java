/*-
 * #%L
 * Appose: multi-language interprocess cooperation with shared memory.
 * %%
 * Copyright (C) 2023 Appose developers.
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

public class Builder {

	public Environment build() {
		// TODO Build the thing!~
		// Hash the state to make a base directory name.
		// - Construct conda environment from condaEnvironmentYaml.
		// - Download and unpack JVM of the given vendor+version.
		// - Populate ${baseDirectory}/jars with Maven artifacts?
		String base = baseDir.getPath();
		boolean useSystemPath = systemPath;
		return new Environment() {
			@Override public String base() { return base; }
			@Override public boolean useSystemPath() { return useSystemPath; }
		};
	}

	// -- Configuration --

	private boolean systemPath;

	public Builder useSystemPath() {
		systemPath = true;
		return this;
	}

	private File baseDir;

	public Builder base(File directory) {
		baseDir = directory;
		return this;
	}

	// -- Conda --

	private File condaEnvironmentYaml;

	public Builder conda(File environmentYaml) {
		this.condaEnvironmentYaml = environmentYaml;
		return this;
	}

	// -- Java --

	private String javaVendor;
	private String javaVersion;

	public Builder java(String vendor, String version) {
		this.javaVendor = vendor;
		this.javaVersion = version;
		return this;
	}
}
