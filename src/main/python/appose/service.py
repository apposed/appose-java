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

"""
TODO
"""

import subprocess
import sys
import threading
import uuid
from enum import Enum
from typing import Callable, Dict, List, Optional, Sequence
from .types import Args, decode, encode


class Service:
    """
    An Appose *service* provides access to a linked Appose *worker* running
    in a different process. Using the service, programs create Appose *tasks*
    that run asynchronously in the worker process, which notifies the
    service of updates via communication over pipes (stdin and stdout).
    """

    _service_count = 0

    def __init__(self, cwd: str, args: Sequence[str]) -> None:
        print(args)
        self.process = subprocess.Popen(
            args, stdin=subprocess.PIPE, stdout=subprocess.PIPE, cwd=cwd, text=True
        )
        self.tasks: Dict[str, "Task"] = {}

        self.thread = threading.Thread(
            target=self._loop, name=f"Appose-Service-{Service._service_count}"
        )
        Service._service_count += 1
        self.thread.start()

    def task(self, script: str, inputs: Optional[Args] = None) -> "Task":
        return Task(self, script, inputs)

    def close(self) -> None:
        self.process.stdin.close()

    def __enter__(self) -> "Service":
        return self

    def __exit__(self, exc_type, exc_value, exc_tb) -> None:
        self.close()

    def _loop(self) -> None:
        while True:
            line = self.process.stdout.readline()
            if not line:
                return  # pipe closed
            response = decode(line)
            uuid = response.get("task")
            if uuid is None:
                # TODO: proper logging
                print(f"Invalid service message:\n{line}", file=sys.stderr)
                continue
            task = self.tasks.get(uuid)
            if task is None:
                # TODO: proper logging
                print(f"No such task: {uuid}", file=sys.stderr)
                continue
            task._handle(response)


class TaskStatus(Enum):
    INITIAL = "INITIAL"
    QUEUED = "QUEUED"
    RUNNING = "RUNNING"
    COMPLETE = "COMPLETE"
    CANCELED = "CANCELED"
    FAILED = "FAILED"

    def is_finished(self):
        return self in (
            TaskStatus.COMPLETE,
            TaskStatus.CANCELED,
            self == TaskStatus.FAILED,
        )


class RequestType(Enum):
    EXECUTE = "EXECUTE"
    CANCEL = "CANCEL"


class ResponseType(Enum):
    LAUNCH = "LAUNCH"
    UPDATE = "UPDATE"
    COMPLETION = "COMPLETION"
    CANCELATION = "CANCELATION"
    FAILURE = "FAILURE"


class TaskEvent:
    def __init__(self, task: "Task", response_type: ResponseType) -> None:
        self.task: "Task" = task
        self.response_type: ResponseType = response_type


class Task:
    def __init__(
        self, service: Service, script: str, inputs: Optional[Args] = None
    ) -> None:
        self.uuid = uuid.uuid4().hex
        self.service = service
        self.script = script
        self.inputs: Args = {}
        if inputs is not None:
            self.inputs.update(inputs)
        self.outputs: Args = {}
        self.status: TaskStatus = TaskStatus.INITIAL
        self.message: Optional[str] = None
        self.current: int = 0
        self.maximum: int = 1
        self.error: Optional[str] = None
        self.listeners: List[Callable[["TaskEvent"], None]] = []
        self.cv = threading.Condition()
        self.service.tasks[self.uuid] = self

    def start(self) -> "Task":
        with self.cv:
            if self.status != TaskStatus.INITIAL:
                raise RuntimeError("Task is not in the INITIAL state")

            self.status = TaskStatus.QUEUED

        args = {"script": self.script, "inputs": self.inputs}
        self._request(RequestType.EXECUTE, args)

        return self

    def listen(self, listener: Callable[["TaskEvent"], None]) -> None:
        with self.cv:
            if self.status != TaskStatus.INITIAL:
                raise RuntimeError("Task is not in the INITIAL state")

            self.listeners.append(listener)

    def wait_for(self) -> None:
        with self.cv:
            if self.status == TaskStatus.INITIAL:
                self.start()

            if self.status not in (TaskStatus.QUEUED, TaskStatus.RUNNING):
                return

            self.cv.wait()

    def cancel(self) -> None:
        self._request(RequestType.CANCEL, [])

    def _request(self, request_type: RequestType, args: Args) -> None:
        request = {"task": self.uuid, "requestType": request_type.value}
        if args is not None:
            request.update(args)

        # NB: Flush is necessary to ensure worker receives the data!
        print(encode(request), file=self.service.process.stdin, flush=True)

    def _handle(self, response: Args) -> None:
        maybe_response_type = response.get("responseType")
        if maybe_response_type is None:
            print("Message type not specified", file=sys.stderr)
            return
        response_type = ResponseType(maybe_response_type)

        match response_type:
            case ResponseType.LAUNCH:
                self.status = TaskStatus.RUNNING
            case ResponseType.UPDATE:
                self.message = response.get("message")
                self.current = int(response.get("current", 0))
                self.maximum = int(response.get("maximum", 1))
            case ResponseType.COMPLETION:
                self.service.tasks.pop(self.uuid, None)
                self.status = TaskStatus.COMPLETE
                outputs = response.get("outputs")
                if outputs is not None:
                    self.outputs.update(outputs)
            case ResponseType.CANCELATION:
                self.service.tasks.pop(self.uuid, None)
                self.status = TaskStatus.CANCELED
            case ResponseType.FAILURE:
                self.service.tasks.pop(self.uuid, None)
                self.status = TaskStatus.FAILED
                self.error = response.get("error")

        event = TaskEvent(self, response_type)
        for listener in self.listeners:
            listener(event)

        if self.status.is_finished():
            with self.cv:
                self.cv.notify_all()

        self.service.tasks[self.uuid] = self
