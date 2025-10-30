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

package org.apposed.appose.syntax;

import org.apposed.appose.ScriptSyntax;
import org.apposed.appose.Service;
import org.apposed.appose.util.Plugins;

import java.util.List;

/**
 * Utility class for discovering and working with {@link ScriptSyntax}es.
 *
 * @see ScriptSyntax
 */
public final class Syntaxes {

	private Syntaxes() {
		// Prevent instantiation of utility class.
	}

	/** All known script syntax implementations. */
	private static final List<ScriptSyntax> ALL =
		Plugins.discover(ScriptSyntax.class, null);

	/**
	 * Detects and returns the script syntax with the given name.
	 *
	 * @param name Name of the script syntax
	 * @return The matching script syntax object, or null if no syntax with the given name
	 */
	public static ScriptSyntax get(String name) {
		return Plugins.find(ALL, syntax -> name.equals(syntax.name()));
	}

	/**
	 * Verifies that the given service has an assigned script syntax.
	 *
	 * @param service The service to ensure valid script syntax assignment
	 */
	public static void validate(Service service) {
		if (service.syntax() != null) return; // OK!
		throw new IllegalStateException("No script syntax configured for this service");
	}
}
