# Getter/Setter Javadoc Patterns — Deep Reference

## Section Marker Exact Syntax

The GETTER/SETTER section divider is a **single line** matching this pattern:

```
<2 or more dashes> <GETTER or SETTER> <2 or more dashes>
```

All these are valid:
```
-- GETTER --
--- GETTER ---
--------- GETTER ---------
-- SETTER --
```

**Rules when sections are used:**
1. Text before the first section goes to **both** getter and setter
2. Text inside `-- GETTER --` section goes to getter only
3. Text inside `-- SETTER --` section goes to setter only
4. `@return` / `@param` auto-stripping is **disabled** when sections exist
5. You must manually place `@return` in GETTER section and `@param` in SETTER section

## Pattern Matrix

### Field with @Getter only (final)

```java
/**
 * The registration number of this vehicle.
 * Format: {@code XX-NNN-XX}. Never {@code null}.
 *
 * @return the registration number; never {@code null}
 */
@Getter
private final String registrationNumber;
```

Result: `getRegistrationNumber()` gets full text + `@return`. No setter generated.

---

### Field with @Getter and @Setter (mutable)

```java
/**
 * The vehicle's current mileage in kilometres.
 * Must be {@code >= 0}. Increases monotonically during normal use.
 *
 * @return mileage in km; {@code >= 0}
 * @param mileage mileage in km; must be {@code >= 0}
 */
@Getter
@Setter
private int mileage;
```

Result:
- `getMileage()` → description + `@return` (param stripped)
- `setMileage(int)` → description + `@param` (return stripped)

---

### Field with @Getter + @Setter + different texts

```java
/**
 * Short summary visible in both getter and setter.
 *
 * -- GETTER --
 * Returns the odometer reading recorded at last service.
 * May differ from physical odometer if rollback occurred.
 *
 * @return last service mileage in km; {@code >= 0}
 *
 * -- SETTER --
 * Records a new odometer reading. Only call with verified values
 * from official service records.
 *
 * @param mileage odometer reading in km; must be {@code >= 0}
 */
@Getter
@Setter
private int mileage;
```

---

### @NonNull field — null contract in both tags

```java
/**
 * The brand name of the vehicle manufacturer.
 * Examples: {@code "Renault"}, {@code "Tesla"}, {@code "BMW"}.
 *
 * @return the brand name; never {@code null}
 * @param brand the brand name; must not be {@code null}
 * @throws NullPointerException if {@code brand} is {@code null}
 */
@Getter
@Setter
@NonNull
private String brand;
```

Note: `@NonNull` makes Lombok insert a null check in the setter.
Document the `@throws NullPointerException` on the field — Lombok moves it to setter.

---

### Boolean field — "is" prefix

```java
/**
 * Whether this vehicle has passed its last technical inspection.
 *
 * @return {@code true} if the vehicle passed inspection; {@code false} otherwise
 * @param inspectionPassed {@code true} to mark as passed; {@code false} to mark as failed
 */
@Getter
@Setter
private boolean inspectionPassed;
```

Lombok generates `isInspectionPassed()` not `getInspectionPassed()` for boolean fields.
Document `@return` accordingly with `{@code true}` / `{@code false}` values.

---

### Class-level @Getter/@Setter (no field-level control)

When `@Getter` is on the class, all fields get getters. Field Javadoc still propagates.

```java
@Getter
@Setter
public class Vehicule {

    /**
     * Registration plate. Never {@code null}.
     *
     * @return the registration plate; never {@code null}
     * @param registrationNumber plate to set; must not be {@code null}
     */
    private String registrationNumber;

    /**
     * Odometer reading in km.
     *
     * @return mileage; {@code >= 0}
     * @param mileage new mileage; must be {@code >= 0}
     */
    private int mileage;
}
```

---

### AccessLevel override — suppress Javadoc for package-private accessor

```java
/**
 * Internal sequence number for persistence mapping.
 * Not part of the public API — no `@return` documented intentionally.
 */
@Getter(AccessLevel.PACKAGE)
private long internalId;
```

---

## @Wither / @With — Immutable Setters

`@With` generates `withField(value)` methods returning a new instance.
Document on field with `@return` describing the new instance:

```java
/**
 * The energy type of this vehicle.
 *
 * @return the energy type; never {@code null}
 */
@Getter
@With
private final Energy energy;
```

The `withEnergy(Energy)` method Javadoc is not separately controllable —
document conversion logic in the class `@apiNote`.

---

## Lazy Getter Pattern

```java
/**
 * Cached formatted display label, computed once on first access.
 * Combines registration number, brand, and energy type.
 *
 * @return the display label; never {@code null}
 */
@Getter(lazy = true)
private final String displayLabel = computeLabel();
```

Note: `@Getter(lazy=true)` generates a thread-safe lazy initializer via `AtomicReference`.
State this in the field Javadoc if thread-safety is relevant to callers.

---

## lombok.copyableAnnotations

If you use JSR-305 or JetBrains nullability annotations, configure them to copy to generated methods:

```
# lombok.config
lombok.copyableAnnotations += org.jetbrains.annotations.NotNull
lombok.copyableAnnotations += org.jetbrains.annotations.Nullable
lombok.copyableAnnotations += javax.annotation.Nonnull
```

With this, `@NotNull` on a field propagates to the getter's return and setter's parameter.
Still write null semantics in Javadoc — annotations supplement, not replace, documentation.
