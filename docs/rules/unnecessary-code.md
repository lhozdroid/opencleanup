# Unnecessary-code rules

The unnecessary-code group removes code that has no effect, expresses a simpler
equivalent operation, or repeats information that Java already supplies. These
rewrites reduce noise, improve readability, and make later maintenance easier
without changing the observable behavior of the program.

## Configuration

Each rule is enabled with the exact option value `true`. The rule IDs below can
be placed in the `options` list of an `unnecessary-code` group, or configured as
individual rules. See [Maven configuration](../configuration.md) for the
complete plugin configuration format.

```xml
<rule>
  <id>unnecessary-code</id>
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
```

The group is disabled when `<enabled>false</enabled>` is set. An option with a
value other than `true` does not enable one of these rules. Rules are applied
only to Java source files selected by the plugin, and a file is written only
when at least one rewrite changes its contents.

## Rule reference

### `unused-code.imports`

**What it does:** Removes unused single-type imports and single-static imports.

**How it works:** The rule collects names used outside import declarations and
removes an import whose imported name is not used. Wildcard imports are retained
because a source-only name scan cannot prove which members they provide.

**Benefit:** Keeps the import section small and removes misleading dependencies.

**Safety:** Imports are removed only when their imported simple name has no
matching use in the compilation unit. Wildcard imports and imports that may be
needed by source that cannot be resolved are skipped.

### `unused-code.private-members`

**What it does:** Removes unused private nested types, constructors, fields,
methods, and unused parameters of private methods.

**How it works:** The rule counts source references to private declarations and
removes a declaration or parameter when no reference remains. Private methods
are retained when an invocation with the same method name is found.

**Benefit:** Removes dead implementation details and reduces the maintenance
surface of a class.

**Safety:** Only private declarations are considered. Public, protected, and
package-visible members are never removed. The rule skips declarations that
have source references; reflective, generated, or uses outside this source file
cannot be proven by source analysis, so review changes involving private APIs before
enabling this rule broadly.

### `unused-code.suppress-warnings`

**What it does:** Removes unnecessary warning tokens from `@SuppressWarnings`
annotations and removes the annotation when no tokens remain.

**How it works:** It recognizes the `unused` and `deprecation` tokens and keeps
each token while the corresponding source condition may still exist. Array and
single-value annotation forms are both simplified.

**Benefit:** Keeps suppression annotations focused so that real warnings remain
visible to developers and build tooling.

**Safety:** Only recognized tokens that are no longer needed are removed.
Unknown warning names and annotations that may still suppress a relevant
warning are preserved.

### `casts.unnecessary`

**What it does:** Removes casts whose expression already has the cast type.

**How it works:** It checks syntax with a conservative set of expressions,
including matching primitive literals, `String` literals, and object creation
whose declared type matches the cast.

**Benefit:** Removes visual noise and makes the actual type of an expression
clearer.

**Safety:** A cast is changed only when the supported syntax proves that its
removal preserves the expression type. Casts involving inheritance, overload
selection, generics, or unresolved type information are skipped.

### `strings.redundant-substring-argument`

**What it does:** Changes `text.substring(0, text.length())` to
`text.substring(0)`.

**How it works:** It recognizes a two-argument `substring` call whose receiver is
a simple name and whose second argument is that same name followed by
`.length()`.

**Benefit:** Removes an argument that the method already defaults to the end of
the string.

**Safety:** The receiver and length expression must be stable simple-name
expressions with the expected method names. Calls with computed receivers,
different variables, other overloads, or side effects are left unchanged.

### `arrays.fill`

**What it does:** Replaces a simple loop that assigns the same value to every
array element with `Arrays.fill`.

**How it works:** It recognizes a counted `for` loop that starts at index zero,
increments by one, tests against the array length, and assigns a passive value
to the current array element.

**Benefit:** Expresses the intent directly and removes error-prone index-loop
boilerplate.

**Safety:** The rewrite requires a simple array name, the standard bounds
shape, and a side-effect-free value expression. Loops with unusual bounds,
multiple statements, index-dependent values, comments in sensitive positions,
or complex expressions are skipped.

### `null-checks.evaluate-nullable`

**What it does:** Evaluates supported null comparisons when the result is known
from syntax.

**How it works:** It turns comparisons of `null` with expressions that always
produce a non-null value, such as an object creation or a class literal, into a
boolean literal.

**Benefit:** Removes conditions that cannot affect control flow and exposes
constant behavior to readers and later compiler optimizations.

**Safety:** Only comparisons with a provably constant result are replaced.
Variables, method calls, field accesses, and expressions whose nullability
cannot be established are preserved.

### `negation.push-down`

**What it does:** Pushes `!` through logical `&&` and `||` expressions using
De Morgan's law.

**How it works:** `!(a && b)` becomes `!a || !b`, and `!(a || b)` becomes
`!a && !b`, including supported extended operands.

**Benefit:** Makes individual conditions easier to inspect and can remove a
large outer negation from a complex predicate.

**Safety:** The rule preserves required parentheses and skips expressions where
operator precedence, assignment, conditional evaluation, or another negation
could change meaning. It does not reorder operands, so short-circuit order is
preserved.

