# Code style rules

Code-style rules make small, reviewable changes to control flow, expressions,
numeric literals, declarations, and lambda syntax. They are opt-in: a rule is
run only when it is selected directly or enabled through the `code-style`
group in the Maven configuration.

The plugin parses each Java source file into a syntax tree, checks the exact
shape required by the rule, and schedules a source rewrite only for a proven
candidate. If the source is ambiguous, contains an unsupported construct, or
does not match the rule's safe shape, the rule leaves it unchanged. The
rewriter writes a file only when a selected rule produces a change.

## Complete rule list

The following 13 rule IDs are available in this group. For a group
configuration, the option name is normally the rule ID and the value
`true` selects a boolean rule. The enum and category options are described in
the individual sections below.

| Rule id | Rewrite | Options |
| --- | --- | --- |
| `control-statements.blocks` | Add or remove blocks around control statements. | `always`, `jdt-style`, or `never` |
| `control-statements.else-if` | Join an `else` block containing an `if` into `else if`. | `true` or `false` |
| `control-statements.simplify-boolean-if-else` | Simplify boolean-producing `if`/`else` statements. | `true` or `false` |
| `control-statements.reduce-indentation` | Reduce avoidable nesting in control flow. | `true` or `false` |
| `control-statements.use-switch` | Replace eligible conditional chains with `switch`. | `true` or `false` |
| `control-statements.use-add-all` | Use bulk collection operations where equivalent. | `true` or `false` |
| `expressions.parentheses` | Add or remove optional parentheses. | `always` or `never` |
| `expressions.extract-increment` | Move increment expressions to a dedicated statement when appropriate. | `true` or `false` |
| `expressions.pull-up-assignment` | Move an assignment out of a conditional expression when safe. | `true` or `false` |
| `expressions.instanceof` | Convert eligible class-literal instance checks to `instanceof`. | `true` or `false` |
| `number-literals.suffix` | Normalize numeric literal suffixes. | `true` or `false` |
| `variable-declarations.final` | Add `final` to selected declarations. | `fields`, `parameters`, `locals` |
| `functional-interfaces.lambda-method-reference` | Simplify eligible lambda and method-reference syntax. | `true` or `false` |

## Configuration

### Enable the group

The `code-style` group receives one option per selected rule. This example
enables every rule and shows every supported option name and value:

```xml
<configuration>
  <rules>
    <rule>
      <id>code-style</id>
      <enabled>true</enabled>
      <options>
        <option>
          <name>control-statements.blocks</name>
          <value>jdt-style</value>
        </option>
        <option>
          <name>control-statements.else-if</name>
          <value>true</value>
        </option>
        <option>
          <name>control-statements.simplify-boolean-if-else</name>
          <value>true</value>
        </option>
        <option>
          <name>control-statements.reduce-indentation</name>
          <value>true</value>
        </option>
        <option>
          <name>control-statements.use-switch</name>
          <value>true</value>
        </option>
        <option>
          <name>control-statements.use-add-all</name>
          <value>true</value>
        </option>
        <option>
          <name>expressions.parentheses</name>
          <value>never</value>
        </option>
        <option>
          <name>expressions.extract-increment</name>
          <value>true</value>
        </option>
        <option>
          <name>expressions.pull-up-assignment</name>
          <value>true</value>
        </option>
        <option>
          <name>expressions.instanceof</name>
          <value>true</value>
        </option>
        <option>
          <name>number-literals.suffix</name>
          <value>true</value>
        </option>
        <option>
          <name>fields</name>
          <value>true</value>
        </option>
        <option>
          <name>parameters</name>
          <value>true</value>
        </option>
        <option>
          <name>locals</name>
          <value>true</value>
        </option>
        <option>
          <name>functional-interfaces.lambda-method-reference</name>
          <value>true</value>
        </option>
      </options>
    </rule>
  </rules>
</configuration>
```

Boolean group options use `true` to select a rule. `false` leaves that rule
unselected. The group itself must also have `<enabled>true</enabled>` (or omit
the element, because it defaults to `true`). An omitted or empty option list
selects no code-style rules.

### Select a rule directly

Direct selection is useful when one rule needs an isolated configuration:

