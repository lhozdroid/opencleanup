# Code-organizing rules

Code-organizing rules make source layout predictable. They operate on the Java files in the
project's compile source roots and write a file only when the resulting text is different from the
original. The rules are opt-in and can be selected individually or through the `code-organizing`
group.

## Configuration

Add the plugin execution to the build and place the rule configuration inside the execution's
`<configuration>` element:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>io.github.lhozdroid</groupId>
      <artifactId>opencleanup-maven-plugin</artifactId>
      <version>1.1.0</version>
      <executions>
        <execution>
          <id>opencleanup-rewrite</id>
          <goals>
            <goal>rewrite</goal>
          </goals>
          <configuration>
            <rules>
              <rule>
                <id>code-organizing</id>
                <enabled>true</enabled>
                <options>
                  <option>
                    <name>format.source</name>
                    <value>true</value>
                  </option>
                </options>
              </rule>
            </rules>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

`<enabled>` controls a direct rule or group configuration and defaults to `true`. A group option
selects its rule by option name. Boolean selection values should be written as `true` or `false`.
Rule IDs and option names are case-sensitive. A direct rule ID can be used when its options should
not be shared with another rule:

```xml
<rules>
  <rule>
    <id>format.trailing-whitespace</id>
    <enabled>true</enabled>
    <options>
      <option>
        <name>mode</name>
        <value>ignore-empty-lines</value>
      </option>
    </options>
  </rule>
</rules>
```

When a rule is selected more than once, the first selection is retained. Keep a rule in one
configuration location so that its options are unambiguous. Source-formatting rules run before
AST-based rules for each file; within each kind, configured order is retained.

## Rule summary

| Rule ID | Selection value | Rule-specific options |
| --- | --- | --- |
| `format.source` | `true` or `false` | `tab-character`: `tab`, `space`, or `mixed`; `tab-size`: a decimal integer; `indentation-size`: a decimal integer; `line-split`: a decimal integer |
| `format.trailing-whitespace` | `true` or `false` | `mode`: `all` or `ignore-empty-lines` |
| `format.indentation` | `true` or `false` | `tab-character`: `tab`, `space`, or `mixed`; `tab-size`: a decimal integer; `indentation-size`: a decimal integer; `line-split`: a decimal integer |
| `imports.use-simple-names` | `true` or `false` | None |
| `imports.organize` | `true` or `false` | None |
| `members.sort` | `true` or `false` | `order`: a comma-separated member-category list |

For a group configuration, the selection option uses the rule ID as its name:

```xml
<rule>
  <id>code-organizing</id>
  <enabled>true</enabled>
  <options>
    <option><name>format.source</name><value>true</value></option>
    <option><name>format.trailing-whitespace</name><value>ignore-empty-lines</value></option>
    <option><name>format.indentation</name><value>true</value></option>
    <option><name>imports.use-simple-names</name><value>true</value></option>
    <option><name>imports.organize</name><value>true</value></option>
    <option><name>members.sort</name><value>true</value></option>
    <option><name>order</name><value>static-fields,instance-fields,constructors,methods,types</value></option>
  </options>
</rule>
```

The group recognizes `format.source`, `format.trailing-whitespace`, and `format.indentation` by
the presence of their options, so use a supported value and use `true` for an enabled selection.
The `mode` option is also recognized for `format.trailing-whitespace` in the group. Formatting
options are passed as text to the formatter. Invalid or unsupported formatter values cause the
formatter-based rule to leave the source unchanged.

## `format.source`

### What it transforms

This rule reformats the complete Java source unit. It can change spacing, line breaks, indentation,
brace placement, and comment layout according to the formatter's built-in Java 21 defaults and the
configured formatter options.

For example:

```java
class Example{void run(){int value=1;}}
```

becomes:

```java
class Example {
\tvoid run() {
\t\tint value = 1;
\t}
}
```

### How it recognizes and applies the rewrite

The rule receives the entire file as text and asks the formatter to produce a complete compilation
unit. The resulting text is applied only when the formatter returns an edit. The rule includes
comments in the formatting operation and uses the first line-ending style found in the file.

