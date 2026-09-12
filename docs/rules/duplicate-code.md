# Duplicate-code rules

Duplicate-code rules remove repeated boolean expressions and repeated control-flow bodies while keeping the observable order of operations intact. They operate on the parsed Java syntax tree, compare only the nodes needed for a candidate pattern, and schedule a replacement through the AST rewrite engine. A rule does nothing when it cannot prove that the replacement is safe from syntax alone.

These rules do not require rule-specific options. Enable them individually with their rule ID, or enable the `duplicate-code` group from the plugin configuration.

## `expressions.operand-factorization`

### What it transforms

Factors one common operand from two short-circuit conjunctions:

```java
if ((ready && cached) || (ready && valid)) {
    use();
}
```

becomes:

```java
if (ready && (cached || valid)) {
    use();
}
```

The common operand may be on either side of either `&&` expression.

### How recognition works

The rule visits binary `||` nodes, requires each side to be a binary `&&` with exactly two operands, and compares the operands structurally. Structural comparison means the parsed syntax of the two operands is identical; it does not depend on variable type resolution. The common operand and both remaining operands must be passive names, boolean literals, or logical negations of those expressions.

### Benefit

The shared condition is written once, reducing repetition and making the short-circuit decision structure easier to read.

### Conservative safety and skip conditions

The rule skips extended boolean chains, method calls, assignments, increments, object creation, field accesses, expressions with unknown evaluation effects, and candidates containing unsupported syntax. It also skips a candidate when the common expression cannot be matched exactly. Restricting all participating expressions to passive syntax prevents moving or duplicating side effects.

## `expressions.ternary-operator`

### What it transforms

Converts a boolean selection expressed as two conjunctions joined by `||` into a conditional expression:

```java
boolean result = (enabled && primary) || (!enabled && fallback);
```

becomes:

```java
boolean result = enabled ? primary : fallback;
```

The positive and negative branches may appear in either order, and the condition may occur on either side of each conjunction.

### How recognition works

The rule visits binary `||` nodes and extracts two-operand `&&` branches. It looks for one passive condition `X` in one branch and its logical negation `!X` in the other. It then treats the remaining operands as the true and false values. Matching is structural, and parentheses are ignored while matching equivalent expressions.

### Benefit

The result communicates directly that one value is selected when a condition is true and another when it is false. It removes duplicated boolean control flow from value-producing expressions.

### Conservative safety and skip conditions

Only passive names, boolean literals, and their logical negations are accepted for the condition and result expressions. The rule skips method calls, assignments, increments, field or array accesses, extended `&&`/`||` chains, unmatched negations, and candidates whose branch order cannot be proven. This avoids changing evaluation count or short-circuit behavior.

## `comparisons.strictly-equal-or-different`

### What it transforms

Replaces repeated boolean combinations that describe equality or difference:

```java
boolean same = (left && right) || (!left && !right);
boolean different = (left && !right) || (!left && right);
```

with:

```java
boolean same = left == right;
boolean different = left != right;
```

### How recognition works

The rule visits a binary `||` and requires two binary `&&` branches with two operands each. It records each operand together with whether it is negated, then compares the underlying syntax trees. Matching equal polarity produces `==`; matching opposite polarity produces `!=`. Branch and operand order are accepted when the resulting relation is unchanged.

### Benefit

The direct comparison expresses the intended relationship with fewer operators and less duplicated logic.

### Conservative safety and skip conditions

The rule accepts only passive names, boolean literals, and logical negations of those expressions. It skips calls, assignments, mutable member access, extended boolean expressions, incomplete pairs, and any combination that does not establish exactly two operands. No rewrite is made when syntax-only analysis cannot establish the relation.

## `blocks.merge-conditional`

### What it transforms

Merges consecutive `if`/`else if` branches that have identical bodies:

```java
if (first) {
    publish();
} else if (second) {
    publish();
}
```

becomes:

```java
if (first || second) {
    publish();
}
```

Longer chains are merged one eligible branch at a time.

### How recognition works

The rule visits an `if` statement, follows its `else if` chain, and compares the branch statements as syntax trees. It requires matching bodies and combines the corresponding conditions with short-circuit `||`. The replacement keeps the first body and removes the duplicate branch.

### Benefit

One implementation replaces multiple copies of the same block, making future changes less error-prone and reducing visual noise.

### Conservative safety and skip conditions

The candidate must be a direct `if`/`else if` chain with no other statements between branches. The rule skips chains containing comments, pattern-based conditions, mismatched bodies, unsupported branch structure, or a final `else` that would make the rewrite ambiguous. Comments are treated as attached source content and are never silently relocated.

## `control-flow.merge`

### What it transforms

Pulls an identical trailing statement out of two braced branches:

```java
if (valid) {
    prepare();
    save();
} else {
    retry();
    save();
}
```

becomes:

```java
if (valid) {
    prepare();
} else {
    retry();
}
save();
```

### How recognition works

The rule visits `if` statements with braced `then` and `else` blocks. It compares the final statement in both blocks structurally and, when equal, removes both copies and inserts one copy after the complete `if` statement. The candidate is also checked for declarations that could change scope when moved.

### Benefit

Shared final work is represented once, which reduces duplication while retaining the branch-specific work in each branch.

