# Compose Gallery

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Compose Gallery is an Android Studio / IntelliJ IDEA plugin and Gradle toolkit for turning selected
Jetpack Compose `@Preview` composables into a searchable, browsable visual catalog.

The goal is simple: **make it easier to find and understand the UI components that already exist in
a project.**

In a large Android codebase, finding a particular component can be surprisingly difficult. You may
know what a component looks like, but not its name, module, package, or source file. Compose Gallery
provides a visual catalog where you can search and browse those components directly inside the IDE.

There is no emulator, device, or running application involved. Previews are rendered to images using
**Layoutlib**, the same rendering engine used by Android Studio's Compose Preview tooling.

> 🚧 **Compose Gallery is currently a work in progress.**
>
> There is no published release yet. The project is still being actively developed and the API,
> Gradle tasks, generated data, and IDE UI may change.

![Compose Gallery](assets/gallery-compose.png)

## Why Compose Gallery?

When working on a large application, UI components are often spread across many modules and
packages. Finding an existing component can mean searching through source code, remembering its
name, or opening the application to see what it looks like.

Compose Gallery is intended to make that process more visual:

- Browse components without running the application.
- Search previews by name, group, or module.
- See the actual rendered appearance of components.
- Navigate from a preview back to its source.
- Keep the catalog inside Android Studio / IntelliJ IDEA.
- Work across multiple Gradle modules.

The idea is inspired by projects such as [Airbnb's Showkase](https://github.com/airbnb/showkase),
but the approach is different.

Showkase generates an in-app catalog that you access by running the application. Compose Gallery
instead generates static preview images using Layoutlib and displays them through an IDE plugin.

The screenshot rendering pipeline is also inspired
by [skydoves/compose-nav-graph](https://github.com/skydoves/compose-nav-graph), which provided a
useful reference for rendering Compose UI outside of a running application.

## How it works

Compose Gallery is built around a small pipeline:

1. `@Gallery` identifies the Compose previews that should appear in the catalog.
2. KSP discovers those previews and generates metadata.
3. The Gradle plugin prepares the Layoutlib environment and renders the previews.
4. Each module produces its own gallery manifest and rendered PNGs.
5. The root project aggregates the results from the participating modules.
6. The IntelliJ / Android Studio plugin reads the aggregated gallery and displays the previews in a
   searchable tool window.

This allows the gallery to represent components from multiple modules while keeping each module's
generated images isolated to avoid filename collisions.

## Compose Gallery Plugin

The toolkit currently consists of:

- **Annotations** — `com.dfcruz.compose.gallery.annotation`
    - Provides `@Gallery`, which marks a `@Preview` composable for inclusion.
- **KSP processor** — `com.dfcruz.compose.gallery.ksp`
    - Discovers annotated previews and generates preview metadata.
- **Gradle plugin** — `com.dfcruz.compose.gallery`
    - Prepares Layoutlib, renders previews, and aggregates results.
- **IntelliJ / Android Studio plugin**
    - Displays the generated previews in a searchable gallery tool window.

## Annotate your previews

Add `@Gallery` to the `@Preview` composables you want to include:

```kotlin
@Gallery(
    name = "Primary Button",
    group = "Buttons",
    tags = ["primary", "filled"]
)
@Preview
@Composable
private fun PrimaryButtonPreview() {
    MaterialTheme {
        PrimaryButton(
            text = "Continue",
            onClick = {},
        )
    }
}
```

### `@Gallery` options

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Gallery(
    val name: String = "",
    val group: String = "",
    val tags: Array<String> = [],
)
```

- **`name`** — display name shown in the gallery. If omitted, the preview function name is used.
- **`group`** — organizes previews into groups such as `Buttons`, `Cards`, or `Inputs`.
- **`tags`** — free-form labels associated with the preview.

## Setup

Compose Gallery is not published yet, so the current setup uses the project modules directly.

The module containing the previews needs the Gallery Gradle plugin, the annotations module, and the
KSP processor.

### 1. Apply the plugin

In the module's `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.ksp)
    id("com.dfcruz.compose.gallery")
}
```

### 2. Add the Gallery dependencies

```kotlin
dependencies {
    implementation(project(":compose-gallery-annotations"))
    ksp(project(":compose-gallery-ksp"))

    // Your existing Android / Compose dependencies...
}
```

The important additions are:

```kotlin
implementation(project(":compose-gallery-annotations"))
ksp(project(":compose-gallery-ksp"))
```

The module must also have the normal Android, Kotlin Compose, and KSP configuration required by the
project.

## Gradle tasks

Applying the Gallery Gradle plugin exposes tasks at both the module and root project levels.

### Per-module tasks

For a module such as `:app`:

```text
:app:prepareGalleryLayoutlib
:app:renderGalleryLayoutlib
:app:generateGallery
```

### `prepareGalleryLayoutlib`

Prepares the Layoutlib runtime used to render the Compose previews.

Generated files are stored under:

```text
build/preview-layoutlib/
```

### `renderGalleryLayoutlib`

Renders the discovered previews and generates the module gallery data:

```text
build/gallery/
├── gallery-module.json
└── previews/
    └── ...
