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

/**
 * Exception thrown when a {@link Task} fails to complete successfully.
 * <p>
 * This exception is thrown by {@link Task#waitFor()} when the task finishes
 * in a non-successful state ({@link TaskStatus#FAILED}, {@link TaskStatus#CANCELED},
 * or {@link TaskStatus#CRASHED}).
 * </p>
 */
public class TaskException extends Exception {

	private final Task task;

	public TaskException(String message, Task task) {
		super(message);
		this.task = task;
	}

	/**
	 * Returns the task that failed.
	 *
	 * @return The task associated with this exception.
	 */
	public Task getTask() {
		return task;
	}

	/**
	 * Returns the status of the failed task.
	 *
	 * @return The task's status (FAILED, CANCELED, or CRASHED).
	 */
	public TaskStatus getStatus() {
		return task.status;
	}

	/**
	 * Returns the error message from the task, if available.
	 *
	 * @return The task's error message, or null if none was set.
	 */
	public String getTaskError() {
		return task.error;
	}
}
