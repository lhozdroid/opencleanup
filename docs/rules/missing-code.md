# Missing-code rules

Missing-code rules add small, mechanically derivable declarations or annotations to Java source.
They make contracts visible, reduce repetitive boilerplate, and help classes satisfy the contracts
they explicitly declare. The plugin operates on parsed source and writes only files for which a
selected rule produced a change.

These rules are conservative. They use declarations available in the source unit, never replace an
existing declaration or annotation, and leave ambiguous type relationships unchanged.

## Configuration

Select an individual rule by using its rule ID as `<id>`:

```xml
<rule>
  <id>annotations.override</id>
  <enabled>true</enabled>
</rule>
```

To enable the complete set through the `missing-code` group, place each rule ID and its value in the
group's options:

```xml
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
```

Boolean rules use `true` and `false` as their exact option values. The serial identifier rule uses
the two string values documented below.

## Rules

### `annotations.missing`

#### What it does

Adds source-provable `@Override` and `@Deprecated` annotations that are absent from a declaration.
It is an aggregate rule: it applies the behavior described by `annotations.override`,
`annotations.override-interface`, and `annotations.deprecated`.

#### How it works

The rule examines source-local type declarations and method relationships. It adds `@Override` when
a concrete method matches an inherited class or interface method, and adds `@Deprecated` when the
declaration's Javadoc contains a `@deprecated` tag. Existing annotations are detected by simple or
qualified annotation name and are not duplicated.

#### Benefit

The annotations turn implicit contracts into visible compiler-checked or tooling-visible metadata.
They make accidental signature changes easier to catch and keep deprecation information aligned
with the declaration.

#### Options

| Option name | Exact values | Meaning |
| --- | --- | --- |
| `annotations.missing` | `true` | Enable aggregate missing-annotation insertion. |
| `annotations.missing` | `false` | Disable aggregate missing-annotation insertion. |

#### Safety and skipped cases

Only annotations that can be derived from source syntax are added. Existing annotations, constructors,
interface declarations, unresolved parent types, ambiguous signatures, and relationships outside the
source unit are skipped. The rule does not infer annotations from naming conventions or modify
Javadoc text.

### `annotations.override`

#### What it does

Adds `@Override` to a concrete class method that overrides a matching source-local superclass or
interface method.

#### How it works

The rule compares the method name, parameter types, and varargs shape with inherited methods found by
following source-local superclass and interface declarations. It inserts a marker annotation before
the method modifiers when a matching inherited method is found.

#### Benefit

`@Override` documents the intended relationship and lets the Java compiler report many accidental
signature changes instead of silently treating the method as a new overload.

#### Options

| Option name | Exact values | Meaning |
| --- | --- | --- |
| `annotations.override` | `true` | Add eligible class-method override annotations. |
| `annotations.override` | `false` | Do not add annotations for this rule. |

#### Safety and skipped cases

Constructors, methods already carrying `@Override`, interface declarations, private or static
methods, unresolved parents, and signatures that cannot be matched are left unchanged. The rule
does not guess about compiled dependencies or relationships that are not represented in the current
source set.

### `annotations.override-interface`

#### What it does

Adds `@Override` to a concrete class method that implements a matching method declared by a
source-local interface.

#### How it works

The rule follows source-local interface parents and compares method names, parameter types, and
varargs shape. A missing marker annotation is inserted on the implementing method.

#### Benefit

The implementation's connection to its interface contract is explicit. This improves readability
and makes future signature mistakes visible to the compiler.

#### Options

| Option name | Exact values | Meaning |
| --- | --- | --- |
| `annotations.override-interface` | `true` | Add eligible interface-implementation annotations. |
| `annotations.override-interface` | `false` | Do not add annotations for this rule. |

#### Safety and skipped cases

Existing `@Override` annotations, constructors, interface methods, unresolved interfaces, and
ambiguous or unmatched signatures are skipped. The rule does not add markers based only on a method
name, and it does not inspect types unavailable in the processed source.

### `annotations.deprecated`

#### What it does

Adds `@Deprecated` to declarations whose Javadoc explicitly contains `@deprecated` and that do not
already have the annotation.

#### How it works

The rule visits source declarations and checks their attached Javadoc tags. When the exact
`@deprecated` tag is present, it inserts a marker annotation at the declaration.

#### Benefit

Deprecation is available as structured source metadata instead of being communicated only through
free-form documentation. Build tooling and API consumers can recognize the status consistently.

#### Options

| Option name | Exact values | Meaning |
| --- | --- | --- |
| `annotations.deprecated` | `true` | Add missing deprecation annotations. |
| `annotations.deprecated` | `false` | Do not add deprecation annotations. |

#### Safety and skipped cases

The rule requires an attached Javadoc block with the exact `@deprecated` tag. Declarations without
that tag, declarations already marked `@Deprecated`, and malformed or otherwise unavailable
declarations are unchanged. It does not decide that a declaration is deprecated from its name or
from comments that are not Javadoc tags.

### `serialization.serial-version-uid`

#### What it does

Adds a missing private static final `long serialVersionUID` field to source-local serializable
classes. The field prevents an otherwise implicit serialization identifier from being left
unspecified.

#### How it works

The rule identifies classes that directly declare `Serializable` in their source-level interface
list and that do not already declare a field named `serialVersionUID`. It inserts a field with a
deterministic value according to the selected mode.

#### Benefit

Serialization compatibility becomes an explicit, reviewable part of the class. A deliberate field
also avoids repeated runtime warnings about a missing serialization identifier.

#### Options

| Option name | Exact values | Meaning |
| --- | --- | --- |
| `serialization.serial-version-uid` | `generated` | Insert a deterministic positive long value derived from the class declaration. |
| `serialization.serial-version-uid` | `default` | Insert the stable value `1L`. |

#### Safety and skipped cases

The rule skips classes that do not directly declare `Serializable`, classes that already contain a
`serialVersionUID` field, interfaces, enums, abstract classes, unresolved type references, and
unsupported option values. It does not replace or recalculate an existing identifier. Because the
analysis is source-local, indirect serialization through an unresolved parent is left unchanged.

### `methods.unimplemented`

#### What it does

Adds concrete method stubs for abstract methods required by a non-abstract source-local class.
Generated stubs preserve the method name, parameters, return type, and declared exceptions.

#### How it works

The rule follows source-local superclass and interface declarations, finds inherited abstract
contracts that the class has not implemented, and creates a method body. A `void` method receives an
empty body; primitive return types receive a compile-safe zero or `false`; reference-like return
types receive `null`.

#### Benefit

The class's missing implementation points become explicit and compilable, giving developers a clear
starting point for completing behavior while preserving the inherited method signature.

#### Options

| Option name | Exact values | Meaning |
| --- | --- | --- |
| `methods.unimplemented` | `true` | Add eligible method stubs. |
| `methods.unimplemented` | `false` | Do not generate method stubs. |

#### Safety and skipped cases

The rule skips abstract classes, interfaces, constructors, static or private methods, methods already
implemented by the class, unresolved parent types, and ambiguous signatures. It does not overwrite
an existing method. Generated bodies are deliberately minimal and do not attempt to infer business
logic; review every generated stub before relying on it.
