###
# #%L
# Appose: multi-language interprocess plugins with shared memory ndarrays.
# %%
# Copyright (C) 2023 Appose developers.
# %%
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
# 
# 1. Redistributions of source code must retain the above copyright notice,
#    this list of conditions and the following disclaimer.
# 2. Redistributions in binary form must reproduce the above copyright notice,
#    this list of conditions and the following disclaimer in the documentation
#    and/or other materials provided with the distribution.
# 
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
# ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
# LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
# CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
# SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
# INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
# CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
# ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
# POSSIBILITY OF SUCH DAMAGE.
# #L%
###

import appose
from appose.service import ResponseType, Service, TaskStatus

collatz_groovy = """
// Computes the stopping time of a given value
// according to the Collatz conjecture sequence.
time = 0
BigInteger v = 9999
while (v != 1) {
  v = v%2==0 ? v/2 : 3*v+1
  task.update("[${time}] -> ${v}", time, null)
  time++
}
return time
"""

collatz_python = """
# Computes the stopping time of a given value
# according to the Collatz conjecture sequence.
time = 0
v = 9999
while v != 1:
    v = v//2 if v%2==0 else 3*v+1
    task.update(f"[{time}] -> {v}", current=time)
    time += 1
task.outputs["result"] = time
"""


def test_groovy():
    # TEMP HACK - for testing - somewhere that has bin/java
    env = appose.base("/home/curtis/mambaforge/envs/pyimagej-dev").build()
    class_path = [
        "/home/curtis/code/polyglot/appose/target/appose-0.1.0-SNAPSHOT.jar",
        "/home/curtis/code/polyglot/appose/target/dependency/groovy-3.0.4.jar",
        "/home/curtis/code/polyglot/appose/target/dependency/groovy-json-3.0.4.jar",
        "/home/curtis/code/polyglot/appose/target/dependency/ivy-2.4.0.jar",
        "/home/curtis/code/polyglot/appose/target/dependency/jna-5.13.0.jar",
        "/home/curtis/code/polyglot/appose/target/dependency/jna-platform-5.13.0.jar",
    ]
    with env.groovy(class_path=class_path) as service:
        execute_and_assert(service, collatz_groovy)


def test_python():
    # TEMP HACK - for testing - somewhere with bin/python and appose
    env = appose.base("/home/curtis/mambaforge/envs/appose-dev").build()
    with env.python() as service:
        execute_and_assert(service, collatz_python)


def execute_and_assert(service: Service, script: str):
    task = service.task(script)

    # Record the state of the task for each event that occurs.

    class TaskState:
        def __init__(self, event):
            self.response_type = event.response_type
            self.status = event.task.status
            self.message = event.task.message
            self.current = event.task.current
            self.maximum = event.task.maximum
            self.error = event.task.error

    events = []
    task.listen(lambda event: events.append(TaskState(event)))

    # Wait for task to finish.
    task.wait_for()

    # Validate the execution result.
    result = task.outputs["result"]
    assert 91 == result

    # Validate the events received.

    assert 93 == len(events)

    launch = events[0]
    assert ResponseType.LAUNCH == launch.response_type
    assert TaskStatus.RUNNING == launch.status
    assert launch.message is None
    assert 0 == launch.current
    assert 1 == launch.maximum
    assert launch.error is None

    v = 9999
    for i in range(91):
        v = v // 2 if v % 2 == 0 else 3 * v + 1
        update = events[i + 1]
        assert ResponseType.UPDATE == update.response_type
        assert TaskStatus.RUNNING == update.status
        assert f"[{i}] -> {v}" == update.message
        assert i == update.current
        assert 1 == update.maximum
        assert update.error is None

    completion = events[92]
    assert ResponseType.COMPLETION == completion.response_type
    assert TaskStatus.COMPLETE == completion.status
    assert "[90] -> 1" == completion.message
    assert 90 == completion.current
    assert 1 == completion.maximum
    assert completion.error is None
