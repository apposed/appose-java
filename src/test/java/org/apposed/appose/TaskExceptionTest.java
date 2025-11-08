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
import org.apposed.appose.Service.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link TaskException} behavior.
 */
public class TaskExceptionTest extends TestBase {

	/**
	 * Tests that waitFor() throws TaskException on script failure,
	 * and that the exception provides access to the task details.
	 */
	@Test
	public void testTaskExceptionOnFailure() throws Exception {
		Environment env = Appose.system();
		try (Service service = env.python()) {
			maybeDebug(service);

			// Create a task with a syntax error that will fail.
			Task task = service.task("undefined_variable");

			try {
				task.waitFor();
				fail("Expected TaskException to be thrown for failed task");
			}
			catch (TaskException e) {
				// Verify exception contains useful information.
				assertTrue(e.getMessage().contains("failed"));
				assertTrue(e.getMessage().contains("NameError"));

				// Verify we can access the task and its details through the exception.
				assertSame(task, e.getTask());
				assertEquals(TaskStatus.FAILED, e.getStatus());
				assertNotNull(e.getTaskError());
				assertTrue(e.getTaskError().contains("NameError"));
			}
		}
	}

	/**
	 * Tests that waitFor() returns normally on success and
	 * outputs can be accessed without checking status.
	 */
	@Test
	public void testNoExceptionOnSuccess() throws Exception {
		Environment env = Appose.system();
		try (Service service = env.python()) {
			maybeDebug(service);

			// Create a task that will succeed.
			Task task = service.task("2 + 2").waitFor();

			// No exception thrown - we can directly access the result.
			Number result = (Number) task.result();
			assertEquals(4, result.intValue());
			assertEquals(TaskStatus.COMPLETE, task.status);
		}
	}
}
