# Appose Java

## What is Appose?

Appose is a library for interprocess cooperation with shared memory.
The guiding principles are *simplicity* and *efficiency*.

Appose was written to enable **easy execution of Python-based deep learning
from Java without copying tensors**, but its utility extends beyond that.
The steps for using Appose are:

* Build an Environment with the dependencies you need.
* Create a Service linked to a *worker*, which runs in its own process.
* Execute scripts on the worker by launching Tasks.
* Receive status updates from the task asynchronously via callbacks.

For more about Appose as a whole, see https://apposed.org.

## What is this project?

This is the **Java implementation of Appose**.

## How do I use it?

The dependency coordinate is `org.apposed:appose:0.3.0`.

### Maven

In your project's `pom.xml`:

```xml
<dependencies>
  <dependency>
    <groupId>org.apposed</groupId>
    <artifactId>appose</artifactId>
    <version>0.3.0</version>
  </dependency>
</dependencies>
```

### Gradle

In your project's `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
}
dependencies {
    implementation("org.apposed:appose:0.3.0")
}
```

### Just the JARs!

Clone this repository. Then, from the working copy:

```shell
mvn package dependency:copy-dependencies
```

Then grab the JARs:
* `target/appose-x.y.z-SNAPSHOT.jar`
* `target/dependency/*.jar`

Where `x.y.z-SNAPSHOT` is the version you built.

## Examples

Here is a minimal example for calling into Python from Java:

```java
Environment env = Appose.system();
try (Service python = env.python()) {
    Task task = python.task("5 + 6");
    task.waitFor();
    Object result = task.outputs.get("result");
    assertEquals(11, result);
}
```

It requires your active/system Python to have the
[`appose` Python package](https://github.com/apposed/appose-python)
available (`python -c 'import appose'` should yield no errors).

Here is an example using a few more of Appose's features:

```java
String goldenRatioInPython = """
# Approximate the golden ratio using the Fibonacci sequence.
previous = 0
current = 1
iterations = 50
for i in range(iterations):
    if task.cancel_requested:
        task.cancel()
        break
    task.update(current=i, maximum=iterations)
    v = current
    current += previous
    previous = v
task.outputs["numer"] = current
task.outputs["denom"] = previous
""";

Environment env = Appose.conda("/path/to/environment.yml").build();
try (Service python = env.python()) {
    Task task = python.task(goldenRatioInPython);
    task.listen(event -> {
        switch (event.responseType) {
            case UPDATE:
                System.out.println("Progress: " + task.current + "/" + task.maximum);
                break;
            case COMPLETION:
                long numer = ((Number) task.outputs.get("numer")).longValue();
                long denom = ((Number) task.outputs.get("denom")).longValue();

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
}
```

Of course, the above examples could have been done all in one language. But
hopefully they hint at the possibilities of easy cross-language integration.

## Issue tracker

All implementations of Appose use the same issue tracker:

https://github.com/apposed/appose/issues