```xml
<configuration>
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
</configuration>
```

For a directly selected rule, `<enabled>true</enabled>` selects the rule;
the option controls its mode only where the rule has one. Direct
`control-statements.blocks` defaults to `always` when its option is absent.
Direct `expressions.parentheses` defaults to `never`, and direct
`variable-declarations.final` selects fields, parameters, and locals when no
category options are supplied.

Rule IDs and option names must match exactly. Duplicate selections are
resolved by the first selection, so configure a rule either directly or in
this group, not both.

## `control-statements.blocks`

### What it does

Adds or removes `{}` around the body of `if`, `else`, `while`, `do`, classic
`for`, and enhanced `for` statements.

### How it works

- `always` wraps every eligible body that is not already a block.
- `never` removes a block only when it contains exactly one statement.
- `jdt-style` is this plugin's conservative multiline block mode. It adds a
  block when the body spans more than one source line and leaves single-line
  bodies unchanged. It does not remove existing blocks.

### Benefit

Consistent blocks make control-flow boundaries visible and reduce accidental
errors when statements are added later. `never` can produce a more compact
style when single-statement bodies are preferred.

### Safety and skipped code

Existing blocks are not wrapped again. In `never` mode, blocks containing
comments, more than one statement, or a nested `if` whose dangling `else`
meaning could change are retained. An unsupported mode value causes no
rewrite. The rule does not change a block merely because it is empty.

## `control-statements.else-if`

### What it does

Converts this shape:

```java
if (ready) {
    start();
} else {
    if (fallback) {
        recover();
    }
}
```

into an `else if` with the same nested condition and body.

### How it works

The rule replaces an `else` block only when that block contains exactly one
nested `if` statement. The nested statement is moved into the `else` slot;
its condition and branch bodies are not otherwise changed.

### Benefit

The resulting control flow is flatter and communicates that the conditions
are alternative branches of the same decision.

### Safety and skipped code

An `else` block with additional statements, an empty block, or a non-`if`
statement is left unchanged. The boolean group option `true` selects the
rule; `false` does not select it.

## `control-statements.simplify-boolean-if-else`

### What it does

Replaces an `if`/`else` that returns opposite boolean literals with one return
of the condition or its negation:

```java
if (valid) {
    return true;
} else {
    return false;
}
```

becomes:

```java
return valid;
```

### How it works

Both branches must be a direct `return` or a block containing exactly one
`return`. When the then branch returns `true` and the else branch returns
`false`, the condition is returned. When the values are reversed, the
condition is returned with `!`.

### Benefit

It removes ceremony while retaining the original boolean decision in a form
that is easier to read.

### Safety and skipped code

The rule skips an `if` without an `else`, branches with non-literal return
expressions, equal boolean results, extra statements, or any other branch
shape. It does not inspect or simplify unrelated boolean expressions.

## `control-statements.reduce-indentation`

### What it does

Moves safe statements out of an unnecessary `else` nesting level, or inverts
a final two-branch `if` so the common continuation is less indented.

### How it works

The first supported form has a then branch that cannot complete normally,
such as a branch ending in `return`, `throw`, `break`, or `continue`. The
non-`if` else branch is then moved after the `if`.

The second supported form is a two-branch `if` that is the last statement in
its enclosing block. Both branches must be able to complete normally. The
condition is negated, the former else branch becomes the then branch, and the
former then branch is moved after the `if`.

### Benefit

Less indentation exposes the main path of a method and makes terminal guard
clauses easier to scan.

### Safety and skipped code

The rule uses syntax-only completion checks. It skips nested `else if` cases,
empty branches, candidates outside a block, candidates containing comments,
and moved branches containing local variable declarations. It also skips
cases where the required completion or final-statement conditions are not
met. The conservative checks avoid changing scope or control-flow meaning.

## `control-statements.use-switch`

### What it does

Converts a chain of equality tests into a `switch`:

```java
if (code == 1) {
    one();
} else if (code == 2) {
    two();
} else {
    other();
}
```

becomes a switch on `code` with cases `1`, `2`, and a default branch.

### How it works

