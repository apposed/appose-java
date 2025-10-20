# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Appose is a Java library for interprocess cooperation with shared memory. It enables easy execution of Python-based deep learning from Java without copying tensors, though its utility extends beyond that use case.

**Key concepts:**
- **Builder**: Type-safe classes for creating environments (`PixiBuilder`, `MambaBuilder`, `SystemBuilder`, `DynamicBuilder`)
- **Environment**: A configured environment with executables and dependencies (created by builders)
- **Service**: Provides access to a worker process running in a separate process
- **Task**: Asynchronous operation performed by a service (analogous to a Future)
- **Worker**: Separate process that executes scripts (Python via `python_worker`, Groovy via `GroovyWorker`)

Communication between service and worker happens via JSON over stdin/stdout. Workers must conform to the Appose worker process contract (see javadoc in `Appose.java`).

## Build Commands

```bash
# Build the project
mvn package

# Run tests
mvn test

# Run a specific test class
mvn test -Dtest=ApposeTest

# Run a specific test method
mvn test -Dtest=ApposeTest#testGroovy

# Build with dependencies copied to target/dependency
mvn package dependency:copy-dependencies

# Skip tests during build
mvn package -DskipTests

# Clean and rebuild
mvn clean package
```

## Architecture

### Core API Flow
1. **Builder** → `Environment`: Use `Appose.{pixi|mamba|file|system|wrap}()...build()` to construct environments
2. **Environment** → `Service`: Call `env.python()`, `env.groovy()`, or `env.service()` to launch worker processes
3. **Service** → `Task`: Create tasks with `service.task(script)` to execute code asynchronously
4. **Task** → Results: Tasks provide status updates via `listen()` callbacks and outputs via `outputs` map

### Directory Structure
- `org.apposed.appose` - Main API classes (`Appose`, `Environment`, `Service`, `Builder`)
- `org.apposed.appose.mamba` - Micromamba/conda environment builder
- `org.apposed.appose.pixi` - Pixi environment builder
- `org.apposed.appose.shm` - Platform-specific shared memory implementations (Linux, macOS, Windows)
- `org.apposed.appose.util` - Utility classes for file paths, downloads, type conversion

### Key Interfaces
- **Builder**: Interface for environment builders
  - Discovered via `BuilderFactory` ServiceLoader
  - Implementations: `PixiBuilder`, `MambaBuilder`, `SystemBuilder`, `DynamicBuilder`
  - Core methods: `build(File)`, `build(String)`, `build()`
  - Subscription methods: `subscribeProgress()`, `subscribeOutput()`, `subscribeError()`, `logDebug()`
- **BuilderFactory**: Factory for creating and discovering builders
  - Factory methods: `createBuilder()`, `createBuilder(source)`, `createBuilder(source, scheme)`
  - Discovery methods: `name()`, `supports(scheme)`, `canWrap(File)`, `priority()`
- **Environment**: Interface representing a configured environment
  - Core methods: `base()`, `binPaths()`, `launchArgs()`
  - Worker creation: `python()`, `groovy()`, `java()`, `service()`
- **SharedMemory**: Platform-agnostic shared memory interface
  - Factory discovered via `ShmFactory` ServiceLoader
  - Platform implementations in `org.apposed.appose.shm` package

### Environment Building
Builders are type-safe and builder-specific:
- Each builder (`PixiBuilder`, `MambaBuilder`, etc.) handles its own configuration
- Builders implement `build(File envDir)` to create environments at specific locations
- Default environment location: `~/.local/share/appose/<env-name>`
- Builders can wrap existing environments or create new ones

### Worker Communication
- **Request types**: EXECUTE (run script), CANCEL (stop execution)
- **Response types**: LAUNCH, UPDATE, COMPLETION, CANCELATION, FAILURE, CRASH
- Each task has a UUID for tracking across processes
- Service monitors three streams: stdin (requests), stdout (responses), stderr (errors)

## Testing Requirements

Tests require:
- Java 8+ (project targets Java 8 compatibility)
- Python 3.10+ with `appose` package installed (`pip install appose`)
- System Python accessible on PATH

Some tests (like `testConda`) build conda environments and may take time on first run. Environments are cached in `~/.local/share/appose`.

## Main API Entry Points

The `Appose` class provides static factory methods for creating environments:

**Type-safe builders:**
- `Appose.pixi()` / `Appose.pixi(source)` - Creates a PixiBuilder
- `Appose.mamba()` / `Appose.mamba(source)` - Creates a MambaBuilder
- `Appose.uv()` / `Appose.uv(source)` - Creates a UvBuilder

**Dynamic builder:**
- `Appose.file(source)` - Creates a DynamicBuilder that auto-detects builder based on file extension

**Direct environment creation:**
- `Appose.system()` - Creates a SimpleBuilder with system PATH and inherited Java
- `Appose.wrap(directory)` - Wraps an existing environment directory, auto-detecting its type

