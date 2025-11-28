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

import org.apposed.appose.util.Proxies;

/**
 * Represents a reference to an object living in an Appose worker process.
 * <p>
 * When a task returns a non-JSON-serializable object, it is automatically
 * exported in the worker process and a {@code WorkerObject} reference is
 * returned to the caller. This allows interaction with the remote object
 * without needing to serialize it.
 * </p>
 * <p>
 * Usage:
 * </p>
 * <pre>
 * // Return a non-serializable object from a task
 * WorkerObject dateObj = (WorkerObject) service.task("new java.util.Date()").waitFor().result();
 *
 * // Call methods on the remote object
 * int year = (Integer) dateObj.call("getYear");
 * int month = (Integer) dateObj.call("getMonth");
 *
 * // Or convert to a typed proxy for easier access
 * interface DateLike {
 *     int getYear();
 *     int getMonth();
 *     int getDate();
 * }
 * DateLike date = dateObj.proxy(DateLike.class);
 * int year = date.getYear();
 * </pre>
 *
 * @author Curtis Rueden
 * @see Service.Task#result()
 * @see Proxies#create(Service, String, Class)
 */
public class WorkerObject {

	private final Service service;
	private final String varName;
	private final String queue;

	/**
	 * Constructs a WorkerObject referencing a remote object.
	 *
	 * @param service The service managing the worker process.
	 * @param varName The name of the exported variable in the worker process.
	 */
	public WorkerObject(Service service, String varName) {
		this(service, varName, null);
	}

	/**
	 * Constructs a WorkerObject referencing a remote object with queue control.
	 *
	 * @param service The service managing the worker process.
	 * @param varName The name of the exported variable in the worker process.
	 * @param queue Optional queue identifier for task execution.
	 */
	public WorkerObject(Service service, String varName, String queue) {
		this.service = service;
		this.varName = varName;
		this.queue = queue;
	}

	/**
	 * Returns the service managing the worker process containing this object.
	 *
	 * @return The service instance.
	 */
	public Service service() {
		return service;
	}

	/**
	 * Returns the variable name referencing this object in the worker process.
	 *
	 * @return The variable name.
	 */
	public String varName() {
		return varName;
	}

	/**
	 * Calls a method on the remote object by name.
	 * <p>
	 * This is a blocking operation that waits for the remote method to complete.
	 * Uses {@link ScriptSyntax#invokeMethod(String, String, java.util.List)} to
	 * generate the method invocation script.
	 * </p>
	 *
	 * @param methodName The name of the method to invoke.
	 * @param args The arguments to pass to the method.
	 * @return The result of the method call.
	 * @throws InterruptedException If the calling thread is interrupted while waiting.
	 * @throws TaskException If the method call fails in the worker process.
	 */
	public Object call(String methodName, Object... args) throws InterruptedException, TaskException {
		// Build the method invocation script using the service's syntax.
		java.util.Map<String, Object> inputs = new java.util.HashMap<>();
		java.util.List<String> argNames = new java.util.ArrayList<>();
		for (int i = 0; i < args.length; i++) {
			String argName = "arg" + i;
			inputs.put(argName, args[i]);
			argNames.add(argName);
		}

		org.apposed.appose.syntax.Syntaxes.validate(service);
		String script = service.syntax().invokeMethod(varName, methodName, argNames);

		Service.Task task = service.task(script, inputs, queue);
		task.waitFor();
		if (task.status != Service.TaskStatus.COMPLETE) {
			throw new TaskException("Task failed: " + task.error, task);
		}
		return task.result();
	}