The selector must be one simple variable declared as `byte`, `short`, `char`,
or `int` in the containing method. Every branch must compare that same
selector with a numeric or character literal using `==`. At least two cases
are required. Branches are copied into isolated blocks so local declarations
retain their original scope, and a `break` is added when a branch can complete
normally.

### Benefit

A switch expresses multi-way selection directly, groups related cases, and
removes repeated selector comparisons.

### Safety and skipped code

The rule skips reference, string, enum, and unsupported primitive selectors;
non-literal labels; duplicate labels; mixed selectors; chains with fewer than
two cases; comments in the candidate; and branches containing `break` or
`continue`. It also skips numeric suffixes that cannot be used as supported
case labels. Unsupported or ambiguous conditional shapes remain unchanged.

## `control-statements.use-add-all`

### What it does

Replaces a loop that adds every element from one collection to another with a
single `addAll` call:

```java
for (Item item : source) {
    target.add(item);
}
```

becomes:

```java
target.addAll(source);
```

### How it works

The enhanced `for` expression, the target receiver, and the loop parameter
must each be simple names. The body must contain only the matching one-
argument `target.add(item)` call. The loop is replaced by a call with the
same target and source names.

### Benefit

The bulk operation states the intent directly and lets the collection
implementation perform the transfer without an explicit loop.

### Safety and skipped code

Loops with extra statements, a different method, a different argument, a
receiver or source expression with side effects, comments, or the same name
for source and target are left unchanged. The rule does not infer collection
types or rewrite a loop when the exact syntax does not match.

## `expressions.parentheses`

### What it does

Normalizes optional parentheses in expressions.

### How it works

- `always` adds parentheses around a nested infix expression when its operator
  differs from the containing infix operator. It also adds parentheses around
  an `instanceof` expression when it is nested in an infix expression.
- `never` removes parentheses only when the syntax tree proves that the
  expression can keep the same grouping and remain valid in its surrounding
  expression context.

The implementation also accepts `mode` as an alternate option name for a
direct configuration, while `expressions.parentheses` remains the documented
rule option name.

### Benefit

Consistent grouping can make precedence visible, while removing unnecessary
grouping reduces visual noise without changing the parsed expression.

### Safety and skipped code

The rule does not add parentheses throughout an entire file and does not
remove parentheses when precedence, associativity, or the surrounding grammar
is uncertain. Complex expressions, special syntax contexts, and candidates
that cannot be safely replaced are retained. An unsupported mode causes no
rewrite. With a direct rule and no mode, `never` is used.

## `expressions.extract-increment`

### What it does

Separates a direct increment or decrement from a local declaration:

```java
int value = counter++;
```

becomes:

```java
int value = counter;
counter++;
```

### How it works

The rule handles a declaration with exactly one fragment in a block. The
initializer must be one of `++name`, `--name`, `name++`, or `name--`, where
`name` is a simple variable name. Prefix operations are placed before the
declaration and converted to postfix form; postfix operations remain postfix
and are placed after the declaration.

### Benefit

The declaration's stored value and the later counter update become explicit,
which can make initialization code easier to review.

### Safety and skipped code

Declarations with multiple fragments, non-simple operands, other initializer
expressions, or a non-block parent are left unchanged. The rule does not move
increments from loop headers or expressions with receivers, indexes, or other
side effects.

## `expressions.pull-up-assignment`

### What it does

Moves a direct assignment out of an `if` condition and places it immediately
before the `if`:

```java
if ((ready = check())) {
    start();
}
```

becomes:

```java
ready = check();
if (ready) {
    start();
}
```

### How it works

The complete condition, optionally surrounded by parentheses, must be a plain
`=` assignment. Its left-hand side must be a simple name, a `this` field
access, or a `super` field access. If the `if` is not already in a statement
list, the rule creates a block to keep the assignment and `if` together.

### Benefit

The assignment and the decision are separated, making accidental assignments
inside conditions easier to spot.

### Safety and skipped code

Assignments nested inside `&&`, `||`, comparisons, ternaries, or other larger
conditions are not moved because doing so could change short-circuit behavior.
Compound assignments, indexed targets, method-call receivers, and other
unstable targets are skipped.

## `expressions.instanceof`

### What it does

