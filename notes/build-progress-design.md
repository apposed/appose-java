# Build Progress Reporting Design

**Date:** 2025-10-19
**Context:** Investigating how to provide rich progress reporting for Appose environment building using pixi and micromamba.

## Executive Summary

Pixi and micromamba have limited support for structured progress output. Micromamba has a `--json` flag that provides post-completion summaries, but no real-time progress. Pixi has no JSON output for `install`/`add` commands, only human-readable progress bars via the `indicatif` library.

**Recommendation:** Implement a **phase-based progress model** that parses stderr output to detect major phases, providing meaningful progress updates without brittleness.

---

## Investigation Findings

### Micromamba (`--json` flag)

Micromamba supports `--json` output that provides a **static summary after completion**, not streaming progress.

#### JSON Structure

```json
{
  "success": true,
  "prefix": "/path/to/env",
  "dry_run": false,
  "actions": {
    "FETCH": [
      {
        "name": "python",
        "version": "3.11.14",
        "build": "hec0b533_1_cpython",
        "channel": "conda-forge",
        "size": 14794480,
        "url": "https://conda.anaconda.org/...",
        "md5": "...",
        "sha256": "...",
        "depends": [...],
        "fn": "python-3.11.14-hec0b533_1_cpython.conda"
      }
    ],
    "LINK": [ /* packages to link/install */ ],
    "PREFIX": "/path/to/env"
  }
}
```

#### Capabilities

✅ Provides complete list of packages fetched and installed
✅ Shows final success/failure status
✅ Includes package metadata (size, version, dependencies)

❌ No real-time progress updates
❌ No download progress (bytes downloaded, percentage)
❌ JSON output can be mixed with non-JSON stderr in error cases
❌ Progress bars are shown separately on stderr, not in JSON

#### Known Issues

