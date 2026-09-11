# Source-fixing rules

These rules fix source patterns that are commonly reported as questionable or deprecated.

## Planned rules

| Rule id | Rewrite |
| --- | --- |
| `comparisons.invert-equals` | Put a stable non-null expression on the left side of an equality comparison where safe. Implemented. |
| `comparisons.standard` | Normalize equivalent comparison expressions to the standard form. Implemented for literal-left relational comparisons. |
| `bitwise.check-sign` | Correct or simplify bitwise conditional expressions when the sign check is equivalent. |
| `deprecated.replace-method` | Replace supported deprecated method calls with their recommended alternatives. |
| `deprecated.replace-field` | Replace supported deprecated fields with their recommended alternatives. |

All source-fixing rules are opt-in. Deprecated API replacements must use a maintained mapping and must not be applied when the replacement is unavailable on the project's effective class path.

## Eclipse reference

The option grouping follows the current Eclipse JDT [SourceFixingTabPage](https://github.com/eclipse-jdt/eclipse.jdt.ui/blob/master/org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/preferences/cleanup/SourceFixingTabPage.java).
