# Performance rules

Performance rules replace source patterns that create unnecessary objects, repeat work, or perform avoidable operations. They are selected through the `performance` rule group or by configuring an individual rule ID in the Maven plugin.

Each rewrite is conservative. A rule changes code only when the source shape is recognized and the replacement can be made without changing the result, required null behavior, evaluation order, or synchronization guarantees. Code that does not meet those conditions is left unchanged.

## Configuration

Enable the complete group:

```xml
<configuration>
  <rules>
    <rule>
      <id>performance</id>
      <enabled>true</enabled>
    </rule>
  </rules>
</configuration>
```

Enable selected rules individually:

```xml
<configuration>
  <rules>
    <rule>
      <id>strings.is-blank</id>
      <enabled>true</enabled>
    </rule>
    <rule>
      <id>boxing.value-of</id>
      <enabled>true</enabled>
    </rule>
  </rules>
</configuration>
```

When a rule is disabled, its source patterns are not changed. If multiple enabled rules affect the same code, they run in configuration order and the resulting source is written only when it differs from the original file.

## Rules

### `fields.single-use`

What it does: Replaces a private field that is initialized once and read exactly once with its initializer at that use site.

How it works: The rule finds a field with one simple declarator, a non-null initializer, and one eligible read. It copies the initializer into the read location and removes the field declaration when the field is not used as mutable state.

Benefit: Removes field storage and an indirection, which can reduce object state and make a one-purpose value local to the operation that consumes it.

Safety and skips: The rule skips fields that are public, protected, static, final, written after initialization, referenced more than once, used from an initializer or nested type, or initialized with a construct whose movement could change behavior. It also skips declarations without a simple initializer and ambiguous references.

### `loops.break`

What it does: Converts a supported unconditional loop with a terminating `break` into a loop with an explicit condition.

How it works: For a `while (true)` loop whose body begins with a condition that exits through `break`, the rule moves that condition into the loop expression and retains the remaining statements as the loop body.

Benefit: Makes the loop termination condition visible at the loop header and avoids entering an iteration that can immediately terminate.

Safety and skips: Only the recognized `while (true)` shape is considered. The rule skips loops with multiple exits, labeled breaks, continues that affect control flow, side-effect-sensitive condition expressions, or statements that cannot be moved without changing execution order.

### `classes.static-inner`

What it does: Adds `static` to a member class that does not need an instance of its enclosing class.

How it works: The rule examines a nested member class and checks that its declaration and contained members do not depend on enclosing-instance state. It then adds the `static` modifier.

Benefit: Removes the hidden reference to the enclosing object, reducing retained object graphs and allowing the nested class to be created without an outer instance.

Safety and skips: Anonymous classes, local classes, interfaces, already-static types, classes with instance-dependent members, and classes whose source shape is not unambiguously independent are skipped. The rule does not alter class placement or visibility.

### `strings.string-builder`

What it does: Rewrites eligible string concatenation expressions into `StringBuilder` operations.

How it works: The rule recognizes a concatenation expression containing a string literal, creates a builder, appends the operands in their original order, and converts the final builder value to a string.

Benefit: Reduces the number of temporary string objects created by a multi-part concatenation, especially when the expression contains several operands.

Safety and skips: Operand order is preserved. The rule skips concatenations that are not clearly string concatenations, contain unsupported nested expressions, or could change the required string-conversion behavior. It does not rewrite arbitrary concatenation chains or introduce a builder where the source is already simple.

### `strings.plain-replacement`

What it does: Changes a literal `replaceAll` call to `replace` when the regular-expression engine is not needed.

How it works: The rule requires a literal pattern that has no regular-expression metacharacters and a replacement literal that has no replacement-group or escape semantics. It changes only the method name, leaving the receiver and arguments intact.

Benefit: Avoids regular-expression parsing and matching overhead for a plain text substitution.

Safety and skips: The rule skips non-literal patterns, patterns containing regular-expression operators, replacement strings containing `$` or `\\`, and calls with an unexpected argument count. Dynamic expressions and unknown semantics are never guessed.

### `strings.is-blank`

What it does: Replaces an eligible `trim().isEmpty()` check with `isBlank()`.

How it works: The rule identifies a direct call chain on a simple name, such as `text.trim().isEmpty()`, and replaces it with `text.isBlank()`.

Benefit: Expresses the intent directly and lets the string implementation perform the blank check without creating the trimmed intermediate string.

Safety and skips: Only the exact simple-name receiver shape is changed. Calls with complex receivers, additional method calls, arguments, or a different method chain are skipped. The original receiver is evaluated once in the replacement.

### `operators.lazy-logical`

What it does: Replaces eager boolean operators with short-circuit operators when both operands are known to be boolean expressions.

How it works: The rule changes `&` to `&&` and `|` to `||` for boolean expressions whose operands are side-effect free and whose evaluation does not rely on both operands always running.

Benefit: Prevents unnecessary evaluation of the right-hand operand and can avoid work when the left-hand value already determines the result.

Safety and skips: The rule does not change numeric bitwise expressions. It skips operands with method calls, assignments, increments, object creation, or other observable effects, because short-circuiting could prevent required behavior. Unsupported or ambiguous expressions remain unchanged.

### `boxing.value-of`

What it does: Replaces eligible one-argument wrapper constructors such as `new Integer(value)` with `Integer.valueOf(value)`.

How it works: The rule recognizes direct construction of supported boxed primitive types and changes the construction expression to the corresponding static `valueOf` call.

