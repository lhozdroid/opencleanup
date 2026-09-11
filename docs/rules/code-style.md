# Code style rules

These rules normalize control-flow, expressions, literals, variable declarations, and related Java syntax.

## Planned rules

| Rule id | Rewrite | Options |
| --- | --- | --- |
| `control-statements.blocks` | Add or remove blocks around control statements. | Implemented for `always`; `jdt-style`, `never` planned |
| `control-statements.else-if` | Join an `else` block containing an `if` into `else if`. | Implemented; `true` or `false` |
| `control-statements.simplify-boolean-if-else` | Simplify boolean-producing `if`/`else` statements. | Implemented; `true` or `false` |
| `control-statements.reduce-indentation` | Reduce avoidable nesting in control flow. | `true` or `false` |
| `control-statements.use-switch` | Replace eligible conditional chains with `switch`. | `true` or `false` |
| `control-statements.use-add-all` | Use bulk collection operations where equivalent. | `true` or `false` |
| `expressions.parentheses` | Add or remove optional parentheses. | Implemented for safe removal; `always` planned |
| `expressions.extract-increment` | Move increment expressions to a dedicated statement when appropriate. | Implemented for direct local declarations |
| `expressions.pull-up-assignment` | Move an assignment out of a conditional expression when safe. | `true` or `false` |
| `expressions.instanceof` | Simplify eligible `instanceof` expressions. | `true` or `false` |
| `number-literals.suffix` | Normalize numeric literal suffixes. | Implemented for canonical uppercase suffixes |
| `variable-declarations.final` | Add `final` to selected declarations. | `fields`, `parameters`, `locals` |
| `functional-interfaces.lambda-method-reference` | Simplify eligible lambda and method-reference syntax. | `true` or `false` |

## Notes

The `final` rule is intentionally split into field, parameter, and local-variable options because Eclipse exposes those choices independently. Control-flow rewrites must preserve comments and dangling-`else` behavior.

## Eclipse reference

The option grouping follows the current Eclipse JDT [CodeStyleTabPage](https://github.com/eclipse-jdt/eclipse.jdt.ui/blob/master/org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/preferences/cleanup/CodeStyleTabPage.java).
