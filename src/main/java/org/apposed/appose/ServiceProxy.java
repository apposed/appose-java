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

import groovy.lang.GroovyObjectSupport;

import java.util.Arrays;
import java.util.List;

/**
 * A proxy object, living in a worker process, that provides access to a
 * remote object living in the calling (service) process.
 * <p>
 * This is the mirror image of {@link WorkerObject}: instead of the service
 * reaching into the worker, the worker reaches back into the service. Each
 * property access, method call, or {@link #dir()} on the proxy sends a
 * {@link Service.ResponseType#CALL CALL} message to the service, then blocks
 * until the service sends a {@link Service.RequestType#REPLY REPLY}.
 * </p>
 * <p>
 * Service proxies are created automatically: when a task input is not
 * JSON-serializable, the service exports it and the worker receives a
 * {@code ServiceProxy} in its place. For example, on the service side:
 * </p>
 * <pre>
 * Map&lt;String, Object&gt; inputs = new HashMap&lt;&gt;();
 * inputs.put("image", myVirtualImage); // Not JSON-serializable -&gt; proxied.
 * inputs.put("buffer", ndArray); // Shared memory -&gt; passed by reference.
 * service.task("image.readTile(0, 0, buffer)", inputs);
 * </pre>
 * <p>
 * Like {@link WorkerObject}, property values that are not JSON-serializable
 * come back as further {@code ServiceProxy} instances, so chaining works
 * naturally. And passing a {@code ServiceProxy} back to the service (as a
 * call argument or task output) yields the original service object again.
 * </p>
 * <p>
 * <strong>Error handling:</strong> If the remote operation fails, a
 * {@link RuntimeException} is thrown containing the error message from the
 * service.
 * </p>
 *
 * @author Curtis Rueden
 */
public class ServiceProxy extends GroovyObjectSupport {

	private final GroovyWorker worker;
	private final String varName;

	public ServiceProxy(GroovyWorker worker, String varName) {
		this.worker = worker;
		this.varName = varName;
	}

	/**
	 * Returns the variable name referencing this object in the service process.
	 *
	 * @return The variable name.
	 */
	public String varName() {
		return varName;
	}

	/**
	 * Calls this remote object as a function.
	 *
	 * @param args The arguments to pass.
	 * @return The result of the call.
	 */
	public Object call(Object... args) {
		return worker.invoke(varName, "call", null, Arrays.asList(args));
	}

	/**
	 * Lists the names of the remote object's properties and methods.
	 *
	 * @return The list of names.
	 */
	@SuppressWarnings("unchecked")
	public List<String> dir() {
		return (List<String>) worker.invoke(varName, "dir", null, null);
	}

	@Override
	public Object getProperty(String name) {
		return worker.invoke(varName, "get", name, null);
	}

	/** Forwards calls to methods of the remote object. */
	@SuppressWarnings("unused")
	public Object methodMissing(String name, Object args) {
		Object method = getProperty(name);
		if (!(method instanceof ServiceProxy)) {
			throw new RuntimeException("Service object attribute is not callable: " + name);
		}
		return ((ServiceProxy) method).call((Object[]) args);
	}

	@Override
	public String toString() {
		return "ServiceProxy[" + varName + "]";
	}
}
