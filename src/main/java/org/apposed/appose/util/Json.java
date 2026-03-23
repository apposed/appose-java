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

package org.apposed.appose.util;

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;

/**
 * Utility class for simple JSON serialization and deserialization.
 * Encapsulates the Groovy JSON library so that other classes do not
 * need to import it directly.
 *
 * @author Curtis Rueden
 */
public final class Json {

	private Json() {
		// Prevent instantiation of utility class.
	}

	/**
	 * Converts an object to a JSON string.
	 * Supports standard JSON-compatible types:
	 * {@link java.util.Map}, {@link java.util.List}, {@link String},
	 * {@link Number}, {@link Boolean}, and {@code null}.
	 *
	 * @param obj The object to serialize.
	 * @return JSON string representation.
	 */
	public static String toJson(Object obj) {
		return JsonOutput.toJson(obj);
	}

	/**
	 * Parses a JSON string into a Java object.
	 * Returns a {@link java.util.Map} for JSON objects,
	 * a {@link java.util.List} for JSON arrays, or
	 * a primitive wrapper for scalar values.
	 *
	 * @param json The JSON string to parse.
	 * @return Parsed Java object.
	 */
	public static Object parseJson(String json) {
		return new JsonSlurper().parseText(json);
	}
}
