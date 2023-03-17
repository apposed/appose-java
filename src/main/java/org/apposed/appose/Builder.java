package org.apposed.appose;

import java.io.File;

public class Builder {

	public Environment build() {
		// TODO Build the thing!~
		// Hash the state to make a base directory name.
		// - Construct conda environment from condaEnvironmentYaml.
		// - Download and unpack JVM of the given vendor+version.
		// - Populate ${baseDirectory}/jars with Maven artifacts?
		return new Environment(base);
	}

	private File base;

	public Builder base(File directory) {
		this.base = directory;
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
