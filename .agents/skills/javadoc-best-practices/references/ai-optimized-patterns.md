# AI/Agent-Optimized Javadoc Patterns

## Why Structure Matters for LLMs

LLMs and code agents parse Javadoc to:
1. Understand what a type *is* and what it *does*
2. Learn how to instantiate / call it correctly (from examples)
3. Understand contracts (nullability, exceptions, ordering)
4. Navigate the type graph (via `{@link}`)

Inconsistent, vague, or example-free Javadoc forces the AI to guess — and guess wrong.

---

## Pattern 1: The "Semantic Summary" Class Template

Every class should answer these questions in order:

```
Q1: What does this type represent? (first sentence)
Q2: What are its key invariants / guarantees?
Q3: How do I create one? (@apiNote with builder/constructor example)
Q4: What does it relate to? (@see / {@link} to collaborators)
```

Full pattern:
```java
/**
 * Represents a [domain concept] with [key attributes].                    ← Q1
 *
 * <p>[Key invariants: immutability, thread-safety, equality basis,        ← Q2
 * null guarantees, lifecycle constraints].</p>
 *
 * @apiNote                                                                 ← Q3
 * <pre>{@code
 * Foo foo = Foo.builder()
 *     .fieldA("value")
 *     .fieldB(42)
 *     .build();
 * }</pre>
 *
 * @see Bar                                                                 ← Q4
 * @since 1.0
 */
```

---

## Pattern 2: Null Contract Annotation

State null semantics **everywhere** — agents need explicit, not implicit.

```java
// Parameter
* @param name the display name; must not be {@code null} or blank

// Return
* @return the result; never {@code null}
* @return the result; may be {@code null} if not found

// Optional
* @return an {@link java.util.Optional} containing the result, or empty if not found

// Collection
* @return unmodifiable list; never {@code null}, may be empty
```

---

## Pattern 3: Exception Contract Matrix

Document all exceptions that an agent must handle:

```java
/**
 * Loads a configuration from the given path.
 *
 * @param path path to the config file; must not be {@code null}
 * @return parsed configuration; never {@code null}
 * @throws NullPointerException if {@code path} is {@code null}
 * @throws IllegalArgumentException if {@code path} does not exist or is not readable
 * @throws ConfigParseException if the file content is malformed
 * @throws IOException if an I/O error occurs during reading
 */
```

---

## Pattern 4: Builder Class Documentation

When using `@Builder` (Lombok or manual), document the build chain in the class `@apiNote`:

```java
/**
 * Immutable vehicle value object.
 *
 * @apiNote
 * Construct via builder — all fields are required:
 * <pre>{@code
 * Vehicule v = Vehicule.builder()
 *     .registrationNumber("AB-123-CD")   // French plate format
 *     .brand("Renault")
 *     .registrationDate(LocalDate.now())
 *     .energy(Energy.ELECTRIC)
 *     .build();
 * }</pre>
 */
```

---

## Pattern 5: Utility Class Method Examples

For `@UtilityClass` / static-only classes, every method should show input → output:

```java
/**
 * Formats a vehicle as a human-readable summary string.
 *
 * @apiNote
 * <pre>{@code
 * String s = VehiculeUtils.format(vehicule);
 * // → "Registration number: AB-123-CD, Brand: Renault, Energy: ELECTRIC"
 * }</pre>
 *
 * @param vehicule the vehicle to format; must not be {@code null}
 * @return formatted string; never {@code null} or empty
 * @throws NullPointerException if {@code vehicule} is {@code null}
 */
public static String format(Vehicule vehicule) { ... }
```

---

## Pattern 6: Semantic Cross-Linking

Create a traversable semantic graph via `{@link}`. LLMs follow links to build context.

Rules:
- Link to **every collaborator type** mentioned in prose
- Link to **related methods** within the same class when discussing interaction
- Link to **standard library types** when their behavior is relied upon (`{@link java.util.Collections#unmodifiableSet}`)

```java
/**
 * Manages a collection of {@link Vehicule} instances with insertion-order preservation.
 *
 * <p>Use {@link #addVehicule(Vehicule)} to register a vehicle,
 * and {@link #getVehicules()} to retrieve the unmodifiable view.</p>
 */
```

---

## Pattern 7: Domain Vocabulary in `{@index}`

Tag key domain terms so the Javadoc search index covers them:

```java
/**
 * {@index "registration number" the unique plate assigned to a vehicle}
 * Unique registration plate assigned by the national vehicle authority.
 */
```

---

## Pattern 8: Deprecation with Migration Path

Agents must know what to use instead — always provide it:

```java
/**
 * @deprecated since 2.0 — use {@link #newMethod(Type)} instead.
 *   This method will be removed in version 3.0.
 */
@Deprecated(since = "2.0", forRemoval = true)
public void oldMethod() { ... }
```

---

## Pattern 9: Thread-Safety Contract

State thread-safety explicitly — agents generating concurrent code need this:

```java
/**
 * Immutable and thread-safe.
 */
// OR
/**
 * Not thread-safe. Use external synchronization or {@link java.util.concurrent.CopyOnWriteArrayList}.
 */
// OR
/**
 * Thread-safe. All public methods are synchronized on {@code this}.
 */
```

---

## Anti-Patterns (What to Avoid)

| Anti-pattern | Why it fails for AI | Fix |
|---|---|---|
| `/** Gets the name. */` | Restates the method name — zero information gain | Document contract, null, format |
| `/** TODO: document */` | Agent sees empty contract → treats as unconstrained | Write the doc now |
| `/** @param x the x */` | Tautological — no type hint, no null info | `@param x the registration plate; never {@code null}` |
| No `@apiNote` example | Agent must guess usage → hallucination risk | Always add one example per class |
| `{@link #method()}` with no context | Bare link without explanation | `see also {@link #method()} for the inverse operation` |
| First sentence ending mid-thought | Summary in Javadoc index truncated wrong | End first sentence at a clean full stop |
| Prose-only descriptions of enums | Agents need each constant documented | Add `/** ... */` above every enum constant |
