# Missing-code rules

These rules add source that Eclipse can derive from the type hierarchy or Java serialization conventions.

## Implemented rules

| Rule id | Rewrite | Options |
| --- | --- | --- |
| `annotations.missing` | Add missing annotations. | `true` or `false` |
| `annotations.override` | Add `@Override` to overriding methods. | `true` or `false` |
| `annotations.override-interface` | Add `@Override` to interface method implementations. | `true` or `false` |
| `annotations.deprecated` | Add `@Deprecated` where the declaration indicates deprecation. | `true` or `false` |
| `serialization.serial-version-uid` | Add a missing `serialVersionUID`. | `generated` or `default` |
| `methods.unimplemented` | Add method stubs for unimplemented methods. | `true` or `false` |

Generated source uses deterministic templates and never overwrites an existing declaration. Ambiguous type-hierarchy cases are left unchanged.

## Eclipse reference

The option grouping follows the current Eclipse JDT [MissingCodeTabPage](https://github.com/eclipse-jdt/eclipse.jdt.ui/blob/master/org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/preferences/cleanup/MissingCodeTabPage.java).
