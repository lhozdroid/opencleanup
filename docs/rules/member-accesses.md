# Member-access rules

These rules make member access qualification consistent.

## Planned rules

| Rule id | Rewrite | Options |
| --- | --- | --- |
| `member-accesses.non-static-fields` | Qualify non-static field access with `this`. | `always` or `when-necessary` |
| `member-accesses.non-static-methods` | Qualify non-static method access with `this`. | `always` or `when-necessary` |
| `member-accesses.static-members` | Qualify static member access with its declaring type. | `true` or `false` |

Qualification should not be added where it changes overload resolution or makes generated code invalid. The field and method settings are independent because Eclipse exposes separate controls.

## Eclipse reference

The option grouping follows the current Eclipse JDT [MemberAccessesTabPage](https://github.com/eclipse-jdt/eclipse.jdt.ui/blob/master/org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/preferences/cleanup/MemberAccessesTabPage.java).
