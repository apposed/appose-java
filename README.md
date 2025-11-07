# Appose Java

[![Build Status](https://github.com/apposed/appose-java/actions/workflows/build.yml/badge.svg)](https://github.com/apposed/appose-java/actions/workflows/build.yml)

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

The dependency coordinate is `org.apposed:appose:0.8.0`.

### Maven

In your project's `pom.xml`:

```xml
<dependencies>
  <dependency>
    <groupId>org.apposed</groupId>
    <artifactId>appose</artifactId>
    <version>0.8.0</version>
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
    implementation("org.apposed:appose:0.8.0")
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
    assertEquals(11, task.result());
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

Environment env = Appose.file("/path/to/environment.yml").build();
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

## Caching and disk usage

Appose uses multiple layers of caching to improve performance and reduce
redundant downloads. Understanding these cache locations can help you manage
disk usage and troubleshoot environment issues.

### Appose environment cache

**Location:** `~/.local/share/appose/` (customizable via `appose.envs-dir` system property)

This directory contains:
- **Tool binaries:** Pixi, uv, and Micromamba executables downloaded by Appose
  - `.pixi/bin/pixi` (v0.39.5)
  - `.uv/bin/uv` (v0.5.25)
  - `.mamba/bin/micromamba` (latest)
- **Built environments:** Each named environment created via `build(envName)`

### Package manager caches

Each package manager maintains its own cache for downloaded packages:

**Pixi** (uses Rattler cache):
- Environment variable: `PIXI_CACHE_DIR` or `RATTLER_CACHE_DIR`
- Linux: `~/.cache/rattler/cache`
- macOS: `~/Library/Caches/rattler/cache`
- Windows: `%LocalAppData%\rattler\cache`
- Check location: `pixi info`

**uv**:
- Environment variable: `UV_CACHE_DIR`
- Linux: `~/.cache/uv`
- macOS: `~/.cache/uv`
- Windows: `%LocalAppData%\uv\cache`
- Check location: `uv cache dir`

**Mamba**:
- Environment variable: `MAMBA_ROOT_PREFIX` (changes the entire root, including `pkgs/` subdirectory)
- Linux: `~/.local/share/mamba/pkgs`, `~/.mamba/pkgs`
- macOS: `~/.local/share/mamba/pkgs`, `~/.mamba/pkgs`
- Windows: `%AppData%\mamba\pkgs`, `~\.mamba\pkgs`, `%AppData%\.mamba\pkgs`
- Check location: `micromamba info`
- Customizable via: `micromamba config append pkgs_dirs /path/to/cache`

### Clearing caches

To free up disk space, you can clear individual caches:

```bash
# Clear uv cache
uv cache clean

# Clear Pixi/Rattler cache
pixi clean cache --yes

# Clear Micromamba cache
micromamba clean --all --yes

# Remove all Appose environments and tools (nuclear option)
rm -rf ~/.local/share/appose
```

**Note:** Package manager caches are shared across projects and significantly
speed up subsequent environment creation. Only clear them if disk space is critical.

## Issue tracker

All implementations of Appose use the same issue tracker:

https://github.com/apposed/appose/issues
