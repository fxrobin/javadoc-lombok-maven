---
name: javadoc-best-practices
description: This skill should be used when the user asks to "write javadoc", "add javadoc", "apply javadoc best practices", "document this class", "add documentation", "improve javadoc", "document the API", or when reviewing/generating Java documentation comments. Covers human-readable, AI-agent-optimized Javadoc with examples, structured tags, and Lombok compatibility.
version: 1.0.0
---

# Javadoc Best Practices

Apply this skill when writing or reviewing Javadoc for Java classes, methods, fields, or packages.

## Core Philosophy

Good Javadoc is simultaneously:
- **Human-readable**: narrative, clear, structured
- **AI/agent-optimized**: consistent structure, example-driven, semantic
- **Complete**: no missing @param / @return / @throws

Treat the Javadoc as the contract between the code and its consumers — human or machine.

---

## 1. First Sentence Rule

The first sentence appears in package/class-level summaries. Make it count.

- **Third-person, active verb**: "Represents a…", "Manages the…", "Provides utility methods for…"
- **Ends at the first period followed by whitespace** (Javadoc auto-truncates there)
- **No "This class…" prefix** — implied
- **Punchy, ≤ 15 words**

```java
/**
 * Represents a motor vehicle with registration, brand, and energy type.
 */
```

Never:
```java
/**
 * This class is used to represent a vehicle. It has fields.
 */
```

---

## 2. Class-Level Documentation

Class docs must answer: *What is it? Why use it? How do I use it?*

### Required Structure

```
1. Summary sentence (first sentence — appears in index)
2. Extended description (what it models, key invariants, thread-safety)
3. @apiNote — usage example with <pre>{@code ...}</pre> block
4. @implSpec — constraints for subclasses (if extensible)
5. Standard tags: @param (for generics), @since, @version, @see
```

### Class Example Template

```java
/**
 * Represents a motor vehicle with a unique registration number, brand, and energy type.
 *
 * <p>Vehicles are value objects: equality and hash-code are based solely on the
 * {@code registrationNumber}. Instances are immutable and thread-safe.</p>
 *
 * <p>Build instances via the fluent builder:</p>
 *
 * @apiNote
 * <pre>{@code
 * Vehicule v = Vehicule.builder()
 *     .registrationNumber("AB-123-CD")
 *     .brand("Renault")
 *     .registrationDate(LocalDate.of(2020, 1, 15))
 *     .energy(Energy.ELECTRIC)
 *     .build();
 * }</pre>
 *
 * @since 1.0
 * @version 1.0
 * @see Garage
 */
```

**Rules for class examples:**
- Always include at least one `@apiNote` with a `<pre>{@code}</pre>` block
- Show the most common usage pattern (builder, constructor, factory)
- Include realistic field values, not `"foo"` / `null`
- If the class integrates with others, show interaction

---

## 3. Method-Level Documentation

### Tag Order (mandatory)

```
description
@apiNote       (optional — usage hint or rationale)
@implSpec      (optional — contract for overriders)
@implNote      (optional — impl detail, may change)
@param         (one per parameter, in declaration order)
@return        (omit only for void)
@throws        (one per checked + important unchecked)
@since
@deprecated    (if applicable, always with @see or migration note)
```

### Method Example

```java
/**
 * Adds a vehicle to this garage.
 *
 * <p>Duplicate vehicles (same registration number) are silently ignored
 * because the backing collection is a {@link java.util.LinkedHashSet}.</p>
 *
 * @param vehicule the vehicle to add; must not be {@code null}
 * @throws NullPointerException if {@code vehicule} is {@code null}
 * @since 1.0
 */
public void addVehicule(Vehicule vehicule) { ... }
```

### Return documentation

```java
/**
 * Returns the vehicles in this garage as an unmodifiable, insertion-ordered set.
 *
 * @return unmodifiable view; never {@code null}, may be empty
 */
public Set<Vehicule> getVehicules() { ... }
```

---

## 4. Field-Level Documentation

Document fields when:
- Their purpose is not obvious from the name
- They have invariants (non-null, range, format)
- They participate in equality/hash

```java
/**
 * Unique registration plate assigned by the national authority.
 * Format: {@code XX-NNN-XX} (French system). Never {@code null}.
 */
@Getter
private final String registrationNumber;
```

---

