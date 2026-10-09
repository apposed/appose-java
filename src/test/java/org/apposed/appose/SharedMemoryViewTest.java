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
import org.apposed.appose.NDArray.Shape;
import org.apposed.appose.util.Messages;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.apposed.appose.NDArray.Shape.Order.C_ORDER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests {@link SharedMemoryView} and its encoding, without worker processes. */
public class SharedMemoryViewTest extends TestBase {

	@Test
	public void testView() {
		try (SharedMemory block = SharedMemory.create(64)) {
			ByteBuffer buf = block.buf();
			for (int i = 0; i < 64; i++) buf.put(i, (byte) i);

			SharedMemoryView view = block.view(16, 8);
			assertEquals(block.name(), view.name());
			assertEquals(64, view.rsize());
			assertEquals(16, view.offset());
			assertEquals(8, view.size());
			ByteBuffer vbuf = view.buf();
			assertEquals(8, vbuf.capacity());
			for (int i = 0; i < 8; i++) assertEquals(16 + i, vbuf.get(i));
			assertEquals(18, view.buf(2, 3).get(0));

			assertThrows(IllegalArgumentException.class, () -> view.buf(4, 9));
			assertThrows(IllegalArgumentException.class, () -> block.view(60, 8));
			assertThrows(UnsupportedOperationException.class, view::unlink);
		}
	}

	@Test
	public void testRegionRoundTrip() {
		try (SharedMemory block = SharedMemory.create(64)) {
			ByteBuffer buf = block.buf();
			for (int i = 0; i < 64; i++) buf.put(i, (byte) i);

			NDArray nda = new NDArray(DType.UINT8, new Shape(C_ORDER, 2, 4), block.view(16, 8));
			String json = Messages.encode(Collections.singletonMap("nda", nda));
			assertTrue(json.contains("\"offset\":16"), json);
			assertTrue(json.contains("\"length\":8"), json);
			assertFalse(json.contains("managed"), json);

			Object decoded = Messages.decode(json).get("nda");
			assertInstanceOf(NDArray.class, decoded);
			NDArray received = (NDArray) decoded;
			assertInstanceOf(SharedMemoryView.class, received.shm());
			ByteBuffer rbuf = received.buffer();
			for (int i = 0; i < 8; i++) assertEquals(16 + i, rbuf.get(i));

			// Regions of one block share a single mapping.
			Object other = Messages.decode(Messages.encode(
				Collections.singletonMap("shm", block.view(0, 16)))).get("shm");
			assertInstanceOf(SharedMemoryView.class, other);
			assertSame(((SharedMemoryView) received.shm()).block(), ((SharedMemoryView) other).block());

			received.close();
			((SharedMemoryView) other).close();
		}
	}
}
