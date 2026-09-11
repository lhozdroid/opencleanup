# Unnecessary-code rules

These rules remove redundant code or simplify expressions without requiring a new Java language feature.

## Planned rules

| Rule id | Rewrite |
| --- | --- |
| `unused-code.imports` | Remove unused single-type and single-static imports; wildcard imports are retained conservatively. |
| `unused-code.private-members` | Remove unused private types, constructors, fields, methods, or method parameters. |
| `unused-code.suppress-warnings` | Remove unnecessary `@SuppressWarnings` tokens. |
| `casts.unnecessary` | Remove unnecessary casts. |
| `strings.redundant-substring-argument` | Remove a redundant `substring` argument. |
| `arrays.fill` | Use `Arrays.fill` where equivalent. |
| `null-checks.evaluate-nullable` | Simplify an expression whose null check is unnecessary. |
| `negation.push-down` | Push negation into an expression. Implemented conservatively. |
| `booleans.value-rather-than-comparison` | Use a boolean value instead of comparing it with `true` or `false`. |
| `booleans.double-negation` | Remove double negation. |
| `statements.redundant-comparison` | Remove a comparison statement with no effect. |
| `constructors.redundant-super` | Remove a redundant `super()` call. |
| `blocks.unreachable` | Remove unreachable blocks. |
| `collections.direct-map-method` | Call a map method directly when an intermediate check is redundant. |
| `collections.clone` | Simplify eligible collection cloning. |
| `maps.clone` | Simplify eligible map cloning. |
| `assignments.overridden` | Remove an assignment that is overwritten before use. |
| `modifiers.redundant` | Remove redundant modifiers. |
| `if.embedded` | Restructure an embedded `if` when equivalent. |
| `semicolons.redundant` | Remove redundant semicolons. Implemented for standalone block statements. |
| `comparators.redundant` | Remove an unnecessary comparator. |
| `arrays.creation` | Remove unnecessary array creation. |
| `arrays.initializer` | Use an array initializer where possible. Implemented for local declarations. |
| `returns.expression` | Simplify a return expression. |
| `returns.useless` | Remove a useless return. |
| `continues.useless` | Remove a useless continue. |
| `loops.unlooped-while` | Simplify a `while` loop that executes at most once. |

Removing unused members is potentially destructive. It must be independently configurable for each member kind and should be disabled by default until symbol-usage analysis is reliable.

## Eclipse reference

The option grouping follows the current Eclipse JDT [UnnecessaryCodeTabPage](https://github.com/eclipse-jdt/eclipse.jdt.ui/blob/master/org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/preferences/cleanup/UnnecessaryCodeTabPage.java).
