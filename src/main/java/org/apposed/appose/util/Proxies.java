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
package org.apposed.appose.util;

import groovy.json.JsonGenerator;
import groovy.json.JsonSlurper;
import org.apposed.appose.NDArray;
import org.apposed.appose.Service;
import org.apposed.appose.SharedMemory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.apposed.appose.NDArray.Shape.Order.C_ORDER;

/**
 * Utility class for managing local proxies of remote objects.
 *
 * @author Curtis Rueden
 */
public final class Proxies {

	private Proxies() {
		// Prevent instantiation of utility class.
	}

	public static <T> T create(Service service, String var, Class<T> api) {
		return create(service, var, api, null);
	}

	@SuppressWarnings("unchecked")
	public static <T> T create(Service service, String var, Class<T> api, String queue) {
		return (T) Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] {api},
			(proxy, method, args) ->
		{
			// Construct map of input arguments.
			Map<String, Object> inputs = new HashMap<>();
			List<String> argNames = new ArrayList<>();
			int i = 0;
			for (Object arg : args) {
				String name = "arg" + i++;
				inputs.put(name, arg);
				argNames.add(name);
			}

			// Script generation assumes dot-notation method syntax: obj.method(arg0, arg1)
			// This works for Python, Groovy, JavaScript, Ruby, and most dynamic languages.
			// If Appose adds support for languages with different syntax, this will need
			// to become language-aware (possibly via some sort of script generator plugin).
			String script = var + "." + method.getName() +
				"(" + String.join(",", argNames) + ")";

			Service.Task task = service.task(script, inputs, queue);
			task.waitFor();
			if (task.status != Service.TaskStatus.COMPLETE) {
				throw new RuntimeException(task.error);
			}
			return task.outputs.get("result");
		});
	}
}
