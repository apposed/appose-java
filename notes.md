## Next steps

1. Port recent appose-java improvements to appose-python.
2. Release appose-python 0.3.0.
3. Update https://github.com/imglib/imglib2-appose to work with appose 0.2.0+.

## Later

1. Implement build handlers for maven and openjdk.
2. Implement a build handler built on pixi, to replace the micromamba one.
3. Implement pixi, maven, and openjdk build handlers in appose-python, too.
4. Once it all works: release 1.0.0.

## Builder API

* Want an API to create an environment from an envFile: i.e. `pixi.toml` or `environment.yml`.
* Want an API to build up an environment piecemeal: adding dependencies one by one.
* Do we need an API to mix and match these two things? I.e. start from envFile but then add on?
  - An argument for this: what if you want to mix in Java JARs? pixi.toml can't do that yet.
* Want an API to create an environment from a *string* representation of an envFile.

Most flexible to put all these things into the Builder, not only directly in Appose (like `system()`).

What sorts of dependencies do we want to support adding?

1. conda-forge packages
2. PyPI packages
3. Maven coords
4. Java itself

Pixi gets us (1) and (2).

* Maven coords can be gotten by Groovy Grape in Java, by jgo in Python. (jgo needs work)
* Java itself can be gotten by cjdk in Python; what about from Java? Port cjdk? Or hack it with openjdk conda for now?

Should we make the API more general than the above? Yes! We can use `ServiceLoader`, same as we do with `ShmFactory`.

The interface is `BuildHandler`:
* `boolean include(String content, String scheme)`
* `boolean channel(String name, String location)`

And the implementations + supported schemes are:
* `PixiHandler` -- `environment.yml`, `pixi.toml`, `pypi`, `conda`, and null.
* `MavenHandler` -- `maven`
* `JDKHandler` -- `openjdk`

Although the term "scheme" might be confused with URI scheme, other terms are problematic too:
* "platform" will be confused with OS/arch.
* "method" will be confused with functions of a class.
* "system" might be confused with the computer itself, and/or system environment, path, etc.
* "paradigm" sounds too pretentious.
* "repoType" is rather clunky.

The `Builder` then has its own `include` and `channel` methods that delegate to
all discovered `BuildHandler` plugins. The `Builder` can also have more
convenience methods:

* `Builder file(String filePath) { return file(new File(filePath)); }`
* `Builder file(String filePath, String scheme) { return file(new File(filePath), scheme); }`
* `Builder file(File file) { return file(file, file.getName()); }`
* `Builder file(File file, String scheme) { return include(readContentsAsString(file), scheme); }`

For the `file`-to-`include` trick to work with files like `requirements.txt`,
the handling of `conda`/null scheme should split the content string into lines,
and process them in a loop.

Here are some example API calls made possible by the above design:
```java
Appose.env()
    .file("/path/to/environment.yml")
    // OR: .file("/path/to/pixi.toml")
    // OR: .file("/path/to/requirements.txt", "pypi")
    .include("cowsay", "pypi")
    .include("openjdk>=17")  // Install OpenJDK from conda-forge!
    .include("maven")  // Install Maven from conda-forge... confusing, yeah?
    .include("conda-forge::maven")  // Specify channel explicitly with environment.yml syntax.
    .include("org.scijava:parsington", "maven")
    // OR: .include("org.scijava:parsington") i.e. infer `maven` from the colon?
    // OR: .include("org.scijava:parsington:2.0.0", "maven")
    .channel("scijava", "maven:https://maven.scijava.org/content/groups/public")
    .include("sc.fiji:fiji", "maven")
    .include("zulu:17", "openjdk")  // Install an OpenJDK from the Coursier index.

    .channel("bioconda")  // Add a conda channel
    .channel(name: str, location: str = None)
    .build()  // Whew!
```

### 2024-08-20 update

