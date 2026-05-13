---
name: javadoc-lombok
description: This skill should be used when the user asks to "write javadoc for a lombok class", "document lombok annotations", "add javadoc to @Getter @Setter fields", "document @Builder class", "add javadoc to lombok annotated fields", "document @UtilityClass", "fix lombok javadoc", or when writing documentation for any class using Lombok annotations (@Getter, @Setter, @Builder, @Value, @Data, @UtilityClass, @ToString, @EqualsAndHashCode, @AllArgsConstructor, @RequiredArgsConstructor). Handles Lombok-specific Javadoc propagation rules and workarounds.
version: 1.0.0
---

# Javadoc for Lombok-Annotated Code

Lombok generates code at compile time — Javadoc tooling only sees source. This creates
specific rules about *where* to put documentation so it propagates correctly to generated methods.

---

## Critical Rule: Document on the Field

For all Lombok-generated accessors, the **field is the canonical documentation location**.
Lombok copies field Javadoc to generated methods. Never document the generated method directly —
it does not exist in source.

---

## 1. @Getter / @Setter — Field Javadoc Propagation

Since Lombok v1.12.0, field Javadoc is copied to generated getters and setters with smart tag routing:

| Tag in field Javadoc | Where it goes |
|---------------------|---------------|
| `@return`           | Moved to getter only (deleted from field) |
| `@param`            | Moved to setter only (deleted from field) |
| All other text      | Copied to both getter and setter |

### Standard Pattern (field has both getter and setter)

```java
/**
 * The vehicle's registration plate assigned by national authority.
 * Format: {@code XX-NNN-XX} (French system). Never {@code null}.
 *
 * @return the registration plate; never {@code null}
 * @param registrationNumber the registration plate to set; must not be {@code null}
 */
@Getter
@Setter
private String registrationNumber;
```

After Lombok processing:
- `getRegistrationNumber()` → gets the description + `@return`
- `setRegistrationNumber(String)` → gets the description + `@param`

### Getter-Only Pattern (final field)

```java
/**
 * The date the vehicle was first registered.
 *
 * @return the registration date; never {@code null}
 */
@Getter
private final LocalDate registrationDate;
```

No `@param` needed — no setter is generated.

### GETTER/SETTER Section Markers (different text per accessor)

When getter and setter need **different descriptions**, use section dividers.
Syntax: a line containing `--` or more dashes, then `GETTER` or `SETTER`, then `--` or more dashes.

```java
/**
 * The vehicle's current mileage in kilometres.
 *
 * -- GETTER --
 * Returns the odometer reading at last service.
 *
 * @return mileage in km; {@code >= 0}
 *
 * -- SETTER --
 * Updates the odometer reading. Only call after a verified service record.
 *
 * @param mileage new mileage in km; must be {@code >= 0}
 */
@Getter
@Setter
private int mileage;
```

**Important**: When sections are present, `@return`/`@param` automatic stripping is **disabled**.
Place `@return` inside the GETTER section and `@param` inside the SETTER section manually.

---

## 2. @Builder — Class-Level Documentation (Workaround Required)

