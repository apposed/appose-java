package org.apposed.appose;

import static org.apposed.appose.shm.ndarray.Shape.Order.C_ORDER;

import groovy.json.JsonGenerator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.apposed.appose.shm.SharedMemoryArray;
import org.apposed.appose.shm.ndarray.NDArray;

// TODO merge into Types?
class JsonUtils {

	static String toJson(Object obj) {
		return GENERATOR.toJson(obj);
	}

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
		.addConverter(convert(SharedMemoryArray.class, "shm", (map, shm) -> {
			map.put("name", shm.name());
			map.put("size", shm.size());
		})).addConverter(convert(NDArray.class, "ndarray", (map, ndArray) -> {
			map.put("shm", ndArray.shm());
			map.put("dtype", ndArray.dType().json());
			map.put("shape", ndArray.shape().toIntArray(C_ORDER));
		})).build();
}
