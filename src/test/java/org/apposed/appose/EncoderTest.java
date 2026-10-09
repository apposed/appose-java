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

import org.apposed.appose.util.Messages;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the registration of encoders for custom types. */
public class EncoderTest {

	public static class Animal {}

	public static class Dog extends Animal {
		final boolean shy;

		Dog(boolean shy) {
			this.shy = shy;
		}
	}

	@Test
	public void testRegisterEncoder() {
		Messages.registerEncoder(Animal.class, "animal",
			a -> Collections.singletonMap("kind", "animal"));
		Messages.registerEncoder(Dog.class, "dog",
			d -> d.shy ? null : Collections.singletonMap("kind", "dog"));
		Map<String, Object> exported = new HashMap<>();
		exported.put("appose_type", "service_object");
		exported.put("var_name", "shy_dog");

		// The most specific encoder applies.
		String json = Messages.encode(Collections.singletonMap("pet", new Dog(false)), obj -> exported);
		assertTrue(json.contains("\"appose_type\":\"dog\""), json);
		json = Messages.encode(Collections.singletonMap("pet", new Animal()), obj -> exported);
		assertTrue(json.contains("\"appose_type\":\"animal\""), json);

		// An encoder may decline an object, which is then exported.
		json = Messages.encode(Collections.singletonMap("pet", new Dog(true)), obj -> exported);
		assertTrue(json.contains("\"var_name\":\"shy_dog\""), json);
	}

	public static class Kennel {
		final Dog dog = new Dog(false);
	}

	@Test
	public void testEncoderSubstitution() {
		Messages.registerEncoder(Dog.class, "dog", d -> Collections.singletonMap("kind", "dog"));
		Messages.registerEncoder(Kennel.class, "kennel", k -> k.dog);
		// An encoder may give another object to encode in its place.
		String json = Messages.encode(Collections.singletonMap("home", new Kennel()), obj -> null);
		assertTrue(json.contains("\"home\":{\"appose_type\":\"dog\",\"kind\":\"dog\"}"), json);
	}

	/** An object whose encoding sends a message of its own. */
	public static class Talker {}

	/** A message encoded while encoding another does not disturb the outer encoding. */
	@Test
	public void testNestedEncode() {
		Messages.registerEncoder(Talker.class, "talker", obj -> {
			Messages.encode(Collections.singletonMap("inner", new Object()),
				inner -> Collections.singletonMap("var_name", "inner"));
			return Collections.singletonMap("said", "hi");
		});
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("talker", new Talker());
		data.put("other", new Object());
		String json = Messages.encode(data, obj -> Collections.singletonMap("var_name", "outer"));
		assertTrue(json.contains("\"other\":{\"var_name\":\"outer\"}"), json);
	}
}
