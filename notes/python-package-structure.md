# Python Package Structure Plan

This document defines the target Python package structure for appose-python and how the Java API should align with it.

## Design Principles

1. **Plugin Architecture**: Use Python entry points (not ServiceLoader) for extensibility
2. **Monolithic Subsystem Files**: Group related implementations together (`builder.py`, `scheme.py`, `syntax.py`)
3. **Singular Naming**: Use singular names for consistency (`platform.py`, not `platforms.py`)
4. **Protocol-based Interfaces**: Use `typing.Protocol` for duck-typed interfaces, not ABC
5. **Forward Compatibility**: Structure anticipates Java subsystems not yet ported to Python

## Target Python Package Structure

```
appose/
  __init__.py          # Definition of appose.__all__ via subpackage imports
  appose.py            # Appose class for toplevel builder access
  environment.py       # Environment
  service.py           # Service, Task, TaskStatus, RequestType, ResponseType, TaskEvent
  shm.py               # NDArray, SharedMemory, DType, Shape, Order

  # Subsystems (all implementations + utility class in each)
  scheme.py            # PixiTomlScheme, EnvironmentYmlScheme, PyProjectTomlScheme,
                       # RequirementsTxtScheme, Schemes, Scheme protocol
  syntax.py            # PythonSyntax, GroovySyntax, Syntaxes, ScriptSyntax protocol

  # Utilities
  platform.py          # is_windows(), is_linux(), is_mac(), etc.
  proxy.py             # create() and proxy utilities
  filepath.py          # find_exe() and path utilities
  process.py           # Process utilities
  types.py             # JSON utilities, typedefs (e.g. Args)

  # Workers (language-specific)
  python_worker.py     # Python worker implementation
  groovy_worker.py     # Groovy worker implementation (if needed)

  # Builder subsystem (each implemention in a separate file)
  builder/__init__.py  # Builder protocol, BaseBuilder, DynamicBuilder,
                       # SimpleBuilder, Builders, BuilderFactory protocol
  builder/mamba.py     # MambaBuilder, MambaBuilderFactory
  builder/pixi.py      # PixiBuilder, PixiBuilderFactory
  builder/uv.py        # UvBuilder, UvBuilderFactory

  # Tool subsystem (each implemention in a separate file)
  tool/__init__.py     # Tool protocol
  tool/mamba.py        # Mamba
  tool/pixi.py         # Pixi
  tool/uv.py           # Uv
```

## Plugin System via Entry Points

Extensions can register plugins via `pyproject.toml`:

```python
# In pyproject.toml
entry_points={
    'appose.builders': [
        'pixi = appose.builder:PixiBuilderFactory',
        'mamba = appose.builder:MambaBuilderFactory',
        'uv = appose.builder:UvBuilderFactory',
    ],
    'appose.schemes': [
        'pixi-toml = appose.scheme:PixiTomlScheme',
        'environment-yml = appose.scheme:EnvironmentYmlScheme',
        'pyproject-toml = appose.scheme:PyProjectTomlScheme',
        'requirements-txt = appose.scheme:RequirementsTxtScheme',
    ],
    'appose.syntaxes': [
        'python = appose.syntax:PythonSyntax',
        'groovy = appose.syntax:GroovySyntax',
    ],
}
```

Third-party packages can extend Appose by providing their own entry points.

## Interface Design: Protocol vs ABC

Use `typing.Protocol` for interfaces to support structural subtyping:

```python
from typing import Protocol

class Builder(Protocol):
    """Builder interface for creating environments."""
    def build(self) -> Environment: ...
    def name(self) -> str: ...
    def rebuild(self) -> Environment: ...

class BuilderFactory(Protocol):
    """Factory for creating builders."""
    def create_builder(self) -> Builder: ...
    def supports_scheme(self, scheme: str) -> bool: ...
    def name(self) -> str: ...
```

**Why Protocol?**
- Structural subtyping (duck typing with type hints)
- No explicit inheritance required
- Third-party plugins don't need to import base classes
- More Pythonic

## Import Style

```python
# Recommended imports
from appose import builder, scheme, syntax
from appose import platform, proxy, path

# Usage examples
if platform.is_windows():
    print("Running on Windows")

proxy.create(service, var, api)
builder.Builders.find_by_name("pixi")
scheme.Schemes.from_content(content_string)
```

**Why singular naming?**
- Consistency across all modules
- Matches Python stdlib (`json`, `pathlib`, `subprocess`)
- `appose.builder.PixiBuilder` reads naturally
- Avoids confusion between "instances" vs "subsystem"