The following options are read from the rule configuration:

- `tab-character`: `tab`, `space`, or `mixed`.
- `tab-size`: a decimal integer used for tab width.
- `indentation-size`: a decimal integer used for indentation width.
- `line-split`: a decimal integer used as the preferred line width.

These values are strings in the POM. If an option is omitted, the built-in default is retained. If
formatting cannot produce an edit or the formatter rejects the input or options, the original text
is returned.

### Benefits and safety

Consistent whole-file formatting reduces review noise and makes layout consistent across the
project. It is intentionally a broad rewrite: spacing and line structure may
change throughout the file. Malformed or unsupported input is not partially formatted by this
rule; the original source is preserved when formatting fails. Review the formatter options before
enabling it across an existing codebase because a first run can produce a large diff.

## `format.trailing-whitespace`

### What it transforms

This rule removes trailing spaces and tabs from ordinary source lines. It preserves line endings,
does not remove other characters, and does not alter whitespace inside Java text blocks because
that whitespace can be part of the string value.

With the default `all` behavior:

```java
int value = 1;␠␠␠
```

The `␠` characters above represent spaces at the end of the source line.

becomes:

```java
int value = 1;
```

The final line is handled as a line even when it has no line-ending delimiter.

### How it recognizes and applies the rewrite

The rule splits the source while retaining each original line delimiter. It removes only terminal
space and tab characters from each eligible line. It parses text blocks to identify their interior
lines and skips those lines. The `mode` option has exactly these values:

- `all`: remove trailing spaces and tabs from every eligible ordinary line, including blank lines.
- `ignore-empty-lines`: remove them from non-empty ordinary lines but preserve whitespace on blank
  lines.

If `mode` is omitted, `all` is used. The same mode can be supplied directly with the option name
`mode`, or through the rule's ID as the option name in configurations that use that form.

### Benefits and safety

Removing accidental line-end whitespace prevents invisible formatting changes and keeps diffs
small. Only spaces and tabs at the end of ordinary source lines are candidates. Line-ending style,
text-block contents, and all non-trailing characters are preserved. Use `ignore-empty-lines` when
blank-line indentation is significant to local tooling or review conventions.

## `format.indentation`

### What it transforms

This rule corrects leading indentation while preserving the rest of each source line. It can
replace spaces with tabs, tabs with spaces, or adjust indentation depth without changing tokens,
comments, line contents after the first non-space/tab character, or line boundaries.

### How it recognizes and applies the rewrite

The rule first formats the complete source using the same formatter options as `format.source`.
It then compares the original and formatted files line by line and copies only the formatted
leading spaces and tabs. The options are:

- `tab-character`: `tab`, `space`, or `mixed`.
- `tab-size`: a decimal integer used for tab width.
- `indentation-size`: a decimal integer used for indentation width.
- `line-split`: a decimal integer passed to the formatter while determining indentation.

Blank lines retain their original content. If formatting changes the number of source lines, the
rule skips the indentation rewrite and returns the original source.

### Benefits and safety

This rule repairs nesting indentation while limiting changes to line prefixes. It is useful when
the project wants indentation consistency but does not want a full formatting pass. The line-count
check prevents indentation from being copied across a changed line structure, and preserving the
original delimiters prevents line-ending normalization. A formatter failure therefore leaves the
source unchanged.

## `imports.use-simple-names`

### What it transforms

This rule replaces fully qualified type names with their simple class names and adds the required
ordinary imports. For example:

```java
class Example {
    java.util.List<String> values;
}
```

becomes:

```java
import java.util.List;

class Example {
    List<String> values;
}
```

The rule also handles fully qualified annotation types and nested types such as
`java.util.Map.Entry`.

### How it recognizes and applies the rewrite

The rule visits JDT type and annotation nodes, identifies names with a conventional lowercase
package prefix, and records a simple-name replacement plus an ordinary import. It does not rewrite
fully qualified static member references such as `java.util.Collections.emptyList()`.

### Benefits and safety