### `booleans.value-rather-than-comparison`

**What it does:** Replaces comparisons of a boolean expression with `true` or
`false` with the boolean expression itself or its negation.

**How it works:** `enabled == true` becomes `enabled`, `enabled == false`
becomes `!enabled`, and equivalent `!=` forms are simplified accordingly.

**Benefit:** Removes redundant boolean vocabulary and makes the condition read
like the value it tests.

**Safety:** Only boolean-literal comparisons are considered. The original
operand remains in the same evaluation position, and unsupported or ambiguous
expressions are skipped.

### `booleans.double-negation`

**What it does:** Removes two consecutive logical negations.

**How it works:** `!!condition` is replaced with `condition`, preserving the
inner expression and its evaluation order.

**Benefit:** Eliminates unnecessary punctuation and exposes the condition being
tested.

**Safety:** Only the exact nested prefix form is changed. A negation inside a
different operator, a bitwise complement, or an expression requiring different
parentheses is not rewritten.

### `statements.redundant-comparison`

**What it does:** Removes a standalone comparison statement whose result is
ignored.

**How it works:** A comparison used as an expression statement is deleted when
evaluating it cannot provide a useful result and the supported operands are
passive.

**Benefit:** Removes statements that communicate no lasting effect and reduces
dead code in method bodies.

**Safety:** The rule does not remove comparisons that may invoke methods,
trigger field access behavior, or otherwise carry observable evaluation. A
comparison used as part of a larger expression is never treated as a standalone
statement.

### `constructors.redundant-super`

**What it does:** Removes an explicit no-argument `super();` constructor call
when Java supplies the same call automatically.

**How it works:** It finds the first statement in a constructor body and removes
the call when it has no arguments and no qualifying expression.

**Benefit:** Shortens constructors without changing superclass initialization.

**Safety:** Calls with arguments, an explicit qualifier, or a position other
than the first constructor statement are retained. Constructor bodies that do
not match the exact implicit-call form are left unchanged.

### `blocks.unreachable`

**What it does:** Removes statements that occur after an unconditional flow
terminator in a block.

**How it works:** It scans block statements after `return`, `throw`, `break`, or
`continue` and removes the unreachable suffix where the syntax proves that
control cannot reach it.

**Benefit:** Removes misleading dead code and makes the actual control flow
easier to follow.

**Safety:** Only syntactically unreachable statements are removed. The rule
does not infer reachability through arbitrary method calls, conditions, or
exception analysis, and it preserves comments when the edit cannot be safely
located.

### `collections.direct-map-method`

**What it does:** Replaces an eligible map presence check followed by a map
operation with the direct map method that already expresses the operation.

**How it works:** It recognizes supported map receiver and method shapes where
the intermediate check is redundant, then removes the check while retaining the
map operation.

**Benefit:** Reduces duplicated lookups and makes map access intent clearer.

**Safety:** Only supported concrete source shapes and stable receivers are
changed. Calls with different receivers, side effects, intervening statements,
or behavior that depends on the separate check are skipped.

### `collections.clone`

**What it does:** Simplifies eligible collection-copy sequences into a
constructor initialized from the existing collection.

**How it works:** A supported empty collection construction followed immediately
by `addAll(source)` becomes construction with `source` as its argument.

**Benefit:** Uses one expression instead of two statements and communicates that
the result is a copy.

**Safety:** The receiver must be a supported concrete collection, the constructor
must be non-anonymous and empty, and the source expression must be stable. The
rule skips unsupported types, extra operations, and sequences where evaluation
order could change.

### `maps.clone`

**What it does:** Simplifies eligible map-copy sequences into a map constructor
initialized from the source map.

**How it works:** A supported empty map construction followed immediately by
`putAll(source)` becomes construction with `source` as its argument.

**Benefit:** Makes map copying shorter and easier to understand.

**Safety:** Only supported concrete map types, empty constructors, stable source
expressions, and adjacent `putAll` calls are changed. Anonymous types,
additional constructor arguments, and non-adjacent operations are preserved.

### `assignments.overridden`

**What it does:** Removes an earlier assignment when the same variable is
assigned again before the earlier value is read.

**How it works:** It examines adjacent simple assignment statements and removes
the first assignment when both the target and the passive right-hand side meet
the supported shape.

**Benefit:** Removes overwritten initialization and reduces misleading
intermediate state.

**Safety:** Only adjacent assignments to the same simple target are considered.
Assignments with method calls, complex targets, side effects, intervening
statements, or potentially significant right-hand-side evaluation are skipped.

### `modifiers.redundant`

**What it does:** Removes modifiers that are implied by an interface or
annotation declaration.

**How it works:** It removes syntax-guaranteed redundant modifiers from the
supported declaration kinds while leaving modifiers with independent meaning.

**Benefit:** Reduces visual noise and keeps declarations focused on information
that readers need.

**Safety:** The implementation is limited to interface and annotation
declarations. Class, enum, record, field, and method modifiers are retained
unless the rule can prove that the modifier is redundant for a supported
declaration.

