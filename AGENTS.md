# AGENTS.md

> **Self-update rule:** When you make changes to project structure, conventions, dependencies, API patterns, or workflows described in this file, update this file to reflect those changes as part of the same commit.

## Overview

Kotlin shared library monorepo (`tiltakspenger-libs`) for NAV's "tiltakspenger" (employment scheme benefits) services. Published to GitHub Packages. Consumed by multiple downstream services (e.g. `tiltakspenger-saksbehandling-api`, `tiltakspenger-soknad-api`, `tiltakspenger-meldekort-api`, `tiltakspenger-datadeling`, `tiltakspenger-tiltak`).

## Architecture

- **Gradle submodules** — see `settings.gradle.kts` for the full list. Each is a focused library (IDs, DTOs, clients, utilities).
- **Convention plugin**: Shared build logic lives in `buildSrc/src/main/kotlin/tiltakspenger-lib-conventions.gradle.kts`. All submodules apply it via `plugins { id("tiltakspenger-lib-conventions") }`. It configures Kotlin/JVM target, Spotless with a pinned ktlint version from `gradle/libs.versions.toml`, JUnit 5, publishing, and JUnit 4 exclusion. Plugin and library versions are centralized in `gradle/libs.versions.toml`. Individual modules can opt into JetBrains Kover for coverage reporting (currently `jobber` and `arenatiltak-dtos`).
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
| `httpklient`    | Shared Java `HttpClient` wrapper with `HttpKlient`, Arrow `Either` errors, JSON String/DTO helpers, optional bearer-token support via `AccessToken`, opt-in retry based on Arrow Resilience `Schedule` (idempotent-method default predicate, per-attempt timing in metadata, excessive-retry hook), and opt-in circuit breaker based on Arrow Resilience `CircuitBreaker` |
| `test-common`   | Shared test infra: `fixedClock`, `TikkendeKlokke`, `getOrFail()` for Arrow's Either, `HttpKlientFake`, wiremock helpers                     |
| `texas`         | NAIS Texas auth: token introspection, system tokens, Ktor auth provider                                                                   |
| `ktor-common`   | Ktor server extensions (error responses, request parsing). Uses `compileOnly` for ktor deps                                               |
| `jobber`        | Leader election + stoppable job abstractions for NAIS                                                                                     |
| `*-dtos`        | API contract types shared between services                                                                                                |

## Conventions

### `httpklient` structure
The public client contract is `HttpKlient`. Consumers create a client via the
`HttpKlient(clock = ...) { ... }` factory, which configures and returns the internal
`JavaHttpKlient` (`java.net.http.HttpClient` based) implementation. The implementation type is
intentionally `internal` so callers depend only on the interface. All defaults — connect/timeout,
success-status predicate, logging, retry, circuit breaker, auth-token provider — are set via the
`HttpKlient.HttpKlientConfig` DSL passed to the factory; per-request overrides on `RequestBuilder`
take precedence. Tests outside the `httpklient` module should generally use `HttpKlientFake` from
`test-common`; tests inside `httpklient` should exercise the real implementation with WireMock/raw
sockets where practical.
Retry-related types keep package `no.nav.tiltakspenger.libs.httpklient` for API stability, but
source files live under `httpklient/src/main/kotlin/httpklient/retry/` to keep retry execution
close to `RetryConfig` and away from `JavaHttpKlient`. Circuit breaker-related types follow the
same package-stability rule and live under `httpklient/src/main/kotlin/httpklient/circuitbreaker/`.
`CircuitBreakerConfig.None` is the default; enabled configs are opt-in, fluent, named explicitly,
backed by Arrow Resilience `CircuitBreaker`, and state is local to each `HttpKlient` instance
per circuit breaker name. Circuit breaker protection wraps the whole retry execution, so only the
final result after retries is recorded.

### Error Handling
Use Arrow's `Either<ErrorType, SuccessType>` instead of exceptions. Error types are sealed interfaces with descriptive data objects/classes (see `FellesPersonklientError` for the pattern). In tests, use `getOrFail()` from `test-common` to unwrap.

