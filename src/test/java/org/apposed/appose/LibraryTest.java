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

import org.apposed.appose.Service.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Tests library code registered via {@link Service#importLibrary}. */
public class LibraryTest extends TestBase {

	private static final String MODELS_GROOVY =
		"package mylib\n" +
		"class Models {\n" +
		"  static final Map<String, String> CACHE = [:]\n" +
		"  static int loadCount = 0\n" +
		"  static String get(String key) {\n" +
		"    CACHE.computeIfAbsent(key) { loadCount++; Util.load(it) }\n" +
		"  }\n" +
		"}\n";

	private static final String UTIL_GROOVY =
		"package mylib\n" +
		"class Util {\n" +
		"  static String load(String key) { key.toUpperCase() }\n" +
		"}\n";

	private static final String MODELS_PYTHON =
		"_MODELS = {}\n" +
		"load_count = 0\n" +
		"def get_model(key):\n" +
		"    global load_count\n" +
		"    if key not in _MODELS:\n" +
		"        load_count += 1\n" +
		"        _MODELS[key] = key.upper()\n" +
		"    return _MODELS[key]\n";

	private static Map<String, String> groovyLib(String models) {
		Map<String, String> files = new LinkedHashMap<>();
		files.put("mylib/Models.groovy", models);
		files.put("mylib/Util.groovy", UTIL_GROOVY);
		return files;
	}

	@Test
	public void testGroovyWarmState() throws Exception {
		try (Service service = Appose.system().groovy()) {
			maybeDebug(service);
			service.importLibrarySource("mylib", groovyLib(MODELS_GROOVY));
			String script = "import mylib.Models\n";
			assertEquals("A", service.task(script + "Models.get('a')").waitFor().result());
			assertEquals("A", service.task(script + "Models.get('a')").waitFor().result());
			assertEquals("B", service.task(script + "Models.get('b')").waitFor().result());
			// Each model was loaded only once, and stayed warm across tasks.
			assertEquals(2, service.task(script + "Models.loadCount").waitFor().result());
		}
	}

	@Test
	public void testGroovySingleSource() throws Exception {
		try (Service service = Appose.system().groovy()) {
			maybeDebug(service);
			service.start();
			service.importLibrarySource("Greeter",
				"class Greeter { static String greet(String who) { \"Hello, $who\" } }");
			Task task = service.task("Greeter.greet('world')").waitFor();
			assertEquals("Hello, world", task.result());
		}
	}

	@Test
	public void testGroovyInitScript() throws Exception {
		try (Service service = Appose.system().groovy()) {
			maybeDebug(service);
			service.importLibrarySource("mylib", groovyLib(MODELS_GROOVY))
				.init("mylib.Models.get('warm')");
			Task task = service.task("mylib.Models.loadCount").waitFor();
			assertEquals(1, task.result());
		}
	}

	@Test
	public void testGroovyReregister() throws Exception {
		try (Service service = Appose.system().groovy()) {
			maybeDebug(service);
			service.importLibrarySource("mylib", groovyLib(MODELS_GROOVY));
			service.task("mylib.Models.get('a')").waitFor();

			// Unchanged source: class state is retained.
			service.importLibrarySource("mylib", groovyLib(MODELS_GROOVY));
			assertEquals(1, service.task("mylib.Models.loadCount").waitFor().result());

			// Changed source: classes are replaced with the new code.
			String models2 = MODELS_GROOVY.replace("toString", "") +
				"class Version { static int get() { 2 } }\n";
			service.importLibrarySource("mylib", groovyLib(models2));
			Task task = service.task("[mylib.Models.loadCount, mylib.Version.get()]").waitFor();
			assertEquals(Arrays.asList(0, 2), task.result());
		}
	}

	/** Tests that awkward characters survive the trip into the worker intact. */
	@Test
	public void testGroovyQuoting() throws Exception {
		String text = "it's a \\ \"$dollar\" ''' \\u0041 \t line\r\nbreak";
		String source = "class Text { static String get() { '''" +
			text.replace("\\", "\\\\").replace("'", "\\'").replace("$", "\\$")
				.replace("\r", "\\r") +
			"''' } }";
		try (Service service = Appose.system().groovy()) {
			maybeDebug(service);
			service.importLibrarySource("Text", source);
			assertEquals(text, service.task("Text.get()").waitFor().result());
		}
	}

	@Test
	public void testGroovyPath(@TempDir File tmp) throws Exception {
		File root = new File(tmp, "src");
		write(new File(root, "mylib/Models.groovy"), MODELS_GROOVY);
		write(new File(root, "mylib/Util.groovy"), UTIL_GROOVY);
		write(new File(root, "mylib/icon.txt"), "hello");
		try (Service service = Appose.system().groovy()) {
			maybeDebug(service);
			service.importLibrary("mylib", root);

			// Code is snapshotted at registration, not read from disk at use.
			write(new File(root, "mylib/Util.groovy"), UTIL_GROOVY.replace("toUpperCase", "reverse"));
			assertEquals("AB", service.task("mylib.Models.get('ab')").waitFor().result());

			// Resources are read from disk on demand.
			Task task = service.task("mylib.Models.getResource('icon.txt').text").waitFor();
			assertEquals("hello", task.result());
		}
	}

	@Test
	public void testPythonWarmState() throws Exception {
		try (Service service = pythonEnv().python()) {
			maybeDebug(service);
			service.importLibrarySource("mylib", MODELS_PYTHON);
			String script = "import mylib\n";
			assertEquals("A", service.task(script + "mylib.get_model('a')").waitFor().result());
			assertEquals("A", service.task(script + "mylib.get_model('a')").waitFor().result());
			assertEquals(1, service.task(script + "mylib.load_count").waitFor().result());
		}
	}

	@Test
	public void testPythonPath(@TempDir File tmp) throws Exception {
		File pkg = new File(tmp, "pkg");
		write(new File(pkg, "__init__.py"), MODELS_PYTHON);
		write(new File(pkg, "data.txt"), "hello");
		try (Service service = pythonEnv().python()) {
			maybeDebug(service);
			service.importLibrary("mypkg", pkg);
			Task task = service.task(
				"import importlib.resources, mypkg\n" +
				"[mypkg.get_model('x'), " +
				"importlib.resources.files('mypkg').joinpath('data.txt').read_text()]"
			).waitFor();
			assertEquals(Arrays.asList("X", "hello"), task.result());
		}
	}

	@Test
	public void testInvalidName() {
		Service service = Appose.system().groovy();
		assertThrows(IllegalArgumentException.class,
			() -> service.importLibrarySource("my-lib", ""));
		assertThrows(IllegalArgumentException.class,
			() -> service.importLibrary("mylib", new File("no/such/path")));
	}

	private static void write(File file, String text) throws IOException {
		file.getParentFile().mkdirs();
		Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
	}
}
