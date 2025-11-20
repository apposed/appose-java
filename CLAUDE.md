# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Appose is a Java library for interprocess cooperation with shared memory. It enables easy execution of Python-based deep learning from Java without copying tensors, though its utility extends beyond that use case.

**Key concepts:**
- **Builder**: Type-safe classes for creating environments (`PixiBuilder`, `MambaBuilder`, `UvBuilder`, `SimpleBuilder`, `DynamicBuilder`)
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
1. **Builder** → `Environment`: Use `Appose.{pixi|mamba|file|custom|wrap}()...build()` to construct environments
2. **Environment** → `Service`: Call `env.python()`, `env.groovy()`, or `env.service()` to launch worker processes
3. **Service** → `Task`: Create tasks with `service.task(script)` to execute code asynchronously
4. **Task** → Results: Tasks provide status updates via `listen()` callbacks and outputs via `outputs` map

### Directory Structure
- `org.apposed.appose` - Core API interfaces and main entry point (`Appose`, `Environment`, `Service`, `Builder`, `BuilderFactory`, `Scheme`, `SharedMemory`)
- `org.apposed.appose.builder` - All environment builder implementations and builder discovery utilities
  - `PixiBuilder`, `MambaBuilder`, `UvBuilder` - Type-safe builder implementations
  - `SimpleBuilder` - System PATH-based builder
  - `DynamicBuilder` - Auto-detecting builder
  - `Builders` - Builder discovery and management utility
- `org.apposed.appose.scheme` - Configuration file format schemes (`PixiTomlScheme`, `EnvironmentYmlScheme`, `PyProjectTomlScheme`, `RequirementsTxtScheme`)
- `org.apposed.appose.shm` - Platform-specific shared memory implementations and utilities
  - `ShmLinux`, `ShmMacOS`, `ShmWindows` - Platform implementations
  - `Shms` - Shared memory utility and factory methods
- `org.apposed.appose.util` - Cross-cutting utility classes (`Plugins`, `Schemes`, `FilePaths`, `Processes`, `Types`, etc.)

### Key Interfaces
- **Environment**: Interface representing a configured environment
  - Core methods: `base()`, `binPaths()`, `launchArgs()`
  - Worker creation: `python()`, `groovy()`, `java()`, `service()`
- **Builder**: Interface for environment builders
  - Implementations: `PixiBuilder`, `MambaBuilder`, `UvBuilder`, `SimpleBuilder`, `DynamicBuilder`
  - Core terminator method: `build()`
  - Subscription methods: `subscribeProgress()`, `subscribeOutput()`, `subscribeError()`, `logDebug()`
- **BuilderFactory**: Factory for creating and discovering builders
  - Factory method: `createBuilder()`
  - Discovery methods: `name()`, `supportsScheme(scheme)`, `canWrap(File)`, `priority()`
  - Implementations discovered via ServiceLoader and managed by `Builders` utility class
- **Scheme**: Interface for configuration file format detection and parsing
  - Implementations: `PixiTomlScheme`, `EnvironmentYmlScheme`, `PyProjectTomlScheme`, `RequirementsTxtScheme`
  - Methods: `name()`, `supportsContent(content)`, `priority()`
  - Implementations discovered via ServiceLoader and managed by `Schemes` utility class
- **SharedMemory**: Platform-agnostic shared memory interface
  - Platform implementations: `ShmLinux`, `ShmMacOS`, `ShmWindows`
  - Factory methods provided by `Shms` utility class
  - Implementations discovered via `ShmFactory` ServiceLoader

### Plugin Architecture

The project uses Java's ServiceLoader mechanism for extensibility, with utility classes managing discovery:

- **Plugins** (`org.apposed.appose.util.Plugins`) - Core ServiceLoader abstraction
  - `discover()` - Load and optionally sort all implementations of an interface
  - `find()` - Find first implementation matching a predicate
  - `create()` - Try factories until one produces a result
  - Used by all other plugin utilities for consistent discovery patterns

- **Builders** (`org.apposed.appose.builder.Builders`) - Builder discovery and management
  - `findFactoryByName(name)` - Find builder by name (e.g., "pixi", "mamba")
  - `findFactoryByScheme(scheme)` - Find builder supporting a scheme (e.g., "pixi-toml", "conda")
  - `findFactoryForWrapping(envDir)` - Detect builder for existing environment
  - Factories cached and sorted by priority

- **Schemes** (`org.apposed.appose.scheme.Schemes`) - Configuration file format detection
  - `fromContent(content)` - Detect scheme from file contents
  - `fromName(name)` - Get scheme by name (e.g., "pixi-toml")
  - Schemes sorted by priority for correct detection order

