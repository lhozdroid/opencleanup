# Member-access rules

Member-access rules make the owner of a field, method, or static member explicit. This improves
readability, makes name resolution easier to review, and gives a codebase one consistent style.
Each rule works on Java source during the `rewrite` goal. Rules only schedule a rewrite when the
source structure provides enough information to preserve the meaning of the expression.

## Configuration

Select an individual rule by using its rule ID as `<id>`:

```xml
<rule>
  <id>member-accesses.non-static-fields</id>
  <enabled>true</enabled>
  <options>
    <option>
      <name>member-accesses.non-static-fields</name>
      <value>always</value>
    </option>
  </options>
</rule>
```

When a rule is configured through the `member-accesses` group, use the same rule ID as the option
name. The option values are case-sensitive; use the exact values documented below. If a direct
rule has no option, its default behavior is used.

## Rules

### `member-accesses.non-static-fields`

#### What it does

Adds `this.` before eligible unqualified instance-field references. For example:

```java
count++;
```

becomes:

```java
this.count++;
```

#### How it works

The rule collects instance fields declared by the surrounding named type and qualifies references
that are currently unqualified. Field declarations, already-qualified accesses, static contexts,
and syntax that is not an expression reference are not changed.

#### Benefit

The result clearly distinguishes state owned by the current object from local variables and method
parameters. It also makes code review and later refactoring easier because the receiver is visible at
the use site.

#### Options

| Option name | Exact values | Meaning |
| --- | --- | --- |
| `member-accesses.non-static-fields` | `always` | Qualify every field reference that can be proven safe. This is the default. |
| `member-accesses.non-static-fields` | `when-necessary` | Do not add qualification. This mode is accepted for compatibility and leaves the source unchanged because a binding-free rewrite cannot prove when qualification is necessary. |

#### Safety and skipped cases

The rule skips a reference when a local variable, parameter, or lambda variable may shadow the same
name; when the reference is inside a static method, static initializer, or static field initializer;
when the member belongs to an interface-like type; or when the node is a declaration, type name,
import, annotation, method name, or already-qualified access. Anonymous and inherited-member cases
are left unchanged when ownership cannot be established from the source being processed.

### `member-accesses.non-static-methods`

#### What it does

Adds `this.` before eligible unqualified calls to instance methods. For example:

```java
refresh();
```

becomes:

```java
this.refresh();
```

#### How it works

The rule collects instance method names declared directly by the surrounding named type. An
unqualified invocation is qualified only when the type has no same-named static method that could
make the target ambiguous.

#### Benefit

The receiver becomes explicit, so readers can immediately see that the call targets the current
object. This is useful in classes containing helpers, callbacks, or inherited behavior with similar
names.

#### Options

| Option name | Exact values | Meaning |
| --- | --- | --- |
| `member-accesses.non-static-methods` | `always` | Qualify every method call that can be proven safe. This is the default. |
| `member-accesses.non-static-methods` | `when-necessary` | Do not add qualification. This mode is accepted for compatibility and leaves the source unchanged because a binding-free rewrite cannot prove when qualification is necessary. |

#### Safety and skipped cases

The rule skips calls that already have a receiver, calls in static methods or static initializers,
calls whose name may be shadowed by a local variable, and calls whose target cannot be established
as a directly declared instance method. It also skips names shared by static and instance methods,
anonymous-class cases, inherited-only methods, and other cases where qualification could affect
overload resolution or dispatch.

### `member-accesses.static-members`

#### What it does

Qualifies eligible unqualified static field and method references with the declaring type. For
example:

```java
MAX_SIZE;
```

can become:

```java
Limits.MAX_SIZE;
```

The exact type name is taken from the source declaration.

#### How it works

The rule records static fields and methods declared by source-local named types. It then replaces
safe unqualified references with a type-qualified name. The rule handles static fields and static
methods independently while preserving declarations and syntax nodes that are not member uses.

#### Benefit

The declaring type is visible where the member is used. This reduces ambiguity, documents ownership,
and makes accidental dependence on an inherited or imported static member easier to detect.

#### Options

| Option name | Exact values | Meaning |
| --- | --- | --- |
| `member-accesses.static-members` | `true` | Enable static-member qualification. This is the default. |
| `member-accesses.static-members` | `false` | Disable this rule. |

#### Safety and skipped cases

The rule skips members that are already qualified, declarations, imports, type names, annotations,
method names, and other non-expression syntax. It also skips local-shadowing cases, anonymous classes,
inherited members, static imports, and ambiguous names. A static member is qualified only when its
declaring type can be identified from the source being processed; otherwise the original source is
preserved.
