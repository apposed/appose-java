/*-
 * #%L
 * Appose: multi-language interprocess cooperation with shared memory.
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

import java.io.IOException;
import org.apposed.appose.Service.Task;
import org.apposed.appose.shm.SharedMemoryArray;

public class PlaygroundSharedMemory {

    public static void main(String[] args) throws IOException, InterruptedException {
		final SharedMemoryArray shm = SharedMemoryArray.create(24 * Float.BYTES);
		System.out.println("shm.getName() = " + shm.getName());
		final float[] buf = new float[24];
		for (int i = 0; i < buf.length; i++) {
			buf[i] = i;
		}
		shm.getPointer().write(0, buf, 0, buf.length);


		Environment env = Appose.base("/opt/homebrew/Caskroom/miniforge/base/envs/appose/").build();
		try (Service service = env.python()) {
			final String script = String.format(PRINT_NDARRAY, shm.getNameForPython());
			System.out.println(script);
			Task task = service.task(script);
			System.out.println(task);
			task.waitFor();
//			System.out.println("task.inputs = " + task.inputs);
//			System.out.println("task.outputs = " + task.outputs);
			final String result = (String) task.outputs.get("result");
			System.out.println("result = " + result);
		}

		shm.close();
    }

	private static final String PRINT_NDARRAY = "" + //
		"from multiprocessing import shared_memory\n" + //
		"import numpy as np\n" + //
		"size = 24\n" + //
		"im_shm = shared_memory.SharedMemory(name='%s', size=size * 4)\n" + //
		"arr = np.ndarray(size, dtype='float32', buffer=im_shm.buf).reshape([2, 3, 4])\n" + //
//		"print(arr)\n" + //
		"task.outputs['result'] = str(arr)\n" + //
		"im_shm.unlink()";
}
