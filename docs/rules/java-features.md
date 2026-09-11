# Java feature rules

The Java feature rules modernize source code while preserving its intended
behavior. They replace older language or library idioms with equivalent forms
that are easier to read, safer to maintain, or better aligned with the Java
language level used by the project.

Each rule is opt-in through the Maven configuration. A rule changes a source
file only when its syntax matches the rule's supported shape. Unsupported,
ambiguous, incomplete, or potentially behavior-changing code is left as-is.

## Rule catalog

The catalog below preserves the rule IDs and their exact Java-level and option
values. A Java level means the minimum source level for the resulting syntax
or API; it does not change the compiler level configured for the Maven build.

| Rule ID | Transformation | Java level / options |
| --- | --- | --- |
| `modules.use-module-imports` | Use module-aware imports where applicable. | Java 25+; recognized but skipped for Java 21 |
| `instanceof.pattern-matching` | Use pattern variables with `instanceof`. | Implemented for matching first declarations; Java 16+ |
| `instanceof.to-switch` | Convert eligible pattern checks to `switch`. | Java 21+ |
| `switch.expressions` | Convert eligible switch statements to switch expressions. | Java 14+ |
| `variable-declarations.var` | Use `var` for eligible local declarations. | Java 10+ |
| `functional-interfaces.convert` | Convert between anonymous functional implementations and lambdas. | `lambda` or `anonymous` |
| `functional-interfaces.simplify-lambda` | Simplify eligible lambda expressions. | `true` or `false` |
| `comparators.criteria` | Use comparator construction based on comparison criteria. | Java 8+ |
| `strings.join` | Use `String.join` for eligible concatenation patterns. | Java 8+ |
| `try-with-resources` | Convert eligible resource cleanup to try-with-resources. | Java 7+ |
| `multi-catch` | Combine catch clauses with the same body. | Java 7+ |
| `type-parameters.remove-redundant` | Remove redundant type arguments. | Java 7+ |
| `hash.modernize` | Use modern hash construction APIs where equivalent. | Java 7+ |
| `objects.equals` | Use `Objects.equals` for null-safe equality. | Java 7+ |
| `system-properties.constants` | Replace supported system-property lookups with constants. | Java 7+ |
| `loops.enhanced-for` | Convert eligible indexed loops to enhanced `for` loops. | Implemented for conservative array/list loops; Java 5+ |
| `boxing.autoboxing` | Use autoboxing where equivalent. | Java 5+ |
| `boxing.unboxing` | Use unboxing where equivalent. | Java 5+ |

## Detailed behavior

### `modules.use-module-imports`

**What it does:** Selects module-aware import syntax when source and target
language levels support it.

**How it works:** The rule is recognized by the configuration system, but the
Java 21 parser and rewrite pipeline do not generate module-import declarations.
When selected, it performs no source rewrite and returns the file unchanged.

**Benefit:** Selecting the rule remains configuration-compatible without
silently emitting syntax that the plugin's parser or a Java 21 build cannot
understand.

**Safety:** This is an intentional no-op for this plugin's Java 21 execution
environment. It never invents module names, changes ordinary imports, or
produces newer syntax. Enable it only when a future parser/runtime explicitly
supports the required Java level.

### `instanceof.pattern-matching`

**What it does:** Replaces a type test followed by a separate variable
declaration with an `instanceof` pattern variable.

**How it works:** For a matching first declaration, code shaped like
`if (value instanceof String) { String text = (String) value; ... }` can become
`if (value instanceof String text) { ... }`. The declaration and redundant cast
are removed when the variable can be introduced without changing scope.

**Benefit:** The type check, cast, and variable declaration become one readable
operation, reducing repeated code and opportunities for mismatched casts.

**Safety:** The rule requires a direct, matching type declaration and a scope
where the new pattern variable is valid. It skips declarations with different
types, uses that would escape the condition's scope, complex control flow, and
cases where evaluation order could change.

### `instanceof.to-switch`

**What it does:** Converts a conservative chain of type tests into a pattern
`switch` statement.

**How it works:** A compatible `if`/`else if` chain that tests the same value
with type patterns is rewritten to switch labels using the tested value. Each
branch keeps its original body and branch order.

**Benefit:** Several related type alternatives are presented as one structured
decision, making exhaustive handling and future extensions easier to see.

**Safety:** Only chains with one stable selector, supported type patterns, and
compatible branch bodies are changed. The rule skips null-sensitive selectors,
side-effecting expressions, mixed conditions, fall-through-dependent code, and
branches whose control flow cannot be represented safely.

### `switch.expressions`

**What it does:** Converts eligible assignments or returns surrounding a switch
statement into a switch expression.

**How it works:** A switch whose cases consistently assign the same target or
return a value can be represented with `case ... ->` arms and an expression
result. Case-local statements are retained when they can be moved into a safe
expression or block arm.