## Important Notes

- **Dependencies in pom.xml must stay in sync** with the classpath construction in `Environment.java:130-138`
- When adding Appose dependencies, update both locations
- The project uses scijava-pom-parent, which provides standard Maven lifecycle configuration
- Shared memory implementations are platform-specific; changes should be tested on Linux, macOS, and Windows
- Worker scripts must be single expressions for task results, or must populate `task.outputs["result"]` explicitly
- Builder classes are type-safe and builder-specific (no generic `include()` / `channel()` methods on base `Builder` interface)
- The plugin architecture uses ServiceLoader with `BuilderFactory` for discovery

## Builders

The project provides type-safe builder classes for different environment types:

### Implemented Builders

**PixiBuilder** - Modern package manager supporting both conda and PyPI
- Created via `Appose.pixi()` or `Appose.pixi(source)`
- Type-safe methods: `conda(packages...)`, `pypi(packages...)`, `channels(channels...)`
- Supports `pixi.toml` and `environment.yml` files
- Uses `pixi run --manifest-path <envDir>/pixi.toml` for activation
- Environment structure: `<envDir>/.pixi/envs/default`
- Location: `org.apposed.appose.pixi.PixiBuilder`

**MambaBuilder** - Traditional conda environments via micromamba
- Created via `Appose.mamba()` or `Appose.mamba(source)`
- Supports `environment.yml` files
- Uses `mamba run -p <envDir>` for activation
- Location: `org.apposed.appose.mamba.MambaBuilder`

**UvBuilder** - Fast Python virtual environments via UV
- Created via `Appose.uv()` or `Appose.uv(source)`
- Type-safe methods: `include(packages...)`, `python(version)`
- Supports `requirements.txt` files
- Standard Python venv structure (no special activation needed)
- Environment structure: `<envDir>/bin` (or `Scripts` on Windows)
- Location: `org.apposed.appose.uv.UvBuilder`

**DynamicBuilder** - Auto-detects appropriate builder based on source file
- Created via `Appose.file(source)`
- Methods: `scheme(scheme)`, `builder(builderName)`
- Delegates to PixiBuilder or MambaBuilder based on file extension or explicit scheme
- Uses ServiceLoader discovery with builder priorities
- Location: `org.apposed.appose.DynamicBuilder`

**SystemBuilder** - Uses system PATH without installing packages
- Created via `Appose.system()` or `Appose.system(directory)`
- No package installation; uses whatever executables are on the system
- Method: `useSystemPath(boolean)` to control PATH inclusion
- Location: `org.apposed.appose.SystemBuilder`

### API Examples

```java
// Type-safe Pixi builder
Environment env = Appose.pixi("path/to/pixi.toml")
    .logDebug()
    .build();

// Type-safe Pixi builder with programmatic packages
Environment env = Appose.pixi()
    .conda("python>=3.8", "numpy")
    .pypi("cowsay")
    .channels("conda-forge")
    .build("my-env");

// Mamba builder
Environment env = Appose.mamba("path/to/environment.yml")
    .build();

// UV builder with requirements.txt
Environment env = Appose.uv("path/to/requirements.txt")
    .build();

// UV builder with programmatic packages
Environment env = Appose.uv()
    .python("3.11")
    .include("numpy", "pandas", "matplotlib")
    .build("my-env");

// Dynamic builder (auto-detects)
Environment env = Appose.file("path/to/environment.yml")
    .logDebug()
    .build();

// Wrap existing environment
Environment env = Appose.wrap("/path/to/existing/env");

// System environment
Environment env = Appose.system();
```

### Builder Discovery

Builders are discovered via ServiceLoader using `BuilderFactory`:
- Each builder implements `supports(scheme)` to declare supported file types and package schemes
- Each builder implements `canWrap(File)` to declare if it can wrap an existing environment directory
- Builders have priorities for conflict resolution when multiple support the same scheme
- `DynamicBuilder` uses this system to automatically select the appropriate builder
- `Appose.wrap()` uses `canWrap()` to auto-detect environment type (pixi, conda, or fallback to system)

### Environment Configuration
Builders create environments with three key properties:
- **launchArgs**: Args to prepend when launching workers (e.g., `["pixi", "run"]` for pixi)
- **binPaths**: Directories to search for executables
- **base**: The root directory of the environment

### Progress Monitoring
All builders support subscription methods for monitoring:
- `subscribeProgress(consumer)` - Progress updates with current/total steps
- `subscribeOutput(consumer)` - Standard output from build process
- `subscribeError(consumer)` - Error output from build process
- `logDebug()` - Convenience method that logs output and errors to stderr

## Related Projects

- appose-python: Python implementation of Appose (https://github.com/apposed/appose-python)
- Issue tracker shared across all Appose implementations: https://github.com/apposed/appose/issues
