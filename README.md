# OpenCleanup Maven Plugin

OpenCleanup rewrites Java source files during a Maven build. It provides a Maven-facing catalog of practical Java cleanup transformations. Rules are explicitly selected in the POM, and only source files changed by a selected rule are written back.

The current implementation targets Java 21 and contains all 102 documented rule identifiers. The implementations are intentionally conservative: when a transformation cannot be proven safe from the available source syntax, the source is left unchanged.

## Contents

- [Quick start](#quick-start)
- [Requirements and build](#requirements-and-build)
- [Complete Maven configuration](#complete-maven-configuration)
- [Configuration elements](#configuration-elements)
- [Selecting rules](#selecting-rules)
- [Rule catalog](#rule-catalog)
- [Execution and source scope](#execution-and-source-scope)
- [Rewrite behavior and safety](#rewrite-behavior-and-safety)
- [Documentation](#documentation)

## Quick start

The plugin coordinates for the first release are:

```xml
<groupId>io.github.lhozdroid</groupId>
<artifactId>opencleanup-maven-plugin</artifactId>
<version>1.0.0</version>
```

Add the plugin to the project that should be rewritten. Maven plugin configuration uses nested elements, so rule properties must be written as `<id>`, `<enabled>`, and `<options>` elements rather than XML attributes.

```xml
<build>
  <plugins>
    <plugin>
      <groupId>io.github.lhozdroid</groupId>
      <artifactId>opencleanup-maven-plugin</artifactId>
      <version>1.0.0</version>
      <executions>
        <execution>
          <id>opencleanup-rewrite</id>
          <goals>
            <goal>rewrite</goal>
          </goals>
        </execution>
      </executions>
      <configuration>
        <rules>
          <rule>
            <id>unnecessary-code</id>
            <enabled>true</enabled>
            <options>
              <option>
                <name>unused-code.imports</name>
                <value>true</value>
              </option>
              <option>
                <name>booleans.double-negation</name>
                <value>true</value>
              </option>
            </options>
          </rule>
        </rules>
      </configuration>
    </plugin>
  </plugins>
</build>
```

The execution is bound to Maven's `process-sources` phase by the goal annotation. Run it with:

```bash
mvn process-sources
```

For a one-time explicit invocation, use:

```bash
mvn opencleanup:rewrite
```

The explicit invocation requires the plugin to be resolvable from the configured repositories or from the local Maven repository. When using this checkout before publishing the release to a Maven repository, install it locally with `mvn install`.

## Requirements and build

- Java 21 or newer to run and build the plugin.
- Maven 3.9.11 or newer.
- A Maven project, because the goal requires an injected `MavenProject`.
- Java source files located under Maven compile source roots.

Build and verify the plugin from this repository with:

```bash
mvn test
mvn package
```

`mvn package` generates the Maven plugin descriptor with the `opencleanup` goal prefix and the `rewrite` goal. Use `mvn install` when another local project needs the `1.0.0` artifact from this checkout.

## Complete Maven configuration

The complete configuration shape is:

```xml
<configuration>
  <rules>
    <rule>
      <id>RULE_OR_GROUP_ID</id>
      <enabled>true</enabled>
      <options>
        <option>
          <name>OPTION_NAME</name>
          <value>OPTION_VALUE</value>
        </option>
      </options>
    </rule>
  </rules>
</configuration>
```

The names are case-sensitive. A rule can be selected directly by its rule ID or indirectly by putting its option in the matching group. A direct rule configuration is useful when the rule has no options or when its options should be isolated from a group:

```xml
<rules>
  <rule>
    <id>strings.is-blank</id>
    <enabled>true</enabled>
  </rule>
  <rule>
    <id>control-statements.blocks</id>
    <enabled>true</enabled>
    <options>
      <option>
        <name>control-statements.blocks</name>
        <value>always</value>
      </option>
    </options>
  </rule>
</rules>
```

For an option-bearing direct rule, the option name and value are interpreted by that rule. The rule pages document the supported values. For example, the direct `control-statements.blocks` rule reads the option named `control-statements.blocks`; `mode` is also accepted by the implementation for convenience in direct configurations.

### Configuration elements

| Element | Required | Meaning |
| --- | --- | --- |
| `rules` | No | List of `rule` configuration objects. An omitted or empty list performs no rewrites. |
| `rule` | No | One rule or cleanup group. |
| `rule/id` | Yes | One of the 102 rule IDs or one of the nine group IDs listed below. |
| `rule/enabled` | No | `true` or `false`; defaults to `true`. A disabled rule or group selects nothing. |
| `rule/options` | No | Options passed to the selected rule or group. |
| `rule/options/option` | No | One named option. |
| `option/name` | Yes for an option | The rule-specific option name. |
| `option/value` | Yes for an option | The string value interpreted by the selected rule. |

Options do not enable a rule by themselves. For boolean options, use the literal value `true`; a value of `false` leaves that option disabled. Enum options such as `always`, `never`, `generated`, and `lambda` are enabled by selecting the rule and supplying the supported value.

When the same rule is selected more than once, the first selection wins. This also applies when a direct rule and a group select the same ID. Avoid duplicate selections because the later configuration does not replace the earlier rule configuration.

## Selecting rules

Rules are opt-in. There are no implicit presets and no default rule set. The safest starting point is one direct rule:

```xml
<rules>
  <rule>
    <id>booleans.double-negation</id>
  </rule>
</rules>
```

Groups use the same stable IDs as the rule catalog pages. A group only selects the options explicitly present in its `<options>` list:

```xml
<rules>
  <rule>
    <id>code-style</id>
    <enabled>true</enabled>
    <options>
      <option>
        <name>control-statements.else-if</name>
        <value>true</value>
      </option>
      <option>
        <name>control-statements.blocks</name>
        <value>jdt-style</value>
      </option>
      <option>
        <name>variable-declarations.final</name>
        <value>true</value>
      </option>
      <option>
        <name>fields</name>
        <value>true</value>
      </option>
      <option>
        <name>locals</name>
        <value>true</value>
      </option>
    </options>
  </rule>
</rules>
```

For `variable-declarations.final`, `fields`, `parameters`, and `locals` are independent options. The rule is selected when at least one of those options is `true`.

The special non-boolean group options are:

- `control-statements.blocks`: `always`, `jdt-style` (conservative multiline style), or `never`.
- `expressions.parentheses`: `always` or `never`.
- `functional-interfaces.convert`: `lambda` or `anonymous`.
- `member-accesses.non-static-fields`: `always` or `when-necessary`.
- `member-accesses.non-static-methods`: `always` or `when-necessary`.
- `serialization.serial-version-uid`: `generated` or `default`.
- `format.trailing-whitespace`: `all` or `ignore-empty-lines`.

Every group and its complete rule list is documented in the [rule catalog](#rule-catalog).

## Rule catalog

| Group ID | Rules and options |
| --- | --- |
| `code-organizing` | [Code-organizing rules](docs/rules/code-organizing.md): formatting, imports, and member ordering. |
| `code-style` | [Code-style rules](docs/rules/code-style.md): control flow, expressions, literals, declarations, and lambdas. |
| `duplicate-code` | [Duplicate-code rules](docs/rules/duplicate-code.md): repeated expressions and control-flow paths. |
| `java-features` | [Java-feature rules](docs/rules/java-features.md): language and library modernization. |
| `member-accesses` | [Member-access rules](docs/rules/member-accesses.md): qualification of fields, methods, and static members. |
| `missing-code` | [Missing-code rules](docs/rules/missing-code.md): derived annotations, stubs, and serialization declarations. |
| `performance` | [Performance rules](docs/rules/performance.md): allocation, parsing, boxing, and branching improvements. |
| `source-fixing` | [Source-fixing rules](docs/rules/source-fixing.md): bitwise and deprecated API corrections. |
| `unnecessary-code` | [Unnecessary-code rules](docs/rules/unnecessary-code.md): redundant code, expressions, and declarations. |

The nine group pages enumerate all 102 stable rule IDs and the supported option values for each rule. The IDs are the public configuration API; internal preference keys are not accepted.

## Execution and source scope

The `rewrite` goal:

1. requires a Maven project;
2. reads the project's compile source roots from `MavenProject#getCompileSourceRoots()`;
3. recursively visits regular files whose names end in `.java`;
4. applies selected source-text rules, such as formatting, then selected AST rules;
5. writes a file only when the final text differs from the original; and
6. logs the number of visited and changed Java files plus the rule IDs that changed at least one file.

The plugin currently has no include, exclude, test-source, generated-source, dry-run, backup, or output-directory parameters. Test source roots are not processed unless they are also compile source roots. Non-Java files are ignored.

The goal runs in `process-sources`, before compilation. Rewrites happen in place, so run the goal on a clean working tree or review the resulting diff immediately after execution.

## Rewrite behavior and safety

- Source encoding is read from the Maven property `project.build.sourceEncoding`; UTF-8 is used when the property is absent or blank.
- An unsupported encoding causes the goal to fail rather than silently corrupt source.
- The parser and rewrite engine target Java 21 syntax.
- Rules that need type bindings, project-wide analysis, or newer syntax skip ambiguous cases rather than guessing.
- `modules.use-module-imports` is recognized but intentionally emits no Java 25 `import module` syntax because this plugin targets Java 21.
- Formatting can create broad diffs. Enable formatting, indentation, import organization, and member sorting separately.
- Rules that add declarations or remove members are opt-in and conservative, but they should still be reviewed like any source modification.
- A selected rule may report no change when its preconditions are not satisfied.
- The plugin does not guarantee that all rewrites are semantically equivalent in every project; compile and test the rewritten source.

## Documentation

- [Detailed Maven configuration](docs/configuration.md)
- [Code-organizing rules](docs/rules/code-organizing.md)
- [Code-style rules](docs/rules/code-style.md)
- [Duplicate-code rules](docs/rules/duplicate-code.md)
- [Java-feature rules](docs/rules/java-features.md)
- [Member-access rules](docs/rules/member-accesses.md)
- [Missing-code rules](docs/rules/missing-code.md)
- [Performance rules](docs/rules/performance.md)
- [Source-fixing rules](docs/rules/source-fixing.md)
- [Unnecessary-code rules](docs/rules/unnecessary-code.md)
