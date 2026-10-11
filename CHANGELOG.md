# Changelog

All notable changes to OpenCleanup are documented in this file.

## [1.1.1] - 2026-10-11

### Fixed

- Parse and format source at Java 21 source level, so `record`s are handled correctly.
  Previously the default 1.8 level dropped records from the AST, which caused
  unused-record-header imports to be deleted and record formatting to be mangled.
- `variable-declarations.final` is now idempotent; it no longer re-adds `final` on
  each run.
- `format.source` no longer corrupts text blocks. The formatter may re-indent
  text-block interiors, but the runtime string value is unchanged.