**Benefit:** The value-producing nature of the switch is explicit, reducing
mutable temporary variables and making missing result branches easier to spot.

**Safety:** The rule requires a consistent target or return shape across cases.
It skips fall-through-sensitive cases, unrelated statements, multiple targets,
side-effect-sensitive expressions, and switches that do not have a safe value
equivalent.

### `variable-declarations.var`

**What it does:** Replaces an eligible explicit local type with `var`.

**How it works:** A local declaration with an initializer whose type can be
inferred directly is rewritten from `Type value = initializer` to
`var value = initializer`.

**Benefit:** Repeated or verbose implementation types are removed while the
initializer remains the single source of type information.

**Safety:** The rule changes local declarations only when inference is clear
from the initializer. It skips declarations without initializers, multiple
declarators, implicit typing that would obscure the code, and shapes where
source or target compatibility could change.

### `functional-interfaces.convert`

**What it does:** Converts between an anonymous implementation of a functional
interface and a lambda expression.

**How it works:** The `lambda` option converts supported anonymous functional
implementations to lambdas. The `anonymous` option converts supported lambdas
to anonymous implementations. The selected value determines the direction.

**Benefit:** Lambda form is compact for simple behavior, while anonymous form
can be clearer or necessary when an explicit object body is preferred.

**Safety:** Conversion is limited to interfaces with one abstract method and
simple bodies whose parameters, return behavior, and exception behavior remain
compatible. The rule skips anonymous classes with extra members, state,
overrides, comments that cannot be preserved safely, or ambiguous target types.

### `functional-interfaces.simplify-lambda`

**What it does:** Removes unnecessary syntax from lambda expressions.

**How it works:** With the option set to `true`, eligible single-expression or
single-return lambda bodies are shortened by removing redundant braces,
`return`, and semicolons. With `false`, the rule is disabled for that
configuration.

**Benefit:** Small functional expressions become easier to scan without
changing their parameter list or result.

**Safety:** Only a lambda with one safe expression or one directly returned
expression is simplified. Block bodies with multiple statements, declarations,
control flow, or required side effects are preserved.

### `comparators.criteria`

**What it does:** Replaces eligible hand-written comparator logic with criteria
based comparator construction.

**How it works:** A comparator that compares one extracted value at a time can
be expressed with comparator factories and key extractors, preserving the
comparison direction where it is directly recognizable.

**Benefit:** Comparator intent is visible in the compared fields instead of
being buried in repetitive branching and integer comparisons.

**Safety:** The rule requires a supported, consistently ordered comparison
shape. It skips comparators with mixed fields, custom tie-breaking behavior,
side effects, null handling that is not explicit, or control flow that cannot
be mapped exactly.

### `strings.join`

**What it does:** Replaces eligible repeated string concatenation with a
delimiter-based join operation.

**How it works:** A supported sequence of string values and the same separator
is rewritten to `String.join(delimiter, ...)` while preserving the value order.

**Benefit:** Delimiter handling is centralized, reducing trailing-separator
mistakes and making the intended output format clear.

**Safety:** Only concatenations with a stable delimiter and supported operands
are changed. The rule skips null-sensitive expressions, arbitrary interleaved
side effects, non-string conversions whose evaluation order matters, and
patterns that would produce different empty-value behavior.

### `try-with-resources`

**What it does:** Converts explicit resource closing in a `finally` block to a
try-with-resources statement.

**How it works:** A resource created before a `try` and closed in the matching
cleanup path is moved into the try resource specification. The body and catch
handling remain in place.

**Benefit:** Resource closure is automatic on normal and exceptional exits, and
the language can preserve suppressed exceptions from cleanup failures.

**Safety:** The rule requires a directly identifiable resource and matching
`close` operation. It skips resources with multiple owners, conditional or
out-of-order closure, reassignment, extra `finally` behavior, or cleanup code
whose execution cannot be preserved.

### `multi-catch`

**What it does:** Combines catch clauses that execute the same body.

**How it works:** Adjacent catches for compatible exception types with
identical bodies are merged into one catch parameter using the `|` separator.

**Benefit:** Duplicate error-handling code is removed while the list of handled
exception types remains explicit.

**Safety:** The rule checks that the bodies are equivalent and that the caught
types can legally share a multi-catch clause. It skips catches with different
variable usage, incompatible exception relationships, or different behavior.

### `type-parameters.remove-redundant`

**What it does:** Removes explicit constructor type arguments when the diamond
operator can infer the same type arguments.

**How it works:** A parameterized object creation such as
`new ArrayList<String>()` is shortened to `new ArrayList<>()` when the target
type and initializer context provide the same inference result.

**Benefit:** Generic construction is shorter and avoids repeating type
information already present in the declaration or target context.

