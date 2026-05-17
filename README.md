# Maven Javadoc + Lombok — Java 25 Demo

Demonstration project showing how to generate complete, accurate Javadoc for a project using [Lombok](https://projectlombok.org/) annotations with Java 25 and Maven.

Companion article on [fxjavadevblog.fr](https://www.fxjavadevblog.fr).

---

## The Problem

Lombok generates boilerplate code (`@Builder`, `@ToString`, `@EqualsAndHashCode`, getters…) at compile time. Standard `javadoc` only sees the source — not the generated methods — leaving the Javadoc incomplete or full of warnings.

## The Solution

This project combines three steps in a Maven `javadoc` profile:

1. **Delombok** — expands Lombok annotations into plain Java source
2. **Groovy scripts** — post-process the expanded source to inject accurate Javadoc on generated methods
3. **`javadoc:javadoc`** — generates the final HTML Javadoc from the patched source

---

## Groovy Script Pipeline

The scripts live in `scripts/` and run via the [GMavenPlus](https://github.com/groovy/GMavenPlus) plugin. They are loaded in dependency order:

| Script | Role |
|--------|------|
| `source-analyzer.groovy` | Parses Lombok annotations (`@Builder`, `@ToString`, `@EqualsAndHashCode`) and extracts field metadata from source files |
| `javadoc-utils.groovy` | Shared Javadoc manipulation utilities (paragraph removal, blank-line collapsing, comment injection) |
| `builder-javadoc-patcher.groovy` | Injects Javadoc on builder class, setter methods, `build()`, `toString()`, and factory method |
| `equals-hashcode-javadoc-patcher.groovy` | Injects Javadoc on `equals()`, `hashCode()`, `canEqual()`, `toString()` and updates the class-level Javadoc |
| `lombok-javadoc-propagator.groovy` | Entry point — orchestrates all patchers for each delomboked `.java` file |
| `lombok-javadoc-propagator-tests.groovy` | Standalone self-tests (no build tool required) |

The scripts use **Groovy traits** (`SourceAnalyzer`, `JavadocUtils`) for shared behavior and **composition** for collaborators — no inheritance chains.

---

## Groovy Self-Tests

The script pipeline ships with a standalone test suite. No Maven, no build tool — just Groovy.

Tests cover: annotation detection, field extraction, builder setter patching, `@Builder` override sections, `@ToString` / `@EqualsAndHashCode` parameter parsing, effective field computation, Javadoc injection, and paragraph removal.

**Prerequisite:** Groovy installed locally. Install with [SDKMAN!](https://sdkman.io):

```bash
sdk install groovy
```

Verify:

```bash
groovy --version
```

```bash
groovy scripts/lombok-javadoc-propagator-tests.groovy
```

Expected output:

```
Self-tests: PASSED
```

Run this after any change to the scripts to verify correctness before triggering the Maven build.

---

## Project Structure

```
.
├── pom.xml
├── scripts/                          # Groovy Javadoc enhancement pipeline
└── src/main/java/fr/fxjavadevblog/mvnlmbkjdoc/
    ├── garage/
    │   └── Garage.java               # @Builder, @ToString
    └── vehicules/
        ├── Vehicule.java             # @Builder, @ToString, @EqualsAndHashCode
        ├── VehiculeUtils.java        # @UtilityClass
        └── Energy.java               # Enum
```

---

## Build

### Generate Javadoc (with Lombok support)

```bash
mvn javadoc:javadoc -P javadoc
```

Output: `target/site/apidocs/`

### Skip the Groovy enhancement (raw delombok output only)

```bash
mvn javadoc:javadoc -P javadoc -DskipJavadocEnhancement=true
```

### Standard build (no Javadoc)

```bash
mvn clean compile
```

---

## Requirements

- Java 25
- Maven 3.9+
- Groovy (for standalone self-tests only — not required for the Maven build)