	/**
	 * Invokes this remote object as a callable (function, method reference, or closure).
	 * <p>
	 * This is used when this WorkerObject represents a callable object, such as:
	 * </p>
	 * <ul>
	 * <li>A method reference obtained via {@link #getAttribute(String)}</li>
	 * <li>A function or closure</li>
	 * <li>Any object with a {@code __call__} method (Python) or {@code call} method (Groovy)</li>
	 * </ul>
	 * <p>
	 * This is a blocking operation that waits for the remote invocation to complete.
	 * Uses {@link ScriptSyntax#call(String, java.util.List)} to generate the call script.
	 * </p>
	 * <p>
	 * Example:
	 * </p>
	 * <pre>
	 * WorkerObject obj = ...;
	 * WorkerObject methodRef = (WorkerObject) obj.getAttribute("compute");
	 * Object result = methodRef.invoke(arg1, arg2);  // Invokes the method reference
	 * </pre>
	 *
	 * @param args The arguments to pass to the callable.
	 * @return The result of invoking the remote object.
	 * @throws InterruptedException If the calling thread is interrupted while waiting.
	 * @throws TaskException If the invocation fails in the worker process.
	 */
	public Object invoke(Object... args) throws InterruptedException, TaskException {
		// Build the call script using the service's syntax.
		java.util.Map<String, Object> inputs = new java.util.HashMap<>();
		java.util.List<String> argNames = new java.util.ArrayList<>();
		for (int i = 0; i < args.length; i++) {
			String argName = "arg" + i;
			inputs.put(argName, args[i]);
			argNames.add(argName);
		}

		org.apposed.appose.syntax.Syntaxes.validate(service);
		String script = service.syntax().call(varName, argNames);

		Service.Task task = service.task(script, inputs, queue);
		task.waitFor();
		if (task.status != Service.TaskStatus.COMPLETE) {
			throw new TaskException("Task failed: " + task.error, task);
		}
		return task.result();
	}

	/**
	 * Retrieves an attribute from the remote object.
	 * <p>
	 * The behavior depends on the worker language:
	 * </p>
	 * <ul>
	 * <li><strong>Python:</strong> Returns the attribute value (field) or bound method object.</li>
	 * <li><strong>Groovy:</strong> Tries field access first; if no such field exists,
	 *     returns a method reference (closure).</li>
	 * </ul>
	 * <p>
	 * If the result is a method reference or other non-JSON-serializable object,
	 * it will be auto-proxied as a {@code WorkerObject} that can be called using
	 * {@link #invoke(Object...)}.
	 * </p>
	 * <p>
	 * This is a blocking operation that waits for the remote operation to complete.
	 * Uses {@link ScriptSyntax#getAttribute(String, String)} to generate the attribute
	 * access script.
	 * </p>
	 * <p>
	 * Example:
	 * </p>
	 * <pre>
	 * WorkerObject obj = ...;
	 *
	 * // Field access - returns value directly
	 * String label = (String) obj.getAttribute("label");
	 *
	 * // Method reference - returns WorkerObject that can be invoked
	 * WorkerObject methodRef = (WorkerObject) obj.getAttribute("compute");
	 * Object result = methodRef.invoke(arg1, arg2);
	 * </pre>
	 *
	 * @param attributeName The name of the attribute to retrieve.
	 * @return The attribute value (field) or a WorkerObject (method reference).
	 * @throws InterruptedException If the calling thread is interrupted while waiting.
	 * @throws TaskException If the attribute access fails in the worker process.
	 */
	public Object getAttribute(String attributeName) throws InterruptedException, TaskException {
		org.apposed.appose.syntax.Syntaxes.validate(service);
		String script = service.syntax().getAttribute(varName, attributeName);

		Service.Task task = service.task(script, queue);
		task.waitFor();
		if (task.status != Service.TaskStatus.COMPLETE) {
			throw new TaskException("Task failed: " + task.error, task);
		}
		return task.result();
	}

	/**
	 * Creates a strongly-typed proxy for this remote object.
	 * <p>
	 * The proxy implements the given interface and forwards all method calls
	 * to the remote object. This provides a more natural, object-oriented API
	 * compared to using {@link #call(String, Object...)}.
	 * </p>
	 * <p>
	 * Example:
	 * </p>
	 * <pre>
	 * interface Calculator {
	 *     int add(int a, int b);
	 *     int multiply(int a, int b);
	 * }
	 *
	 * WorkerObject calcObj = ...;
	 * Calculator calc = calcObj.proxy(Calculator.class);
	 * int result = calc.add(2, 3); // Executes remotely
	 * </pre>
	 *
	 * @param <T> The interface type.
	 * @param api The interface class that the proxy should implement.
	 * @return A proxy object that forwards method calls to the remote object.
	 */
	public <T> T proxy(Class<T> api) {
		return Proxies.create(service, varName, api, queue);
	}

	@Override
	public String toString() {
		return "WorkerObject[" + varName + "]";
	}
}
