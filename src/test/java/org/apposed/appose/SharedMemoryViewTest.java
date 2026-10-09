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
			assertFalse(view.managed());
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

	@Test
	public void testManagedClose() throws InterruptedException {
		NDArray nda = NDArray.managed(DType.FLOAT32, new Shape(C_ORDER, 8));
		assertInstanceOf(SharedMemoryView.class, nda.shm());
		assertTrue(((SharedMemoryView) nda.shm()).managed());
		nda.buffer().asFloatBuffer().put(0, 1.5f);
		String name = nda.shm().name();
		assertTrue(shmExists(name));
		nda.close();
		assertTrue(eventually(() -> regionCount() == 0));
		assertFalse(shmExists(name));
	}

	@Test
	public void testUnknownManagedRegion() {
		String json = "{\"shm\":{\"appose_type\":\"shm\",\"name\":\"psm_bogus\"," +
			"\"rsize\":64,\"offset\":0,\"length\":8,\"managed\":true}}";
		IllegalArgumentException exc = assertThrows(IllegalArgumentException.class,
			() -> Messages.decode(json, testLink()));
		assertTrue(exc.getMessage().contains("No such managed shared memory region"), exc.getMessage());
	}

	@Test
	public void testManagedCollection() {
		NDArray nda = NDArray.managed(DType.INT32, new Shape(C_ORDER, 3));
		try (SharedMemory block = SharedMemory.create(16)) {
			Map<String, Object> data = new HashMap<>();
			data.put("managed", nda);
			data.put("unmanaged", new NDArray(DType.INT32, new Shape(C_ORDER, 4), block));
			java.util.List<SharedMemoryView> managed = new java.util.ArrayList<>();
			String json = Messages.encode(data, obj -> null, testLink(), managed);
			assertTrue(json.contains("\"managed\":true"), json);
			assertEquals(Collections.singletonList(nda.shm()), managed);
		}
		finally {
			nda.close();
		}
	}

	@Test
	public void testSlabs() throws InterruptedException {
		java.util.List<SharedMemoryView> views = new java.util.ArrayList<>();
		for (int i = 0; i < 7; i++) views.add(SharedMemoryView.allocate(1000));
		java.util.Set<String> blocks = new java.util.HashSet<>();
		for (SharedMemoryView view : views) {
			blocks.add(view.name());
			assertEquals(0, view.offset() % SlabMemory.ALIGN);
		}
		assertEquals(3, blocks.size()); // Slabs of 1, 2 and 4 slots.
		views.forEach(SharedMemoryView::close);
		assertTrue(eventually(() -> regionCount() == 0));
		for (String name : blocks) assertFalse(shmExists(name));
	}

	/** A toy memory link, which refers to views by tokens of its own. */
	private static class TokenLink implements MemoryLink {
		final Map<String, SharedMemoryView> views = new HashMap<>();
		final java.util.List<SharedMemoryView> sent = new java.util.ArrayList<>();

		@Override
		public Map<String, Object> describe(SharedMemoryView view) {
			String token = "token-" + views.size();
			views.put(token, view);
			return Collections.singletonMap("token", token);
		}

		@Override
		public void sent(java.util.List<SharedMemoryView> views) {
			sent.addAll(views);
		}

		@Override
		public SharedMemoryView resolve(Map<String, Object> ref) {
			return views.get((String) ref.get("token"));
		}
	}

	/** Managed references are described and resolved by the memory link alone. */
	@Test
	public void testLinkRoutesManagedReferences() {
		TokenLink link = new TokenLink();
		NDArray data = NDArray.managed(DType.UINT8, new Shape(C_ORDER, 4));
		try {
			for (int i = 0; i < 4; i++) data.buffer().put(i, (byte) 9);
			java.util.List<SharedMemoryView> managed = new java.util.ArrayList<>();
			Map<String, Object> inputs = Collections.singletonMap("x", data);
			String json = Messages.encode(inputs, null, link, managed);
			assertTrue(json.contains("{\"appose_type\":\"shm\",\"token\":\"token-0\",\"managed\":true}"), json);
			assertEquals(Collections.singletonList(data.shm()), managed);
			link.sent(managed);
			assertEquals(managed, link.sent);

			NDArray decoded = (NDArray) Messages.decode(json, link).get("x");
			for (int i = 0; i < 4; i++) assertEquals(9, decoded.buffer().get(i));

			// Without a link, managed memory cannot be sent or received.
			IllegalStateException exc = assertThrows(IllegalStateException.class, () -> Messages.encode(inputs));
			assertTrue(exc.getMessage().contains("no managed memory"), exc.getMessage());
			exc = assertThrows(IllegalStateException.class, () -> Messages.decode(json));
			assertTrue(exc.getMessage().contains("none on this connection"), exc.getMessage());
		}
		finally {
			data.close();
		}
	}

	/** An object whose encoding sends a message of its own, e.g. a worker's allocation call. */
	public static class Chatty {}

	/** A message sent while encoding another does not disturb the outer encoding. */
	@Test
	public void testNestedEncode() {
		Messages.registerEncoder(Chatty.class, "chatty", obj -> {
			Messages.encode(Collections.singletonMap("call", "_appose_allocate"), null, testLink(), new java.util.ArrayList<>());
			return Collections.singletonMap("said", "hi");
		});
		NDArray data = NDArray.managed(DType.UINT8, new Shape(C_ORDER, 4));
		try {
			Map<String, Object> inputs = new java.util.LinkedHashMap<>();
			inputs.put("chatty", new Chatty());
			inputs.put("data", data);
			java.util.List<SharedMemoryView> managed = new java.util.ArrayList<>();
			String json = Messages.encode(inputs, null, new TokenLink(), managed);
			assertTrue(json.contains("\"token\":\"token-0\""), json);
			assertEquals(Collections.singletonList(data.shm()), managed);
		}
		finally {
			data.close();
		}
	}
}
