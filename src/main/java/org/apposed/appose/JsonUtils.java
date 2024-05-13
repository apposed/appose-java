package org.apposed.appose;

import groovy.json.JsonGenerator;
import groovy.json.JsonSlurper;
import org.apposed.appose.shm.SharedMemoryArray;
import org.apposed.appose.shm.Shm;
import org.apposed.appose.shm.ndarray.DType;
import org.apposed.appose.shm.ndarray.NDArray;
import org.apposed.appose.shm.ndarray.Shape;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.apposed.appose.shm.ndarray.Shape.Order.C_ORDER;

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
		})).addConverter(convert(Shm.class, "shm", (map, shm) -> {
			map.put("name", shm.name());
			map.put("size", shm.size());
		})).addConverter(convert(NDArray.class, "ndarray", (map, ndArray) -> {
			map.put("shm", ndArray.shm());
			map.put("dtype", ndArray.dType().json());
			map.put("shape", ndArray.shape().toIntArray(C_ORDER));
		})).build();

	@SuppressWarnings("unchecked")
	static Map<String, Object> fromJson(String json) {
		Map<String, Object> map = (Map<String, Object>) new JsonSlurper().parseText(json);
		map = (Map<String, Object>) processMap(map);
//		printMap(map, "");
		return map;
	}

	private static void printMap(Map<String, Object> map, String indent) {
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			System.out.print(indent + entry.getKey() + ":");
			final Object value = entry.getValue();
			if (value instanceof Map) {
				System.out.println("{");
				printMap((Map<String, Object>) value, indent + "  ");
				System.out.println(indent + "}");
			}
			else System.out.println(value);
		}
	}

	private static Object processMap(Map<String, Object> map)
	{
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			if ( entry.getValue() instanceof Map ) {
				entry.setValue(processMap((Map<String, Object>) entry.getValue()));
			}
		}

		final Object v = map.get("appose_type");
		if ( v instanceof String )
		{
			final String appose_type = ( String) v;
			switch (appose_type) {
				case "shm":
					final String name = (String) map.get("name");
					final int size = (int) map.get("size");
					return new Shm(name, false, size);
				case "ndarray":
					final Shm shm = (Shm) map.get("shm");
					final DType dType = DType.fromJson((String) map.get("dtype"));
					final Shape shape = new Shape(C_ORDER, ((List<Integer>) map.get("shape")).stream().mapToInt(Integer::intValue).toArray());
					return new NDArray(shm, dType, shape);
				default:
					System.err.println("unknown appose_type \"" + appose_type + "\"");
			}
		}

		return map;
	}
}
