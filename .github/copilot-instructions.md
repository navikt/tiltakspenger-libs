# Guidelines

- Minimize external dependencies. Scope test/compile-only deps accordingly.
- Use internal `json` and `logging` libraries, not third-party equivalents.
- Modules may split into `domene` (no external deps) and `infrastruktur` (external deps allowed).
- Arrow is accepted everywhere. Most libs depend on `common` and `test-common`; and several on `logging` and `json`.
