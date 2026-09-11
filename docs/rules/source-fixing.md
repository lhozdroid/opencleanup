# Source-fixing rules

Source-fixing rules make narrowly defined changes to comparisons, bitwise checks, and deprecated declarations. They are useful when a project wants a consistent source form or wants to remove a compatibility wrapper without changing the surrounding code.

Every rule in this group is opt-in. A rule is selected by its rule ID in the plugin configuration. Rules do not modify comments, string contents, or files outside the configured source roots. A source file is written only when at least one selected rule produces a valid rewrite.

## Configuration

The rules can be enabled individually in the plugin's `<rules>` list:

```xml
<plugin>
  <groupId>io.github.lhozdroid</groupId>
  <artifactId>opencleanup-maven-plugin</artifactId>
      <version>1.0.0</version>
  <configuration>
    <rules>
      <rule>
        <id>comparisons.invert-equals</id>
        <enabled>true</enabled>
      </rule>
      <rule>
        <id>comparisons.standard</id>
        <enabled>true</enabled>
      </rule>
      <rule>
        <id>bitwise.check-sign</id>
        <enabled>true</enabled>
      </rule>
      <rule>
        <id>deprecated.replace-method</id>
        <enabled>true</enabled>
      </rule>
      <rule>
        <id>deprecated.replace-field</id>
        <enabled>true</enabled>
      </rule>
    </rules>
  </configuration>
</plugin>
```

These rules have no rule-specific options. Omitting `<enabled>` uses the default value `true` for a rule that is explicitly listed. An omitted rule is not selected. For the complete plugin configuration, source-root behavior, and execution examples, see [`docs/configuration.md`](../configuration.md).

## Rule summary

| Rule ID | Transformation |
| --- | --- |
| `comparisons.invert-equals` | Moves `null` from the left side of `==` or `!=` to the right side. |
| `comparisons.standard` | Moves a numeric or character literal on the left of a relational comparison to the right and reverses the operator. |
| `bitwise.check-sign` | Changes a supported bitwise-expression `> 0` check to a `!= 0` check. |
| `deprecated.replace-method` | Replaces calls to eligible local deprecated forwarding methods with the method they forward to. |
| `deprecated.replace-field` | Replaces references to eligible deprecated compile-time constants with equivalent literals. |

## `comparisons.invert-equals`

### What it does

This rule standardizes null comparisons so the non-null expression appears first:

```java
null == value
null != value
```

becomes:

```java
value == null
value != null
```

### How it works

The rule visits binary equality comparisons and rewrites only those where the left operand is exactly the `null` literal. It copies the right operand to the left position and copies `null` to the right position. Only `==` and `!=` are considered.

### Benefit

One consistent comparison form makes null checks easier to scan and search. It also places the expression being tested at the visually prominent left side of the comparison.

### Safety and skipped code

Swapping the operands of Java equality and inequality comparisons preserves the result, including when the non-null operand is a method call or another expression with side effects. The rule skips comparisons that use more than two operands and comparisons whose left operand is not exactly `null`. It does not change `equals(...)` method calls, relational comparisons, or already-standard comparisons.

## `comparisons.standard`

### What it does

This rule puts a numeric or character literal on the right side of a relational comparison:

```java
10 < value
'a' <= character
```

becomes:

```java
value > 10
character >= 'a'
```

### How it works

The rule recognizes a two-operand comparison using `<`, `<=`, `>`, or `>=` when the left operand is a numeric or character literal and the right operand is not one of those literals. It exchanges the operands and uses the mathematically equivalent reversed operator.

The operator mapping is:

| Original | Rewritten |
| --- | --- |
| `literal < expression` | `expression > literal` |
| `literal <= expression` | `expression >= literal` |
| `literal > expression` | `expression < literal` |
| `literal >= expression` | `expression <= literal` |

### Benefit

Consistent literal placement makes threshold checks easier to recognize and keeps related comparisons visually uniform.

### Safety and skipped code

Each rewrite is an algebraically equivalent swap of a relational comparison. The rule skips equality comparisons, literal-to-literal comparisons, extended comparisons with additional operands, and expressions whose left side is not a numeric or character literal. It does not attempt to infer types or rewrite arbitrary arithmetic expressions.

## `bitwise.check-sign`

### What it does

This rule replaces a supported bitwise-expression sign check with an explicit non-zero check:

```java
(flags & mask) > 0
```

becomes:

```java
(flags & mask) != 0
```

The same transformation is available for `|` and `^` expressions.

### How it works

The rule looks for a two-operand comparison whose left side is a two-operand bitwise AND (`&`), OR (`|`), or XOR (`^`) expression and whose right side is the decimal integer literal `0`. When the comparison operator is `>`, it changes only that operator to `!=`.

