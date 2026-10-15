# OpenCleanup Maven Plugin

[![Maven Central](https://img.shields.io/maven-central/v/io.github.lhozdroid/opencleanup-maven-plugin?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.lhozdroid/opencleanup-maven-plugin)
[![Java 21+](https://img.shields.io/badge/Java-21%2B-ED8B00)](https://www.oracle.com/java/technologies/javase/21-overview.html)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

**Deterministic, opt-in Java source cleanup for Maven builds.**

OpenCleanup is a production-oriented Maven plugin that formats, modernizes, and
cleans Java source directly in the build. Every rule is explicitly selected in
`pom.xml`, rewrites are conservative, and only files whose final content changes
are written back.

> Make source cleanup repeatable, reviewable, and independent of a developer's IDE.

## At a glance

| Capability | What it provides |
| --- | --- |
| 103 documented rules | Formatting, code style, performance, Java features, imports, and more. |
| Maven-native configuration | Stable rule IDs and explicit options committed with the project. |
| Conservative rewrites | Ambiguous syntax is skipped instead of guessed. |
| Lombok-aware cleanup | Generated accessors, builders, constructors, and related APIs are protected. |
| CI-friendly execution | Runs in `process-sources`, reports changed-file counts and applied rules, and supports source-root configuration. |

## Why OpenCleanup

IDE formatting is useful for individuals, but it is difficult to guarantee that
every contributor and CI job applies the same cleanup policy. OpenCleanup moves
that policy into Maven, where it can be reviewed, versioned, and applied
consistently across local builds and automation.

## Contents

- [Quick start](#quick-start)
- [At a glance](#at-a-glance)
- [Why OpenCleanup](#why-opencleanup)
- [Maven Central](#maven-central)
- [Requirements and build](#requirements-and-build)
- [Complete Maven configuration](#complete-maven-configuration)
- [Configuration elements](#configuration-elements)
- [Selecting rules](#selecting-rules)
- [Rule catalog](#rule-catalog)
- [Execution and source scope](#execution-and-source-scope)
- [Rewrite behavior and safety](#rewrite-behavior-and-safety)
- [Publishing](#publishing)
- [Documentation](#documentation)

## Compatibility

| Requirement | Supported baseline |
| --- | --- |
| Java | 21 or newer |
| Maven | 3.9.11 or newer |
| Packaging | `maven-plugin` |
| Source processing | Maven compile source roots by default; custom roots supported |

OpenCleanup targets Java 21 syntax and uses Eclipse JDT for parsing and
formatting. It does not require Lombok in the consuming project, but it
recognizes common Lombok annotations when they are present in source.

## Quick start

Add the released plugin from [Maven Central](https://central.sonatype.com/artifact/io.github.lhozdroid/opencleanup-maven-plugin/1.2.0), select a small set of rules, then run `mvn process-sources`.

## Maven Central

The published coordinates are:

```xml
<groupId>io.github.lhozdroid</groupId>
<artifactId>opencleanup-maven-plugin</artifactId>
<version>1.2.0</version>
```

Add the plugin to the project that should be rewritten. Maven plugin configuration uses nested elements, so rule properties must be written as `<id>`, `<enabled>`, and `<options>` elements rather than XML attributes.

```xml
<build>
  <plugins>
    <plugin>
      <groupId>io.github.lhozdroid</groupId>
      <artifactId>opencleanup-maven-plugin</artifactId>
      <version>1.2.0</version>
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
mvn io.github.lhozdroid:opencleanup-maven-plugin:1.2.0:rewrite
```

When the plugin is declared in the project's `<build><plugins>` section, the shorter `mvn opencleanup:rewrite` form can also be used. Maven resolves the plugin from Maven Central automatically.

## Requirements and build

- Java 21 or newer to run and build the plugin.
- Maven 3.9.11 or newer.
- A Maven project, because the goal requires an injected `MavenProject`.
- Java source files located under Maven compile source roots, or under explicitly configured source roots.

Build and verify the plugin from this repository with:

```bash
mvn test
mvn package
```

`mvn package` generates the Maven plugin descriptor with the `opencleanup` goal prefix and the `rewrite` goal. Use `mvn install` only when testing an unpublished checkout from another local project.

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
| `rule/id` | Yes | One of the 103 rule IDs or one of the nine group IDs listed below. |
| `rule/enabled` | No | `true` or `false`; defaults to `true`. A disabled rule or group selects nothing. |
| `rule/options` | No | Options passed to the selected rule or group. |
| `rule/options/option` | No | One named option. |
| `option/name` | Yes for an option | The rule-specific option name. |
| `option/value` | Yes for an option | The string value interpreted by the selected rule. |

For a direct rule, the rule ID selects the rule and its options configure it. For a group, child-rule options select the corresponding rules. For boolean group options, use the literal value `true`; a value of `false` leaves that child rule disabled. Enum options such as `always`, `never`, `generated`, and `lambda` must use the supported value documented on the rule page.

When the same rule is selected more than once, the first selection wins. This also applies when a direct rule and a group select the same ID. Avoid duplicate selections because the later configuration does not replace the earlier rule configuration.

### Formatting configuration

`line-split` controls the preferred width for ordinary Java source. Use
`comment-line-length` to control comment wrapping independently. JDT's default
preferred widths are 120 for source and 80 for comments.

```xml
<rule>
  <id>code-organizing</id>
  <enabled>true</enabled>
  <options>
    <option><name>format.source</name><value>true</value></option>
    <option><name>line-split</name><value>120</value></option>
    <option><name>comment-line-length</name><value>80</value></option>
  </options>
</rule>
```

The comment width is a formatter preference, not a hard physical maximum:
long unbreakable tokens, inline comments, and some one-line block comments may
remain wider. The option is supported by `format.source` and is passed through
when `format.indentation` performs its conservative formatting pass. For the
most predictable comment rewrite, use `format.source`.

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

The nine group pages enumerate all 103 stable rule IDs and the supported option values for each rule. The IDs are the public configuration API; internal preference keys are not accepted.

## Execution and source scope

The `rewrite` goal:

1. requires a Maven project;
2. reads configured source roots, defaulting to Maven compile source roots;
3. recursively visits regular files whose names end in `.java`;
4. applies selected source-text rules, such as formatting, then selected AST rules;
5. writes a file only when the final text differs from the original; and
6. logs the number of visited and changed Java files plus the rule IDs that changed at least one file.

The optional `opencleanup.sourceRoots` property selects the source folders to process as a comma-separated list. Relative paths are resolved against the Maven project directory. For example, `-Dopencleanup.sourceRoots=src/main/java,src/test/java` processes both production and test sources. If the property is omitted, Maven compile source roots are processed. Non-Java files are ignored.

The goal runs in `process-sources`, before compilation. Rewrites happen in place, so run the goal on a clean working tree or review the resulting diff immediately after execution.

## Rewrite behavior and safety

- Source encoding is read from the Maven property `project.build.sourceEncoding`; UTF-8 is used when the property is absent or blank.
- An unsupported encoding causes the goal to fail rather than silently corrupt source.
- The parser and rewrite engine target Java 21 syntax.
- Rules that need type bindings, project-wide analysis, or newer syntax skip ambiguous cases rather than guessing.
- `modules.use-module-imports` is recognized but intentionally emits no Java 25 `import module` syntax because this plugin targets Java 21.
- Formatting can create broad diffs. Enable formatting, indentation, import organization, and member sorting separately.
- Rules that add declarations or remove members are opt-in and conservative, but they should still be reviewed like any source modification.
- Lombok-aware rules preserve fields and methods used by generated getters, setters, builders, constructors, withers, field-name constants, and related APIs. Annotated members are treated conservatively and may be left unchanged.
- A selected rule may report no change when its preconditions are not satisfied.
- The plugin does not guarantee that all rewrites are semantically equivalent in every project; compile and test the rewritten source.

### Lombok compatibility

The following rules account for common Lombok-generated members and avoid
deleting or changing source declarations that generated APIs may depend on:

- `unused-code.private-members`
- `fields.single-use`
- `variable-declarations.final`
- `strings.buffer-to-builder`

For example, a private field under `@Data`, `@Getter`, `@Setter`, `@Builder`,
`@Value`, `@With`, `@SuperBuilder`, `@FieldNameConstants`, or a related type
annotation is preserved. Explicitly annotated members are also skipped when a
framework or annotation processor may consume them. The analysis is source
based rather than binding based, so ambiguous cases are intentionally left
unchanged.

```java
@lombok.Data
class Account {
    private String name;
}
```

Cleanup will not delete `name` or make it final in a way that removes the
generated setter.

## Publishing

The project is licensed under the [MIT License](LICENSE). Maven Central
release configuration is documented in [Publishing OpenCleanup](docs/publishing.md).

Version `1.2.0` is published as
`io.github.lhozdroid:opencleanup-maven-plugin:1.2.0` and can be used directly
from Maven Central.

The release workflow runs after a GitHub release is published, then deploys the
artifacts to Maven Central when the required Central credentials and GPG signing
secrets are configured.

## Contributing and support

Bug reports and improvement proposals are welcome in the
[GitHub issue tracker](https://github.com/lhozdroid/opencleanup/issues). For
source-rewrite changes, include a focused regression test, explain the safety
precondition, and update the relevant rule documentation. Run `mvn test` and
review the generated source diff before opening a pull request.

For security-sensitive reports, use a private GitHub security advisory rather
than posting exploit details publicly.

## Documentation

- [Documentation index](docs/index.md)
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
- [Changelog](CHANGELOG.md)
- [Publishing guide](docs/publishing.md)
- [Contributing and issues](https://github.com/lhozdroid/opencleanup/issues)