### Conservative safety and skip conditions

Both branches must be blocks with a non-empty statement list and the same final statement. The rule skips candidates containing comments, local variable declarations in the candidate, labels, control-flow constructs whose target could change, or any structure that could make the moved statement execute in a different scope. It does not move code from a branch that can fall through differently.

## `blocks.one-if-for-fall-through`

### What it transforms

Combines consecutive sibling `if` statements with the same terminating body:

```java
if (first) {
    return;
}
if (second) {
    return;
}
```

becomes:

```java
if (first || second) {
    return;
}
```

### How recognition works

The rule examines statements in a containing block and groups adjacent `if` statements. Every statement in the group must have no `else`, an identical body, and a body ending in `return`, `throw`, `break`, or `continue`. The conditions are combined in source order with short-circuit `||`, and all but one duplicate statement are removed.

### Benefit

It presents several equivalent exit checks as one decision and avoids repeating the same terminating action.

### Conservative safety and skip conditions

Only adjacent sibling statements are considered. The rule skips non-braced or non-terminating bodies, `else` branches, pattern-based conditions, comments in the candidate range, mismatched bodies, and candidates where labels or jump targets could change. Conditions are not reordered.

## `blocks.redundant-fall-through-end`

### What it transforms

Removes a branch-ending jump when the same jump immediately follows the entire `if`:

```java
if (ready) {
    return value;
}
return value;
```

becomes:

```java
if (ready) {
}
return value;
```

When the branch is empty after the removal, the source rewrite engine may format it as an empty block. The transformation is deliberately limited to the exact supported syntax.

### How recognition works

The rule visits an `if` inside a block, reads the statement immediately after it, and compares it with the final statement of the braced `then` block and, when present, the braced `else` block. It removes a matching `return`, `throw`, `break`, or `continue` from the branch.

### Benefit

It removes a duplicate exit statement and leaves one shared fall-through endpoint.

### Conservative safety and skip conditions

The branch must be braced, the following statement must be an exact structural match, and the jump must be one of the four supported kinds. The rule skips comments overlapping the `if`, empty branches, non-jump statements, labels, unbraced branches, and candidates without a direct following sibling. It does not attempt to infer equivalent expressions or jump targets.

## `conditions.redundant-if`

### What it transforms

Removes a negated condition that is already implied by the preceding branch:

```java
if (ready) {
    useReady();
} else if (!ready) {
    useFallback();
}
```

becomes:

```java
if (ready) {
    useReady();
} else {
    useFallback();
}
```

### How recognition works

The rule finds an outer `if` with an `else` containing exactly one nested `if`. It compares the outer condition with the nested condition after removing one logical-not from the nested condition. The nested `if` must have no second `else`, so its body can safely become the outer `else` body.

### Benefit

The control flow becomes shorter and makes the mutually exclusive `if`/`else` relationship explicit.

### Conservative safety and skip conditions

Conditions are limited to passive names or qualified names. The rule skips calls, assignments, compound expressions, nested `else` branches, comments in the candidate, and expressions that are not exact logical negations. It does not simplify conditions by assuming facts about arbitrary expressions.

## `conditions.pull-out-if`

### What it transforms

Pulls the same inner condition out of both branches of an outer decision:

```java
if (primary) {
    if (available) {
        usePrimary();
    }
} else {
    if (available) {
        useBackup();
    }
}
```

becomes:

```java
if (available) {
    if (primary) {
        usePrimary();
    } else {
        useBackup();
    }
}
```

### How recognition works

The rule visits an outer `if` whose `then` and `else` branches each contain exactly one inner `if`. Both inner statements must have no `else`, and their conditions must have identical syntax trees. The shared condition becomes the new outer condition; the original outer condition becomes an inner `if` with the two original inner bodies as its `then` and `else` branches.

### Benefit

The shared guard is evaluated once and the branch-specific action is kept in one compact decision tree. This reduces duplicated checks and clarifies the relationship between the guard and the selected action.

### Conservative safety and skip conditions

The outer and shared conditions must be passive names or qualified names. The rule skips branches with additional statements, nested `else` clauses, comments, calls, assignments, pattern variables, or any condition whose evaluation or scope cannot be preserved by moving it. It only handles the exact two-branch shape described above.

## Configuration example

Enable the complete group:

```xml
<plugin>
  <groupId>io.github.lhozdroid</groupId>
  <artifactId>opencleanup-maven-plugin</artifactId>
  <version>1.1.0</version>
  <configuration>
    <rules>
      <rule>
        <id>duplicate-code</id>
        <enabled>true</enabled>
      </rule>
    </rules>
  </configuration>
</plugin>
```

Enable selected rules instead:

```xml
<rules>
  <rule>
    <id>expressions.operand-factorization</id>
    <enabled>true</enabled>
  </rule>
  <rule>
    <id>conditions.pull-out-if</id>
    <enabled>true</enabled>
  </rule>
</rules>
```

The plugin processes Java files in the configured compile source roots. It writes a file only when at least one selected rule schedules a valid replacement. Parsing failures, unsupported syntax, comments that could be displaced, and uncertain control-flow cases are left unchanged; they are not guessed at or forcefully rewritten.
