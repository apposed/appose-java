package org.apposed.appose;

import org.apposed.appose.util.Types;
import org.junit.jupiter.api.Test;

import org.apposed.appose.Service.Task;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProxyTest {
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
			assertEquals(Service.TaskStatus.COMPLETE, setup.status, setup.error);

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
