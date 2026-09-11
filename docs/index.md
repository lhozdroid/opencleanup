# OpenCleanup documentation

OpenCleanup is a planned Maven plugin for applying Java source rewrites during a Maven build. Its rule catalog follows the cleanup capabilities exposed by Eclipse JDT, while the selected rules and their options are configured in `pom.xml`.

The plugin is being implemented incrementally. The rule catalog pages distinguish implemented rules from planned rules.

## Documentation map

### Plugin configuration

- [Maven configuration](configuration.md) — proposed POM structure, rule selection, options, source scope, and safety expectations.

### Cleanup rule groups

Each page maps to one cleanup configuration section from Eclipse JDT. The individual entries in each page are the planned selectable rules.

- [Code style](rules/code-style.md)
- [Java features](rules/java-features.md)
- [Source fixing](rules/source-fixing.md)
- [Performance](rules/performance.md)
- [Member accesses](rules/member-accesses.md)
- [Unnecessary code](rules/unnecessary-code.md)
- [Missing code](rules/missing-code.md)
- [Code organizing](rules/code-organizing.md)
- [Duplicate code](rules/duplicate-code.md)

## Design principles

- A build must apply only the rules selected in the POM.
- Rule options must be explicit and reviewable in source control.
- Rewrites must preserve valid Java syntax and avoid changing behavior unless the selected rule intentionally requests a semantic modernization.
- Java-version-sensitive rules must be guarded by the project's configured source level.
- The plugin should report which files and rules changed so rewrites are visible in CI.

## Current implementation

- `unused-code.imports` removes unused single-type and single-static imports.
- `booleans.value-rather-than-comparison` simplifies comparisons with boolean literals.
- `booleans.double-negation` removes consecutive boolean negations.
- `constructors.redundant-super` removes explicit no-argument superclass calls.
- `returns.useless` removes a final empty return from a void method.
- `continues.useless` removes final unlabeled continues from while-style loops.
- `imports.organize` orders normal and static imports deterministically.
- `control-statements.else-if` joins an `else` block containing one `if` statement.
- `control-statements.simplify-boolean-if-else` simplifies opposite boolean returns.
- `booleans.literal` folds expressions made only from boolean literals.
- `comparisons.invert-equals` places the non-null side first in null comparisons.
- `negation.push-down` applies a conservative De Morgan rewrite.
- `arrays.initializer` removes redundant local array creation syntax.
- `semicolons.redundant` removes standalone empty statements from blocks.
- `if.embedded` combines a safe nested `if` into a short-circuit condition.
- `returns.expression` simplifies direct boolean conditional returns.
- `expressions.parentheses` removes parentheses around simple expressions in safe contexts.
- `control-statements.blocks` adds blocks around non-block control bodies.
- `instanceof.pattern-matching` converts a matching cast declaration to a pattern variable.
- `comparisons.standard` places numeric and character literals on the right of relational comparisons.
- `expressions.extract-increment` extracts direct increments from local declarations.
- `number-literals.suffix` normalizes lowercase numeric literal suffixes.
- `modifiers.redundant` removes syntax-guaranteed redundant interface modifiers.
- `strings.redundant-substring-argument` removes a redundant string length argument.
- `loops.enhanced-for` converts a conservative index loop to an enhanced `for` loop.
- `strings.is-blank` converts an exact `trim().isEmpty()` shape to `isBlank()`.
- Wildcard imports are retained conservatively.
- The `unnecessary-code`, `code-organizing`, `code-style`, `java-features`, `performance`, and `source-fixing` groups can enable implemented rules through their options.

## Eclipse reference

The rule groups are based on the [Eclipse JDT Clean Up preference page](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.user/reference/preferences/java/codestyle/ref-preferences-cleanup.htm) and the corresponding [JDT cleanup extension point](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/extension-points/org_eclipse_jdt_ui_cleanUps.html).
