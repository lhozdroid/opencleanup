# Duplicate-code rules

These rules collapse repeated conditional expressions or control-flow blocks.

## Planned rules

| Rule id | Rewrite |
| --- | --- |
| `expressions.operand-factorization` | Factor repeated operands from a conditional expression. |
| `expressions.ternary-operator` | Use a ternary expression where it remains clear and equivalent. |
| `comparisons.strictly-equal-or-different` | Normalize repeated strict equality or inequality checks. |
| `blocks.merge-conditional` | Merge conditional blocks with the same outcome. |
| `control-flow.merge` | Merge equivalent control-flow paths. |
| `blocks.one-if-for-fall-through` | Replace duplicate fall-through blocks with one `if`. |
| `blocks.redundant-fall-through-end` | Remove a redundant fall-through block end. |
| `conditions.redundant-if` | Remove a redundant `if` condition. |
| `conditions.pull-out-if` | Pull a shared condition out of an `if`/`else` structure. |

These rewrites need strong control-flow equivalence checks. They should preserve comments, labels, fall-through behavior, and the order of side effects.

## Eclipse reference

The option grouping follows the current Eclipse JDT [DuplicateCodeTabPage](https://github.com/eclipse-jdt/eclipse.jdt.ui/blob/master/org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/preferences/cleanup/DuplicateCodeTabPage.java).
