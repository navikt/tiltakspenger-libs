# AGENTS.md — tiltakspenger-libs

This repo follows the monorepo conventions in [`../AGENTS.md`](../AGENTS.md) and the Kotlin/JVM backend conventions in [`../AGENTS-backend.md`](../AGENTS-backend.md). Read those first.

> **Self-update rule:** When you make changes to project structure, conventions, dependencies, API patterns, or workflows described in this file, update this file to reflect those changes as part of the same commit.

## Overview

Kotlin shared library monorepo. Published to GitHub Packages. **Not deployed to NAIS.** Consumed by `tiltakspenger-saksbehandling-api`, `tiltakspenger-soknad-api`, `tiltakspenger-meldekort-api`, `tiltakspenger-datadeling`, `tiltakspenger-tiltak`, and others.

## Architecture

- **Gradle submodules** — see `settings.gradle.kts`. Each submodule is a focused library (IDs, DTOs, clients, utilities).
- **Convention plugin**: Shared build logic lives in `buildSrc/src/main/kotlin/tiltakspenger-lib-conventions.gradle.kts`. All submodules apply it via `plugins { id("tiltakspenger-lib-conventions") }`. It configures Kotlin/JVM target, Spotless with a pinned ktlint version from `gradle/libs.versions.toml`, JUnit 5, publishing, and JUnit 4 exclusion. Plugin and library versions are centralized in `gradle/libs.versions.toml`. Individual modules can opt into JetBrains Kover for coverage (currently `jobber` and `arenatiltak-dtos`).
- **Source layout**: standard Kotlin/Gradle layout. Per [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html#directory-structure), the common root package `no.nav.tiltakspenger.libs` is omitted from the directory structure (e.g. `common/src/main/kotlin/common/SakId.kt` for package `no.nav.tiltakspenger.libs.common`).
- **Domene/infrastruktur split**: Modules with external dependencies (HTTP clients, DB) split into `*-domene` (pure domain, no external deps) and `*-infrastruktur` (external deps allowed). See `personklient/` and `persistering/`. Parent aggregator projects (`persistering/build.gradle.kts`, `personklient/build.gradle.kts`) only disable jar tasks.
- **Core dependency chain**: most modules depend on `common` → `logging`. Tests depend on `test-common`, which re-exports `common`, kotest, mockk, wiremock, and JUnit 5.

## Key Modules

| Module          | Purpose                                                                                                                                   |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `common`        | Shared domain types: typed IDs (`SakId`, `BehandlingId`, `MeldekortId`, `SøknadId`), `Bruker`/`Saksbehandler`, `CorrelationId`, ULID base |
| `periodisering` | Date period logic (`Periode`, `Periodisering`, `Tidslinje`)                                                                               |
| `json`          | Shared Jackson `objectMapper` + `serialize()`/`deserialize()` helpers                                                                     |
| `logging`       | `Sikkerlogg` for NAIS secure logging via markers                                                                                          |
| `test-common`   | Shared test infra: `fixedClock`, `TikkendeKlokke`, `getOrFail()` for `Either`, wiremock helpers                                           |
| `texas`         | NAIS Texas auth: token introspection, system tokens, Ktor auth provider                                                                   |
| `ktor-common`   | Ktor server extensions (uses `compileOnly` for ktor deps)                                                                                 |
| `jobber`        | Leader election + stoppable job abstractions for NAIS                                                                                     |
| `*-dtos`        | API contract types shared between services                                                                                                |

## Build & Test (libs-specific)

```bash
./lint_and_build.sh                          # lint + build + test (preferred)
./clean_lint_and_build.sh                    # clean + lint + build + test
./gradlew :<module>:test                     # test a single module
./gradlew :jobber:koverXmlReport             # coverage report for jobber
./gradlew :arenatiltak-dtos:koverXmlReport   # coverage report for arenatiltak-dtos
```

- `spotlessApply` is run with `--no-parallel --max-workers=1` from the helper scripts, because Spotless + ktlint can throw a flaky `InvocationTargetException` when multiple `spotlessKotlin` tasks initialise ktlint in parallel. **Prefer the helper scripts** over `./gradlew clean spotlessApply build`.
- Configuration cache is enabled. Avoid `System.getenv()` in build scripts — use `providers.environmentVariable()` instead.
- Shared build config (Kotlin/JVM version, spotless config, compiler flags, JUnit 4 exclusion, `per_class` test lifecycle) lives in `buildSrc/src/main/kotlin/tiltakspenger-lib-conventions.gradle.kts` — check it before changing build behaviour in individual modules.

## Dependencies

Minimize external dependencies. Scope test/compile-only deps where possible (`compileOnly` for ktor in `ktor-common`). See `gradle/libs.versions.toml` for the version catalog and allowed libraries.

