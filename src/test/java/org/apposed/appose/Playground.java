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

public class Playground {

    public static void main(String[] args) throws IOException, InterruptedException {
        Environment env = Appose.base("/opt/homebrew/Caskroom/miniforge/base/envs/appose/").build();
        try (Service service = env.python()) {
//            service.debug(System.err::println);
//            executeAndAssert(service, COLLATZ_PYTHON);
            System.out.println(COLLATZ_PYTHON);
            Task task = service.task(COLLATZ_PYTHON);
            System.out.println(task);
            task.waitFor();
            System.out.println("task.inputs = " + task.inputs);
            System.out.println("task.outputs = " + task.outputs);
        }
    }

    private static final String COLLATZ_PYTHON = "" + //
            "# Computes the stopping time of a given value\n" + //
            "# according to the Collatz conjecture sequence.\n" + //
            "time = 0\n" + //
            "v = 9999\n" +
            "while v != 1:\n" + //
            "    v = v//2 if v%2==0 else 3*v+1\n" + //
            "    task.update(f\"[{time}] -> {v}\", current=time)\n" + //
            "    time += 1\n" + //
            "task.outputs[\"result\"] = time\n";

}
