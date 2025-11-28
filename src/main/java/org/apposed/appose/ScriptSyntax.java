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

package org.apposed.appose;

import java.util.List;

/**
 * Strategy interface for generating language-specific script syntax.
 * <p>
 * Different scripting languages have different syntax for common operations
 * like exporting variables, calling functions, and invoking methods on objects.
 * This interface allows {@link Service} to generate correct scripts for any
 * supported language.
 * </p>
 *
 * @see Service#syntax(String)
 */
public interface ScriptSyntax {

	/** The name of this script syntax (e.g. "python", "groovy"). */
	String name();

	/**
	 * Generates a script expression to retrieve a variable's value.
	 * <p>
	 * The variable must have been previously exported using {@code task.export()}
	 * to be accessible across tasks.
	 * </p>
	 *
	 * @param name The name of the variable to retrieve.
	 * @return A script expression that evaluates to the variable's value.
	 * @see Service#getVar
	 */
	String getVar(String name);

	/**
	 * Generates a script to set a variable and export it for future tasks.
	 * <p>
	 * The value is provided via a task input variable (typically named "_value").
	 * The generated script should assign the value to the named variable and
	 * export it using {@code task.export()}.
	 * </p>
	 *
	 * @param name The name of the variable to set.
	 * @param valueVarName The name of the input variable containing the value.
	 * @return A script that sets and exports the variable.
	 * @see Service#putVar
	 */
	String putVar(String name, String valueVarName);

	/**
	 * Generates a script expression to call a function with arguments.
	 * <p>
	 * The function must be accessible in the worker's global scope (either
	 * built-in or previously defined/imported). Arguments are provided via
	 * task input variables.
	 * </p>
	 *
	 * @param function The name of the function to call.
	 * @param argVarNames The names of input variables containing the arguments.
	 * @return A script expression that calls the function and evaluates to its result.
	 * @see Service#call
	 */
	String call(String function, List<String> argVarNames);

	/**
	 * Generates a script expression to invoke a method on an object.
	 * <p>
	 * The object must have been previously exported using {@code task.export()}.
	 * This is used by the proxy mechanism to forward method calls to remote objects.
	 * </p>
	 *
	 * @param objectVarName The name of the variable referencing the object.
	 * @param methodName The name of the method to invoke.
	 * @param argVarNames The names of input variables containing the arguments.
	 * @return A script expression that invokes the method and evaluates to its result.
	 * @see Service#proxy
	 */
	String invokeMethod(String objectVarName, String methodName, List<String> argVarNames);

	/**
	 * Generates a script expression to retrieve an attribute from an object.
	 * <p>
	 * The object must have been previously exported using {@code task.export()}.
	 * This is used to access fields or obtain method references from remote objects.
	 * </p>
	 * <p>
	 * The behavior depends on the language:
	 * </p>
	 * <ul>
	 * <li><strong>Python:</strong> Returns the attribute value (field) or bound method object.</li>
	 * <li><strong>Groovy:</strong> Tries field access first; if no such field exists,
	 *     returns a method reference using {@code .&} syntax.</li>
	 * </ul>
	 * <p>
	 * If the result is a method reference, it will be auto-proxied as a {@link WorkerObject}
	 * that can be called using {@link WorkerObject#call(Object...)}.
	 * </p>
	 *
	 * @param objectVarName The name of the variable referencing the object.
	 * @param attributeName The name of the attribute to retrieve.
	 * @return A script expression that evaluates to the attribute value or method reference.
	 * @see WorkerObject#getAttribute(String)
	 */
	String getAttribute(String objectVarName, String attributeName);
}
