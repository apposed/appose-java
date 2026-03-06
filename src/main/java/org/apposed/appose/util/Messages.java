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

import groovy.json.JsonGenerator;
import groovy.json.JsonSlurper;
import org.apposed.appose.NDArray;
import org.apposed.appose.SharedMemory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.apposed.appose.NDArray.Shape.Order.C_ORDER;

/**
 * Utility class for encoding and decoding messages.
 *
 * @author Curtis Rueden
 * @author Tobias Pietzsch
 */
public final class Messages {

	private Messages() {
		// Prevent instantiation of utility class.
	}

	// Flag indicating whether we're in worker mode (set by GroovyWorker).
	public static boolean workerMode = false;

	// Reference to the worker exports map for auto-exporting.
	public static Map<String, Object> workerExports = null;

	// Counter for auto-generated proxy variable names.
	private static int proxyCounter = 0;

	// Registry of class -> (appose_type, encoder) for encoding custom types.
	private static final Map<Class<?>, String> ENCODER_TYPES = new ConcurrentHashMap<>();
	private static final Map<Class<?>, Function<Object, Object>> ENCODER_FNS = new ConcurrentHashMap<>();

	// Registry of appose_type -> factory for decoding custom types.
	private static final Map<String, Function<Map<String, Object>, Object>> DECODERS =
		new ConcurrentHashMap<>();

	/**
	 * Registers encoder and decoder functions for a custom Appose type.
	 * <p>
	 * When encoding, if an object is an instance of {@code objType}, {@code encoder}
	 * is called and its return value is wrapped as
	 * {@code {"appose_type": apposeType, "data": <encoded>}}.
	 * </p>
	 * <p>
	 * When decoding, if a JSON object has the given {@code apposeType}, {@code decoder}
	 * is called with the {@code "data"} field value and should return the
	 * reconstructed object.
	 * </p>
	 *
	 * @param <T>        The type being registered.
	 * @param objType    The class of objects to encode.
	 * @param apposeType The {@code appose_type} string used on the wire.
	 * @param encoder    Function from object to JSON-compatible value (without appose_type).
	 * @param decoder    Function from decoded data map to reconstructed object.
	 */
	@SuppressWarnings("unchecked")
	public static <T> void register(
		Class<T> objType,
		String apposeType,
		Function<T, Object> encoder,
		Function<Map<String, Object>, Object> decoder
	) {
		ENCODER_TYPES.put(objType, apposeType);
		ENCODER_FNS.put(objType, (Function<Object, Object>) (Function<?, ?>) encoder);
		DECODERS.put(apposeType, decoder);
	}

	static {
		// NB: Built-in type registrations live here rather than in their respective
		// classes (SharedMemory, NDArray) because Java static initializers only run
		// when a class is first loaded. If Messages.decode() were called before
		// SharedMemory or NDArray had been referenced, their decoders would not yet
		// be registered. Keeping registrations here ensures they are always in place
		// as soon as the Messages class itself is loaded.
		register(SharedMemory.class, "shm",
			shm -> {
				Map<String, Object> payload = new LinkedHashMap<>();
				payload.put("name", shm.name());
				payload.put("rsize", shm.rsize());
				return payload;
			},
			map -> SharedMemory.attach(
				(String) map.get("name"),
				((Number) map.get("rsize")).longValue()
			)
		);
		register(NDArray.class, "ndarray",
			nda -> {
				Map<String, Object> payload = new LinkedHashMap<>();
				payload.put("dtype", nda.dType().label());
				payload.put("shape", nda.shape().toIntArray(C_ORDER));
				payload.put("shm", nda.shm());
				return payload;
			},
			map -> new NDArray(
				toDType((String) map.get("dtype")),
				toShape((List<Integer>) map.get("shape")),
				(SharedMemory) map.get("shm")
			)
		);
	}

	/**
	 * Converts a Map into a JSON string.
	 * @param data
	 *      data that wants to be encoded
	 * @return string containing the info of the data map
	 */
	public static String encode(Map<?, ?> data) {
		return GENERATOR.toJson(data);
	}

	/**
	 * Converts a JSON string into a map.
	 * @param json
	 *      json string
	 * @return a map of with the information of the json
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> decode(String json) {
		return postProcess(new JsonSlurper().parseText(json));
	}

	/**
	 * Dumps the given exception, including stack trace, to a string.
	 *
	 * @param t
	 *      the given exception {@link Throwable}
	 * @return the String containing the whole exception trace
	 */
	public static String stackTrace(Throwable t) {
		StringWriter sw = new StringWriter();
		t.printStackTrace(new PrintWriter(sw));
		return sw.toString();
	}


	// -- Serialization --