```

Each preview variant records whether rendering succeeded or failed:

```text
SUCCESS
FAILED
```

### `generateGallery`

A convenience task that runs the module's gallery generation pipeline.

For example:

```bash
./gradlew :app:generateGallery
```

This generates the gallery for that module.

## Root project tasks

The root project also provides:

```text
:aggregateGallery
:generateGallery
```

### `aggregateGallery`

Collects the generated gallery data and rendered images from all modules that have the Gallery
plugin applied.

The aggregated output is stored in the root project's:

```text
build/gallery/
├── gallery.json
└── previews/
    ├── moduleA/
    │   └── ...
    └── moduleB/
        └── ...
```

Images are placed under a module-specific directory so previews from different modules cannot
overwrite each other when they contain the same preview or image path.

The aggregated `gallery.json` groups previews by module, for example:

```json
{
  "modules": [
    {
      "module": ":sample",
      "previews": [
        {
          "name": "Primary Button",
          "group": "Buttons"
        }
      ]
    }
  ]
}
```

### `generateGallery`

The root `generateGallery` task is the main entry point currently used by the IDE plugin to generate
the complete gallery.

> The root task pipeline is still evolving while the project is under development.

## IntelliJ / Android Studio plugin

The IDE plugin provides the visual interface for the generated gallery.

It currently supports:

- Browsing previews from multiple modules.
- Grouping previews by module.
- Grouping previews by preview group.
- Searching by preview name, group, or module.
- Filtering by module.
- Filtering by group.
- Zooming previews.
- Opening a larger preview.
- Navigating from a preview back to its source function.
- Regenerating previews through the Gradle task.

The intended workflow is:

```text
Write @Preview
     ↓
Add @Gallery
     ↓
Run gallery generation
     ↓
Layoutlib renders PNG
     ↓
Gallery metadata is aggregated
     ↓
Open Compose Gallery in the IDE
     ↓
Search / browse / inspect components
```

## Project structure

The project is currently split into several modules:

```text
compose-gallery/
├── compose-gallery-annotations/
├── compose-gallery-protocol/
├── compose-gallery-ksp/
├── compose-gallery-gradle/
├── compose-gallery-intellij/
└── sample/
```

The modules have separate responsibilities so the preview discovery, rendering pipeline, shared data
model, Gradle integration, and IDE presentation can evolve independently.

## Inspiration

Compose Gallery takes inspiration from two different projects for different parts of the problem.

### Airbnb Showkase

[Showkase](https://github.com/airbnb/showkase) demonstrates the value of automatically collecting UI
components and making them browsable.

Compose Gallery shares that goal, but uses a different presentation model. Instead of generating an
in-app catalog, Compose Gallery aims to make the catalog available directly inside the IDE.

### skydoves/compose-nav-graph

[compose-nav-graph](https://github.com/skydoves/compose-nav-graph) was an important reference for
the screenshot-rendering side of the project.

In particular, the project helped demonstrate how Layoutlib can be used to render Compose UI without
launching an application on a device or emulator.

## Status

🚧 **Work in progress**

Compose Gallery is currently an experimental project and **does not have a published release yet**.

The project is being actively developed, and the following areas are expected to evolve:

- Gradle task configuration.
- Generated gallery metadata.
- Preview rendering.
- IDE plugin UI.
- Search and filtering.
- Plugin packaging and distribution.
- Public API and annotations.

For now, the project is primarily intended for experimentation, development, and exploring the
architecture behind an IDE-based Compose component catalog.

## License

```text
Copyright 2026 dfcruz

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

This project includes portions of code adapted
from [skydoves/compose-nav-graph](https://github.com/skydoves/compose-nav-graph/) (also Apache 2.0).
See [`NOTICE`](./NOTICE) for attribution.
