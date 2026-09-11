# Maven configuration

This page is the authoritative guide to configuring the OpenCleanup Maven plugin. The plugin exposes one goal, `rewrite`, and one user-configurable parameter, `rules`.

The released plugin is available from [Maven Central](https://central.sonatype.com/artifact/io.github.lhozdroid/opencleanup-maven-plugin/1.0.0). Maven projects can use it directly without adding a repository or installing the plugin locally.

## Plugin coordinates and goal

The current project coordinates are:

```xml
<groupId>io.github.lhozdroid</groupId>
<artifactId>opencleanup-maven-plugin</artifactId>
<version>1.0.0</version>
```

The goal prefix is `opencleanup`, so the direct command is:

```bash
mvn opencleanup:rewrite
```

The goal is associated with Maven's `process-sources` phase. To run it as part of the lifecycle, declare an execution:

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
    </plugin>
  </plugins>
</build>
```

The plugin requires a Maven project and Java 21 or newer. Maven resolves the published plugin from Central automatically. Run `mvn install` only when another local project needs to test an unpublished checkout.

## Minimal configuration

Rules are opt-in. This configuration enables one direct rule:

```xml
<configuration>
  <rules>
    <rule>
      <id>booleans.double-negation</id>
    </rule>
  </rules>
</configuration>
```

`enabled` defaults to `true`, so it may be written explicitly for clarity:

```xml
<rule>
  <id>booleans.double-negation</id>
  <enabled>true</enabled>
</rule>
```

An empty or omitted `rules` list selects nothing and leaves source files unchanged.

## Exact XML model

Maven maps the plugin parameter to Java bean properties. Use nested child elements with these names; do not put `id`, `enabled`, `name`, or `value` in XML attributes.

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

| XML path | Java property | Required | Description |
| --- | --- | --- | --- |
| `configuration/rules` | `List<RuleConfiguration> rules` | No | Rule/group configurations. |
| `rule/id` | `String id` | Yes | A direct rule ID or one of the group IDs. |
| `rule/enabled` | `boolean enabled` | No | Defaults to `true`; `false` disables that configuration. |
| `rule/options` | `List<RuleOption> options` | No | Options passed to the selected rule or group. |
| `rule/options/option` | `List<RuleOption>` item | No | One named option. |
| `option/name` | `String name` | Yes for an option | Stable option name. |
| `option/value` | `String value` | Yes for an option | String value interpreted by the selected rule. |

Boolean values are case-insensitive in the implementation, but lowercase `true` and `false` are recommended for readable POM files. Option names and rule IDs are case-sensitive.

## Direct rules versus groups

A direct rule configuration uses the rule ID as `<id>`:

```xml
<rules>
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

A group configuration uses a group ID and selects rules through option names:

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
    </options>
  </rule>
</rules>
```

An option does not enable a parent rule by itself. A group option with `false` is ignored. For enum-valued options, the option must have a supported value; see the rule pages for exact values.

If the same direct rule and group select one ID, the first selection in the POM wins. Keep each rule selected in one place to avoid configuration ambiguity.

## Complete group configuration example

The following example demonstrates the XML shape and representative option types across all groups. It is valid Maven configuration, but it enables many rewrites and should be introduced gradually:

```xml
<configuration>
  <rules>
    <rule>
      <id>code-organizing</id>
      <options>
        <option><name>format.source</name><value>true</value></option>
        <option><name>format.trailing-whitespace</name><value>ignore-empty-lines</value></option>
        <option><name>format.indentation</name><value>true</value></option>
        <option><name>imports.organize</name><value>true</value></option>
        <option><name>members.sort</name><value>true</value></option>
      </options>
    </rule>
    <rule>
      <id>code-style</id>
      <options>
        <option><name>control-statements.blocks</name><value>jdt-style</value></option>
        <option><name>control-statements.else-if</name><value>true</value></option>
        <option><name>control-statements.simplify-boolean-if-else</name><value>true</value></option>
        <option><name>control-statements.reduce-indentation</name><value>true</value></option>
        <option><name>control-statements.use-switch</name><value>true</value></option>
        <option><name>control-statements.use-add-all</name><value>true</value></option>
        <option><name>expressions.parentheses</name><value>never</value></option>
        <option><name>expressions.extract-increment</name><value>true</value></option>
        <option><name>expressions.pull-up-assignment</name><value>true</value></option>
        <option><name>expressions.instanceof</name><value>true</value></option>
        <option><name>number-literals.suffix</name><value>true</value></option>
        <option><name>variable-declarations.final</name><value>true</value></option>
        <option><name>fields</name><value>true</value></option>
        <option><name>parameters</name><value>true</value></option>
        <option><name>locals</name><value>true</value></option>
        <option><name>functional-interfaces.lambda-method-reference</name><value>true</value></option>
      </options>
    </rule>
    <rule>
      <id>duplicate-code</id>
      <options>
        <option><name>expressions.operand-factorization</name><value>true</value></option>
        <option><name>expressions.ternary-operator</name><value>true</value></option>
        <option><name>comparisons.strictly-equal-or-different</name><value>true</value></option>
        <option><name>blocks.merge-conditional</name><value>true</value></option>
        <option><name>control-flow.merge</name><value>true</value></option>
        <option><name>blocks.one-if-for-fall-through</name><value>true</value></option>
        <option><name>blocks.redundant-fall-through-end</name><value>true</value></option>
        <option><name>conditions.redundant-if</name><value>true</value></option>
        <option><name>conditions.pull-out-if</name><value>true</value></option>
      </options>
    </rule>
    <rule>
      <id>java-features</id>
      <options>
        <option><name>instanceof.pattern-matching</name><value>true</value></option>
        <option><name>instanceof.to-switch</name><value>true</value></option>
        <option><name>switch.expressions</name><value>true</value></option>
        <option><name>variable-declarations.var</name><value>true</value></option>
        <option><name>functional-interfaces.convert</name><value>lambda</value></option>
        <option><name>functional-interfaces.simplify-lambda</name><value>true</value></option>
        <option><name>comparators.criteria</name><value>true</value></option>
        <option><name>strings.join</name><value>true</value></option>
        <option><name>try-with-resources</name><value>true</value></option>
        <option><name>multi-catch</name><value>true</value></option>
        <option><name>type-parameters.remove-redundant</name><value>true</value></option>
        <option><name>hash.modernize</name><value>true</value></option>
        <option><name>objects.equals</name><value>true</value></option>
        <option><name>system-properties.constants</name><value>true</value></option>
        <option><name>boxing.autoboxing</name><value>true</value></option>
        <option><name>boxing.unboxing</name><value>true</value></option>
        <option><name>loops.enhanced-for</name><value>true</value></option>
      </options>
    </rule>
    <rule>
      <id>member-accesses</id>
      <options>
        <option><name>member-accesses.non-static-fields</name><value>always</value></option>
        <option><name>member-accesses.non-static-methods</name><value>always</value></option>
        <option><name>member-accesses.static-members</name><value>true</value></option>
      </options>
    </rule>
    <rule>
      <id>missing-code</id>
      <options>
        <option><name>annotations.missing</name><value>true</value></option>
        <option><name>annotations.override</name><value>true</value></option>
        <option><name>annotations.override-interface</name><value>true</value></option>
        <option><name>annotations.deprecated</name><value>true</value></option>
        <option><name>serialization.serial-version-uid</name><value>generated</value></option>
        <option><name>methods.unimplemented</name><value>true</value></option>
      </options>
    </rule>
    <rule>
      <id>performance</id>
      <options>
        <option><name>fields.single-use</name><value>true</value></option>
        <option><name>loops.break</name><value>true</value></option>
        <option><name>classes.static-inner</name><value>true</value></option>
        <option><name>strings.string-builder</name><value>true</value></option>
        <option><name>strings.plain-replacement</name><value>true</value></option>
        <option><name>strings.is-blank</name><value>true</value></option>
        <option><name>operators.lazy-logical</name><value>true</value></option>
        <option><name>boxing.value-of</name><value>true</value></option>
        <option><name>boxing.primitive-comparison</name><value>true</value></option>
        <option><name>parsing.primitive</name><value>true</value></option>
        <option><name>serialization.primitive</name><value>true</value></option>
        <option><name>boxing.primitive-rather-than-wrapper</name><value>true</value></option>
        <option><name>regular-expressions.precompile</name><value>true</value></option>
        <option><name>strings.buffer-to-builder</name><value>true</value></option>
        <option><name>strings.no-string-creation</name><value>true</value></option>
        <option><name>booleans.literal</name><value>true</value></option>
      </options>
    </rule>
    <rule>
      <id>source-fixing</id>
      <options>
        <option><name>comparisons.invert-equals</name><value>true</value></option>
        <option><name>comparisons.standard</name><value>true</value></option>
        <option><name>bitwise.check-sign</name><value>true</value></option>
        <option><name>deprecated.replace-method</name><value>true</value></option>
        <option><name>deprecated.replace-field</name><value>true</value></option>
      </options>
    </rule>
    <rule>
      <id>unnecessary-code</id>
      <options>
        <option><name>unused-code.imports</name><value>true</value></option>
        <option><name>unused-code.private-members</name><value>true</value></option>
        <option><name>unused-code.suppress-warnings</name><value>true</value></option>
        <option><name>casts.unnecessary</name><value>true</value></option>
        <option><name>arrays.fill</name><value>true</value></option>
        <option><name>null-checks.evaluate-nullable</name><value>true</value></option>
        <option><name>statements.redundant-comparison</name><value>true</value></option>
        <option><name>blocks.unreachable</name><value>true</value></option>
        <option><name>collections.direct-map-method</name><value>true</value></option>
        <option><name>collections.clone</name><value>true</value></option>
        <option><name>maps.clone</name><value>true</value></option>
        <option><name>assignments.overridden</name><value>true</value></option>
        <option><name>comparators.redundant</name><value>true</value></option>
        <option><name>arrays.creation</name><value>true</value></option>
        <option><name>loops.unlooped-while</name><value>true</value></option>
        <option><name>strings.redundant-substring-argument</name><value>true</value></option>
        <option><name>negation.push-down</name><value>true</value></option>
        <option><name>booleans.value-rather-than-comparison</name><value>true</value></option>
        <option><name>booleans.double-negation</name><value>true</value></option>
        <option><name>constructors.redundant-super</name><value>true</value></option>
        <option><name>modifiers.redundant</name><value>true</value></option>
        <option><name>if.embedded</name><value>true</value></option>
        <option><name>semicolons.redundant</name><value>true</value></option>
        <option><name>arrays.initializer</name><value>true</value></option>
        <option><name>returns.expression</name><value>true</value></option>
        <option><name>returns.useless</name><value>true</value></option>
        <option><name>continues.useless</name><value>true</value></option>
      </options>
    </rule>
  </rules>
</configuration>
```

Use the individual rule pages for the rewrite shape, supported values, Java-version notes, and safety limitations. The example above is intentionally broad; enabling one group or a few direct rules at a time makes source review easier.

## Group and rule reference

The rule pages are the complete catalog:

- [Code-organizing](rules/code-organizing.md)
- [Code-style](rules/code-style.md)
- [Duplicate-code](rules/duplicate-code.md)
- [Java features](rules/java-features.md)
- [Member accesses](rules/member-accesses.md)
- [Missing code](rules/missing-code.md)
- [Performance](rules/performance.md)
- [Source fixing](rules/source-fixing.md)
- [Unnecessary code](rules/unnecessary-code.md)

## Source roots, files, and execution order

The goal reads `MavenProject#getCompileSourceRoots()`. It recursively visits regular `.java` files below those directories. It does not currently expose include/exclude patterns, test-source selection, generated-source selection, a dry-run mode, backups, or a separate output directory.

For each file, complete-source rules such as formatting are applied first. The resulting source is parsed as Java 21, then selected AST rules record and apply edits. A file is written only if its final content differs from the original. A selected rule that finds no safe candidate does not modify the file.

The goal reports messages similar to:

```text
OpenCleanup visited 12 Java files and changed 3.
Applied rules: imports.organize, booleans.double-negation
```

The applied-rule list contains rule IDs that changed at least one file in the execution.

## Encoding and Java version

The goal reads `project.build.sourceEncoding` from the Maven project properties. If the property is absent or blank, it uses UTF-8:

```xml
<properties>
  <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>
```

The parser uses a Java 21 AST. Java-version-sensitive transformations are conservative. In particular, `modules.use-module-imports` is recognized in the catalog but does not emit Java 25 `import module` syntax from this Java 21 plugin.

## Operational safety

OpenCleanup edits source files in place. Before enabling a broad group:

1. commit or otherwise back up the source;
2. enable a small set of rules;
3. run `mvn process-sources`;
4. inspect the diff;
5. compile and run tests; and
6. expand the selection only after reviewing the result.

Rules that remove members, add declarations, alter control flow, or change language/library constructs require particular review. The implementation skips cases with ambiguous syntax, missing proof, comments that cannot be preserved safely, or unsupported Java constructs.

## Configuration compatibility

OpenCleanup IDs and option names are its public API. They are the stable names accepted by the plugin's POM configuration.