### Benefit

The rewritten form states the intended condition directly: the selected bitwise result must contain a non-zero value. This avoids presenting the check as a numeric sign test when the code is testing whether any bits are set.

### Safety and skipped code

The rule is deliberately shape-based and does not rewrite every possible bitwise condition. It skips `<`, `>=`, and `<=` checks; zero written in another form such as `0x0`; bitwise expressions with additional operands; non-bitwise expressions; and expressions wrapped in unsupported structures. Parentheses around the bitwise expression or zero are accepted. The plugin does not claim safety for cases outside this exact pattern, so those cases remain unchanged.

## `deprecated.replace-method`

### What it does

This rule removes a local deprecated forwarding method at its call sites. For example:

```java
/** @deprecated use {@link #current(int)} */
int old(int value) {
    return current(value);
}

int result() {
    return old(41);
}
```

can become:

```java
int result() {
    return current(41);
}
```

The deprecated declaration itself is not deleted.

### Supported deprecated mappings

This rule supports mappings expressed by a local deprecated method's forwarding implementation and its `@deprecated` documentation:

| Deprecated call | Supported replacement |
| --- | --- |
| `old()` | `current()` |
| `old(value)` | `current(value)` |
| `old(first, second)` | `current(first, second)` |
| `receiver.old(value)` | `receiver.current(value)` when the forwarding method is an instance method without its own receiver |

The deprecated method must contain exactly one `return replacement(parameters);` statement for a value-returning method, or exactly one `replacement(parameters);` expression statement for a void method. Forwarded arguments must be direct parameter references, and the replacement name must be present as a method link in the method's `@deprecated` tag. The implementation supports any parameter count that satisfies these conditions; it is not a hard-coded library-method table.

### How it works

The rule resolves method bindings, finds the matching local declaration, verifies that the declaration is marked deprecated, reads the first linked replacement method from its `@deprecated` tag, and confirms that the method body forwards directly to that method. It then copies the forwarding call and substitutes each parameter with the corresponding argument from the original call.

### Benefit

Call sites use the current implementation directly, reducing compatibility indirection and making the intended replacement visible to readers and tools. Keeping the old declaration preserves source compatibility for callers that have not been rewritten.

### Safety and skipped code

The rule requires resolved bindings and an exact declaration match. It skips APIs outside the project source, unresolved calls, overloaded or ambiguous declarations, methods without an explicit replacement link, methods with more than one body statement, non-forwarding bodies, forwarding expressions that are not direct parameters, and calls inside the deprecated declaration itself. It also skips replacements when the forwarded method cannot be resolved. No call is changed when the rule cannot prove that the forwarding shape is preserved.

## `deprecated.replace-field`

### What it does

This rule replaces a reference to a deprecated field with the field's compile-time constant value:

```java
/** @deprecated use the value directly */
static final int OLD_LIMIT = 100;

int limit() {
    return OLD_LIMIT;
}
```

can become:

```java
int limit() {
    return 100;
}
```

### Supported deprecated mappings

The supported mapping is from a deprecated field reference to an equivalent Java literal. The field must expose a compile-time constant value. Supported value types are:

| Field value type | Replacement form |
| --- | --- |
| `boolean` | `true` or `false` |
| `char` | A character literal |
| `String` | A string literal with the same value |
| Integral or floating-point number | A numeric literal with the required type suffix where applicable |

Qualified references such as `Constants.OLD_LIMIT`, explicit field access, and superclass field access are replaced as one complete reference. The rule does not map one field name to another field name and does not maintain a configurable replacement table.

### How it works

The rule resolves each field reference, checks that it is a deprecated field, reads its compile-time constant value, creates a literal of the corresponding Java type, and replaces the complete reference. Non-finite floating-point values are not emitted as numeric literals.

### Benefit

The source no longer depends on a deprecated constant name, while the generated literal preserves the value available at compile time. This can simplify migration and make constant values visible at the use site.

### Safety and skipped code

Only resolved deprecated fields with a compile-time constant value are changed. The rule skips fields whose values are computed at runtime, object references, non-constant arrays or enums, unresolved references, declarations, and unsupported numeric values. It does not remove the original field, alter its declaration, evaluate arbitrary expressions, or replace references when the value cannot be represented safely as a Java literal.

## Safety model

Source-fixing rules use local syntax and, where required, resolved symbols to decide whether a rewrite is eligible. They do not execute project code, download replacement definitions, or edit generated output unless that output is included in the configured source roots. Unsupported or ambiguous code is left unchanged. Review the generated diff and compile the project after enabling these rules, especially when deprecated replacements depend on project-specific bindings.
