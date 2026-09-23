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

import org.apposed.appose.NDArray.DType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests {@link DType}. */
public class DTypeTest {

	@Test
	public void testStandardLabels() {
		assertDType(DType.INT8, "int8", 1);
		assertDType(DType.INT16, "int16", 2);
		assertDType(DType.INT32, "int32", 4);
		assertDType(DType.INT64, "int64", 8);
		assertDType(DType.UINT8, "uint8", 1);
		assertDType(DType.UINT16, "uint16", 2);
		assertDType(DType.UINT32, "uint32", 4);
		assertDType(DType.UINT64, "uint64", 8);
		assertDType(DType.FLOAT16, "float16", 2);
		assertDType(DType.FLOAT32, "float32", 4);
		assertDType(DType.FLOAT64, "float64", 8);
		assertDType(DType.COMPLEX64, "complex64", 8);
		assertDType(DType.COMPLEX128, "complex128", 16);
		assertDType(DType.BOOL, "bool", 1);
	}

	@Test
	public void testShortLabels() {
		assertShortLabel(DType.INT8, "i1");
		assertShortLabel(DType.INT16, "i2");
		assertShortLabel(DType.INT32, "i4");
		assertShortLabel(DType.INT64, "i8");
		assertShortLabel(DType.UINT8, "u1");
		assertShortLabel(DType.UINT16, "u2");
		assertShortLabel(DType.UINT32, "u4");
		assertShortLabel(DType.UINT64, "u8");
		assertShortLabel(DType.FLOAT16, "f2");
		assertShortLabel(DType.FLOAT32, "f4");
		assertShortLabel(DType.FLOAT64, "f8");
		assertShortLabel(DType.COMPLEX64, "c8");
		assertShortLabel(DType.COMPLEX128, "c16");
		assertShortLabel(DType.BOOL, "b1");
		assertShortLabel(DType.BOOL, "?");
	}

	@Test
	public void testExplicitByteOrder() {
		for (String label : new String[] { "<u2", ">u2", "<f4", ">c16", "<uint16", ">float32" }) {
			IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> DType.fromLabel(label), label);
			assertTrue(e.getMessage().contains("native byte order"), e.getMessage());
		}
	}

	@Test
	public void testUnsupported() {
		String[] labels = {
			"", "=", "==u2", "|=u2", "=uint16", "|uint8", "uint", "u3", "f16",
			"c32", "?1", "l", "g", "longdouble", "float128", "intp", "U10",
			"datetime64[ns]", "object", "FLOAT32"
		};
		for (String label : labels) {
			IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> DType.fromLabel(label), label);
			assertTrue(e.getMessage().startsWith("Unsupported dtype"), e.getMessage());
		}
	}

	private static void assertDType(DType expected, String label, int bytes) {
		assertEquals(label, expected.label());
		assertEquals(bytes, expected.bytesPerElement());
		assertSame(expected, DType.fromLabel(label));
	}

	private static void assertShortLabel(DType expected, String shortLabel) {
		assertSame(expected, DType.fromLabel(shortLabel), shortLabel);
		assertSame(expected, DType.fromLabel("=" + shortLabel), "=" + shortLabel);
		assertSame(expected, DType.fromLabel("|" + shortLabel), "|" + shortLabel);
	}
}
