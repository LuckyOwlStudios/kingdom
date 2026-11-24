# Kingdoms

A Minecraft mod that adds the ability to build your own kingdom.

- Minecraft **1.21.1**
- Loaders: **Fabric** and **NeoForge** (multiloader, based on the
  [MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template) layout)
- Java **21**

## Project layout

| Module      | Purpose                                                                 |
|-------------|-------------------------------------------------------------------------|
| `common`    | Loader-agnostic code and resources, compiled against vanilla (NeoForm). |
| `fabric`    | Fabric entry point and platform implementations (Loom).                 |
| `neoforge`  | NeoForge entry point and platform implementations (ModDevGradle).       |
| `buildSrc`  | Shared Gradle convention plugins (`multiloader-common`, `multiloader-loader`). |

Common code reaches loader-specific features through service interfaces in
`common/.../platform/services`, implemented per loader and registered under
`META-INF/services`.

Mod metadata (id, name, versions) lives in `gradle.properties`.

## Building and running

```sh
./gradlew build                 # builds jars into fabric/build/libs and neoforge/build/libs
./gradlew :fabric:runClient     # Fabric dev client
./gradlew :neoforge:runClient   # NeoForge dev client
```

In IntelliJ IDEA, import the root `build.gradle` as a Gradle project; run configurations
for both loaders are generated on sync.