One tricky thing is the base directory when combining paradigms:
* Get rid of "base directory" naming (in conda, the "base" environment is something else, so it's a confusing word here) in favor of `${appose-cache-dir}/${env-name}` convention. By default, `appose-cache-dir` equals `~/.local/share/appose`, but we could provide a way to override it...
* Similarly, get rid of the `base(...)` builder method in favor of adding a `build(String envName) -> Environment` signature.
* But what to name `Environment#base` property now? I really like `base`... Maybe `basedir`? Or `prefix``?
* Each `BuildHandler` catalogs the `include` and `channel` calls it feels are relevant, but does not do anything until `build` is finally called.
* If `build()` is called with no args, then the build handlers are queried sequentially (`default String envName() { return null; }`?). The first non-null name that comes back is taken as truth and then `build(thatName)` is passed to all handlers. Otherwise, an exception is raised "No environment name given".
* Environments all live in `~/.local/share/appose/<name>`, where `<name>` is the name of the environment. If `name:` is given in a `pixi.toml` or `environment.yml`, great, the `PixiBuildHandler` can parse out that string when its `envName()` method is called.

What about starting child processes via `pixi run`? That's not agnostic of the builder...
* What if the build handlers are also involved in child process launches? The `service(exes, args)` method could delegate to them...
* `BuildHandler` &rarr; `EnvHandler`?
* In `Environment`, how about replacing `use_system_path` with just a `path` list of dirs to check for executables being launched by `service`? Then we could dispense with the boilerplate `python`, `bin/python` repetition.
* Each env handler gets a chance to influence the worker launch args... and/or bin dirs...
  - The pixi handler could prepend `.../pixi run` when a `pixi.toml` is present.
    But this is tricky, because it goes *before* the selected exe... pre-args vs post-args uhhh

So, environment has:
* path (list of directories -- only used if `all_args[0]` is not already an absolute path to an executable program already?)
* launcher (list of string args to prepend)
* classpath (list of elements to include when running java)

* `Map<String, List<String>>` is what's returned by the `build(...)` step of each `BuildHandler`.
  - Relevant keys include: "path", "classpath", "launcher"
  - The `new Environment() { ... }` invocation will aggregate the values given here into its accessors.

* When running a service, it first uses the path to search for the requested exe, before prepending the launcher args.
  - What about pixi + cjdk? We'll need the full path to java...
  - How can we tell the difference between that and pixi alone with openjdk from conda-forge?
  - In the former case, we need the full path, in the latter case, no.
  - Pixi should just put `${envDir}/bin` onto the path, no?
  - There is an edge case where `pixi run foo` works, even though `foo` is not an executable on the path... in which case, the environment service can just plow ahead with it when it can't find a `foo` executable on the path. But it should *first* make an attempt to reify the `foo` from the environment `path` before punting in that way.

#### pixi

During its build step, it prepends `[/path/to/pixi, run]` to the environment's launcher list, and `${env-dir}/bin` to the environment's path list.

#### cjdk

```
cjdk -j adoptium:21 java-home
/home/curtis/.cache/cjdk/v0/jdks/d217ee819493b9c56beed2e4d481e4c370de993d/jdk-21.0.4+7
/home/curtis/.cache/cjdk/v0/jdks/d217ee819493b9c56beed2e4d481e4c370de993d/jdk-21.0.4+7/bin/java -version
openjdk version "21.0.4" 2024-07-16 LTS
OpenJDK Runtime Environment Temurin-21.0.4+7 (build 21.0.4+7-LTS)
OpenJDK 64-Bit Server VM Temurin-21.0.4+7 (build 21.0.4+7-LTS, mixed mode, sharing)
```

So `JDKHandler`, during its build step, prepends the java-home directory to the environment's path: `$(cjdk -j adoptium:21 java-home)/bin`

#### Maven

No need to add any directories to the environment path!

However, if Maven artifacts are added via `includes`, they should not only be downloaded, but also be part of the class path when launching java-based programs. We could do that by putting classpath into the `Environment` class directly, along side `path`... it's only a little hacky ;_;

## Pixi

Is even better than micromamba. It's a great fit for Appose's requirements.

### Setup for Appose

```shell
# Install a copy of Pixi into Appose's workspace.
mkdir -p ~/.local/share/appose/tmp
cd ~/.local/share/appose/tmp
curl -fsLO https://github.com/prefix-dev/pixi/releases/download/v0.27.1/pixi-x86_64-unknown-linux-musl.tar.gz
mkdir -p ../.pixi/bin
cd ../.pixi/bin
tar xf ../tmp/pixi-x86_64-unknown-linux-musl.tar.gz
alias pixi=~/.local/share/appose/.pixi/bin/pixi
```

And/or consider setting `PIXI_HOME` to `$HOME/.local/share/appose`
when using the `$HOME/.local/share/appose/.pixi/bin/pixi` binary.
This would let us, in the future, tweak Pixi's Appose-wide configuration
by adding a `$HOME/.local/share/appose/.pixi/config.toml` file.

#### Create an Appose environment

```shell
mkdir -p ~/.local/share/appose/sc-fiji-spiff
pixi init ~/.local/share/appose/sc-fiji-spiff
```

#### Add channels to the project/environment

```shell
cd ~/.local/share/appose/sc-fiji-spiff
pixi project channel add bioconda pytorch
```

Doing them all in one command will have less overhead.

#### Add dependencies to the project/environment

```shell
cd ~/.local/share/appose/sc-fiji-spiff
pixi add python pip
pixi add --pypi cowsay
```

Doing them all in two commands (one for conda, one for pypi) will have less overhead.

#### Use it

```shell
pixi run python -c 'import cowsay; cowsay.cow("moo")'
```

One awesome thing is that Appose will be able to launch the
child process using `pixi run ...`, which takes care of running
activation scripts before launch&mdash;so the child program should
work as though run from an activated environment (or `pixi shell`).

### Bugs

#### Invalid environment names do not fail fast

```shell
pixi project environment add sc.fiji.spiff
pixi tree
```
Fails with: `Failed to parse environment name 'sc.fiji.spiff', please use only lowercase letters, numbers and dashes`

### Multiple environments in one pixi project?

I explored making a single Appose project and using pixi's multi-environment
support to manage Appose environments, all within that one project, as follows:

```shell
# Initialize the shared Appose project.
pixi init
pixi project description set "Appose: multi-language interprocess cooperation with shared memory."

# Create a new environment within the Appose project.
pixi project environment add sc-fiji-spiff
# Install dependencies into a feature with matching name.
pixi add --feature sc-fiji-spiff python pip
pixi add --feature sc-fiji-spiff --pypi cowsay
# No known command to link sc-fiji-spiff feature with sc-fiji-spiff project...
mv pixi.toml pixi.toml.old
sed 's/sc-fiji-spiff = \[\]/sc-fiji-spiff = ["sc-fiji-spiff"]/' pixi.toml.old > pixi.toml
# Finally, we can use the environment!
pixi run --environment sc-fiji-spiff python -c 'import cowsay; cowsay.cow("moo")'
```

This works, but a single `pixi.toml` file for all of Appose is probably too
fragile, whereas a separate project folder for each Appose environment should
be more robust, reducing the chance that one Appose-based project (e.g. JDLL)
might stomp on another Appose-based project (e.g. TrackMate) due to their usage
of the same `pixi.toml`.

So we'll just settle for pixi's standard behavior here: a single environment
named `default` per pixi project, with one pixi project per Appose environment.
Unfortunately, that means our environment directory structure will be:
```
~/.local/share/appose/sc-fiji-spiff/.pixi/envs/default
```
for an Appose environment named `sc-fiji-spiff`.
(Note that environment names cannot contain dots, only alphameric and dash.)

To attain a better structure, I tried creating a `~/.local/share/appose/sc-fiji-spiff/.pixi/config.toml` with contents:
```toml
detached-environments: "/home/curtis/.local/share/appose"
```

It works, but then the environment folder from above ends up being:
```
~/.local/share/appose/sc-fiji-spiff-<many-numbers>/envs/sc-fiji-spiff
```
Looks like pixi creates one folder under `envs` for each project, with a
numerical hash to reduce collisions between multiple projects with the same
name... and then still makes an `envs` folder for that project beneath it.
So there is no escape from pixi's directory convention of:
```
<project-folder>/envs/<env-name>
```
Which of these is the least annoying?
```
~/.local/share/appose/sc-fiji-spiff/.pixi/envs/default
~/.local/share/appose/sc-fiji-spiff-782634298734/envs/default
~/.local/share/appose/sc-fiji-spiff/envs/sc-fiji-spiff
~/.local/share/appose/sc-fiji-spiff-782634298734/envs/sc-fiji-spiff
```
The detached-environments approach is actually longer, and entails
additional configuration and more potential for confusion; the
shortest path ends up being the first one, which is pixi's standard
behavior anyway.

The only shorter one would be:
```
~/.local/share/appose/.pixi/envs/sc-fiji-spiff
```
if we opted to keep `~/.local/share/appose` as a single Pixi project
root with multiple environments... but the inconvenience and risks
around a single shared `pixi.toml`, and hassle of multi-environment
configuration, outweigh the benefit of slightly shorter paths.

With separate Pixi projects we can also let Appose users specify their own
`pixi.toml` (maybe a partial one?), directly. Or an `environment.yml` that gets
used via `pixi init --import`. Maybe someday even a `requirements.txt`, if the
request (https://github.com/prefix-dev/pixi/issues/1410) gets implemented.
