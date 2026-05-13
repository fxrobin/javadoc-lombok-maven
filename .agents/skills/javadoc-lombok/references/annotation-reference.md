# Lombok Annotation Javadoc Reference

## Per-Annotation Rules

### @Getter

**Javadoc propagation**: YES — field Javadoc → getter method  
**Tag routing**: `@return` moved to getter; rest copied  
**Control**: GETTER section marker overrides automatic behavior

**Where to write**: Field  
**Required tags**: `@return` (with null semantics)

```java
/**
 * Unique registration plate. Never {@code null}.
 *
 * @return the plate; never {@code null}
 */
@Getter
private final String registrationNumber;
```

---

### @Setter

**Javadoc propagation**: YES — field Javadoc → setter method  
**Tag routing**: `@param` moved to setter; rest copied  
**Control**: SETTER section marker overrides automatic behavior

**Where to write**: Field  
**Required tags**: `@param` (with null semantics and validation constraints)

```java
/**
 * Current speed in km/h.
 *
 * @param speed speed in km/h; must be in range {@code [0, 300]}
 */
@Setter
private int speed;
```

---

### @Builder

**Javadoc propagation**: PARTIAL — field Javadoc does NOT reach builder methods in IDE  
(Open bug: [lombok#2481](https://github.com/projectlombok/lombok/issues/2481))  
delombok DOES generate it — Javadoc HTML generation works correctly.

**Workaround**: Write full builder chain in class-level `@apiNote` with `<pre>{@code}</pre>`  
**Required**: Class-level `@apiNote` showing `.builder()…build()` pattern with realistic values  
**Also**: Field-level `@return` still required for `@Getter`-annotated fields

```java
/**
 * Immutable vehicle entity.
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
 */
@Builder
public class Vehicule { ... }
```

---

### @Builder.Default

**Javadoc propagation**: Same as `@Builder` — IDE gap exists  
**Where to write**: Field — state default value explicitly

```java
/**
 * Maximum garage capacity. Defaults to {@code 10} if not set.
 *
 * @return capacity; {@code > 0}, default {@code 10}
 */
@Getter
@Builder.Default
private final int capacity = 10;
```

---

### @Value

**Javadoc propagation**: YES for @Getter (included in @Value); constructor NOT propagated  
**Equivalent to**: `@Getter + @ToString + @EqualsAndHashCode + @AllArgsConstructor + final fields`

**Where to write**: Class (with `@apiNote` constructor example) + each field  
**Required tags on fields**: `@return` (with null semantics)

```java
/**
 * Immutable monetary amount.
 *
 * @apiNote
 * <pre>{@code
 * Money price = new Money(BigDecimal.valueOf(9.99), "EUR");
 * }</pre>
 */
@Value
public class Money {
    /** Amount. @return amount; never {@code null} */
    BigDecimal amount;
    /** ISO 4217 code. @return code; never {@code null} */
    String currency;
}
```

---

### @Data

**Javadoc propagation**: YES for @Getter (all fields) + @Setter (non-final fields)  
**Equivalent to**: `@Getter + @Setter (non-final) + @ToString + @EqualsAndHashCode + @RequiredArgsConstructor`

**Where to write**: Class (with `@apiNote`) + each field with both `@return` and `@param`  
**Note**: Use GETTER/SETTER sections if getter and setter semantics differ

---

### @ToString

**Javadoc propagation**: NONE — no mechanism for toString() docs  
**Generated**: `toString()` returns field name-value pairs

**Where to write**: Document exclusions/inclusions in class-level description

```java
/**
 * Password hash excluded from {@code toString()} for security.
 */
@ToString(exclude = "passwordHash")
public class User { ... }
```

Control members:
- `@ToString.Exclude` — skip this field from output
- `@ToString.Include` — force inclusion (or include a method)

---

### @EqualsAndHashCode

**Javadoc propagation**: NONE — no mechanism  
**Generated**: `equals()` + `hashCode()` + `canEqual()` (unless final)

**Where to write**: Document equality basis in class-level description

```java
/**
 * Equality is based on {@code registrationNumber} only.
 * Brand and mileage do not affect equality or hash-code.
 */
@EqualsAndHashCode(of = "registrationNumber")
public class Vehicule { ... }
```

Control members:
- `@EqualsAndHashCode.Exclude` — skip this field
- `@EqualsAndHashCode.Include` — explicit inclusion

---

### @AllArgsConstructor / @RequiredArgsConstructor / @NoArgsConstructor

**Javadoc propagation**: NONE — constructor Javadoc not generated from fields  
**onConstructor**: Adds Java annotations (not Javadoc text) — experimental

**Where to write**: Class-level `@apiNote` with construction example showing all required args

```java
/**
 * @apiNote
 * Required fields: {@code street}, {@code city}, {@code postalCode}.
 * <pre>{@code
 * Address a = new Address("12 Rue de la Paix", "Paris", "75001");
 * }</pre>
 */
@RequiredArgsConstructor
public class Address { ... }
```

---

### @UtilityClass (experimental)

**Javadoc propagation**: N/A — private constructor is not documented  
**Generated**: Private throwing constructor, all members become static

**Where to write**: Class + each public method (as static methods)  
**Note**: No constructor `@param` needed — it's private and throws unconditionally

```java
/**
 * Formatting utilities for {@link Vehicule}. All methods are thread-safe.
 */
@UtilityClass
public class VehiculeUtils {

    /**
     * @param vehicule the vehicle; must not be {@code null}
     * @return formatted string; never {@code null}
     */
    public String format(Vehicule vehicule) { ... }
}
```

---

### @With / @Wither

**Javadoc propagation**: PARTIAL — field text copies but generated `withX()` has limited doc  
**Where to write**: Field `@return` describes the *original field value* (getter semantics)  
Document `withX()` behavior in class-level `@apiNote`

```java
/**
 * @apiNote
 * Create modified copies via with-methods:
 * <pre>{@code
 * Vehicule ev = v.withEnergy(Energy.ELECTRIC);
 * }</pre>
 */
@Builder
public class Vehicule {
    /**
     * Energy type. Never {@code null}.
     *
     * @return the energy type; never {@code null}
     */
    @Getter
    @With
    private final Energy energy;
}
```

---

### @NonNull

**Javadoc propagation**: YES (with `lombok.copyableAnnotations`)  
**Generated**: Null check in setter / constructor (throws NullPointerException)

**Where to write**: Field — state null prohibition in description AND `@param` / `@throws`

```java
/**
 * The vehicle brand. Never {@code null}.
 *
 * @return the brand; never {@code null}
 * @param brand the brand; must not be {@code null}
 * @throws NullPointerException if {@code brand} is {@code null}
 */
@Getter
@Setter
@NonNull
private String brand;
```

---

## Known Limitations Summary

| Annotation | Field→Method Javadoc | Workaround |
|---|---|---|
| `@Getter` | ✅ Copies all + routes `@return` | None needed |
| `@Setter` | ✅ Copies all + routes `@param` | None needed |
| `@Builder` | ⚠️ IDE gap (bug #2481); HTML Javadoc OK | `@apiNote` on class |
| `@Value` | ✅ Getter part; ❌ constructor | `@apiNote` on class |
| `@Data` | ✅ Getter+Setter; ❌ constructor | `@apiNote` on class |
| `@ToString` | ❌ No propagation | Document in class description |
| `@EqualsAndHashCode` | ❌ No propagation | Document in class description |
| `@AllArgsConstructor` | ❌ No propagation | `@apiNote` on class |
| `@UtilityClass` | N/A (private ctor) | Document class + each method |
| `@With` | ⚠️ Partial | `@apiNote` with example |

## lombok.config Options Affecting Javadoc

```properties
# Copy nullability annotations to generated methods/params
lombok.copyableAnnotations += javax.annotation.Nonnull
lombok.copyableAnnotations += javax.annotation.Nullable
lombok.copyableAnnotations += org.jetbrains.annotations.NotNull
lombok.copyableAnnotations += org.jetbrains.annotations.Nullable

# Suppress experimental warnings (onX, UtilityClass)
lombok.onX.flagUsage = allow
lombok.utilityClass.flagUsage = allow
```