## 5. Enum Documentation

Document the enum itself + every constant.

```java
/**
 * Energy source type of a vehicle's powertrain.
 *
 * @apiNote
 * <pre>{@code
 * Vehicule v = Vehicule.builder().energy(Energy.ELECTRIC).build();
 * }</pre>
 *
 * @since 1.0
 */
public enum Energy {

    /** Fully electric — battery-powered, zero tailpipe emissions. */
    ELECTRIC,

    /** Internal combustion — gasoline/petrol fuel. */
    GASOLINE,

    /** Internal combustion — diesel fuel. */
    DIESEL,

    /** Combination of electric motor and internal combustion engine. */
    HYBRID,

    /** Fuel cell — hydrogen converted to electricity. */
    HYDROGEN
}
```

---

## 6. Package-Level Documentation (`package-info.java`)

Every package must have a `package-info.java`.

```java
/**
 * Domain model for vehicle management.
 *
 * <p>Core classes:</p>
 * <ul>
 *   <li>{@link fr.example.vehicules.Vehicule} — immutable vehicle value object</li>
 *   <li>{@link fr.example.vehicules.Energy} — energy type enumeration</li>
 *   <li>{@link fr.example.vehicules.VehiculeUtils} — formatting utilities</li>
 * </ul>
 *
 * @since 1.0
 */
package fr.example.vehicules;
```

---

## 7. Inline Tags Cheatsheet

| Intent | Tag | Example |
|--------|-----|---------|
| Reference type | `{@link Class}` | `{@link Vehicule}` |
| Reference with label | `{@link Class#method label}` | `{@link Garage#addVehicule addVehicule}` |
| Inline code | `{@code expr}` | `{@code null}`, `{@code registrationNumber}` |
| Code block | `<pre>{@code ... }</pre>` | multi-line examples |
| Literal (no formatting) | `{@literal <T>}` | HTML-unsafe chars |
| Value of constant | `{@value CONSTANT}` | `{@value MAX_SIZE}` |

---

## 8. AI/Agent Optimization Rules

Generative AI and code agents extract meaning from Javadoc patterns. Follow these rules to maximize machine comprehension:

1. **Consistent first-sentence pattern** per element type:
   - Classes: "Represents a…" / "Manages…" / "Provides…"
   - Methods: "Returns…" / "Adds…" / "Removes…" / "Computes…"
   - Fields: noun phrase ("The registration plate…")

2. **Null semantics always stated**: say `never {@code null}` or `may be {@code null}`

3. **@apiNote for usage examples** — agents learn from examples, not prose

4. **Avoid vague descriptions**: no "does stuff", no "processes the input". Name the transformation.

5. **Cross-reference semantically related types** via `{@link}` — this creates a traversable semantic graph

6. **Tag every @throws** including `NullPointerException` and `IllegalArgumentException` when they apply

7. **Use structured lists** for "either/or" conditions or multiple return states:
   ```java
   * @return the formatted string, or {@code "N/A"} if the vehicle has no registration
   ```

---

## 9. Lombok-Specific Rules

When using Lombok annotations, document **on the field**, not on the generated method (the delombok process copies field Javadoc to generated getters/setters/builder methods).

```java
/**
 * Returns the vehicle's energy type.
 *
 * @return energy source; never {@code null}
 */
@Getter
private final Energy energy;
```

For `@Builder`, document the **class** with a `@apiNote` showing the builder pattern (since the builder class itself is generated and invisible in source).

For `@UtilityClass`, document the class and all public methods normally; no constructor docs needed.

---

## 10. Completeness Checklist

Before marking Javadoc done, verify:

- [ ] Every public class has: summary sentence + extended description + `@apiNote` example + `@since`
- [ ] Every public method has: description + `@param` (all) + `@return` (if non-void) + `@throws` (all checked + key unchecked)
- [ ] Every public field has: at least a summary sentence
- [ ] Every enum constant has: at least a summary sentence (`/** ... */` above the constant)
- [ ] Every package has a `package-info.java` with package Javadoc
- [ ] Null semantics stated on every param and return
- [ ] At least one `@apiNote` code example per public class

## Additional Resources

- **`references/tags-reference.md`** — complete tag reference with all standard and extended tags
- **`references/ai-optimized-patterns.md`** — patterns specifically optimized for LLM/agent consumption
