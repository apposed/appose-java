/*-
 * #%L
 * Appose: multi-language interprocess plugins with shared memory ndarrays.
 * %%
 * Copyright (C) 2023 Appose developers.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apposed.appose.Service.ResponseType;
import org.apposed.appose.Service.Task;
import org.apposed.appose.Service.TaskStatus;
import org.junit.jupiter.api.Test;

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;

public class ApposeTest {

	@Test
	public void testJson() {
		Map<String, Object> map = new HashMap<>();
		map.put("stringValue", "QWERTYUIOP");
		map.put("intValue", 369_252_963);
		map.put("longValue", 123_456_789_987_654_321L);
		String json = JsonOutput.toJson(map);
		assertEquals("{\"stringValue\":\"QWERTYUIOP\",\"intValue\":369252963,\"longValue\":123456789987654321}", json);
		Map<?, ?> parsed = (Map<?, ?>) new JsonSlurper().parseText(json);
		assertEquals(map,  parsed);
	}

	@Test
	public void testGroovy() throws IOException, InterruptedException {
		Environment env = Appose
			// TEMP HACK - for testing
			.base(new File("/home/curtis/mambaforge/envs/pyimagej-dev"))
			.build();

		// Computes the stopping time of a given value
		// according to the Collatz conjecture sequence.
		int n = 9999;
		String collatz = "" + //
			"time = 0\n" + //
			"BigInteger v = " + n + "\n" +
			"while (v != 1) {\n" + //
			"  v = v%2==0 ? v/2 : 3*v+1\n" + //
			"  task.update(\"[${time}] -> ${v}\", time, null)\n" + //
			"  time++\n" + //
			"}\n" + //
			"return time";

		Service groovy = env.groovy();
		Task task = groovy.task(collatz);

		// Record the state of the task for each event that occurs.
		class TaskState {
			ResponseType responseType;
			TaskStatus status;
			String message;
			Long current;
			Long maximum;
			String error;
			TaskState(TaskEvent event) {
				responseType = event.responseType;
				status = event.task.status;
				message = event.task.message;
				current = event.task.current;
				maximum = event.task.maximum;
				error = event.task.error;
			}
		}
		List<TaskState> events = new ArrayList<>();
		task.listen(event -> events.add(new TaskState(event)));

		// Wait for task to finish.
		task.waitFor();

		// Validate the execution result.
		Number result = (Number) task.outputs.get("result");
		assertEquals(91, result.intValue());

		// Validate the events received.
		assertEquals(93, events.size());
		TaskState launch = events.get(0);
		assertSame(ResponseType.LAUNCH, launch.responseType);
		assertSame(TaskStatus.RUNNING, launch.status);
		assertNull(launch.message);
		assertEquals(0, launch.current);
		assertEquals(1, launch.maximum);
		assertNull(launch.error);
		int v = n;
		for (int i=0; i<91; i++) {
			v = v%2==0 ? v/2 : 3*v+1;
			TaskState update = events.get(i + 1);
			assertSame(ResponseType.UPDATE, update.responseType);
			assertSame(TaskStatus.RUNNING, update.status);
			assertEquals("[" + i + "] -> " + v, update.message);
			assertEquals(0, update.current); // FIXME: BUG: should be i, not 0.
			assertEquals(1, update.maximum);
			assertNull(update.error);
		}
		TaskState completion = events.get(92);
		assertSame(ResponseType.COMPLETION, completion.responseType);
		assertSame(TaskStatus.COMPLETE, completion.status);
		assertEquals("[90] -> 1", completion.message);
		assertEquals(0, completion.current); // FIXME: BUG: should be 91, not 0.
		assertEquals(1, completion.maximum);
		assertNull(completion.error);
	}
}
