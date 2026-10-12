# Changelog

All notable changes to OpenCleanup are documented in this file.

## [1.1.2] - 2026-10-12

### Fixed

- `control-statements.reduce-indentation` no longer hoists a then branch out
  of its block when the else branch can complete normally. The inverted
  two-branch rewrite now requires the else branch to be terminal, so the
  hoisted then branch cannot run on the else path. Previously it duplicated
  the then branch's statements in both cases (for example double-counting an
  accumulator) even though the source was written correctly.

## [1.1.1] - 2026-10-11

### Fixed

- Parse and format source at Java 21 source level, so `record`s are handled correctly.
  Previously the default 1.8 level dropped records from the AST, which caused
  unused-record-header imports to be deleted and record formatting to be mangled.
- `variable-declarations.final` is now idempotent; it no longer re-adds `final` on
  each run.
- `format.source` no longer corrupts text blocks. The formatter may re-indent
  text-block interiors, but the runtime string value is unchanged.
