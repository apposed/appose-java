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

package org.apposed.appose.gui;

import org.apposed.appose.Builder;
import org.apposed.appose.builder.BuildException;

import java.io.File;

/**
 * Describes an Appose environment for management UI purposes.
 * <p>
 * An environment descriptor contains:
 * </p>
 * <ul>
 *     <li>A human-readable display name</li>
 *     <li>An optional description</li>
 *     <li>A builder that can create/rebuild the environment</li>
 *     <li>The environment directory name (must match builder's name() or base())</li>
 * </ul>
 * <p>
 * This class is used by {@link EnvironmentPanel} and {@link EnvironmentManagerPanel}
 * to display and manage environments.
 * </p>
 * <p>
 * Example usage:
 * </p>
 * <pre>
 * EnvironmentDescriptor desc = new EnvironmentDescriptor(
 *     "My Python Environment",
 *     "Python 3.10 with numpy and pandas",
 *     "my-py-env",  // Must match builder.name("my-py-env")
 *     Appose.uv()
 *         .python("3.10")
 *         .include("numpy", "pandas")
 *         .name("my-py-env")
 * );
 * </pre>
 *
 * @author Curtis Rueden
 */
public class EnvironmentDescriptor {

	private final String displayName;
	private final String description;
	private final String envDirName;
	private final Builder<?> builder;

	/**
	 * Creates an environment descriptor.
	 *
	 * @param displayName Human-readable display name (e.g., "Python Data Science")
	 * @param description Optional description (may be null)
	 * @param envDirName Environment directory name - must match what was passed to builder.name()
	 * @param builder Builder to create/rebuild this environment
	 */
	public EnvironmentDescriptor(String displayName, String description, String envDirName, Builder<?> builder) {
		if (displayName == null || displayName.isEmpty()) {
			throw new IllegalArgumentException("Display name cannot be null or empty");
		}
		if (envDirName == null || envDirName.isEmpty()) {
			throw new IllegalArgumentException("Environment directory name cannot be null or empty");
		}
		if (builder == null) {
			throw new IllegalArgumentException("Builder cannot be null");
		}
		this.displayName = displayName;
		this.description = description;
		this.envDirName = envDirName;
		this.builder = builder;
	}

	/**
	 * Creates an environment descriptor without a description.
	 *
	 * @param displayName Human-readable display name
	 * @param envDirName Environment directory name
	 * @param builder Builder to create/rebuild this environment
	 */
	public EnvironmentDescriptor(String displayName, String envDirName, Builder<?> builder) {
		this(displayName, null, envDirName, builder);
	}

	/**
	 * Gets the directory where this environment is or will be located.
	 *
	 * @return Environment directory
	 */
	public File getExpectedDir() {
		return new File(org.apposed.appose.util.Environments.apposeEnvsDir(), envDirName);
	}

	/**
	 * Gets the human-readable display name of this environment.
	 *
	 * @return Display name
	 */
	public String getName() {
		return displayName;
	}

	/**
	 * Gets the description of this environment.
	 *
	 * @return Description, or null if none provided
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * Gets the builder for this environment.
	 *
	 * @return Builder instance
	 */
	public Builder<?> getBuilder() {
		return builder;
	}

	/**
	 * Builds or rebuilds this environment.
	 *
	 * @param rebuild If true, deletes and rebuilds; if false, builds only if not present
	 * @throws BuildException If the build fails
	 */
	public void build(boolean rebuild) throws BuildException {
		if (rebuild) {
			builder.rebuild();
		}
		else {
			builder.build();
		}
	}
}