	/*
		SharedMemory is represented in JSON as
		{
			appose_type:shm
			name:psm_4812f794
			rsize:16384
		}

		where
			"name" is the unique name of the shared memory segment
			"rsize" is the nominal/requested size in bytes.
		(as required for python shared_memory.SharedMemory constructor)


		NDArray is represented in JSON as
		{
			appose_type:ndarray
			shm:{...}
			dtype:float32
			shape:[2, 3, 4]
		}

		where
			"shm" is the representation of the underlying shared memory segment (as described above)
			"dtype" is the data type of the array elements
			"shape" is the array dimensions in C-Order
	*/

	/**
	 * Checks if a type is natively JSON-serializable by Groovy's built-in JSON encoder.
	 * <p>
	 * These are the basic JSON types that don't need special handling:
	 * Map, List, String (and other CharSequences), Number, Boolean, and primitives.
	 * </p>
	 * <p>
	 * Other types either implement {@link ForJson} (and are handled by the ForJson
	 * converter) or will be auto-proxied as worker_object references when in worker mode.
	 * </p>
	 * <p>
	 * Note: This list should remain stable. Types implementing {@link ForJson} are
	 * handled before the catch-all and do not require changes here.
	 * </p>
	 *
	 * @param type The class to check
	 * @return true if this is a basic JSON type that Groovy can serialize natively
	 */
	private static boolean isNativelyJsonSerializable(Class<?> type) {
		return Map.class.isAssignableFrom(type)
			|| List.class.isAssignableFrom(type)
			|| CharSequence.class.isAssignableFrom(type)
			|| Number.class.isAssignableFrom(type)
			|| Boolean.class.isAssignableFrom(type)
			|| type.isPrimitive();
	}

	static final JsonGenerator GENERATOR = new JsonGenerator.Options() //
			.addConverter(new JsonGenerator.Converter() {
				@Override
				public boolean handles(Class<?> type) {
					return ENCODER_TYPES.keySet().stream().anyMatch(c -> c.isAssignableFrom(type));
				}

				@Override
				public Object convert(Object value, String key) {
					for (Map.Entry<Class<?>, Function<Object, Object>> entry : ENCODER_FNS.entrySet()) {
						if (entry.getKey().isAssignableFrom(value.getClass())) {
							Map<String, Object> map = new LinkedHashMap<>();
							map.put("appose_type", ENCODER_TYPES.get(entry.getKey()));
							@SuppressWarnings("unchecked")
							Map<String, Object> payload = (Map<String, Object>) entry.getValue().apply(value);
							map.putAll(payload);
							return map;
						}
					}
					throw new IllegalStateException("No encoder for " + value.getClass());
				}
			}).addConverter(new JsonGenerator.Converter() {
				// Catch-all converter for non-serializable objects in worker mode.
				// This should be the LAST converter in the chain, so it only handles
				// objects that no other converter claimed.
				@Override
				public boolean handles(Class<?> type) {
					// Only active in worker mode.
					if (!workerMode) return false;

					// Don't auto-proxy types that Groovy's JSON encoder handles natively.
					// Registered types are earlier in the chain and will have already claimed theirs.
					return !isNativelyJsonSerializable(type);
				}

				@Override
				public Object convert(Object value, String key) {
					// Auto-export the object and return a worker_object reference.
					String varName = "_appose_auto_" + (proxyCounter++);
					if (workerExports != null) {
						workerExports.put(varName, value);
					}
					Map<String, Object> map = new LinkedHashMap<>();
					map.put("appose_type", "worker_object");
					map.put("var_name", varName);
					return map;
				}
			}).build();


	// -- Deserialization --

	@SuppressWarnings("unchecked")
	private static Map<String, Object> postProcess(Object parseResult) {
		return processMap((Map<String, Object>) parseResult);
	}

	private static Map<String, Object> processMap(Map<String, Object> map) {
		map.entrySet().forEach(entry -> entry.setValue(processValue(entry.getValue())));
		return map;
	}

	@SuppressWarnings("unchecked")
	private static Object processValue(Object value) {
		if (value instanceof Map) {
			Map<String, Object> map = processMap((Map<String, Object>) value);
			Object v = map.get("appose_type");
			if (v instanceof String) {
				String appose_type = (String) v;
				if ("worker_object".equals(appose_type)) {
					// Return map as-is; will be converted to WorkerObject
					// by Proxies.proxifyWorkerObjects() in Service.Task.handle().
					return map;
				}
				Function<Map<String, Object>, Object> factory = DECODERS.get(appose_type);
				if (factory != null) return factory.apply(map);
				System.err.println("unknown appose_type \"" + appose_type + "\"");
			}
			return map;
		} else {
			return value;
		}
	}

	private static NDArray.DType toDType(String dtype) {
		return NDArray.DType.fromLabel(dtype);
	}

	private static NDArray.Shape toShape(List<Integer> shape) {
		int[] ints = new int[shape.size()];
		Arrays.setAll(ints, shape::get);
		return new NDArray.Shape(C_ORDER, ints);
	}
}