### Typed IDs
IDs follow a strict pattern: private constructor, `Ulid` delegation via `UlidBase`, prefixed strings. Factory methods: `random()`, `fromString()`, `fromUUID()`. Use `init`/`require` blocks for invariant enforcement. See existing IDs in `common/src/main/kotlin/` for the canonical pattern and prefix conventions.

### Imports
Never use star imports. Always use explicit imports.

### Clocks
Use `java.time.Clock` (not Kotlin's). Never call `Instant.now()` or `nå()` without a `Clock` parameter — use `Instant.now(clock)` / `nå(clock)`. In tests, use `fixedClock` or `TikkendeKlokke` from `test-common`. Production code should accept `Clock` as a constructor/function parameter.

### JSON
Use the shared `objectMapper` from the `json` module and its `serialize()`/`deserialize()` helpers. Do not create custom ObjectMapper instances.

### Logging
Use `Sikkerlogg` from the `logging` module for sensitive data. Standard logging uses `kotlin-logging` (`io.github.oshai`).

### Style
- Functional style, immutability preferred. No mutable state where avoidable.
- Domain-driven design: logic belongs on the domain model closest to the data.
- `init` blocks enforce domain invariants (see `Periode`, typed IDs).
- No `Optional` or Arrow's `Option` — use nullable types or `Either`.

### No default parameter values in domain types or public APIs
Default values belong in **config/builder objects** (e.g. `HttpKlientLoggingConfig`, `RetryConfig`),
**not** in data carriers, domain models, or constructor parameters whose values describe what
actually happened or who the caller is. Concretely:

- **Data records that describe an event/result** (e.g. `HttpKlientMetadata` — request/response,
  attempt count, timings) must require all fields explicitly. Defaults like `attempts = 1` or
  `attemptDurations = emptyList()` mask bugs where the producer forgot to populate the field.
- **`Clock` parameters** must be required in production code. Never default to `Clock.systemUTC()`
  in `main/`. Tests may generally default to `fixedClock` or `TikkendeKlokke` from `test-common` — almost never to `Clock.systemUTC()`.
- **Other "ambient" services** (loggers, ID generators, random sources, etc.) follow the same rule:
  required in production, sensible test default in `test-common`.
- **Test helpers** that fabricate domain values (e.g. `tomMetadata()` in the `httpklient` tests)
  must fill every field explicitly so the test surface is greppable when the type changes.

If you find yourself adding a default to make broken/missing call sites compile, **fix the call
sites instead** — the default is hiding the problem.

## Build & Test

```bash
./lint_and_build.sh                  # lint + build + test (preferred)
./clean_lint_and_build.sh            # clean + lint + build + test
./gradlew :<module>:test             # test single module
./gradlew :jobber:koverXmlReport     # generate coverage report for jobber
./gradlew :arenatiltak-dtos:koverXmlReport # generate coverage report for arenatiltak-dtos
```

Note: `spotlessApply` is run with `--no-parallel --max-workers=1` from the helper scripts because Spotless 8.x + ktlint occasionally throws a flaky `InvocationTargetException` when multiple `spotlessKotlin` tasks initialise ktlint in parallel. Prefer the helper scripts over `./gradlew clean spotlessApply build`.

- Shared build config lives in `buildSrc/src/main/kotlin/tiltakspenger-lib-conventions.gradle.kts`. Check it for Kotlin/JVM versions, spotless config, and compiler flags.
- JUnit 4 is excluded globally (in the convention plugin). Test lifecycle is `per_class`.
- Kotest is used for assertions (`shouldBe`, `shouldThrowWithMessage`), not as test runner. Do not use JUnit assertions (`assertEquals`, `assertTrue`, etc.).
- Configuration cache is enabled. Avoid `System.getenv()` in build scripts; use `providers.environmentVariable()` instead.

## Dependencies

Minimize external dependencies. Scope test/compile-only deps where possible (`compileOnly` for ktor in `ktor-common`). See `gradle/libs.versions.toml` for the version catalog and allowed libraries.