Converts a reference class literal check:

```java
Widget.class.isInstance(value)
```

to:

```java
value instanceof Widget
```

### How it works

The receiver must be a non-parameterized reference type literal, the method
must be `isInstance`, and there must be exactly one argument. The argument is
accepted when its type binding or syntax identifies it as a reference value.

### Benefit

The `instanceof` form is shorter and directly communicates the language-level
type test.

### Safety and skipped code

Primitive class literals, parameterized class literals, primitive arguments,
generic calls, unresolved expressions with no conservative reference shape,
and calls with the wrong arity are retained. The rule does not rewrite a call
when the required reference conversion cannot be established safely.

## `number-literals.suffix`

### What it does

Changes lowercase numeric suffixes to uppercase: `l` becomes `L`, `f` becomes
`F`, and `d` becomes `D`.

### How it works

Only the final suffix character of a numeric token is considered. Digits,
radix prefixes, separators, exponents, and the kind of suffix are not
otherwise changed.

### Benefit

Uniform suffixes are easier to distinguish from digits and make numeric
literal style consistent.

### Safety and skipped code

Numbers without a supported lowercase `l`, `f`, or `d` suffix are unchanged.
The rule does not add a suffix, remove one, or normalize any other part of a
numeric literal.

## `variable-declarations.final`

### What it does

Adds `final` to selected declarations that are initialized and not written
again. The supported categories are independent:

- `fields` selects private, non-static instance fields.
- `parameters` selects method and constructor parameters.
- `locals` selects local variables, enhanced-`for` variables, catch
  variables, and declaration variables used by loops or resources.

### How it works

In a group, add `fields`, `parameters`, and/or `locals` with value `true`.
At least one of these category options selects the rule, and each enabled
category is processed independently:

```xml
<options>
  <option>
    <name>variable-declarations.final</name>
    <value>true</value>
  </option>
  <option>
    <name>fields</name>
    <value>true</value>
  </option>
  <option>
    <name>parameters</name>
    <value>true</value>
  </option>
  <option>
    <name>locals</name>
    <value>true</value>
  </option>
</options>
```

With a directly selected rule, the same category option names are supplied
under that rule. If no category options are supplied directly, all three
categories are considered.

### Benefit

The modifier documents the intended immutability of a value and allows the
compiler and reviewers to catch accidental reassignment.

### Safety and skipped code

Fields must be private instance fields with initializers; static, already
final, volatile, transient, or uninitialized fields are skipped. Parameters
and locals must not be written after declaration, including through supported
assignment and increment forms. Declarations with ambiguous writes, missing
initializers where one is required, unsupported scopes, or existing `final`
modifiers are left unchanged.

## `functional-interfaces.lambda-method-reference`

### What it does

Replaces a lambda whose body directly delegates to a method or constructor
with a method reference:

```java
items.forEach(item -> this.save(item));
```

becomes:

```java
items.forEach(this::save);
```

### How it works

The rule recognizes direct method calls on `this`, `super`, or an explicitly
typed first lambda parameter, as well as direct constructor calls. It also
handles a lambda block containing one return statement. Arguments must map
directly to lambda parameters in the same order, with no extra expressions or
type arguments.

### Benefit

Method references remove redundant parameter plumbing and make delegation or
construction intent immediately visible.

### Safety and skipped code

Lambdas with reordered, transformed, captured, or otherwise ambiguous
arguments are retained. Anonymous constructions, qualified or side-effecting
receivers, unsupported lambda bodies, implicit receiver ambiguity, and static
context cases where `this` is unavailable are skipped. The rule does not use a
method reference when the direct parameter mapping cannot be proven.

## Safety summary

Code-style rules are deterministic and opt-in. They inspect Java source under
the Maven project's compile source roots and apply only the selected rule
implementations. AST rewrites preserve the surrounding source structure as
far as the candidate permits; rules that move statements explicitly reject
comments, declarations, or control transfers when moving them could alter
scope or flow. A source shape outside a rule's supported pattern is a normal
no-op, not an error.

Review generated diffs as part of the normal build workflow, especially when
enabling several control-flow or expression rules together. Select a small
set first, run the build and tests, then expand the configuration as desired.