**Known limitation**: `@Builder` does **not** propagate field Javadoc to builder setter methods
in the IDE (open bug [#2481](https://github.com/projectlombok/lombok/issues/2481)).
`delombok` generates it, but IDE users won't see it.

**Workaround**: Document the builder chain in the **class-level `@apiNote`** with a full example.

```java
/**
 * Immutable vehicle value object with registration, brand, and energy type.
 *
 * <p>Equality is based solely on {@code registrationNumber}.
 * Instances are immutable and thread-safe.</p>
 *
 * @apiNote
 * Build instances via the fluent builder — all fields are required:
 * <pre>{@code
 * Vehicule v = Vehicule.builder()
 *     .registrationNumber("AB-123-CD")   // French plate, never null
 *     .brand("Renault")
 *     .registrationDate(LocalDate.of(2020, 3, 15))
 *     .energy(Energy.ELECTRIC)
 *     .build();
 * }</pre>
 *
 * @since 1.0
 * @version 1.0
 */
@Builder
@ToString
@EqualsAndHashCode(of = "registrationNumber")
public class Vehicule implements Serializable {

    /**
     * Unique registration plate assigned by the national authority.
     * Format: {@code XX-NNN-XX}. Never {@code null}.
     *
     * @return the registration plate; never {@code null}
     */
    @Getter
    private final String registrationNumber;

    /**
     * The date the vehicle was first registered with national authorities.
     *
     * @return the registration date; never {@code null}
     */
    @Getter
    private final LocalDate registrationDate;

    /**
     * Manufacturer brand name (e.g., {@code "Renault"}, {@code "Tesla"}).
     *
     * @return the brand name; never {@code null}
     */
    @Getter
    private final String brand;

    /**
     * Energy source type of the vehicle's powertrain.
     *
     * @return the energy type; never {@code null}
     */
    @Getter
    private final Energy energy;
}
```

### @Builder.Default — Document the Default

```java
/**
 * Maximum capacity (number of vehicles) of this garage.
 * Defaults to {@code 10} if not set in the builder.
 *
 * @return capacity; {@code > 0}
 */
@Getter
@Builder.Default
private final int capacity = 10;
```

---

## 3. @UtilityClass — Static Methods

`@UtilityClass` generates a private throwing constructor and makes all members static.
Document the class and all public methods. No constructor Javadoc needed (private, throws).

```java
/**
 * Formatting utilities for {@link Vehicule} instances.
 *
 * <p>All methods are stateless and thread-safe.</p>
 *
 * @apiNote
 * <pre>{@code
 * String s = VehiculeUtils.format(vehicule);
 * // → "Registration number: AB-123-CD, Brand: Renault, Energy: ELECTRIC"
 * }</pre>
 *
 * @since 1.0
 */
@UtilityClass
public class VehiculeUtils {

    /**
     * Formats a vehicle as a human-readable summary string.
     *
     * @param vehicule the vehicle to format; must not be {@code null}
     * @return formatted summary string; never {@code null} or empty
     * @throws NullPointerException if {@code vehicule} is {@code null}
     */
    public String format(Vehicule vehicule) { ... }
}
```

---

## 4. @Value — Immutable Class Pattern

`@Value` = `@Getter` + `@ToString` + `@EqualsAndHashCode` + `@AllArgsConstructor` + final fields.
Same rules as `@Builder` + `@Getter`: document class + fields. Add `@apiNote` for construction.

```java
/**
 * Immutable money amount with currency.
 *
 * @apiNote
 * <pre>{@code
 * Money price = new Money(BigDecimal.valueOf(29.99), "EUR");
 * }</pre>
 *
 * @since 1.0
 */
@Value
public class Money {

    /**
     * The numeric amount.
     *
     * @return amount; never {@code null}
     */
    BigDecimal amount;

    /**
     * ISO 4217 currency code (e.g., {@code "EUR"}, {@code "USD"}).
     *
     * @return currency code; never {@code null}
     */
    String currency;
}
```

---

## 5. @Data — Mixed Mutable Class

`@Data` = `@Getter` + `@Setter` (non-final) + `@ToString` + `@EqualsAndHashCode` + `@RequiredArgsConstructor`.
Use GETTER/SETTER sections on fields that need different getter/setter descriptions.

```java
/**
 * Mutable garage entity with capacity and vehicle collection.
 *
 * @apiNote
 * <pre>{@code
 * Garage g = new Garage("Main Depot");
 * g.setCapacity(20);
 * }</pre>
 *
 * @since 1.0
 */
@Data
public class Garage {

    /**
     * Human-readable name for this garage location.
     * Used in reports and UI labels. Never {@code null} or blank.
     *
     * @return the name; never {@code null}
     * @param name new name; must not be {@code null} or blank
     */
    private String name;

    /**
     * Maximum vehicle capacity.
     *
     * -- GETTER --
     * Returns the total slot count, including occupied and free slots.
     *
     * @return capacity; {@code > 0}
     *
     * -- SETTER --
     * Sets the maximum capacity. Must exceed current vehicle count.
     *
     * @param capacity new capacity; must be {@code > 0}
     */
    private int capacity;
}
```

---

## 6. @ToString / @EqualsAndHashCode — Exclusion Annotations

No Javadoc propagation for these — generated methods are boilerplate.
Document exclusion/inclusion choices with brief inline comments on the annotation.

```java
/**
 * Represents a user account.
 *
 * <p>Equality is based on {@code userId} only. Password is excluded from
 * {@code toString()} for security.</p>
 */
@ToString(exclude = "passwordHash")
@EqualsAndHashCode(of = "userId")
public class User {

    /** Unique system-assigned user identifier. */
    @Getter
    private final String userId;

    /** Hashed password — excluded from toString for security. */
    @Getter
    private String passwordHash;
}
```

---

## 7. Constructor Annotations

No Javadoc propagation from constructors annotations.
Document via class Javadoc describing construction requirements.

```java
/**
 * Address value object. Constructed via {@link #builder()} or directly if all
 * fields are known at construction time.
 *
 * @apiNote
 * Required fields: {@code street}, {@code city}, {@code country}.
 * <pre>{@code
 * Address a = new Address("12 Rue de la Paix", "Paris", "FR");
 * }</pre>
 */
@RequiredArgsConstructor
@Getter
public class Address {

    /**
     * Street name and number. Never {@code null}.
     *
     * @return street; never {@code null}
     */
    @NonNull
    private final String street;

    /**
     * City name. Never {@code null}.
     *
     * @return city; never {@code null}
     */
    @NonNull
    private final String city;

    /**
     * ISO 3166-1 alpha-2 country code (e.g., {@code "FR"}, {@code "DE"}).
     *
     * @return country code; never {@code null}
     */
    @NonNull
    private final String country;
}
```

---

## 8. Completeness Checklist for Lombok Classes

- [ ] Class has summary sentence + extended description + `@apiNote` builder/constructor example
- [ ] Every `@Getter` field has `@return` in field Javadoc (moved to getter by Lombok)
- [ ] Every `@Setter` field has `@param` in field Javadoc (moved to setter by Lombok)
- [ ] Fields with **both** `@Getter` and `@Setter` have both `@return` and `@param`
- [ ] Fields needing different getter/setter descriptions use `-- GETTER --` / `-- SETTER --` sections
- [ ] `@Builder` classes document the full builder chain in class `@apiNote` (IDE workaround)
- [ ] `@Builder.Default` fields state the default value in Javadoc
- [ ] `@NonNull` fields state null-prohibition in both description and `@param`
- [ ] `@UtilityClass` methods have `@param` + `@return` + `@throws`
- [ ] Equality basis documented in class description for `@EqualsAndHashCode`

## Additional Resources

- **`references/getter-setter-patterns.md`** — section marker patterns, edge cases, `@NonNull` combinations
- **`references/annotation-reference.md`** — per-annotation Javadoc rules and known limitations