Benefit: Reuses cached wrapper instances where available and avoids an unnecessary allocation.

Safety and skips: Only supported wrapper types with exactly one argument are considered. Anonymous subclasses, unsupported constructors, multiple arguments, and expressions whose constructor identity cannot be established are skipped. The rewrite does not change the declared type.

### `boxing.primitive-comparison`

What it does: Replaces eligible wrapper comparisons with primitive comparison methods or operations.

How it works: The rule recognizes supported wrapper comparison forms and changes them to a comparison of their primitive values while preserving the original comparison direction and operator.

Benefit: Avoids wrapper-level comparison work and makes value comparison explicit.

Safety and skips: Null-sensitive comparisons, identity comparisons, unsupported wrapper types, and expressions with uncertain evaluation behavior are skipped. The rule does not rewrite code where unboxing could introduce a new null failure or change identity semantics.

### `parsing.primitive`

What it does: Uses primitive parsing methods instead of wrapper-producing factory methods when the target declaration is primitive.

How it works: For a primitive declaration initialized from a simple wrapper factory call, the rule changes methods such as `Integer.valueOf(text)` to `Integer.parseInt(text)` while preserving the argument.

Benefit: Avoids creating a wrapper object that is immediately unboxed.

Safety and skips: The rule only changes recognized primitive declarations and exact simple-name factory calls. Wrapper declarations, assignments where the target type is unknown, unsupported methods, and complex receiver or argument expressions are skipped.

### `serialization.primitive`

What it does: Replaces eligible wrapper-to-string conversion calls with the corresponding static primitive serialization method.

How it works: The rule recognizes exact wrapper serialization calls and changes them to the wrapper type's `toString(value)` form, preserving the value expression.

Benefit: Avoids creating or routing through an unnecessary wrapper object when only its textual value is needed.

Safety and skips: Only supported wrapper types and exact recognized call shapes are changed. Null-sensitive values, instance methods, overloaded calls that cannot be identified safely, and complex unsupported forms are skipped.

### `boxing.primitive-rather-than-wrapper`

What it does: Changes eligible local wrapper variables to primitive variables.

How it works: The rule finds local declarations of supported wrapper types initialized with a non-null literal value and replaces the declared wrapper type with its primitive equivalent. It removes a redundant wrapper factory around the initializer when the value is directly convertible.

Benefit: Reduces heap allocation, memory use, and unboxing operations for values that do not need wrapper behavior.

Safety and skips: Fields, parameters, null initializers, non-literal initializers, unsupported wrappers, and declarations where wrapper nullability or identity may matter are skipped. The rule does not change public APIs or infer conversions across unrelated statements.

### `regular-expressions.precompile`

What it does: Precompiles a regular expression that is matched repeatedly in one method body.

How it works: The rule finds a local string containing a literal regular expression and at least two simple `matches` calls using that variable. It introduces a `Pattern` beside the declaration and changes each match to `pattern.matcher(value).matches()`.

Benefit: Parses and compiles the expression once instead of once per match operation.

Safety and skips: The rule requires a literal regex declaration in a block and repeated simple-name uses. It skips patterns used fewer than two times, reassigned variables, complex receivers, incompatible scopes, anonymous-class use, and names that cannot be generated without a collision. It does not move a pattern outside its original scope.

### `strings.buffer-to-builder`

What it does: Replaces direct `StringBuffer` declarations and constructions with `StringBuilder`.

How it works: The rule changes a local or field declaration when every fragment is directly initialized with a non-anonymous `StringBuffer` construction, and changes the matching constructor type as well.

Benefit: Removes synchronization overhead when the buffer is used only within a thread-confined context.

Safety and skips: The rule requires the declaration and every initializer to be the simple `StringBuffer` shape. Anonymous subclasses, mixed declarations, indirect assignments, complex types, and code that may require synchronization are skipped. It does not rewrite arbitrary references or method signatures.

### `strings.no-string-creation`

What it does: Removes unnecessary `String` object creation for an empty string or a string literal.

How it works: `new String()` becomes `""`, and `new String("literal")` becomes the literal itself. The replacement is copied as an expression at the original location.

Benefit: Reuses the string literal and avoids an unnecessary `String` allocation.

Safety and skips: Only the exact no-argument and one-literal-argument constructors are changed. Constructors receiving character arrays, byte arrays, another string expression, a charset, or other values are skipped because they may perform conversion or have different behavior. Anonymous subclasses are also skipped.

### `booleans.literal`

What it does: Evaluates boolean expressions made entirely from boolean literals and replaces them with the resulting literal.

How it works: The rule evaluates supported `!`, `&&`, `||`, and parenthesized literal expressions, including extended operands, then writes `true` or `false` in place of the expression.

Benefit: Removes dead boolean computation and makes the resulting condition immediately clear to readers and later tools.

Safety and skips: Expressions containing variables, fields, method calls, assignments, object creation, or operators outside the supported literal forms are skipped. This avoids changing unboxing, evaluation order, or side effects.

## Safety model

The plugin parses each Java source file, applies only the enabled rules, and writes a file only when a rewrite produces a different result. Rules inspect source structure before scheduling edits; they do not execute application code, load application classes, or evaluate arbitrary expressions.

When a pattern is incomplete, ambiguous, dynamic, null-sensitive, synchronization-sensitive, or outside a rule's supported shape, the source is left unchanged. Review the generated diff as part of the normal build process, and run the project's tests after applying rewrites.
