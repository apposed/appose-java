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

import java.util.List;

/**
 * Groovy-specific script syntax implementation.
 * <p>
 * Generates Groovy code for common operations like variable export,
 * function calls, and method invocation. This is automatically used
 * when creating Groovy services via {@code Environment#groovy()}.
 * </p>
 *
 * @see ScriptSyntax
 */
public class GroovySyntax implements ScriptSyntax {

	@Override
	public String name() {
		return "groovy";
		}

	@Override
	public String getVar(String name) {
		// In Groovy, just reference the variable name
		return name;
	}

	@Override
	public String putVar(String name, String valueVarName) {
		// Assign the value and export using Groovy map syntax
		// Using explicit map literal [name: value] which is then passed to export(Map)
		return name + " = " + valueVarName + "\ntask.export([" + name + ": " + name + "])";
	}

	@Override
	public String call(String function, List<String> argVarNames) {
		// Groovy function call syntax: function(arg0, arg1, ...)
		return function + "(" + String.join(", ", argVarNames) + ")";
	}

	@Override
	public String invokeMethod(String objectVarName, String methodName, List<String> argVarNames) {
		// Groovy method invocation: object.method(arg0, arg1, ...)
		return objectVarName + "." + methodName + "(" + String.join(", ", argVarNames) + ")";
	}
}
