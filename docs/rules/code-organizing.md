# Code-organizing rules

These rules organize source layout and whitespace. Formatting behavior should be delegated to a defined formatter configuration rather than reimplemented independently in every rule.

## Planned rules

| Rule id | Rewrite | Options |
| --- | --- | --- |
| `format.source` | Format source using the selected formatter profile. | `true` or `false` |
| `format.trailing-whitespace` | Remove trailing whitespace. | `all` or `ignore-empty-lines` |
| `format.indentation` | Correct indentation. | `true` or `false` |
| `imports.organize` | Organize imports using deterministic normal-then-static ordering. | Implemented; `true` or `false` |
| `members.sort` | Sort members using the configured member order. | `true` or `false` |

Formatting, import organization, and member sorting can create broad diffs. The plugin should make each operation independently selectable and should report the formatter and ordering configuration used.

## Eclipse reference

The option grouping follows the current Eclipse JDT [CodeFormatingTabPage](https://github.com/eclipse-jdt/eclipse.jdt.ui/blob/master/org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/preferences/cleanup/CodeFormatingTabPage.java).
