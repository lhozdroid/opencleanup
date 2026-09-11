# Java feature rules

These rules modernize Java code by using language features or library APIs available at a specified Java release.

## Planned rules

| Rule id | Rewrite | Java level / options |
| --- | --- | --- |
| `modules.use-module-imports` | Use module-aware imports where applicable. | Java 25+ |
| `instanceof.pattern-matching` | Use pattern variables with `instanceof`. | Implemented for matching first declarations; Java 16+ |
| `instanceof.to-switch` | Convert eligible pattern checks to `switch`. | Java 21+ |
| `switch.expressions` | Convert eligible switch statements to switch expressions. | Java 14+ |
| `variable-declarations.var` | Use `var` for eligible local declarations. | Java 10+ |
| `functional-interfaces.convert` | Convert between anonymous functional implementations and lambdas. | `lambda` or `anonymous` |
| `functional-interfaces.simplify-lambda` | Simplify eligible lambda expressions. | `true` or `false` |
| `comparators.criteria` | Use comparator construction based on comparison criteria. | Java 8+ |
| `strings.join` | Use `String.join` for eligible concatenation patterns. | Java 8+ |
| `try-with-resources` | Convert eligible resource cleanup to try-with-resources. | Java 7+ |
| `multi-catch` | Combine catch clauses with the same body. | Java 7+ |
| `type-parameters.remove-redundant` | Remove redundant type arguments. | Java 7+ |
| `hash.modernize` | Use modern hash construction APIs where equivalent. | Java 7+ |
| `objects.equals` | Use `Objects.equals` for null-safe equality. | Java 7+ |
| `system-properties.constants` | Replace supported system-property lookups with constants. | Java 7+ |
| `loops.enhanced-for` | Convert eligible indexed loops to enhanced `for` loops. | Java 5+ |
| `boxing.autoboxing` | Use autoboxing where equivalent. | Java 5+ |
| `boxing.unboxing` | Use unboxing where equivalent. | Java 5+ |

## Safety

These transformations are language-level dependent. The plugin must read the project's effective Java source level and skip a rule when its target feature is unavailable. Rules that alter evaluation order or exception behavior need especially conservative preconditions.

## Eclipse reference

The option grouping follows the current Eclipse JDT [JavaFeatureTabPage](https://github.com/eclipse-jdt/eclipse.jdt.ui/blob/master/org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/preferences/cleanup/JavaFeatureTabPage.java).
