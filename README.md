# Appose

Appose is a library for interprocess cooperation with shared memory.
The guiding principles are *simplicity* and *efficiency*.

Appose was written to enable **easy execution of Python-based deep learning
from Java without copying tensors**, but its utility extends beyond that.
The steps for using Appose are:

* Build an Environment with the dependencies you need.
* Create a Service linked to a *worker*, which runs in its own process.
* Execute scripts on the worker by launching Tasks.
* Receive status updates from the task asynchronously via callbacks.

## Goals

Python, Java, and JavaScript, working in harmony, side by side.
Separate processes, shared memory, minimal dependencies.

1. Construct an environment. E.g.:
   * Java with dependencies from Maven.
   * Python with dependencies from conda-forge.
   * JavaScript with dependencies from NPM.

2. Invoke routines in that environment:
   * Routines are run in a separate process.
   * The routine's inputs and outputs are passed via pipes (stdin/stdout).
   * NDArrays are passed as named shared memory buffers,
     for zero-copy access across processes.

## Examples

Here is a very simple example written in Python:

```python
import appose
env = appose.java(vendor="zulu", version="17").build()
groovy = env.groovy()
Task task = groovy.task("""
    5 + 6
""")
task.waitFor()
result = task.outputs.get("result")
assert 11 == result
```

The same example, but written in Java and calling into Python:

```java
Environment env = Appose.conda("/path/to/environment.yml").build();
Service python = env.python();
Task task = python.task("""
    5 + 6
""");
task.waitFor();
Object result = task.outputs.get("result");
assertEquals(11, result);
```

Here is a Python example using a few more of Appose's features:

```python
import appose
from time import sleep

env = appose.java(vendor="zulu", version="17").build()
groovy = env.groovy()
task = groovy.task("""
    // Approximate the golden ratio using the Fibonacci sequence.
    previous = 0
    current = 1
    for (i=0; i<iterations; i++) {
        if (task.cancelRequested) {
            task.cancel()
            break
        }
        task.status(null, i, iterations)
        v = current
        current += previous
        previous = v
    }
    task.outputs["numer"] = current
    task.outputs["denom"] = previous
""")

def task_listener(event):
    match event.responseType:
        case UPDATE:
            print(f"Progress {task.current}/{task.maximum}")
        case COMPLETION:
            numer = task.outputs["numer"]
            denom = task.outputs["denom"]
            ratio = numer / denom
            print(f"Task complete. Result: {numer}/{denom} =~ {ratio}");
        case CANCELATION:
            print("Task canceled")
        case FAILURE:
            print(f"Task failed: {task.error}")

task.listen(task_listener)

task.start()
sleep(1)
if not task.status.isFinished():
    # Task is taking too long; request a cancelation.
    task.cancel()

task.waitFor()
```

And the Java version:

```java
Environment env = Appose.conda("/path/to/environment.yml").build();
Service python = env.python();
Task golden_ratio = python.task("""
    # Approximate the golden ratio using the Fibonacci sequence.
    previous = 0
    current = 1
    for i in range(iterations):
        if task.cancel_requested:
            task.cancel()
            break
        task.status(current=i, maximum=iterations)
        v = current
        current += previous
        previous = v
    task.outputs["numer"] = current
    task.outputs["denom"] = previous
    """);
task.listen(event -> {
    switch (event.responseType) {
        case UPDATE:
            System.out.println("Progress: " + task.current + "/" + task.maximum);
            break;
        case COMPLETION:
            long numer = (Long) task.outputs["numer"];
            long denom = (Long) task.outputs["denom"];
            double ratio = (double) numer / denom;
            System.out.println("Task complete. Result: " + numer + "/" + denom + " =~ " + ratio);
            break;
        case CANCELATION:
            System.out.println("Task canceled");
            break;
        case FAILURE:
            System.out.println("Task failed: " + task.error);
            break;
    }
});
task.start();
Thread.sleep(1000);
if (!task.status.isFinished()) {
    // Task is taking too long; request a cancelation.
    task.cancel();
}
task.waitFor();
```

Of course, the above examples could have been done all in one language. But
hopefully they hint at the possibilities of easy cross-language integration.

## Workers

A *worker* is a separate process created by Appose to do asynchronous
computation on behalf of the calling process. The calling process interacts
with a worker via its associated {@link Service}.

Appose comes with built-in support for two worker implementations:
{@code python-worker} to run Python scripts, and {@link GroovyWorker} to run
Groovy scripts. These workers can be created easily by invoking the
{@link Environment#python} and {@link Environment#groovy} methods
respectively. But Appose is compatible with any program that abides by the
*Appose worker process contract*:

1. The worker must accept requests in Appose's request format on its
   standard input (stdin) stream.
2. The worker must issue responses in Appose's response format on its
   standard output (stdout) stream.

TODO - write up the request and response formats in detail here!
JSON, one line per request/response.
