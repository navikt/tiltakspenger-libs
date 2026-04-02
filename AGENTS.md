# AGENTS.md

> **Self-update rule:** When you make changes to project structure, conventions, dependencies, API patterns, or workflows described in this file, update this file to reflect those changes as part of the same commit.

## Overview

Kotlin shared library monorepo (`tiltakspenger-libs`) for NAV's "tiltakspenger" (employment scheme benefits) services. Published to GitHub Packages. Consumed by multiple downstream services (e.g. `tiltakspenger-saksbehandling-api`, `tiltakspenger-soknad-api`, `tiltakspenger-meldekort-api`, `tiltakspenger-datadeling`, `tiltakspenger-tiltak`).

## Architecture

- **Gradle submodules** — see `settings.gradle.kts` for the full list. Each is a focused library (IDs, DTOs, clients, utilities).
- **Convention plugin**: Shared build logic lives in `buildSrc/src/main/kotlin/tiltakspenger-lib-conventions.gradle.kts`. All submodules apply it via `plugins { id("tiltakspenger-lib-conventions") }`. It configures Kotlin/JVM target, spotless/ktlint, JUnit 5, publishing, and JUnit 4 exclusion. Plugin and library versions are centralized in `gradle/libs.versions.toml`.
- **Standard Kotlin/Gradle source layout**: sources live in `src/main/kotlin/` and `src/test/kotlin/` following Gradle conventions. Resources live in `src/main/resources/` and `src/test/resources/`. Per [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html#directory-structure), the common root package `no.nav.tiltakspenger.libs` is omitted from the directory structure (e.g., `common/src/main/kotlin/common/SakId.kt` for package `no.nav.tiltakspenger.libs.common`).
- **Domene/infrastruktur split**: Modules with external dependencies (HTTP clients, DB) split into `*-domene` (pure domain, no external deps) and `*-infrastruktur` (external deps allowed). See `personklient/` and `persistering/` for examples. Parent aggregator projects (`persistering/build.gradle.kts`, `personklient/build.gradle.kts`) only disable jar tasks.
- **Core dependency chain**: Most modules depend on `common` → `logging`. Tests depend on `test-common` which re-exports `common`, kotest, mockk, wiremock, and JUnit 5.

## Key Modules

| Module          | Purpose                                                                                                                                   |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `common`        | Shared domain types: typed IDs (`SakId`, `BehandlingId`, `MeldekortId`, `SøknadId`), `Bruker`/`Saksbehandler`, `CorrelationId`, ULID base |
| `periodisering` | Date period logic (`Periode`, `Periodisering`, `Tidslinje`), used heavily across the domain                                               |
| `json`          | Shared Jackson `objectMapper` config + serialize/deserialize helpers. Use this, not your own ObjectMapper                                 |
| `logging`       | `Sikkerlogg` object for NAIS secure logging via markers. Use this, not raw loggers for sensitive data                                     |
| `test-common`   | Shared test infra: `fixedClock`, `TikkendeKlokke`, `getOrFail()` for Arrow's Either, wiremock helpers                                     |
| `texas`         | NAIS Texas auth: token introspection, system tokens, Ktor auth provider                                                                   |
| `ktor-common`   | Ktor server extensions (error responses, request parsing). Uses `compileOnly` for ktor deps                                               |
| `jobber`        | Leader election + stoppable job abstractions for NAIS                                                                                     |
| `*-dtos`        | API contract types shared between services                                                                                                |

## Conventions

### Error Handling
Use Arrow's `Either<ErrorType, SuccessType>` instead of exceptions. Error types are sealed interfaces with descriptive data objects/classes (see `FellesPersonklientError` for the pattern). In tests, use `getOrFail()` from `test-common` to unwrap.

### Typed IDs
IDs follow a strict pattern: private constructor, `Ulid` delegation via `UlidBase`, prefixed strings. Factory methods: `random()`, `fromString()`, `fromUUID()`. Use `init`/`require` blocks for invariant enforcement. See existing IDs in `common/src/main/kotlin/` for the canonical pattern and prefix conventions.

### Imports
Never use star imports. Always use explicit imports.

### Clocks
Use `java.time.Clock` (not Kotlin's). In tests, use `fixedClock` or `TikkendeKlokke` from `test-common`.

### JSON
Use the shared `objectMapper` from the `json` module and its `serialize()`/`deserialize()` helpers. Do not create custom ObjectMapper instances.

### Logging
Use `Sikkerlogg` from the `logging` module for sensitive data. Standard logging uses `kotlin-logging` (`io.github.oshai`).

### Style
- Functional style, immutability preferred. No mutable state where avoidable.
- Domain-driven design: logic belongs on the domain model closest to the data.
- `init` blocks enforce domain invariants (see `Periode`, typed IDs).
- No `Optional` or Arrow's `Option` — use nullable types or `Either`.

## Build & Test

```bash
./gradlew spotlessApply build        # lint + build + test (preferred)
./gradlew clean spotlessApply build  # clean build
./gradlew :<module>:test             # test single module
```

- Shared build config lives in `buildSrc/src/main/kotlin/tiltakspenger-lib-conventions.gradle.kts`. Check it for Kotlin/JVM versions, spotless config, and compiler flags.
- JUnit 4 is excluded globally (in the convention plugin). Test lifecycle is `per_class`.
- Kotest is used for assertions (`shouldBe`, `shouldThrowWithMessage`), not as test runner. Do not use JUnit assertions (`assertEquals`, `assertTrue`, etc.).
- Configuration cache is enabled. Avoid `System.getenv()` in build scripts; use `providers.environmentVariable()` instead.

## Dependencies

Minimize external dependencies. Scope test/compile-only deps where possible (`compileOnly` for ktor in `ktor-common`). See `gradle/libs.versions.toml` for the version catalog and allowed libraries.