### `if.embedded`

**What it does:** Combines a nested `if` statement into one short-circuit
condition.

**How it works:**

```java
if (outer) {
    if (inner) {
        action();
    }
}
```

becomes:

```java
if (outer && inner) {
    action();
}
```

**Benefit:** Removes a level of indentation and makes the complete condition
visible in one place.

**Safety:** The rule requires a conservative nested shape, preserves the body,
and avoids cases containing local declarations or overlapping comments. It does
not merge shapes where an `else`, dangling-`else` binding, or evaluation-order
change could affect behavior.

### `semicolons.redundant`

**What it does:** Removes standalone empty statements from blocks.

**How it works:** A statement containing only `;` is removed when it is an
independent block statement rather than part of a required declaration or loop
syntax.

**Benefit:** Removes accidental empty statements that make control flow harder
to read.

**Safety:** Only AST nodes representing standalone empty statements are removed.
Semicolons required by Java grammar and semicolons inside strings, comments, or
text blocks are untouched.

### `comparators.redundant`

**What it does:** Removes a natural-order comparator argument when the called
operation already uses natural ordering by default.

**How it works:** It recognizes supported `Collections.sort`, `Collections.min`,
and `Collections.max` calls with `Comparator.naturalOrder()` or an equivalent
simple natural comparator, then removes that comparator argument.

**Benefit:** Removes a repeated default and makes the collection operation more
compact.

**Safety:** Only recognized natural comparators and supported collection methods
are changed. Custom comparators, reversed order, overloaded calls, and
expressions with uncertain type or side effects are retained.

### `arrays.creation`

**What it does:** Removes unnecessary explicit array creation from
`Arrays.asList` calls.

**How it works:**

```java
Arrays.asList(new String[] {first, second})
```

becomes:

```java
Arrays.asList(first, second)
```

**Benefit:** Uses the varargs form directly and removes redundant array syntax.

**Safety:** Only an unqualified `Arrays.asList` call with one array initializer
argument is changed. Calls with array dimensions, side-effect-sensitive
expressions, other methods, or a different receiver are skipped.

### `arrays.initializer`

**What it does:** Removes the redundant array type and `new` keyword from a
local array declaration initializer.

**How it works:** `int[] values = new int[] {1, 2};` becomes
`int[] values = {1, 2};` when the initializer type matches the declaration.

**Benefit:** Makes local array initialization shorter without losing the type
information already present in the declaration.

**Safety:** The rule applies only to local variable declarations whose array
creation has an initializer and whose type matches the declaration. Fields,
method arguments, sized arrays, anonymous constructs, and mismatched types are
left unchanged.

### `returns.expression`

**What it does:** Simplifies a direct conditional return containing boolean
literals.

**How it works:**

```java
return condition ? true : false;
```

becomes `return condition;`, while the inverse becomes `return !condition;`.

**Benefit:** States the returned boolean value directly and removes unnecessary
conditional syntax.

**Safety:** Only direct return expressions with exactly two boolean literal
branches are changed. Conditional expressions with method calls, non-boolean
values, nested behavior, or other branch expressions are preserved.

### `returns.useless`

**What it does:** Removes a final empty `return;` from a `void` method.

**How it works:** It removes the return when it is the final statement in a
method body and the method has no return value.

**Benefit:** Removes a statement that Java inserts implicitly and leaves the
method body focused on its actual work.

**Safety:** Only a top-level final empty return in a `void` method is removed.
Returns inside conditionals, loops, nested blocks, constructors, methods with a
value, and methods whose final statement is not that return are retained.

### `continues.useless`

**What it does:** Removes a final unlabeled `continue` from a `while` or `do`
loop body when it is the loop's natural next action.

**How it works:** It checks the final statement of the loop body and removes an
unlabeled continue when no later statement can be skipped by its removal.

**Benefit:** Removes control-flow noise at the end of loops.

**Safety:** Labeled continues are never removed. The rule only handles the final
statement of supported `while` and `do` bodies and leaves continues in `for`
loops, nested control flow, or bodies where the target loop is ambiguous.

### `loops.unlooped-while`

**What it does:** Converts a `while` loop that unconditionally breaks after its
body into an `if` statement.

**How it works:** A supported block such as:

```java
while (ready) {
    work();
    break;
}
```

becomes:

```java
if (ready) {
    work();
}
```

**Benefit:** Represents a loop that can execute at most once with the simpler
one-shot conditional form.

**Safety:** The loop must have a block body ending in an unlabeled `break`, and
the rule removes only that final break. Loops with labeled breaks, additional
flow paths, nested-loop interactions, or unsupported body shapes are skipped.

## Safety model

These rules make source edits through the Java syntax tree. They do not execute
the application, infer runtime values, or rewrite compiled bytecode. Each rule
checks a narrow syntax pattern and declines to edit code when the available
source information cannot establish equivalence. Rewrites are collected before
the file is written, and unchanged files are not rewritten.

Review generated diffs, run the project's tests and static checks, and commit
the result through normal source-control review. For a gradual rollout, enable
individual rule options first, then expand to the complete group after the
resulting diffs are accepted.