## API Stub Structure

The Java API dump should produce files matching this structure:

```
api/appose/
  __init__.pyi         # Appose class (factory methods)
  environment.pyi      # Environment, Builder protocol
  service.pyi          # Service, Task, TaskStatus, RequestType, ResponseType, TaskEvent
  types.pyi            # NDArray, SharedMemory, DType, Shape, Order, Args
  builder.pyi          # All builder classes + Builders + BuilderFactory protocol
  scheme.pyi           # All scheme classes + Schemes + Scheme protocol
  syntax.pyi           # All syntax classes + Syntaxes + ScriptSyntax protocol
  platform.pyi         # Platform utilities
  proxy.pyi            # Proxy utilities
  path.pyi             # Path utilities
  process.pyi          # Process utilities
  python_worker.pyi    # Python worker
  groovy_worker.pyi    # Groovy worker (if applicable)
```

## Java to Python Mapping Rules

### Package to File Mapping

| Java Package/Class                  | Python Module         |
|-------------------------------------|-----------------------|
| `org.apposed.appose.Appose`         | `appose.py`           |
| `org.apposed.appose.Environment`    | `environment.py`      |
| `org.apposed.appose.RequestType`    | `service.py`          |
| `org.apposed.appose.ResponseType`   | `service.py`          |
| `org.apposed.appose.Service*`       | `service.py`          |
| `org.apposed.appose.Task*`          | `service.py`          |
| `org.apposed.appose.scheme.*`       | `scheme.py`           |
| `org.apposed.appose.syntax.*`       | `syntax.py`           |
| `org.apposed.appose.NDArray*`       | `shm.py`              |
| `org.apposed.appose.SharedMemory`   | `shm.py`              |
| `org.apposed.appose.Builder*`       | `builder/__init__.py` |
| `org.apposed.appose.builder.Mamba*` | `builder/mamba.py`    |
| `org.apposed.appose.builder.Pixi*`  | `builder/pixi.py`     |
| `org.apposed.appose.builder.Uv*`    | `builder/uv.py`       |
| `org.apposed.appose.util.FilePaths` | `filepath.py`         |
| `org.apposed.appose.util.Platforms` | `platform.py`         |
| `org.apposed.appose.util.Processes` | `process.py`          |
| `org.apposed.appose.util.Proxies`   | `proxy.py`            |
| `org.apposed.appose.util.Types`     | `types.py`            |
| `org.apposed.appose.GroovyWorker`   | N/A                   |

### Special Cases

**Inner Classes**: Extract and place in same file as parent
- `Service.Task` → `service.py` (as `class Task`)
- `NDArray.DType` → `shm.py` (as `class DType`)
- `NDArray.Shape` → `shm.py` (as `class Shape`)

**AutoCloseable**: Add `__enter__` and `__exit__` methods automatically
- `Service(AutoCloseable)` → adds context manager protocol
- `SharedMemory(AutoCloseable)` → adds context manager protocol
- `NDArray(AutoCloseable)` → adds context manager protocol

**Enums**: Keep as classes with enum values
- `TaskStatus` → class with `INITIAL`, `QUEUED`, etc. as class attributes

## Rationale for Key Decisions

### Why singular names?
- **Consistency**: All modules use singular form
- **stdlib alignment**: Matches Python standard library conventions
- **Natural reading**: `appose.builder.PixiBuilder` flows well
- **Clarity**: Avoids ambiguity about module purpose

### Why Protocol over ABC?
- **Duck typing**: More Pythonic, supports structural subtyping
- **No inheritance required**: Third-party plugins more flexible
- **Type safety**: Still provides full type checking benefits
- **Plugin friendly**: Extensions don't depend on base classes

### Why entry points?
- **Python standard**: De facto plugin mechanism in Python ecosystem
- **Tool support**: pip, setuptools, poetry all support it
- **Ecosystem adoption**: Used by pytest, Flask, Sphinx, etc.
- **Dynamic discovery**: No need to manually register plugins

## References

- [PEP 561 - Distributing and Packaging Type Information](https://peps.python.org/pep-0561/)
- [PEP 544 - Protocols: Structural subtyping](https://peps.python.org/pep-0544/)
- [Entry Points Specification](https://packaging.python.org/en/latest/specifications/entry-points/)
- [Python Packaging User Guide](https://packaging.python.org/)
