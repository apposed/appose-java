# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Appose is a Java library for interprocess cooperation with shared memory. It enables easy execution of Python-based deep learning from Java without copying tensors, though its utility extends beyond that use case.

**Key concepts:**
- **Environment**: Built using `Builder` with dependencies from conda/mamba, PyPI, Maven, or OpenJDK
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
1. **Builder** → `Environment`: Use `Appose.{file|include|channel}()...build()` to construct environments
2. **Environment** → `Service`: Call `env.python()`, `env.groovy()`, or `env.service()` to launch worker processes
3. **Service** → `Task`: Create tasks with `service.task(script)` to execute code asynchronously
4. **Task** → Results: Tasks provide status updates via `listen()` callbacks and outputs via `outputs` map

### Directory Structure
- `org.apposed.appose` - Main API classes (`Appose`, `Environment`, `Service`, `Builder`)
- `org.apposed.appose.mamba` - Micromamba/conda environment management
- `org.apposed.appose.shm` - Platform-specific shared memory implementations (Linux, macOS, Windows)

### Key Interfaces
- **BuildHandler**: Plugin interface for environment builders (discovered via ServiceLoader)
  - Implementations handle different dependency sources (conda, Maven, JDK, pixi)
  - Methods: `channel()`, `include()`, `envName()`, `build()`
- **SharedMemory**: Platform-agnostic shared memory interface
  - Factory discovered via `ShmFactory` ServiceLoader
  - Platform implementations in `org.apposed.appose.shm` package

### Environment Building
The `Builder` class uses a plugin architecture:
- Delegates to `BuildHandler` implementations discovered via `ServiceLoader`
- Each handler catalogs `include()` and `channel()` calls relevant to it
- During `build()`, handlers populate `config` map with `launchArgs`, `binPaths`, `classpath`
- Final `Environment` is an anonymous implementation aggregating all handler outputs

Default environment location: `~/.local/share/appose/<env-name>`

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

## Important Notes

- **Dependencies in pom.xml must stay in sync** with the classpath construction in `Environment.java:130-138`
- When adding Appose dependencies, update both locations
- The project uses scijava-pom-parent, which provides standard Maven lifecycle configuration
- Shared memory implementations are platform-specific; changes should be tested on Linux, macOS, and Windows
- Worker scripts must be single expressions for task results, or must populate `task.outputs["result"]` explicitly

## Build Handlers

The project uses a ServiceLoader-based plugin system for environment builders:

### Implemented Handlers
- **MambaHandler**: Manages conda environments via micromamba
  - Supports `environment.yml` files
  - Uses `mamba run -p <envDir>` for activation
  - Location: `org.apposed.appose.mamba.MambaHandler`

- **PixiHandler**: Modern package manager (implemented as of 0.7.x)
  - Supports both `environment.yml` and `pixi.toml` files
  - Uses `pixi run --manifest-path <envDir>/pixi.toml` for activation
  - Environment structure: `~/.local/share/appose/<env-name>/.pixi/envs/default`
  - Supports conda packages via `include(pkg, "conda")`
  - Supports PyPI packages via `include(pkg, "pypi")`
  - Location: `org.apposed.appose.pixi.PixiHandler`

### Planned Handlers (not yet implemented)
- **Maven Handler**: For downloading Maven artifacts and adding to classpath
- **JDK Handler**: For installing specific Java versions (e.g., via cjdk or Coursier)

### Builder API Design Philosophy
The Builder uses a plugin architecture (ServiceLoader) to support multiple dependency sources:
- **Conda/PyPI packages**: Via conda-forge and Python package index
- **Maven coordinates**: Java dependencies with full repository support
- **JDK versions**: Specific Java installations
- **File formats**: `environment.yml`, `pixi.toml`, `requirements.txt`

Example API usage with Pixi:
```java
// Using a pixi.toml file
Appose.file("/path/to/pixi.toml", "pixi.toml").build();

// Using environment.yml (works with both Mamba and Pixi handlers)
Appose.file("/path/to/environment.yml", "environment.yml").build();

// Building programmatically with conda and PyPI packages
Appose.include("python>=3.8", "conda")
    .include("pip", "conda")
    .include("cowsay", "pypi")
    .channel("conda-forge")
    .build("my-env-name");

// Future: Maven and JDK support
Appose.include("org.scijava:parsington", "maven")
    .channel("scijava", "maven:https://maven.scijava.org/content/groups/public")
    .include("zulu:17", "openjdk")
    .build();
```

### Environment Configuration
Build handlers populate three key configuration lists during `build()`:
- **launchArgs**: Args to prepend when launching workers (e.g., `pixi run` for pixi environments)
- **binPaths**: Directories to search for executables
- **classpath**: Java classpath elements for JVM-based workers

The `Environment` interface returns these via `launchArgs()`, `binPaths()`, and `classpath()` methods.

### Naming and Terminology
- **"scheme"** refers to dependency type (conda, pypi, maven, jdk) - chosen over "platform", "method", "system"
- **"base"** refers to `Environment#base()` returning the environment directory - may change to avoid confusion with conda's "base environment"
- Environment names cannot contain dots (pixi restriction) - use dashes instead

## Related Projects

- appose-python: Python implementation of Appose (https://github.com/apposed/appose-python)
- Issue tracker shared across all Appose implementations: https://github.com/apposed/appose/issues