- **Shms** (`org.apposed.appose.shm.Shms`) - Shared memory utilities and factory
  - `create(name, create, rsize)` - Create platform-appropriate shared memory
  - Platform-specific utilities for shared memory implementations

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
- `Appose.pixi()` / `Appose.pixi(String|File|URL)` - Creates a PixiBuilder
- `Appose.mamba()` / `Appose.mamba(String|File|URL)` - Creates a MambaBuilder
- `Appose.uv()` / `Appose.uv(String|File|URL)` - Creates a UvBuilder

**Dynamic builder:**
- `Appose.file(String|File)` - Creates a DynamicBuilder that auto-detects builder based on file content
- `Appose.url(String|URL)` - Creates a DynamicBuilder from a URL
- `Appose.content(String)` - Creates a DynamicBuilder from configuration content

**Direct environment creation:**
- `Appose.system()` - Creates an environment with system PATH and inherited Java
- `Appose.wrap(String|File)` - Wraps an existing environment directory, auto-detecting its type
- `Appose.custom()` - Creates a SimpleBuilder for custom environments without package management

## Important Notes

- **Dependencies in pom.xml must stay in sync** with the classpath construction in `Environment.java:130-138`
- When adding Appose dependencies, update both locations
- The project uses the pom-scijava parent POM, which provides standard Maven lifecycle configuration
- Shared memory implementations are platform-specific; changes should be tested on Linux, macOS, and Windows
- Worker scripts must be single expressions for task results, or must populate `task.outputs["result"]` explicitly
- Builder classes are type-safe and builder-specific (no generic `include()` / `channel()` methods on base `Builder` interface)
- The plugin architecture uses ServiceLoader for extensibility, with discovery managed by utility classes (`Plugins`, `Builders`, `Schemes`, `Shms`)

## Builders

The project provides type-safe builder classes for different environment types:

### Implemented Builders

**PixiBuilder** - Modern package manager supporting both conda and PyPI
- Created via `Appose.pixi()` or `Appose.pixi(source)`
- Type-safe methods: `conda(packages...)`, `pypi(packages...)`, `channels(channels...)`
- Supports `pixi.toml` and `environment.yml` files
- Uses `pixi run --manifest-path <envDir>/pixi.toml` for activation
- Environment structure: `<envDir>/.pixi/envs/default`
- Location: `org.apposed.appose.builder.PixiBuilder`

**MambaBuilder** - Traditional conda environments via micromamba
- Created via `Appose.mamba()` or `Appose.mamba(source)`
- Supports `environment.yml` files
- Uses `mamba run -p <envDir>` for activation
- Location: `org.apposed.appose.builder.MambaBuilder`

**UvBuilder** - Fast Python virtual environments via uv
- Created via `Appose.uv()` or `Appose.uv(source)`
- Type-safe methods: `include(packages...)`, `python(version)`
- Supports `requirements.txt` files
- Standard Python venv structure (no special activation needed)
- Environment structure: `<envDir>/bin` (or `Scripts` on Windows)
- Location: `org.apposed.appose.builder.UvBuilder`

**DynamicBuilder** - Auto-detects appropriate builder based on configuration content
- Created via `Appose.file(source)`, `Appose.url(source)`, or `Appose.content(content)`
- Methods: `scheme(scheme)`, `builder(builderName)`, `content(content)`
- Delegates to PixiBuilder, MambaBuilder, or UvBuilder based on content or explicit scheme
- Uses ServiceLoader discovery with builder priorities
- Location: `org.apposed.appose.builder.DynamicBuilder`

**SimpleBuilder** - Uses an existing working directory without installing packages
- Created via `Appose.custom()` or implicitly via `Appose.system()`
- No package installation; uses whatever executables are on the system
- Methods: `binPaths(paths...)`, `appendSystemPath()`, `inheritRunningJava()`
- Location: `org.apposed.appose.builder.SimpleBuilder`

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
    .name("my-env")
    .build();

// Mamba builder
Environment env = Appose.mamba("path/to/environment.yml")
    .build();

// uv builder with requirements.txt
Environment env = Appose.uv("path/to/requirements.txt")
    .build();

// uv builder with programmatic packages
Environment env = Appose.uv()
    .python("3.11")
    .include("numpy", "pandas", "matplotlib")
    .name("my-env")
    .build();

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

Builders are discovered via `BuilderFactory` implementations managed by the `Builders` utility:
- Each factory implements `supportsScheme(scheme)` to declare supported file types and package schemes
- Each factory implements `canWrap(File)` to declare if it can wrap an existing environment directory
- Factories have priorities for conflict resolution when multiple support the same scheme
- `DynamicBuilder` uses scheme auto-detection from content to select the appropriate builder
- `Appose.wrap()` uses `Builders.findFactoryForWrapping()` to auto-detect environment type

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
