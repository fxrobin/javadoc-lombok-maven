# Design: Lombok Javadoc Propagator

**Date:** 2026-05-14  
**Status:** Approved  
**Scope:** `@Builder` propagation (GETTER/SETTER/TOSTRING deferred)

---

## Problem

Lombok `@Builder` generates an inner `XxxBuilder` class with setter methods. These methods have no Javadoc because Lombok does not propagate field-level documentation to them. The `delombok` step expands the annotations to plain Java but leaves builder setters undocumented. The resulting HTML Javadoc is incomplete.

---

## Solution Overview

Introduce a **Groovy script** (`scripts/lombok-javadoc-propagator.groovy`) that runs as a Maven `process-sources` phase step, **between** `delombok` and the `javadoc` plugin. It reads `-- BUILDER --` markers from the delombok output, collects field Javadoc, and injects documentation into the generated builder setter methods **in-place**.

---

## Maven Pipeline

```
[generate-sources]   lombok-maven-plugin (delombok)
                     src/main/java/*.java
                     → target/generated-sources/delombok/*.java
                       (builder setters: no Javadoc)

[process-sources]    gmavenplus-plugin (execute)
                     scripts/lombok-javadoc-propagator.groovy
                     → modifies target/generated-sources/delombok/*.java IN-PLACE
                       (builder setters: Javadoc injected)

[javadoc]            maven-javadoc-plugin
                     sourcepath = target/generated-sources/delombok
                     → complete Javadoc HTML
```

All steps run inside the existing `javadoc` Maven profile. Normal build (`mvn compile`) is unaffected.

---

## Marker Syntax

### Class-level (activates propagation for all fields)

```java
/**
 * Represents an immutable motor vehicle.
 *
 * -- BUILDER --
 */
@Builder
public class Vehicule { ... }
```

### Field-level override (optional, overrides auto-copy for one field)

```java
/**
 * Unique registration plate. Format: AB-123-CD. Never null.
 * @return the plate; never null
 *
 * -- BUILDER-PARAM --
 * Sets the registration plate. Use SIV format AB-123-CD.
 * @param registrationNumber the plate; never null
 */
private final String registrationNumber;
```

**Priority rule:** `-- BUILDER-PARAM --` on a field takes precedence over the class-level default. If absent and the class has `-- BUILDER --`, the script performs automatic copy-and-transform.

---

## Transformation Rules

### Automatic (class has `-- BUILDER --`, field has no `-- BUILDER-PARAM --`)

| Source (field Javadoc)                   | Target (builder setter Javadoc)            |
|------------------------------------------|--------------------------------------------|
| Body lines (no `@` tags)                 | Copied verbatim as setter description      |
| `@return <text>`                         | Becomes `@param fieldName <text>`          |
| *(implicit)*                             | `@return this builder` appended            |

Example:

```
Field:                              Injected setter:
──────────────────────────          ─────────────────────────────────────
/** Unique registration plate.      /**
 *  Format: AB-123-CD.               * Unique registration plate.
 *  Never null.                       * Format: AB-123-CD.
 *  @return the plate; never null.    * Never null.
 */                                   * @param registrationNumber the plate; never null.
                                      * @return this builder
                                      */
```

### Override (field has `-- BUILDER-PARAM --`)

The content of the `-- BUILDER-PARAM --` section is copied verbatim as the setter Javadoc. No transformation applied.

---

## Script Architecture

**File:** `scripts/lombok-javadoc-propagator.groovy`

```
lombok-javadoc-propagator.groovy
├── class FieldDoc
│     String body        // description lines (no @tags)
│     String paramText   // extracted from @return, or null
│     String override    // verbatim -- BUILDER-PARAM -- section, or null
│
├── def extractClassInfo(List<String> lines)
│     → [hasBuilderMarker: boolean, fieldDocs: Map<String, FieldDoc>]
│
├── def extractFieldDoc(List<String> lines, int javadocStartIdx)
│     → FieldDoc
│
├── def injectBuilderDocs(List<String> lines, Map<String, FieldDoc> fieldDocs)
│     → List<String>   (modified file lines)
│
└── main: scan target/generated-sources/delombok/**/*.java → processFile(f)
```

### State Machine in `injectBuilderDocs`

```
SCANNING
  │  sees "public static class XxxBuilder"
  ▼
IN_BUILDER_CLASS
  │  sees "    public XxxBuilder fieldName(final Type fieldName)"
  │  (no existing Javadoc on preceding line)
  ▼
INJECT
  │  insert generated Javadoc block before the method signature
  ▼
IN_BUILDER_CLASS  (continues scanning for next setter)
  │  sees "}" matching builder class close brace
  ▼
SCANNING
```

The script detects the builder class by name convention: `XxxBuilder` where `Xxx` matches the outer class name. It identifies setter methods by the pattern:

```
public <OuterClass>Builder <fieldName>(final <Type> <fieldName>)
```

---

## Maven Configuration (pom.xml changes)

Add inside the `javadoc` profile, after the `lombok-maven-plugin` block:

```xml
<plugin>
    <groupId>org.codehaus.gmavenplus</groupId>
    <artifactId>gmavenplus-plugin</artifactId>
    <version>3.0.2</version>
    <executions>
        <execution>
            <id>inject-builder-javadoc</id>
            <phase>process-sources</phase>
            <goals>
                <goal>execute</goal>
            </goals>
            <configuration>
                <scripts>
                    <script>${project.basedir}/scripts/lombok-javadoc-propagator.groovy</script>
                </scripts>
            </configuration>
        </execution>
    </executions>
</plugin>
```

The script receives Maven project properties via the `project` binding exposed by gmavenplus.

---

## File Locations

```
scripts/
  lombok-javadoc-propagator.groovy   ← new
pom.xml                               ← add gmavenplus-plugin in javadoc profile
src/main/java/
  .../vehicules/Vehicule.java         ← add -- BUILDER -- marker
  .../garage/Garage.java              ← add -- BUILDER -- marker
```

---

## Extensibility

The design is intentionally structured for future markers. Adding `-- GETTER --`, `-- SETTER --`, or `-- TOSTRING --` support requires:

1. Adding a new `extractXxxInfo` pass (or extending `extractClassInfo`)
2. Adding a new state in the injection state machine
3. Defining transformation rules for that annotation type

No structural changes to the Maven pipeline or script entry point needed.

---

## Success Criteria

- `mvn clean compile javadoc:javadoc -Pjavadoc` produces BUILD SUCCESS
- Each `@Builder` setter method in generated HTML has a description + `@param` + `@return this builder`
- Fields with `-- BUILDER-PARAM --` show custom text, not auto-generated text
- Fields without any marker on a class that lacks `-- BUILDER --` are untouched
- Normal `mvn compile` (without `-Pjavadoc`) is unaffected
