/*-
 * #%L
 * Appose: multi-language interprocess cooperation with shared memory.
 * %%
 * Copyright (C) 2023 Appose developers.
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

import groovy.json.JsonGenerator;
import groovy.json.JsonSlurper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.apposed.appose.NDArray.Shape.Order.C_ORDER;

public final class Types {

	private Types() {
		// NB: Prevent instantiation of utility class.
	}

	public static String encode(Map<?, ?> data) {
		return GENERATOR.toJson(data);
	}

	public static Map<String, Object> decode(String json) {
		return postProcess(new JsonSlurper().parseText(json));
	}

	/** Dumps the given exception, including stack trace, to a string. */
	public static String stackTrace(Throwable t) {
		StringWriter sw = new StringWriter();
		t.printStackTrace(new PrintWriter(sw));
		return sw.toString();
	}


	// == serialization =======================================================

	/*
		SharedMemory is represented in JSON as
		{
			appose_type:shm
			name:psm_4812f794
			size:16384
		}

		where
			"name" is the unique name of the shared memory segment
			"size" is the size in bytes.
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
	 * Helper to create a {@link JsonGenerator.Converter}.
	 * <p>
	 * The converter objects of a specified class {@code clz} into a {@code
	 * Map<String, Object>}. The given {@code appose_type} string is put into
	 * the map with key {@code "appose_type"}. The map and value to be converted
	 * are passed to the given {@code BiConsumer} which should serialize the
	 * value into the map somehow.
	 *
	 * @param clz the converter will handle objects of this class (or sub-classes)
	 * @param appose_type the value for key "appose_type" in the returned Map
	 * @param converter accepts a map and a value, and serializes the value into the map somehow.
	 * @return a new converter
	 * @param <T> object type handled by this converter
	 */
	private static <T> JsonGenerator.Converter convert(final Class<T> clz, final String appose_type, final BiConsumer<Map<String, Object>, T> converter) {
		return new JsonGenerator.Converter() {

			@Override
			public boolean handles(final Class<?> type) {
				return clz.isAssignableFrom(type);
			}

			@SuppressWarnings("unchecked")
			@Override
			public Object convert(final Object value, final String key) {
				final Map<String, Object> map = new LinkedHashMap<>();
				map.put("appose_type", appose_type);
				converter.accept(map, (T) value);
				return map;
			}
		};
	}

	static final JsonGenerator GENERATOR = new JsonGenerator.Options() //
			.addConverter(convert(SharedMemory.class, "shm", (map, shm) -> {
				map.put("name", shm.name());
				map.put("size", shm.size());
			})).addConverter(convert(NDArray.class, "ndarray", (map, ndArray) -> {
				map.put("shm", ndArray.shm());
				map.put("dtype", ndArray.dType().label());
				map.put("shape", ndArray.shape().toIntArray(C_ORDER));
			})).build();


	// == deserialization =====================================================

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
			final Map<String, Object> map = processMap((Map<String, Object>) value);
			final Object v = map.get("appose_type");
			if (v instanceof String) {
				final String appose_type = (String) v;
				switch (appose_type) {
					case "shm":
						final String name = (String) map.get("name");
						final int size = (int) map.get("size");
						return SharedMemory.attach(name, size);
					case "ndarray":
						final SharedMemory shm = (SharedMemory) map.get("shm");
						final NDArray.DType dType = toDType((String) map.get("dtype"));
						final NDArray.Shape shape = toShape((List<Integer>) map.get("shape"));
						return new NDArray(shm, dType, shape);
					default:
						System.err.println("unknown appose_type \"" + appose_type + "\"");
				}
			}
			return map;
		} else {
			return value;
		}
	}

	private static NDArray.DType toDType(final String dtype) {
		return NDArray.DType.fromLabel(dtype);
	}

	private static NDArray.Shape toShape(final List<Integer> shape) {
		final int[] ints = new int[shape.size()];
		Arrays.setAll(ints, shape::get);
		return new NDArray.Shape(C_ORDER, ints);
	}
}
