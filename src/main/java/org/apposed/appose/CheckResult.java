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

package org.apposed.appose;

/**
 * Result of an environment up-to-date check.
 *
 * @see Builder#checkUpToDate()
 * @see Environment#checkUpToDate()
 */
public interface CheckResult {

	/**
	 * Whether the environment is up-to-date.
	 */
	boolean isUpToDate();

	/**
	 * Human-readable description of the check result.
	 * Explains why the environment is (or is not) up-to-date.
	 */
	String description();

	/**
	 * Whether a real tool verification was performed
	 * (as opposed to a fast config-level comparison).
	 * <p>
	 * When {@code true}, the underlying package manager was actually invoked
	 * to verify the environment is in sync. When {@code false}, only a fast
	 * comparison of the builder configuration against the stored state was done.
	 * </p>
	 */
	boolean verified();

	// -- Factory methods --

	/**
	 * Creates a CheckResult indicating the environment is up-to-date.
	 *
	 * @param description Human-readable explanation of the check result.
	 * @param verified Whether a real tool verification was performed.
	 * @return A CheckResult with {@link #isUpToDate()} returning {@code true}.
	 */
	static CheckResult upToDate(final String description, final boolean verified) {
		return new CheckResult() {
			@Override public boolean isUpToDate() { return true; }
			@Override public String description() { return description; }
			@Override public boolean verified() { return verified; }
		};
	}

	/**
	 * Creates a CheckResult indicating the environment is stale.
	 *
	 * @param description Human-readable explanation of why the environment is stale.
	 * @param verified Whether a real tool verification was performed.
	 * @return A CheckResult with {@link #isUpToDate()} returning {@code false}.
	 */
	static CheckResult stale(final String description, final boolean verified) {
		return new CheckResult() {
			@Override public boolean isUpToDate() { return false; }
			@Override public String description() { return description; }
			@Override public boolean verified() { return verified; }
		};
	}
}
