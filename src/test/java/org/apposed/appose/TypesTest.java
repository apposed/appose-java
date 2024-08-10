/*-
 * #%L
 * Appose: multi-language interprocess cooperation with shared memory.
 * %%
 * Copyright (C) 2023 - 2024 Appose developers.
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

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests {@link Types}.
 *
 * @author Curtis Rueden
 */
public class TypesTest {

	private static final String JSON = "{" +
		"\"posByte\":123,\"negByte\":-98," +
		"\"posDouble\":9.876543210123456,\"negDouble\":-1.234567890987654E302," +
		"\"posFloat\":9.876543,\"negFloat\":-1.2345678," +
		"\"posInt\":1234567890,\"negInt\":-987654321," +
		"\"posLong\":12345678987654321,\"negLong\":-98765432123456789," +
		"\"posShort\":32109,\"negShort\":-23456," +
		"\"trueBoolean\":true,\"falseBoolean\":false," +
		"\"aChar\":\"\\u0000\"," +
		"\"aString\":\"-=[]\\\\;',./_+{}|:\\\"<>?" +
		"AaBbCcDdEeFfGgHhIiJjKkLlMmNnOoPpQqRrSsTtUuVvWwXxYyZz" +
		"~!@#$%^&*()\"," +
		"\"numbers\":[1,1,2,3,5,8]," +
		"\"words\":[\"quick\",\"brown\",\"fox\"]," +
		"\"ndArray\":{" +
			"\"appose_type\":\"ndarray\"," +
			"\"dtype\":\"float32\"," +
			"\"shape\":[2,20,25]," +
			"\"shm\":{" +
				"\"appose_type\":\"shm\"," +
				"\"name\":\"SHM_NAME\"," +
				"\"size\":4000" +
			"}" +
		"}" +
	"}";

	private static final String STRING = "-=[]\\;',./_+{}|:\"<>?" +
			"AaBbCcDdEeFfGgHhIiJjKkLlMmNnOoPpQqRrSsTtUuVvWwXxYyZz" +
			"~!@#$%^&*()";

	private static final int[] NUMBERS = {1, 1, 2, 3, 5, 8};

	private static final String[] WORDS = {"quick", "brown", "fox"};

	@Test
	public void testEncode() {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("posByte", (byte) 123);
		data.put("negByte", (byte) -98);
		data.put("posDouble", 9.876543210123456);
		data.put("negDouble", -1.234567890987654E302);
		data.put("posFloat", 9.876543f);
		data.put("negFloat", -1.2345678f);
		data.put("posInt", 1234567890);
		data.put("negInt", -987654321);
		data.put("posLong", 12345678987654321L);
		data.put("negLong", -98765432123456789L);
		data.put("posShort", (short) 32109);
		data.put("negShort", (short) -23456);
		data.put("trueBoolean", true);
		data.put("falseBoolean", false);
		data.put("aChar", '\0');
		data.put("aString", STRING);
		data.put("numbers", NUMBERS);
		data.put("words", WORDS);
		NDArray.DType dtype = NDArray.DType.FLOAT32;
		NDArray.Shape shape = new NDArray.Shape(NDArray.Shape.Order.C_ORDER, 2, 20, 25);
		try (NDArray ndArray = new NDArray(dtype, shape)) {
			data.put("ndArray", ndArray);
			String json = Types.encode(data);
			assertNotNull(json);
			String expected = JSON.replaceAll("SHM_NAME", ndArray.shm().name());
			assertEquals(expected, json);
		}
	}

	@Test
	public void testDecode() {
		Map<String, Object> data;
		String shmName;

		// Create name shared memory segment and decode JSON block.
		try (SharedMemory shm = SharedMemory.create(null, 4000)) {
			shmName = shm.name();
			data = Types.decode(JSON.replaceAll("SHM_NAME", shmName));
		}

		// Validate results.
		assertNotNull(data);
		assertEquals(19, data.size());
		assertEquals(123, data.get("posByte")); // NB: decodes back to int
		assertEquals(-98, data.get("negByte")); // NB: decodes back to int
		assertEquals(9.876543210123456, bd(data.get("posDouble")).doubleValue());
		assertEquals(-1.234567890987654E302, bd(data.get("negDouble")).doubleValue());
		assertEquals(9.876543f, bd(data.get("posFloat")).floatValue());
		assertEquals(-1.2345678f, bd(data.get("negFloat")).floatValue());
		assertEquals(1234567890, data.get("posInt"));
		assertEquals(-987654321, data.get("negInt"));
		assertEquals(12345678987654321L, data.get("posLong"));
		assertEquals(-98765432123456789L, data.get("negLong"));
		assertEquals(32109, data.get("posShort")); // NB: decodes back to int
		assertEquals(-23456, data.get("negShort")); // NB: decodes back to int
		assertEquals(true, data.get("trueBoolean"));
		assertEquals(false, data.get("falseBoolean"));
		assertEquals("\0", data.get("aChar"));
		assertEquals(STRING, data.get("aString"));
		List<Integer> numbersList = Arrays.stream(NUMBERS)
			.boxed()
			.collect(Collectors.toList());
		assertEquals(numbersList, data.get("numbers"));
		assertEquals(Arrays.asList(WORDS), data.get("words"));
		try (NDArray ndArray = (NDArray) data.get("ndArray")) {
			assertSame(NDArray.DType.FLOAT32, ndArray.dType());
			assertEquals(NDArray.Shape.Order.C_ORDER, ndArray.shape().order());
			assertEquals(3, ndArray.shape().length());
			assertEquals(2, ndArray.shape().get(0));
			assertEquals(20, ndArray.shape().get(1));
			assertEquals(25, ndArray.shape().get(2));
			assertEquals(shmName, ndArray.shm().name());
			assertEquals(4000, ndArray.shm().size());
		}
	}

	private BigDecimal bd(Object posDouble) {
		if (posDouble instanceof BigDecimal) return ((BigDecimal) posDouble);
		throw new IllegalArgumentException("Not a BigDecimal: " + posDouble.getClass().getName());
	}
}
