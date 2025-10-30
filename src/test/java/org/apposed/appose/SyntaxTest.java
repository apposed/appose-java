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
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tests {@link Service} functions related to {@link ScriptSyntax}. */
public class SyntaxTest extends TestBase {

	/** Tests getting a variable from worker's global scope using {@link Service#getVar}. */
	@Test
	public void testGetVarPython() throws IOException, InterruptedException {
		Environment env = Appose.system();
		try (Service service = env.python()) {
			maybeDebug(service);

			// Set up a variable in the worker using a task and export it
			service.task("test_var = 42\ntask.export(test_var=test_var)").waitFor();

			// Retrieve the variable using getVar
			Object result = service.getVar("test_var");
            assertInstanceOf(Number.class, result);
			assertEquals(42, ((Number) result).intValue());
		}
	}

	/** Tests getting a variable from worker's global scope using {@link Service#getVar}. */
	@Test
	public void testGetVarGroovy() throws IOException, InterruptedException {
		Environment env = Appose.system();
		try (Service service = env.groovy()) {
			maybeDebug(service);

			// Set up a variable in the worker using a task and export it
			service.task("test_string = 'hello world'\ntask.export([test_string: test_string])").waitFor();

			// Retrieve the variable using getVar
			Object result = service.getVar("test_string");
			assertEquals("hello world", result);
		}
	}

	/**
	 * Tests that {@link Service#getVar} throws {@link IllegalStateException}
	 * for non-existent variables.
	 */
	@Test
	public void testGetVarFailure() throws IOException, InterruptedException {
		Environment env = Appose.system();
		try (Service service = env.python()) {
			maybeDebug(service);

			try {
				service.getVar("nonexistent_variable");
				fail("Expected IllegalStateException for nonexistent variable");
			}
			catch (IllegalStateException exc) {
				assertTrue(exc.getMessage().contains("Failed to get variable 'nonexistent_variable'"));
			}
		}
	}

	/** Tests setting a variable in worker's global scope using {@link Service#putVar}. */
	@Test
	public void testPutVarPython() throws IOException, InterruptedException {
		Environment env = Appose.system();
		try (Service service = env.python()) {
			maybeDebug(service);

			// Set a variable using putVar
			service.putVar("my_number", 123);

			// Verify the variable is accessible in subsequent tasks
			Task task = service.task("my_number * 2").waitFor();
			assertComplete(task);
			Number result = (Number) task.result();
			assertEquals(246, result.intValue());
		}
	}

	/** Tests setting a variable in worker's global scope using {@link Service#putVar}. */
	@Test
	public void testPutVarGroovy() throws IOException, InterruptedException {
		Environment env = Appose.system();
		try (Service service = env.groovy()) {
			maybeDebug(service);

			// Set a variable using putVar
			service.putVar("my_string", "test");

			// Verify the variable is accessible in subsequent tasks
			Task task = service.task("my_string.toUpperCase()").waitFor();
			assertComplete(task);
			String result = (String) task.result();
			assertEquals("TEST", result);
		}
	}

	/** Tests that {@link Service#putVar} with a list works correctly. */
	@Test
	public void testPutVarList() throws IOException, InterruptedException {
		Environment env = Appose.system();
		try (Service service = env.python()) {
			maybeDebug(service);

			// Set a list using putVar
			List<Integer> values = Arrays.asList(1, 2, 3, 4, 5);
			service.putVar("my_list", values);

			// Verify the list is accessible and can be manipulated
			Task task = service.task("sum(my_list)").waitFor();
			assertComplete(task);
			Number result = (Number) task.result();
			assertEquals(15, result.intValue());
		}
	}

	/** Tests calling a built-in function using {@link Service#call}. */
	@Test
	public void testCallBuiltinPython() throws IOException, InterruptedException {
		Environment env = Appose.system();
		try (Service service = env.python()) {
			maybeDebug(service);

			// Call Python's built-in max function
			Object result = service.call("max", 5, 10, 3, 8);
            assertInstanceOf(Number.class, result);
			assertEquals(10, ((Number) result).intValue());
		}
	}

	/** Tests calling a built-in function using {@link Service#call}. */
	@Test
	public void testCallBuiltinGroovy() throws IOException, InterruptedException {
		Environment env = Appose.system();
		try (Service service = env.groovy()) {
			maybeDebug(service);

			// Call Groovy's Math.abs function
			Object result = service.call("Math.abs", -42);
            assertInstanceOf(Number.class, result);
			assertEquals(42, ((Number) result).intValue());
		}
	}

	/** Tests calling a custom function using {@link Service#call}. */
	@Test
	public void testCallCustomFunctionPython() throws IOException, InterruptedException {
		Environment env = Appose.system();
		try (Service service = env.python()) {
			maybeDebug(service);

			// Define a custom function in the worker
			service.task(
				"def multiply(a, b):\n" +
				"    return a * b\n" +
				"task.export(multiply=multiply)"
			).waitFor();

			// Call the custom function
			Object result = service.call("multiply", 6, 7);
            assertInstanceOf(Number.class, result);
			assertEquals(42, ((Number) result).intValue());
		}
	}

	/** Tests calling a custom function using {@link Service#call}. */
	@Test
	public void testCallCustomFunctionGroovy() throws IOException, InterruptedException {
		Environment env = Appose.system();
		try (Service service = env.groovy()) {
			maybeDebug(service);

			// Define a custom function in the worker
			service.task("def add(a, b) { return a + b }\ntask.export(add: this.&add)").waitFor();

			// Call the custom function
			Object result = service.call("add", 15, 27);
            assertInstanceOf(Number.class, result);
			assertEquals(42, ((Number) result).intValue());
		}
	}

	/**
	 * Tests that {@link Service#call} throws {@link IllegalStateException}
	 * when function doesn't exist.
	 */
	@Test
	public void testCallNonexistentFunction() throws IOException, InterruptedException {
		Environment env = Appose.system();
		try (Service service = env.python()) {
			maybeDebug(service);

			try {
				service.call("nonexistent_function", 1, 2, 3);
				fail("Expected IllegalStateException for nonexistent function");
			}
			catch (IllegalStateException exc) {
				assertTrue(exc.getMessage().contains("Failed to call function 'nonexistent_function'"));
			}
		}
	}

	interface Creature {
		String walk(int speed);
		boolean fly(int speed, long height);
		String dive(double depth);
	}

	/** Tests {@link Service#proxy}. */
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