**Safety:** Only constructor type arguments that are redundant under the
configured Java level are removed. The rule skips explicit arguments needed by
the target type, anonymous classes, unusual bounds, and contexts where
inference could select a different type.

### `hash.modernize`

**What it does:** Replaces eligible manual hash-building code with a standard
hash construction call.

**How it works:** Supported combinations of field hashing and hash-code
assembly are converted to the corresponding modern utility form, retaining the
same ordered inputs.

**Benefit:** Hash implementations become shorter and less likely to omit a
field or apply inconsistent hash arithmetic.

**Safety:** The rule changes only recognized, side-effect-free hash patterns. It
skips custom arithmetic, conditional fields, mutable intermediate state, and
implementations whose exact ordering or null behavior cannot be proven equal.

### `objects.equals`

**What it does:** Replaces a null-sensitive equality idiom with a null-safe
equality call.

**How it works:** Supported equality comparisons are rewritten to
`Objects.equals(left, right)`, adding the required import when the source uses
imports managed by the rewrite engine.

**Benefit:** Either operand may be null without an exception, and the intended
null-safe comparison is explicit.

**Safety:** The rule changes only object-equality shapes whose operands have
the same equality meaning. It skips primitive comparisons, identity checks using
`==` intentionally, overloaded equality-like methods, and expressions with
side effects where operand evaluation must not move.

### `system-properties.constants`

**What it does:** Replaces supported system-property lookups with the matching
standard runtime constant.

**How it works:** A recognized property name is replaced with the corresponding
constant expression when that constant has the same contract and value source.

**Benefit:** The code communicates that the value is a standard runtime
characteristic and avoids repeated string-key lookups.

**Safety:** Only an exact, supported property key is considered. Dynamic keys,
default-value overloads, unknown properties, and calls whose exception or
configuration behavior matters are left unchanged.

### `loops.enhanced-for`

**What it does:** Converts eligible indexed array or list loops to an enhanced
`for` loop.

**How it works:** A loop that initializes an index, checks the collection size,
increments by one, and reads the current element in a supported way is
rewritten to iterate directly over the elements.

**Benefit:** Index bookkeeping disappears, reducing off-by-one errors and
making element-oriented code easier to read.

**Safety:** The implemented conversion is limited to conservative array/list
loop shapes. It skips loops that use the index for other work, mutate the
collection, depend on exact index values, alter iteration order, or have a
nonstandard increment or termination condition.

### `boxing.autoboxing`

**What it does:** Replaces explicit primitive-to-wrapper factory calls with
Java's automatic boxing conversion.

**How it works:** Supported calls such as wrapper `valueOf` around a primitive
are removed when the surrounding assignment, argument, or return context
already requires the wrapper type.

**Benefit:** The code is shorter and lets the language select the normal
boxing conversion without manual factory noise.

**Safety:** Only contexts with an unambiguous wrapper target are changed. The
rule skips explicit factory calls whose overload, caching behavior, evaluation
order, or type conversion would become unclear.

### `boxing.unboxing`

**What it does:** Removes explicit wrapper-to-primitive conversion calls when
Java can perform the same unboxing conversion.

**How it works:** A supported wrapper conversion such as `integer.intValue()`
is removed when the surrounding operation requires the corresponding
primitive, leaving the expression in a context where Java performs unboxing.

**Benefit:** Repetitive conversion calls are removed and the expected primitive
operation is easier to see.

**Safety:** The rule preserves explicit conversions when they are needed to
select an overload or make a type conversion clear. It also skips potentially
null expressions when removing the call would obscure the point at which a
null failure occurs.

## Java 21 parsing and compatibility

The plugin parses Java source using a Java 21 syntax model before applying any
selected rule. The parser reads source text into a structured syntax tree;
rules then propose narrowly scoped replacements against that tree. A file is
written only when a replacement produces different source text.

The parser level and a rule's Java level serve different purposes. The parser
defines the syntax the plugin can read. The rule's Java level identifies the
minimum Java version required by the resulting language feature or library API.
The plugin does not change `maven.compiler.release`, the project's runtime, or
the compiler configuration.

For this reason, `modules.use-module-imports` is a deliberate no-op. Its
resulting syntax requires Java 25+, while this plugin parses and rewrites with
Java 21 support. Selecting the rule is accepted for configuration consistency,
but the plugin neither emits Java 25 module-import syntax nor changes an
existing file for that rule.

## Safety model

The rewrite engine applies rules to one parsed source file at a time and
reports only files whose text actually changed. Rules do not download code,
execute application code, or modify compiled classes. They do not infer
project-wide behavior from incomplete source; when a rewrite depends on
information the parser cannot establish, the original source is retained.

Before enabling a broad rule set, review the generated diff and run the normal
build and tests. The safest rollout is to enable a small group, inspect its
changes, and then expand the configuration once the project's source and
language level have been verified.
