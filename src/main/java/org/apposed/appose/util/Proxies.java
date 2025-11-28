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

import org.apposed.appose.Service;
import org.apposed.appose.TaskException;
import org.apposed.appose.syntax.Syntaxes;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for creating local proxy objects that provide strongly typed
 * access to remote objects living in Appose worker processes.
 * <p>
 * A proxy object forwards method calls to a corresponding object in the worker
 * process by generating and executing scripts via {@link Service.Task}s. This
 * provides a more natural, object-oriented API compared to manually constructing
 * script strings for each method invocation.
 * </p>
 * <p>
 * <strong>Type safety is honor-system based:</strong> The interface you provide
 * must match the actual methods and signatures of the remote object. If there's
 * a mismatch, you'll get runtime errors from the worker process.
 * </p>
 * <p>
 * <strong>Usage pattern:</strong> First, create and export the remote object via a task,
 * then create a proxy to interact with it:
 * </p>
 * <pre>
 * Service service = env.python();
 * service.task("task.export(my_obj=MyClass())").waitFor();
 * MyInterface proxy = service.proxy("my_obj", MyInterface.class);
 * String result = proxy.someMethod(42); // Executes remotely
 * </pre>
 * <p>
 * <strong>Important:</strong> Variables must be explicitly exported using
 * {@code task.export(varName=value)} in a previous task before they can be
 * proxied. Exported variables persist across tasks within the same service.
 * </p>
 *
 * @author Curtis Rueden
 * @see Service#proxy(String, Class) Convenience method for creating proxies from a service
 */
public final class Proxies {

	private Proxies() {
		// Prevent instantiation of utility class.
	}

	/**
	 * Creates a proxy object providing typed access to a remote object in a worker process.
	 * <p>
	 * <strong>Important:</strong> The variable must have been previously exported using
	 * {@code task.export(varName=value)} in a prior task. Only exported variables are
	 * accessible across tasks.
	 * </p>
	 *
	 * @param <T> The interface type that the proxy will implement.
	 * @param service The service managing the worker process containing the remote object.
	 * @param var The name of the exported variable in the worker process referencing the remote object.
	 * @param api The interface class that the proxy should implement. Method calls on this
	 *            interface will be forwarded to the remote object.
	 * @return A proxy object implementing the specified interface. Method calls block until
	 *         the remote execution completes.
	 * @see #create(Service, String, Class, String) To specify a queue for task execution.
	 * @see Service#proxy(String, Class) Convenience method available on Service instances.
	 */
	public static <T> T create(Service service, String var, Class<T> api) {
		return create(service, var, api, null);
	}

	/**
	 * Creates a proxy object providing typed access to a remote object in a worker process,
	 * with control over which queue executes the forwarded method calls.
	 * <p>
	 * Each method invocation on the returned proxy generates a script of the form
	 * {@code var.methodName(arg0, arg1, ...)} and submits it as a task to the worker.
	 * Arguments are passed via the task's {@code inputs} map, and the return value
	 * is retrieved from {@code task.result()}.
	 * </p>
	 * <p>
	 * <strong>Variable export requirement:</strong> The variable must have been previously
	 * exported using {@code task.export(varName=value)} in a prior task. Only exported
	 * variables are accessible across tasks. For example:
	 * </p>
	 * <pre>
	 * service.task("task.export(calc=Calculator())").waitFor();
	 * Calculator calc = service.proxy("calc", Calculator.class);
	 * </pre>
	 * <p>
	 * <strong>Blocking behavior:</strong> Method calls block the calling thread until
	 * the remote execution completes. If you need asynchronous execution, create tasks
	 * manually using {@link Service#task(String)}.
	 * </p>
	 * <p>
	 * <strong>Error handling:</strong> If the remote execution fails, a
	 * {@link RuntimeException} is thrown containing the error message from the worker.
	 * </p>
	 *
	 * @param <T> The interface type that the proxy will implement.
	 * @param service The service managing the worker process containing the remote object.
	 * @param var The name of the exported variable in the worker process referencing the remote object.
	 * @param api The interface class that the proxy should implement. Method calls on this
	 *            interface will be forwarded to the remote object.
	 * @param queue Optional queue identifier for task execution. Pass {@code "main"} to ensure
	 *              execution on the worker's main thread, or {@code null} for default behavior.
	 * @return A proxy object implementing the specified interface. Method calls block until
	 *         the remote execution completes and return the value from {@code task.result()}.
	 * @throws RuntimeException If a proxied method call fails in the worker process.
	 * @see Service#proxy(String, Class, String) Convenience method available on Service instances.
	 * @see Service#task(String, String) For understanding queue behavior.
	 */
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

			// Use the service's ScriptSyntax to generate the method invocation script.
			// This allows support for different languages with varying syntax.
			Syntaxes.validate(service);
			String script = service.syntax().invokeMethod(var, method.getName(), argNames);

			try {
				Service.Task task = service.task(script, inputs, queue);
				task.waitFor();
				return task.result();
			}
			catch (TaskException e) {
				throw new RuntimeException(e.getMessage(), e);
			}
		});
	}

	/**
	 * Recursively converts worker_object references in task outputs to {@link org.apposed.appose.WorkerObject} instances.
	 * <p>
	 * This is called on task outputs after JSON deserialization to convert any
	 * worker_object references (from auto-exported non-serializable objects) into
	 * actual {@code WorkerObject} instances.
	 * </p>
	 * <p>
	 * When a worker process encounters a non-serializable object (e.g., a datetime
	 * instance), it automatically exports it with a generated variable name and
	 * returns a worker_object reference. This method converts those references
	 * into {@code WorkerObject} instances that can be used to interact with the remote object.
	 * </p>
	 *
	 * @param data The data structure (potentially) containing worker_object references.
	 * @param service The Service instance to use for creating WorkerObjects.
	 * @return The data with worker_object references replaced by WorkerObject instances.
	 */
	@SuppressWarnings("unchecked")
	public static Object proxifyWorkerObjects(Object data, Service service) {
		if (data instanceof Map) {
			Map<String, Object> map = (Map<String, Object>) data;
			if ("worker_object".equals(map.get("appose_type"))) {
				// Convert this worker_object reference to a WorkerObject.
				String varName = (String) map.get("var_name");
				return new org.apposed.appose.WorkerObject(service, varName);
			}
			else {
				// Recursively process map values.
				Map<String, Object> result = new HashMap<>();
				for (Map.Entry<String, Object> entry : map.entrySet()) {
					result.put(entry.getKey(), proxifyWorkerObjects(entry.getValue(), service));
				}
				return result;
			}
		}
		else if (data instanceof List) {
			// Recursively process list elements.
			List<Object> list = (List<Object>) data;
			List<Object> result = new ArrayList<>();
			for (Object item : list) {
				result.add(proxifyWorkerObjects(item, service));
			}
			return result;
		}
		else {
			// Primitive value, return as-is.
			return data;
		}
	}
}
