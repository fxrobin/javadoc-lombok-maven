# Javadoc Tags Complete Reference

## Standard Block Tags

| Tag | Scope | Description |
|-----|-------|-------------|
| `@author` | class, interface | Author name(s). Use git blame instead for living history. |
| `@version` | class, interface | Current version string. Match pom.xml/build.gradle. |
| `@since` | all | First version this element appeared. Never remove. |
| `@deprecated` | all | Mark obsolete. Always include migration guidance + `@see`. |
| `@param <name>` | method, constructor, generic type | Document each parameter. |
| `@return` | method | Describe return value, including null/empty possibilities. |
| `@throws ExceptionType` | method, constructor | One per exception type, checked or notable unchecked. |
| `@see` | all | Cross-reference. Prefer `{@link}` for inline references. |
| `@serial` | field | For `Serializable` classes: serialization details. |
| `@serialField` | field | Component of `serialPersistentFields`. |
| `@serialData` | method | Data written by `writeObject` / `writeExternal`. |

## Extended Tags (JDK 8+, non-standard but widely supported)

Enable in Maven javadoc plugin config:
```xml
<additionalOptions>
  <additionalOption>-tag "apiNote:a:API Note:"</additionalOption>
  <additionalOption>-tag "implSpec:a:Implementation Requirements:"</additionalOption>
  <additionalOption>-tag "implNote:a:Implementation Note:"</additionalOption>
</additionalOptions>
```

| Tag | Purpose | Audience |
|-----|---------|----------|
| `@apiNote` | Usage examples, rationale, non-normative hints | API users |
| `@implSpec` | What any valid implementation must honor (contract for subclasses) | Implementors |
| `@implNote` | Notes about *this* implementation (performance, JDK version) | Curious readers |

### @apiNote — Usage Example + Rationale

Use for code examples and "why it works this way" commentary. Agents learn from examples here.

```java
/**
 * Parses an ISO-8601 date string into a {@link LocalDate}.
 *
 * @apiNote
 * <pre>{@code
 * LocalDate d = DateUtils.parse("2025-06-15");
 * }</pre>
 *
 * @param isoDate ISO-8601 date string; must not be {@code null}
 * @return parsed date; never {@code null}
 * @throws DateTimeParseException if the string is not a valid ISO-8601 date
 */
```

### @implSpec — Subclass Contract

Use on non-final overrideable methods to state what the override must guarantee.

```java
/**
 * Computes the tax amount for this order.
 *
 * @implSpec
 * Implementations must return a non-negative value and must be consistent
 * with {@link #getSubtotal()} — i.e., tax must not exceed the subtotal.
 *
 * @return tax amount; {@code >= 0}, never {@code null}
 */
protected abstract BigDecimal computeTax();
```

### @implNote — Implementation Detail

Use for performance notes, JDK-version-specific behavior, non-normative trivia.

```java
/**
 * Returns all elements sorted by insertion order.
 *
 * @implNote
 * Backed by a {@link java.util.LinkedHashSet}; iteration order is insertion
 * order. This behavior is specific to this implementation and may change.
 *
 * @return unmodifiable ordered set; never {@code null}
 */
```

## Inline Tags

| Tag | Usage | Notes |
|-----|-------|-------|
| `{@code text}` | Inline code snippet | Preferred over `<code>`. Disables HTML interpretation. |
| `{@link pkg.Class#member label}` | Hyperlink to element | Use for semantic cross-referencing |
| `{@linkplain pkg.Class#member label}` | Link, plain font | Use within prose sentences |
| `{@literal text}` | Literal text, no HTML/Javadoc processing | Use for `<`, `>`, `&` in prose |
| `{@value pkg.Class#FIELD}` | Inserts field's constant value | Only for `static final` primitives/String |
| `{@inheritDoc}` | Copy parent Javadoc | Use carefully — can hide missing docs |
| `{@summary text}` | Override auto-computed summary (Java 10+) | Use when first sentence is awkward |
| `{@index term description}` | Add term to search index (Java 9+) | For key concepts and domain terms |
| `{@snippet ...}` | Validated code snippet (Java 18+) | Links to external snippet files |

## HTML Formatting Conventions

```java
// Paragraph break (use instead of <br>)
* <p>Second paragraph starts here.</p>

// Unordered list
* <ul>
*   <li>Item one</li>
*   <li>Item two</li>
* </ul>

// Ordered list
* <ol>
*   <li>First step</li>
*   <li>Second step</li>
* </ol>

// Code block (multi-line)
* <pre>{@code
* Foo f = new Foo.Builder()
*     .bar("baz")
*     .build();
* }</pre>

// Table (use sparingly)
* <table>
*   <caption>Supported formats</caption>
*   <tr><th>Format</th><th>Extension</th></tr>
*   <tr><td>JSON</td><td>.json</td></tr>
* </table>
```

**Never use** `<h1>`, `<h2>`, `<h3>` in method/field docs — the Javadoc tool controls heading hierarchy. Use them only in package and class overview docs.

## Tag Ordering (canonical)

For classes:
```
description
@apiNote
@implSpec
@implNote
@param (generic type params)
@author
@version
@since
@deprecated
@see
```

For methods/constructors:
```
description
@apiNote
@implSpec
@implNote
@param (in declaration order)
@return
@throws (checked first, then notable unchecked)
@since
@deprecated
@see
```
