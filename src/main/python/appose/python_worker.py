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

import ast
import sys
import traceback
from threading import Thread
from typing import Optional

# NB: Avoid relative imports so that this script can be run standalone.
from appose.service import RequestType, ResponseType
from appose.types import Args, decode, encode


class Task:
    def __init__(self, uuid: str) -> None:
        self.uuid = uuid
        self.outputs = {}
        self.cancel_requested = False

    def update(
        self,
        message: Optional[str] = None,
        current: Optional[int] = None,
        maximum: Optional[int] = None,
    ) -> None:
        args = {}
        if message is not None:
            args["message"] = message
        if current is not None:
            args["current"] = current
        if maximum is not None:
            args["maximum"] = maximum
        self._respond(ResponseType.UPDATE, args)

    def cancel(self) -> None:
        self._respond(ResponseType.CANCELATION, None)

    def fail(self, error: Optional[str] = None) -> None:
        args = None if error is None else {"error": error}
        self._respond(ResponseType.FAILURE, args)

    def _start(self, script: str, inputs: Optional[Args]) -> None:
        def execute_script():
            # Populate script bindings.
            binding = {"task": self}
            # TODO: Magically convert shared memory image inputs.
            if inputs is not None:
                binding.update(inputs)

            # Inform the calling process that the script is launching.
            self._report_launch()

            # Execute the script.
            # result = exec(script, locals=binding)
            result = None
            try:
                # NB: Execute the block, except for the last statement,
                # which we evaluate instead to get its return value.
                # Credit: https://stackoverflow.com/a/39381428/1207769

                block = ast.parse(script, mode="exec")
                last = None
                if (
                    len(block.body) > 0
                    and hasattr(block.body[-1], "value")
                    and not isinstance(block.body[-1], ast.Assign)
                ):
                    # Last statement of the script looks like an expression. Evaluate!
                    last = ast.Expression(block.body.pop().value)

                _globals = {}
                exec(compile(block, "<string>", mode="exec"), _globals, binding)
                if last is not None:
                    result = eval(
                        compile(last, "<string>", mode="eval"), _globals, binding
                    )
            except Exception:
                self.fail(traceback.format_exc())
                return

            # Report the results to the Appose calling process.
            if isinstance(result, dict):
                # Script produced a dict; add all entries to the outputs.
                self.outputs.update(result)
            elif result is not None:
                # Script produced a non-dict; add it alone to the outputs.
                self.outputs["result"] = result

            self._report_completion()

        # TODO: Consider whether to retain a reference to this Thread, and
        # expose a "force" option for cancelation that kills it forcibly; see:
        # https://www.geeksforgeeks.org/python-different-ways-to-kill-a-thread/
        Thread(target=execute_script, name=f"Appose-{self.uuid}").start()

    def _report_launch(self) -> None:
        self._respond(ResponseType.LAUNCH, None)

    def _report_completion(self) -> None:
        args = None if self.outputs is None else {"outputs": self.outputs}
        self._respond(ResponseType.COMPLETION, args)

    def _respond(self, response_type: ResponseType, args: Optional[Args]) -> None:
        response = {"task": self.uuid, "responseType": response_type.value}
        if args is not None:
            response.update(args)
        # NB: Flush is necessary to ensure service receives the data!
        print(encode(response), flush=True)


def main() -> None:
    tasks = {}

    while True:
        try:
            line = input().strip()
        except EOFError:
            break
        if not line:
            break

        request = decode(line)
        uuid = request.get("task")
        request_type = request.get("requestType")

        match RequestType(request_type):
            case RequestType.EXECUTE:
                script = request.get("script")
                inputs = request.get("inputs")
                task = Task(uuid)
                tasks[uuid] = task
                task._start(script, inputs)

            case RequestType.CANCEL:
                task = tasks.get(uuid)
                if task is None:
                    # TODO: proper logging
                    # Maybe should stdout the error back to Appose calling process.
                    print(f"No such task: {uuid}", file=sys.stderr)
                    continue
                task.cancel_requested = True


if __name__ == "__main__":
    main()