From GitHub issues ([#1181](https://github.com/mamba-org/mamba/issues/1181), [#1812](https://github.com/mamba-org/mamba/issues/1812), [#1813](https://github.com/mamba-org/mamba/issues/1813)):

- JSON output missing for network errors and bad channels
- Progress bars can interfere with JSON output
- pip subprocess may print non-JSON to stdout

### Pixi (no `--json` for install)

Pixi does **not** have a `--json` output flag for `install` or `add` commands.

#### Available Structured Output

Pixi has `--json` for these commands only:
- `pixi info --json` - Environment information
- `pixi list --json` - Package listings
- `pixi task --json` - Task information
- `pixi config --json` - Configuration

#### Progress Reporting Architecture

From source code analysis (`pixi/crates/pixi_reporters/`, `pixi/crates/pixi_progress/`):

Pixi uses the Rust `indicatif` library for progress bars with these reporters:

**`pixi_reporters` crate:**
- `TopLevelProgress` - Combines all sub-reporters
- `RepodataReporter` - Fetching repository metadata
- `MainProgressBar` - Solving dependencies
- `SyncReporter` - Installing/linking packages
- `GitCheckoutProgress` - Checking out source code
- `UvReporter` - Installing PyPI packages

**`pixi_progress` crate:**
- `global_multi_progress()` - Global multi-progress bar instance
- `default_bytes_style()` - Progress bar style for downloads
- `default_progress_style()` - Progress bar style for countable items
- `long_running_progress_style()` - Spinner style for indeterminate tasks
- `ProgressBarMessageFormatter` - Formats messages for concurrent tasks

#### Example Output

With `RUST_LOG=info`:
```
INFO pixi::lock_file::outdated: environment 'default' is out of date
INFO pixi::repodata: repodata gateway: using max '50' concurrent network requests
INFO resolve_conda: fetched 608 records in 1.318155583s
INFO pixi::lock_file::update: resolved conda environment in 1s 320ms
INFO pixi::lock_file::update: Updating prefix
✔ The default environment has been installed.
```

With progress bars (to stderr):
```
  ⠋ fetching repodata [00:00:01] [━━━━━━━━━━━━━━━━━━━━]  1234 @ 567KB/s
  ⠙ solving            [00:00:02] [━━━━━━━━━━╾─────────]   15/30
  ⠹ installing         [00:00:05] [━━━━━━━━━━━━━━━━━━━━]  30/30
```

#### Capabilities

✅ Human-friendly progress bars with byte/item counts
✅ Concurrent progress bars for parallel operations
✅ Phase-based progress (fetching, solving, installing)
✅ Very fast parallel downloads
✅ Structured logging via `RUST_LOG` (for debugging)

❌ No JSON output for install/add commands
❌ Structured logging is for debugging, not programmatic consumption
❌ Progress bars are `indicatif` rendering to stderr, not structured data

---

## Design Proposals

### Option 1: Phase-Based Progress Model (Recommended)

Parse stderr output to detect major phases and provide meaningful progress updates.

#### Data Model

```java
public class BuildProgress {
    public enum Phase {
        INITIALIZING,      // Setting up environment structure
        FETCHING_METADATA, // Downloading repodata from channels
        SOLVING,           // Resolving dependencies
        DOWNLOADING,       // Fetching package archives
        INSTALLING,        // Installing/linking packages
        COMPLETE,          // Success
        FAILED            // Error occurred
    }

    private final Phase phase;
    private final String message;  // Human-readable description or raw output line
    private final long timestamp;  // When this progress event occurred
}
```

#### API

```java
public interface Builder {
    /**
     * Registers a callback for build progress events.
     * Events are emitted as the build progresses through different phases.
     *
     * @param subscriber Party to inform when build progress happens.
     * @return This builder instance, for fluent-style programming.
     */
    Builder subscribe(Consumer<BuildProgress> subscriber);
}
```

#### Implementation Strategy

**For Pixi:**
1. Capture stderr while running `pixi install`
2. Detect phase transitions by matching patterns:
   - `"fetching repodata"` or `"INFO pixi::repodata"` → FETCHING_METADATA
   - `"solving"` or `"INFO resolve_conda"` → SOLVING
   - `"installing"` or download progress bars → DOWNLOADING/INSTALLING
   - `"✔"` or `"environment has been installed"` → COMPLETE
   - `"Error"` or non-zero exit code → FAILED

**For Micromamba:**
1. Capture stderr during `micromamba create/install`
2. Parse `--json` output after completion for package lists
3. Detect phases from stderr patterns:
   - `"Downloading"` → DOWNLOADING
   - `"Linking"` → INSTALLING
   - Terminal output patterns → phase transitions

#### Pros

✅ Works with both pixi and micromamba as-is
✅ No changes needed to upstream tools
✅ Provides meaningful progress for UIs (progress bars, status text)
✅ Simple to implement and maintain
✅ Robust - phase transitions are reliable patterns

#### Cons

❌ No granular package-by-package progress
❌ No download byte counts or percentages
❌ Parsing stderr is somewhat brittle to output format changes

#### Example Usage

```java
Environment env = Appose.pixi()
    .conda("python>=3.8", "numpy", "pandas")
    .subscribe(progress -> {
        switch (progress.phase()) {
            case FETCHING_METADATA:
                statusLabel.setText("Fetching repository data...");
                break;
            case SOLVING:
                statusLabel.setText("Resolving dependencies...");
                break;
            case DOWNLOADING:
                statusLabel.setText("Downloading packages...");
                progressBar.setIndeterminate(true);
                break;
            case INSTALLING:
                statusLabel.setText("Installing packages...");
                break;
            case COMPLETE:
                statusLabel.setText("Environment ready!");
                progressBar.setValue(100);
                break;
            case FAILED:
                statusLabel.setText("Installation failed: " + progress.message());
                break;
        }
    })
    .build();
```

---

### Option 2: Rich Event Model with Metadata

A more ambitious design that could support future enhancements if tools gain better output.

#### Data Model

```java
public class BuildEvent {
    public enum Type {
        PROGRESS,    // Progress update with current/max
        OUTPUT,      // General output message
        ERROR,       // Error message
        PACKAGE,     // Package-specific operation
        DOWNLOAD,    // File download progress
        TASK,        // Named task (e.g., "solving environment")
        PHASE        // Major phase transition
    }

    private final Type type;
    private final String message;
    private final long current;        // -1 if unknown
    private final long maximum;        // -1 if unknown
    private final Map<String, Object> metadata; // Flexible for tool-specific data
}
```

#### Builder Pattern

```java
BuildEvent event = BuildEvent.phase(Phase.SOLVING)
    .message("Resolving dependencies")
    .metadata("environment", "default")
    .build();
```

#### Pros

✅ Extensible for future capabilities
✅ Can incorporate micromamba's JSON data
✅ Rich metadata for advanced consumers

#### Cons

❌ Over-engineered for current tool capabilities
❌ More complex to implement
❌ Most metadata fields would be unused/unknown

---

### Option 3: Contribute to Pixi Upstream

Add `--json` output mode to pixi's `install` and `add` commands.

#### Proposed Changes to Pixi

Add JSON output similar to micromamba:
1. Add `--json` flag to `install` and `add` commands
2. Output structured progress events as newline-delimited JSON (NDJSON)
3. Each event includes: type, phase, package name, bytes, etc.

Example output:
```json
{"type":"phase","phase":"fetching_repodata","timestamp":1234567890}
{"type":"download","package":"python-3.11.14","bytes":1024,"total":14794480}
{"type":"phase","phase":"solving","timestamp":1234567891}
{"type":"phase","phase":"installing","timestamp":1234567895}
{"type":"complete","timestamp":1234567900}
```

#### Pros

✅ Proper structured output from the source
✅ Benefits all pixi users, not just Appose
✅ Future-proof and extensible

#### Cons

❌ Requires upstream contribution and approval
❌ Timeline uncertain (could take weeks/months)
❌ Need to maintain fork or wait for release
❌ Still need fallback for older pixi versions

---

## Recommendation

**Implement Option 1: Phase-Based Progress Model**

### Rationale

1. **Works immediately** - No dependency on upstream changes
2. **Provides value** - Meaningful progress for GUI applications (progress bars, status text)
3. **Reliable** - Phase transitions are stable patterns in tool output
4. **Simple** - Easy to implement and maintain
5. **Sufficient** - Meets the primary use case of showing users that work is happening

### Implementation Plan

1. **Phase 1:** Add `BuildProgress` class and `subscribe()` method to `Builder` interface
2. **Phase 2:** Implement stderr parsing in `PixiBuilder` to detect phases
3. **Phase 3:** Implement stderr parsing in `MambaBuilder` to detect phases
4. **Phase 4:** Add tests for phase detection
5. **Phase 5:** Update documentation and examples

### Future Work

- If pixi adds `--json` to install commands, we can enhance to use it while maintaining backward compatibility
- Could parse `indicatif` progress bar ANSI codes for more granular progress (low priority)
- Could use micromamba's JSON output to show package counts/sizes post-completion

---

## Appendix: Tool Comparison Matrix

| Feature | Pixi | Micromamba |
|---------|------|------------|
| JSON output for info | ✅ `--json` | ✅ `--json` |
| JSON output for install | ❌ | ✅ `--json` (summary only) |
| Real-time progress events | ❌ | ❌ |
| Progress bars | ✅ `indicatif` | ✅ (basic) |
| Structured logging | ✅ `RUST_LOG` | ⚠️ (limited) |
| Download progress | ✅ (bars only) | ❌ |
| Phase information | ✅ (implicit) | ⚠️ (in stderr) |
| Package-by-package progress | ❌ | ❌ |

---

## References

- Pixi source code: `pixi/crates/pixi_reporters/`, `pixi/crates/pixi_progress/`
- Micromamba issues: [#1181](https://github.com/mamba-org/mamba/issues/1181), [#1812](https://github.com/mamba-org/mamba/issues/1812), [#1813](https://github.com/mamba-org/mamba/issues/1813)
- Indicatif library: https://github.com/console-rs/indicatif
