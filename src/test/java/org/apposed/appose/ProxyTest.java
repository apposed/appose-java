/*-
 * #%L
 * Appose: multi-language interprocess cooperation with shared memory.
 * %%
 * Copyright (C) 2023 - 2025 Appose developers.
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

import org.apposed.appose.Service.Task;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the {@link Service#proxy} feature. */
public class ProxyTest extends TestBase {
	interface Creature {
		String walk(int speed);
		boolean fly(int speed, long height);
		String dive(double depth);
	}

	@Test
	public void testProxy() throws IOException, InterruptedException {
		Environment env = Appose.system();
		try (Service service = env.groovy()) {
			Task setup = service.task(
				"public class Bird {\n" +
				"  public boolean fly(int rate, long altitude) {\n" +
				"    return true\n" +
				"  }\n" +
				"  public String walk(int rate) {\n" +
				"    return rate > 1 ? \"Too fast for birds!\" : \"Hopped at rate: $rate\"\n" +
				"  }\n" +
				"  public String dive(double depth) {\n" +
				"    return depth > 2 ? \"Too deep for birds!\" : \"Dove down $depth deep\"\n" +
				"  }\n" +
				"}\n" +
				"public class Fish {\n" +
				"  public String dive(double depth) {\n" +
				"    return \"Swam down $depth deep\"\n" +
				"  }\n" +
				"  public boolean fly(int rate, long altitude) {\n" +
				"    return rate < 3 && altitude < 5\n" +
				"  }\n" +
				"  public String walk(int rate) {\n" +
				"    return \"Nope! Only the Darwin fish can do that.\"\n" +
				"  }\n" +
				"}\n" +
				"task.export([bird: new Bird(), fish: new Fish()])"
			);
			setup.waitFor();
			assertComplete(setup);

			// Validate bird behavior.
			Creature bird = service.proxy("bird", Creature.class);
			assertEquals("Hopped at rate: 1", bird.walk(1));
			assertEquals("Too fast for birds!", bird.walk(2));
			assertTrue(bird.fly(5, 100));
			assertEquals("Dove down 2.0 deep", bird.dive(2));
			assertEquals("Too deep for birds!", bird.dive(3));

			// Validate fish behavior.
			Creature fish = service.proxy("fish", Creature.class);
			assertEquals("Nope! Only the Darwin fish can do that.", fish.walk(1));
			assertTrue(fish.fly(2, 4));
			assertFalse(fish.fly(2, 10));
			assertEquals("Swam down 100.0 deep", fish.dive(100));
		}
	}
}
