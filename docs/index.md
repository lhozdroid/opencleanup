# OpenCleanup documentation

OpenCleanup is a Maven plugin for applying Java source rewrites during a Maven build. Its rule catalog contains practical Java cleanup transformations, while the selected rules and their options are configured in `pom.xml`.

The complete documented catalog has concrete, conservative implementations. Rules remain opt-in through the POM, and unsupported or ambiguous source is left unchanged.

## Use from Maven Central

The released plugin is available from Maven Central as
`io.github.lhozdroid:opencleanup-maven-plugin:1.1.0`. Add it to the
`<build><plugins>` section of a Maven project and Maven will resolve it
automatically; no repository declaration or `mvn install` is needed. See the
[Maven configuration guide](configuration.md) for the complete setup.

## Documentation map

### Plugin configuration

- [Maven configuration](configuration.md) — exact POM structure, rule selection, options, source scope, and safety behavior.

### Cleanup rule groups

Each page maps to one cleanup category. The individual entries are selectable rules.

- [Code style](rules/code-style.md)
- [Java features](rules/java-features.md)
- [Source fixing](rules/source-fixing.md)
- [Performance](rules/performance.md)
- [Member accesses](rules/member-accesses.md)
- [Unnecessary code](rules/unnecessary-code.md)
- [Missing code](rules/missing-code.md)
- [Code organizing](rules/code-organizing.md)
- [Duplicate code](rules/duplicate-code.md)

## Design principles

- A build must apply only the rules selected in the POM.
- Rule options must be explicit and reviewable in source control.
- Rewrites must preserve valid Java syntax and avoid changing behavior unless the selected rule intentionally requests a semantic modernization.
- Java-version-sensitive rules document their target level and skip unsupported or ambiguous source forms.
- The plugin reports which files and rules changed so rewrites are visible in CI.

## Implementation model

All 103 documented rule identifiers are registered, grouped, and executable through the AST or source-level rewrite engine. The implementation targets Java 21 and reports applied rule identifiers.

For each configured source root, the plugin reads Java files, applies complete-source rules first,
parses the result into a Java 21 syntax tree, applies AST rules, and writes only changed files.
Each rule page explains its recognition conditions, transformation, benefit, and safety skips. The
[Maven configuration guide](configuration.md) explains how to select those rules.

## Design basis

The groups cover source organization, code style, duplicate-code reduction, language features, member access, missing code, performance, source fixing, and unnecessary code. Every transformation is implemented as a conservative source rewrite and is independently selectable from the POM.
