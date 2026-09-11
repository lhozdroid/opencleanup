# Maven configuration

This page defines the proposed Maven-facing configuration for OpenCleanup. The final element names and defaults will be confirmed when the plugin implementation is started.

## Selecting rules

Rules are opt-in unless a future release defines a named preset. A rule may be enabled by its stable OpenCleanup id and may contain rule-specific options.

```xml
<plugin>
  <groupId>com.example</groupId>
  <artifactId>opencleanup-maven-plugin</artifactId>
  <version>${opencleanup.version}</version>
  <configuration>
    <rules>
      <rule id="code-style" enabled="true">
        <option name="control-statements.blocks" value="always"/>
      </rule>
      <rule id="unnecessary-code" enabled="true">
        <option name="unused-code.imports" value="true"/>
      </rule>
      <rule id="java-features" enabled="false"/>
    </rules>
  </configuration>
</plugin>
```

The coordinates in this example are placeholders. They are intentionally not presented as published coordinates.

The first implemented rule can be selected directly with `id="unused-code.imports"`, or through the `unnecessary-code` group as shown above.

## Proposed configuration model

| Element | Meaning |
| --- | --- |
| `rules` | Collection of cleanup rule groups to evaluate. |
| `rule/@id` | Stable OpenCleanup rule-group identifier. |
| `rule/@enabled` | Enables or disables the complete group. |
| `option/@name` | Rule-specific option identifier documented on the rule page. |
| `option/@value` | Option value, such as `true`, `false`, `always`, or `never`. |

An option should not silently enable its parent rule. Parent rules and options should be independently visible in the POM.

## Source scope

The plugin will need explicit scope controls before implementation is complete. The intended controls are:

- source roots to include;
- file include and exclude patterns;
- whether test sources are included; and
- behavior when no rules are enabled.

The exact element names are deliberately left open until the Maven goal and parameter model are implemented.

## Rewrite behavior

The plugin should:

1. parse each selected Java source file;
2. apply only enabled transformations whose preconditions are satisfied;
3. write a file only when its content changes; and
4. provide a concise summary of changed files and applied rules.

Rules that can change runtime behavior, depend on a Java language level, or remove declarations should be clearly marked on their rule page and require explicit opt-in.

## Configuration compatibility

The plugin will mirror Eclipse concepts, not Eclipse's internal preference keys. OpenCleanup ids and option names are its public API and should remain stable even if Eclipse changes its internal implementation.
