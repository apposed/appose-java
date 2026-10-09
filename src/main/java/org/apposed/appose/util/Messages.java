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
import org.apposed.appose.Codec;
import org.apposed.appose.GroovyWorker;
import org.apposed.appose.NDArray;
import org.apposed.appose.ServiceProxy;
import org.apposed.appose.SharedMemory;
import org.apposed.appose.SharedMemoryView;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

	// Reference to the worker instance, for creating service object proxies.
	public static GroovyWorker workerInstance = null;

	// Counter for auto-generated proxy variable names.
	private static int proxyCounter = 0;

	// Exporter for non-JSON-serializable objects, active during an encode() call.
	private static final ThreadLocal<Function<Object, Map<String, Object>>> EXPORTER =
		new ThreadLocal<>();

	// Resources allocated by encoders, to close if an encode() call fails.
	private static final ThreadLocal<List<AutoCloseable>> CLOSE_ON_FAILURE = new ThreadLocal<>();

	// Registry of class -> (appose_type, encoder) for encoding custom types.
	private static final Map<Class<?>, String> ENCODER_TYPES = new ConcurrentHashMap<>();
	private static final Map<Class<?>, Function<Object, Object>> ENCODERS = new ConcurrentHashMap<>();

	// Registry of appose_type -> decoder for decoding custom types.
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
	 * is called on the  and should return the
	 * reconstructed object.
	 * </p>
	 *
	 * @param <T>        The type being registered.
	 * @param objType    The class of objects to encode.
	 * @param apposeType The {@code appose_type} string used on the wire.
	 * @param encoder    Function from object to JSON-compatible value (without appose_type).
	 * @param decoder    Function from decoded data map to reconstructed object.
	 */
	public static <T> void register(
		Class<T> objType,
		String apposeType,
		Function<T, Object> encoder,
		Function<Map<String, Object>, Object> decoder
	) {
		registerEncoder(objType, apposeType, encoder);
		DECODERS.put(apposeType, decoder);
	}

	/**
	 * Registers an encoder function for a custom Appose type, without a decoder.
	 * <p>
	 * This allows several Java types to encode as the same {@code appose_type},
	 * which decodes as whatever the decoder registered for it produces. If an
	 * object is an instance of several registered types, the encoder of the most
	 * specific type applies.
	 * </p>
	 * <p>
	 * The encoder may return null to decline encoding a particular object,
	 * which is then exported (by a service) or auto-exported (by a worker),
	 * as with any other object that is not JSON-serializable. Or the encoder
	 * may return another object (e.g. an {@link NDArray}) to encode in the
	 * given object's place, instead of a map.
	 * </p>
	 *
	 * @param <T>        The type being registered.
	 * @param objType    The class of objects to encode.
	 * @param apposeType The {@code appose_type} string used on the wire.
	 * @param encoder    Function from object to JSON-compatible map (without appose_type),
	 *                   or to another object to encode in its place, or null.
	 */
	@SuppressWarnings("unchecked")
	public static <T> void registerEncoder(
		Class<T> objType,
		String apposeType,
		Function<T, Object> encoder
	) {
		ENCODER_TYPES.put(objType, apposeType);
		ENCODERS.put(objType, (Function<Object, Object>) (Function<?, ?>) encoder);
	}

	/**
	 * Registers a resource, allocated by an encoder for the message currently
	 * being encoded on this thread, to be closed if encoding the message fails.
	 * Outside of an encode() call that collects transfers, this does nothing.
	 *
	 * @param resource The resource to close upon failure.
	 */
	public static void closeOnFailure(AutoCloseable resource) {
		List<AutoCloseable> resources = CLOSE_ON_FAILURE.get();
		if (resources != null) resources.add(resource);
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
				if (shm instanceof SharedMemoryView) {
					SharedMemoryView view = (SharedMemoryView) shm;
					payload.put("offset", view.offset());
					payload.put("length", view.size());
				}
				return payload;
			},
			Messages::decodeShm
		);
		register(NDArray.class, "ndarray",
			nda -> {
				Map<String, Object> payload = new LinkedHashMap<>();
				payload.put("dtype", nda.dType().label());
				// NB: Use a List rather than an array, so that
				// worker mode does not auto-proxy the shape.
				List<Integer> shape = new ArrayList<>();
				for (int d : nda.shape().toIntArray(C_ORDER)) shape.add(d);
				payload.put("shape", shape);
				payload.put("shm", nda.shm());
				return payload;
			},
			map -> new NDArray(
				toDType((String) map.get("dtype")),
				toShape((List<Integer>) map.get("shape")),
				(SharedMemory) map.get("shm")
			)
		);

		// Register the codecs of any plugins.
		for (Codec codec : Plugins.discover(Codec.class, null)) {
			try {
				codec.register();
			}
			catch (RuntimeException | LinkageError exc) {
				System.err.println("[WARNING] Failed to register codec " +
					codec.getClass().getName() + ": " + exc);
			}
		}
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
	 * Converts a Map into a JSON string, using the given exporter function
	 * for objects that are not JSON-serializable.
	 *
	 * @param data The data to encode.
	 * @param exporter Function to call for objects that are not
	 *          JSON-serializable. It must return a JSON-serializable
	 *          reference to the object (e.g. a service_object map).
	 * @return The data encoded as a single line of JSON.
	 */
	public static String encode(Map<?, ?> data, Function<Object, Map<String, Object>> exporter) {
		List<AutoCloseable> allocated = new ArrayList<>();
		// NB: Encoding may itself send a message on this thread, e.g. an
		// encoder calling into the service, so restore the outer message's
		// state afterwards.
		Function<Object, Map<String, Object>> outerExporter = EXPORTER.get();
		List<AutoCloseable> outerAllocated = CLOSE_ON_FAILURE.get();
		EXPORTER.set(exporter);
		CLOSE_ON_FAILURE.set(allocated);
		try {
			return GENERATOR.toJson(data);
		}
		catch (RuntimeException | Error exc) {
			// Free the resources allocated for this message.
			for (AutoCloseable resource : allocated) {
				try {
					resource.close();
				}
				catch (Exception exc2) {
					exc.addSuppressed(exc2);
				}
			}
			throw exc;
		}
		finally {
			EXPORTER.set(outerExporter);
			CLOSE_ON_FAILURE.set(outerAllocated);
		}
	}

	/**
	 * Converts a JSON string into a map.
	 * @param json
	 *      json string
	 * @return a map of with the information of the json
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> decode(String json) {
		return postProcess(Json.parseJson(json));
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
	 * Checks if a type is natively JSON-serializable by the built-in
	 * JSON encoder.
	 * <p>
	 * These are the basic JSON types that don't need special handling:
	 * Map, List, String (and other CharSequences), Number, Boolean, and
	 * primitives.
	 * </p>
	 * <p>
	 * Other types are handled by a registered encoder if available
	 * (see {@link #register(Class, String, Function, Function)}),
	 * or else will be auto-proxied as {@code worker_object} references
	 * when in worker mode.
	 * </p>
	 *
	 * @param type The class to check
	 * @return true if this is a basic JSON type that can be serialized natively
	 */
	private static boolean isNativelyJsonSerializable(Class<?> type) {
		return Map.class.isAssignableFrom(type)
			|| List.class.isAssignableFrom(type)
			|| CharSequence.class.isAssignableFrom(type)
			|| Number.class.isAssignableFrom(type)
			|| Boolean.class.isAssignableFrom(type)
			|| type.isPrimitive();
	}

	/**
	 * Checks if a type can be sent by a service as-is, without exporting it.
	 * <p>
	 * This is broader than {@link #isNativelyJsonSerializable(Class)}, to
	 * preserve the established encoding of arrays, collections, characters
	 * and enums in task inputs.
	 * </p>
	 *
	 * @param type The class to check
	 * @return true if this type can be serialized without exporting it
	 */
	private static boolean isServiceSerializable(Class<?> type) {
		return isNativelyJsonSerializable(type)
			|| Collection.class.isAssignableFrom(type)
			|| Character.class.isAssignableFrom(type)
			|| type.isArray()
			|| type.isEnum();
	}

	static final JsonGenerator GENERATOR = new JsonGenerator.Options() //
			.addConverter(new JsonGenerator.Converter() {
				@Override
				public boolean handles(Class<?> type) {
					return ENCODER_TYPES.keySet().stream().anyMatch(c -> c.isAssignableFrom(type));
				}

				@Override
				public Object convert(Object value, String key) {
					return encodeRegistered(value);
				}
			}).addConverter(new JsonGenerator.Converter() {
				// Catch-all converter for non-serializable objects.
				// This should be the LAST converter in the chain, so it only handles
				// objects that no other converter claimed.
				@Override
				public boolean handles(Class<?> type) {
					// A proxy to a service object travels back as a reference to it,
					// rather than being wrapped in another layer of proxying.
					if (ServiceProxy.class.isAssignableFrom(type)) return true;

					// Export non-serializable objects when an exporter is provided.
					if (EXPORTER.get() != null) return !isServiceSerializable(type);

					// Otherwise, only active in worker mode.
					if (!workerMode) return false;

					// Don't auto-proxy types that the JSON encoder handles natively.
					// Registered types are earlier in the chain and will have already claimed theirs.
					return !isNativelyJsonSerializable(type);
				}

				@Override
				public Object convert(Object value, String key) {
					if (value instanceof ServiceProxy) {
						Map<String, Object> map = new LinkedHashMap<>();
						map.put("appose_type", "service_object");
						map.put("var_name", ((ServiceProxy) value).varName());
						return map;
					}

					return export(value);
				}
			}).build();

	/**
	 * Exports the given object, via the active exporter (when encoding as a
	 * service), or else by auto-exporting it (when encoding as a worker).
	 */
	private static Map<String, Object> export(Object value) {
		Function<Object, Map<String, Object>> exporter = EXPORTER.get();
		if (exporter != null) return exporter.apply(value);
		if (!workerMode) {
			throw new IllegalArgumentException("Cannot encode object of " + value.getClass());
		}

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

	/** Encodes the given object, which is an instance of a registered type. */
	private static Object encodeRegistered(Object value) {
		Class<?> type = mostSpecificEncoderType(value.getClass());
		if (type == null) throw new IllegalStateException("No encoder for " + value.getClass());
		Object encoded = ENCODERS.get(type).apply(value);
		if (encoded == null) {
			// The encoder declined this object.
			return export(value);
		}
		if (!(encoded instanceof Map)) {
			// The encoder gave another object to encode in this one's place.
			return mostSpecificEncoderType(encoded.getClass()) == null ?
				encoded : encodeRegistered(encoded);
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> payload = (Map<String, Object>) encoded;
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("appose_type", ENCODER_TYPES.get(type));
		map.putAll(payload);
		return map;
	}

	/**
	 * Finds the most specific registered type of which the given type is a
	 * subtype, or null if there is none.
	 */
	private static Class<?> mostSpecificEncoderType(Class<?> type) {
		Class<?> best = null;
		for (Class<?> candidate : ENCODER_TYPES.keySet()) {
			if (!candidate.isAssignableFrom(type)) continue;
			if (best == null || best.isAssignableFrom(candidate)) best = candidate;
		}
		return best;
	}

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
				if ("service_object".equals(appose_type)) {
					if (workerMode && workerInstance != null) {
						// Worker side: wrap the reference in a proxy that forwards
						// property accesses and calls back to the service.
						return new ServiceProxy(workerInstance, (String) map.get("var_name"));
					}
					// Service side: return map as-is; will be resolved to the
					// referenced object by Proxies.proxifyWorkerObjects().
					return map;
				}
				Function<Map<String, Object>, Object> decoder = DECODERS.get(appose_type);
				if (decoder != null) return decoder.apply(map);
				System.err.println("unknown appose_type \"" + appose_type + "\"");
			}
			return map;
		} else if (value instanceof List) {
			List<Object> list = (List<Object>) value;
			list.replaceAll(Messages::processValue);
			return list;
		} else {
			return value;
		}
	}

	/**
	 * Decodes a shared memory reference: a whole block, or a region of one.
	 */
	private static SharedMemory decodeShm(Map<String, Object> map) {
		String name = (String) map.get("name");
		long rsize = ((Number) map.get("rsize")).longValue();
		if (!map.containsKey("offset") && !map.containsKey("length")) {
			// A plain block reference, as always.
			return SharedMemory.attach(name, rsize);
		}
		Object offsetValue = map.get("offset");
		long offset = offsetValue == null ? 0 : ((Number) offsetValue).longValue();
		Object lengthValue = map.get("length");
		long length = lengthValue == null ? rsize - offset : ((Number) lengthValue).longValue();
		return SharedMemoryView.attach(name, rsize, offset, length);
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
