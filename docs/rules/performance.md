# Performance rules

These rules replace source patterns with forms that can reduce allocations, repeated work, or avoidable branching. They must only run when the rewrite is behaviorally safe.

## Planned rules

| Rule id | Rewrite |
| --- | --- |
| `fields.single-use` | Inline a field used only once when safe. |
| `loops.break` | Simplify a loop that can terminate through `break`. |
| `classes.static-inner` | Make an inner class static when it does not need an enclosing instance. |
| `strings.string-builder` | Use `StringBuilder` for eligible concatenation patterns. |
| `strings.plain-replacement` | Use plain replacement APIs when a regular expression is not needed. |
| `strings.is-blank` | Use `String.isBlank` for eligible blank checks. Implemented for exact simple-name `trim().isEmpty()` calls. |
| `operators.lazy-logical` | Use short-circuit logical operators when equivalent. |
| `boxing.value-of` | Use `valueOf` instead of wrapper construction where supported. |
| `boxing.primitive-comparison` | Compare primitive values without unnecessary wrapper operations. |
| `parsing.primitive` | Use primitive parsing APIs where the result is not boxed. |
| `serialization.primitive` | Use primitive serialization forms where equivalent. |
| `boxing.primitive-rather-than-wrapper` | Prefer primitive types where wrapper identity and nullability are not required. |
| `regular-expressions.precompile` | Precompile a regular expression reused in one scope. |
| `strings.buffer-to-builder` | Replace `StringBuffer` with `StringBuilder` when synchronization is not required. |
| `strings.no-string-creation` | Avoid unnecessary intermediate `String` creation. |
| `booleans.literal` | Simplify boolean literal expressions. Implemented. |

## Safety

Performance transformations can affect synchronization, allocation timing, evaluation order, or observable identity. The implementation must document and test each rule's preconditions before enabling it.

## Eclipse reference

The option grouping follows the current Eclipse JDT [PerformanceTabPage](https://github.com/eclipse-jdt/eclipse.jdt.ui/blob/master/org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/preferences/cleanup/PerformanceTabPage.java).