A simple name is shortened only when it is unambiguous from the source. Existing explicit imports,
declared types, type parameters, existing simple type uses, and multiple qualified types sharing a
simple name are considered. When a collision cannot be resolved safely, the affected qualified
name remains unchanged. `java.lang` and same-package types do not receive redundant imports.

For example, both types remain qualified because they cannot share one `List` import:

```java
java.util.List<String> standard;
com.example.List custom;
```

If `java.util.List` is already explicitly imported, only references to that type may be shortened;
the `com.example.List` reference remains qualified.

This rule has no rule-specific options. In a group, the option name is
`imports.use-simple-names` and its selection value is `true` or `false`. A direct configuration uses
`<id>imports.use-simple-names</id>` and `<enabled>true</enabled>`.

## `imports.organize`

### What it transforms

This rule orders import declarations deterministically. All ordinary imports are placed before all
static imports. Within each section, imports are ordered by fully qualified name; an on-demand
suffix is included in the comparison.

For example:

```java
import static java.util.Collections.sort;
import java.util.Set;
import java.util.List;
```

becomes:

```java
import java.util.List;
import java.util.Set;
import static java.util.Collections.sort;
```

### How it recognizes and applies the rewrite

The rule collects import declarations in their source order, computes the ordering key for each
declaration, and compares the sorted list with the original list. When the order differs, it moves
the existing declaration nodes into the sorted order. No import is inferred, added, removed, or
converted to a wildcard import.

This rule has no rule-specific options. In a group, the option name is `imports.organize` and its
selection value is `true` or `false`. A direct configuration uses `<id>imports.organize</id>` and
`<enabled>true</enabled>`.

### Benefits and safety

Stable import order makes source files easier to scan and prevents order-only changes from varying
between machines. Because the rule moves existing declarations instead of reconstructing them, it
preserves their spelling and comments as far as the source rewriter permits. It does not remove
unused imports, resolve types, infer missing imports, or reorder declarations outside the import
section. If the imports are already ordered, no edit is scheduled.

## `members.sort`

### What it transforms

This rule sorts declarations inside classes, interfaces, enums, annotation types, records, and
anonymous classes. The default category order is:

1. `static-fields`
2. `instance-fields`
3. `static-initializers`
4. `instance-initializers`
5. `constructors`
6. `methods`
7. `types`

Declarations in the same category retain their original order. Nested types are categorized as
`types`; their own members are sorted when the visitor reaches them.

### How it recognizes and applies the rewrite

The rule visits each supported type declaration, classifies its direct body declarations, and
compares the current list with a stable sort using the effective category order. When a list needs
sorting, it moves the existing declaration nodes into the new order.

The optional `order` option is a comma-separated list. It accepts these exact category names:

- `static-fields`
- `instance-fields`
- `static-initializers`
- `instance-initializers`
- `constructors`
- `methods`
- `types`

It also accepts the aliases `fields` and `initializers`. Omitted categories are appended in their
default order. Unknown names are ignored. If `order` is omitted or blank, the default order is
used. For example:

```xml
<rule>
  <id>members.sort</id>
  <enabled>true</enabled>
  <options>
    <option>
      <name>order</name>
      <value>fields,constructors,methods,types</value>
    </option>
  </options>
</rule>
```

In a group configuration, `members.sort` selects the rule and `order` supplies its category order.

### Benefits and safety

Consistent member order improves navigation and reduces structural review noise. Sorting is stable
within categories, so unrelated methods or fields are not alphabetized or otherwise reordered.
Only direct body-declaration lists are considered; declarations are never renamed or regenerated.
Comments and associated declaration text move with their declaration through the source rewriter.
Already ordered lists are left untouched. Since moving members can affect initialization order and
therefore behavior, inspect the resulting diff before adopting this rule broadly, especially for
fields and initializers with side effects.

## Choosing rules safely

Formatting rules can affect many lines. A cautious rollout is to enable `format.trailing-whitespace`
first, review the diff, then enable `format.indentation`, `imports.organize`, or `members.sort` as
separate changes. Use `format.source` when the project is prepared to accept complete-file
formatting. Run the Maven build and review the generated diff after every configuration change.
