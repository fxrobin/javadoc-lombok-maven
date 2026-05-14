# Lombok Javadoc Propagator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Groovy script that patches builder setter Javadoc in the delombok output, replacing `@return {@code this}.` with proper `@param` + `@return this builder` documentation.

**Architecture:** A `LombokJavadocPropagator` Groovy class reads the `-- BUILDER --` marker from the class-level Javadoc in delombok output, extracts `@return` text from getter methods, and rewrites builder setter Javadoc in-place. Integrated into Maven via gmavenplus-plugin at `process-sources` phase, between delombok and javadoc-plugin. When invoked standalone (no Maven binding), runs embedded self-tests.

**Tech Stack:** Groovy 4.x via gmavenplus-plugin 3.0.2, Maven lifecycle `process-sources`, existing `javadoc` Maven profile.

---

## Key observations from the actual delombok output

Lombok 1.18.46 already copies field body text into builder setter Javadoc. What is **missing** is the `@param` tag. The generated setter looks like:

```java
        /**
         * Unique registration plate...   ← body already there ✓
         * @return {@code this}.           ← wrong: should be @param + @return this builder ✗
         */
        @java.lang.SuppressWarnings("all")
        @lombok.Generated
        public Vehicule.VehiculeBuilder registrationNumber(final String registrationNumber) {
```

The script's job: replace `@return {@code this}.` with `@param fieldName <text>` + `@return this builder`. The `<text>` is harvested from the getter's `@return` in the same delombok file.

`Garage.GarageBuilder` has **no setters** (the `vehicules` field has a default value). The script will process the file, find no setter methods to patch, and skip it silently.

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `src/main/java/.../vehicules/Vehicule.java` | Modify | Add `-- BUILDER --` marker to class Javadoc |
| `src/main/java/.../garage/Garage.java` | Modify | Add `-- BUILDER --` marker to class Javadoc |
| `scripts/lombok-javadoc-propagator.groovy` | Create | Full propagator logic + self-tests |
| `pom.xml` | Modify | Add gmavenplus-plugin in `javadoc` profile |

---

## Task 1: Add `-- BUILDER --` marker to source files

**Files:**
- Modify: `src/main/java/fr/fxjavadevblog/mvnlmbkjdoc/vehicules/Vehicule.java`
- Modify: `src/main/java/fr/fxjavadevblog/mvnlmbkjdoc/garage/Garage.java`

- [ ] **Step 1: Add marker to Vehicule.java**

In `Vehicule.java`, the class-level Javadoc currently ends with:
```java
 * @see <a href="https://www.fxjavadevblog.fr">FX Java Dev Blog</a>
 */
```

Add the `-- BUILDER --` marker as a new line just before the closing ` */`:
```java
 * @see <a href="https://www.fxjavadevblog.fr">FX Java Dev Blog</a>
 *
 * -- BUILDER --
 */
```

- [ ] **Step 2: Add marker to Garage.java**

Same edit in `Garage.java` — insert before the closing ` */` of the class-level Javadoc:
```java
 * @see <a href="https://fxjavadevblog.fr">FX Java Dev Blog</a>
 *
 * -- BUILDER --
 */
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/fr/fxjavadevblog/mvnlmbkjdoc/vehicules/Vehicule.java \
        src/main/java/fr/fxjavadevblog/mvnlmbkjdoc/garage/Garage.java
git commit -m "Add -- BUILDER -- markers to Vehicule and Garage class Javadoc"
```

---

## Task 2: Write the self-test section (TDD first)

**Files:**
- Create: `scripts/lombok-javadoc-propagator.groovy` (skeleton with self-tests only, no implementation yet)

- [ ] **Step 1: Create the script skeleton with failing self-tests**

Create `scripts/lombok-javadoc-propagator.groovy`:

```groovy
class LombokJavadocPropagator {

    boolean hasBuilderMarker(List<String> lines) {
        throw new UnsupportedOperationException("not implemented")
    }

    Map<String, String> extractGetterReturns(List<String> lines) {
        throw new UnsupportedOperationException("not implemented")
    }

    Map<String, String> extractBuilderParamOverrides(List<String> lines) {
        throw new UnsupportedOperationException("not implemented")
    }

    List<String> patchBuilderSetters(List<String> lines,
                                     Map<String, String> getterReturns,
                                     Map<String, String> overrides) {
        throw new UnsupportedOperationException("not implemented")
    }

    void processFile(File file) {
        def lines = file.readLines('UTF-8')
        if (!hasBuilderMarker(lines)) return
        def getterReturns  = extractGetterReturns(lines)
        def overrides      = extractBuilderParamOverrides(lines)
        def patched        = patchBuilderSetters(lines, getterReturns, overrides)
        file.write(patched.join('\n') + '\n', 'UTF-8')
        println "  Patched: ${file.name} (${getterReturns.size()} getters found)"
    }

    void runSelfTests() {
        // ── test data: minimal delombok-like Java file ──────────────────
        def input = [
            '/**',
            ' * Test class.',
            ' *',
            ' * -- BUILDER --',
            ' */',
            'public class Foo {',
            '    /**',
            '     * The name. Never null.',
            '     */',
            '    private final String name;',
            '',
            '    /**',
            '     * The name. Never null.',
            '     *',
            '     * @return the name; never null',
            '     */',
            '    @java.lang.SuppressWarnings("all")',
            '    @lombok.Generated',
            '    public String getName() {',
            '        return this.name;',
            '    }',
            '',
            '    public static class FooBuilder {',
            '',
            '        /**',
            '         * The name. Never null.',
            '         * @return {@code this}.',
            '         */',
            '        @java.lang.SuppressWarnings("all")',
            '        @lombok.Generated',
            '        public FooBuilder name(final String name) {',
            '            this.name = name;',
            '            return this;',
            '        }',
            '',
            '        public Foo build() { return new Foo(name); }',
            '    }',
            '}',
        ]

        // test 1: marker detection
        assert hasBuilderMarker(input), "Should detect -- BUILDER -- marker"
        assert !hasBuilderMarker(['/** no marker */', 'public class Bar {}']),
               "Should NOT detect marker when absent"

        // test 2: getter return extraction
        def getterReturns = extractGetterReturns(input)
        assert getterReturns['name'] == 'the name; never null',
               "Wrong getter return text: '${getterReturns['name']}'"

        // test 3: no overrides in this input
        def overrides = extractBuilderParamOverrides(input)
        assert overrides.isEmpty(), "Should have no overrides"

        // test 4: patch builder setter Javadoc
        def output = patchBuilderSetters(input, getterReturns, overrides)
        def text = output.join('\n')
        assert text.contains('@param name the name; never null'),
               "Missing @param in output:\n${text}"
        assert text.contains('@return this builder'),
               "Missing @return this builder in output"
        assert !text.contains('@return {@code this}'),
               "Old @return not replaced:\n${text}"

        // test 5: override replaces entire Javadoc
        def overrideInput = [
            '/**',
            ' * -- BUILDER --',
            ' */',
            'public class Bar {',
            '    /**',
            '     * The count.',
            '     * -- BUILDER-PARAM --',
            '     * Sets the count (must be positive).',
            '     * @param count positive integer; must be > 0',
            '     */',
            '    private final int count;',
            '',
            '    public static class BarBuilder {',
            '        /**',
            '         * The count.',
            '         * @return {@code this}.',
            '         */',
            '        @java.lang.SuppressWarnings("all")',
            '        @lombok.Generated',
            '        public BarBuilder count(final int count) {',
            '            this.count = count;',
            '            return this;',
            '        }',
            '    }',
            '}',
        ]
        def ov2 = extractBuilderParamOverrides(overrideInput)
        assert ov2['count'] != null, "Should find override for 'count'"
        assert ov2['count'].contains('must be positive'), "Override content wrong: ${ov2['count']}"
        def out2 = patchBuilderSetters(overrideInput, [:], ov2)
        def txt2 = out2.join('\n')
        assert txt2.contains('Sets the count (must be positive)'), "Override not applied"
        assert txt2.contains('@param count positive integer'), "Override @param missing"
        assert !txt2.contains('@return {@code this}'), "Old @return not replaced in override"

        println "Self-tests: PASSED"
    }
}

if (binding.hasVariable('project')) {
    def delombokDir = new File(project.build.directory, 'generated-sources/delombok')
    println "LombokJavadocPropagator: scanning ${delombokDir}"
    def propagator = new LombokJavadocPropagator()
    delombokDir.eachFileRecurse { file ->
        if (file.name.endsWith('.java')) propagator.processFile(file)
    }
    println "LombokJavadocPropagator: done."
} else {
    new LombokJavadocPropagator().runSelfTests()
}
```

- [ ] **Step 2: Run the script to confirm it fails**

```bash
groovy scripts/lombok-javadoc-propagator.groovy
```

Expected output: exception `UnsupportedOperationException: not implemented` from `hasBuilderMarker`.

---

## Task 3: Implement the propagator

**Files:**
- Modify: `scripts/lombok-javadoc-propagator.groovy` (replace stubs with real implementations)

- [ ] **Step 1: Implement `hasBuilderMarker`**

Replace the stub:
```groovy
boolean hasBuilderMarker(List<String> lines) {
    lines.any { it.trim() == '* -- BUILDER --' }
}
```

- [ ] **Step 2: Implement `extractGetterReturns`**

Replace the stub:
```groovy
Map<String, String> extractGetterReturns(List<String> lines) {
    def result = [:]
    for (int i = 0; i < lines.size(); i++) {
        // Match: public Type getFieldName() or public boolean isFieldName()
        def m = (lines[i].trim() =~ /^public\s+\S+\s+(?:get|is)([A-Z]\w*)\s*\(\)/)
        if (!m) continue
        // fieldName: first char lowercase, rest unchanged
        def raw     = m[0][1]
        def fieldName = raw[0].toLowerCase() + (raw.size() > 1 ? raw[1..-1] : '')
        // Scan back in current Javadoc block for @return
        for (int j = i - 1; j >= 0; j--) {
            def content = lines[j].trim().replaceFirst(/^\*\s?/, '')
            if (content.startsWith('@return ')) {
                result[fieldName] = content.substring('@return '.length()).trim()
                break
            }
            if (lines[j].trim().startsWith('/**')) break
        }
    }
    return result
}
```

- [ ] **Step 3: Implement `extractBuilderParamOverrides`**

Replace the stub:
```groovy
Map<String, String> extractBuilderParamOverrides(List<String> lines) {
    def result = [:]
    for (int i = 0; i < lines.size(); i++) {
        if (lines[i].trim() != '* -- BUILDER-PARAM --') continue
        // Collect everything between this marker and the closing '*/'
        def overrideLines = []
        int j = i + 1
        while (j < lines.size() && lines[j].trim() != '*/') {
            overrideLines << lines[j].trim().replaceFirst(/^\*\s?/, '')
            j++
        }
        // j is now at '*/'; find field declaration past annotations
        int k = j + 1
        while (k < lines.size() && lines[k].trim().startsWith('@')) k++
        if (k < lines.size()) {
            // Match: private [final] Type fieldName; or private Type fieldName = ...;
            def fm = (lines[k].trim() =~ /^private\s+(?:final\s+)?[\w<>?,.\[\] ]+\s+(\w+)\s*[;=]/)
            if (fm) result[fm[0][1]] = overrideLines.findAll { it }.join('\n')
        }
    }
    return result
}
```

- [ ] **Step 4: Implement `patchBuilderSetters`**

Replace the stub:
```groovy
List<String> patchBuilderSetters(List<String> lines,
                                  Map<String, String> getterReturns,
                                  Map<String, String> overrides) {
    def result = []
    for (int i = 0; i < lines.size(); i++) {
        def line    = lines[i]
        def trimmed = line.trim()

        // Match builder setter: public *Builder methodName(final Type methodName)
        // where the method name and the parameter name are identical
        def m = (trimmed =~ /^public\s+\S+Builder\s+(\w+)\s*\(final\s+.+\s+(\w+)\s*\)/)
        if (m && m[0][1] == m[0][2]) {
            def fieldName = m[0][1]
            def override  = overrides[fieldName]
            def indent    = line.replaceFirst(/\S.*/, '')

            // Pop any preceding annotations (@SuppressWarnings, @lombok.Generated, etc.)
            def annotations = []
            while (result && result.last().trim().startsWith('@')) {
                annotations.add(0, result.removeLast())
            }

            if (override != null) {
                // Remove the existing Javadoc block (ends with '*/')
                if (result && result.last().trim() == '*/') {
                    while (result && !result.last().trim().startsWith('/**')) result.removeLast()
                    if (result) result.removeLast() // remove '/**'
                }
                // Inject override Javadoc
                result << "${indent}/**"
                override.split('\n').each { result << "${indent} * ${it}" }
                result << "${indent} */"
            } else {
                // Patch: replace '* @return {@code this}.' with @param + @return this builder
                def returnText = getterReturns[fieldName] ?: ''
                int limit = Math.max(0, result.size() - 20)
                for (int j = result.size() - 1; j >= limit; j--) {
                    if (result[j].trim() == '* @return {@code this}.') {
                        def ind = result[j].replaceFirst(/\S.*/, '')
                        result[j] = "${ind}* @param ${fieldName} ${returnText}"
                        result.add(j + 1, "${ind}* @return this builder")
                        break
                    }
                }
            }

            result.addAll(annotations)
        }

        result << line
    }
    return result
}
```

- [ ] **Step 5: Run self-tests**

```bash
groovy scripts/lombok-javadoc-propagator.groovy
```

Expected output:
```
Self-tests: PASSED
```

If any assertion fails, fix the implementation before proceeding.

- [ ] **Step 6: Commit**

```bash
git add scripts/lombok-javadoc-propagator.groovy
git commit -m "Add Groovy Lombok Javadoc propagator with self-tests"
```

---

## Task 4: Wire up Maven (gmavenplus-plugin)

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add gmavenplus-plugin inside the `javadoc` profile**

In `pom.xml`, inside `<profiles><profile><id>javadoc</id><build><plugins>`, add after the `lombok-maven-plugin` block and before the `maven-javadoc-plugin` block:

```xml
                    <plugin>
                        <groupId>org.codehaus.gmavenplus</groupId>
                        <artifactId>gmavenplus-plugin</artifactId>
                        <version>3.0.2</version>
                        <dependencies>
                            <dependency>
                                <groupId>org.apache.groovy</groupId>
                                <artifactId>groovy</artifactId>
                                <version>4.0.21</version>
                                <scope>runtime</scope>
                            </dependency>
                        </dependencies>
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

> **Note:** If Maven cannot resolve `org.apache.groovy:groovy:4.0.21`, check [Maven Central](https://search.maven.org/search?q=g:org.apache.groovy%20a:groovy) for the latest 4.0.x version and update accordingly.

- [ ] **Step 2: Verify pom.xml is valid**

```bash
mvn validate -Pjavadoc
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "Wire gmavenplus-plugin into javadoc profile for Javadoc propagation"
```

---

## Task 5: Integration test

**Files:** None modified

- [ ] **Step 1: Run full javadoc build**

```bash
mvn clean compile javadoc:javadoc -Pjavadoc
```

Expected: `BUILD SUCCESS`

Watch the output for:
```
LombokJavadocPropagator: scanning .../target/generated-sources/delombok
  Patched: Vehicule.java (4 getters found)
  Patched: Garage.java (0 getters found)
LombokJavadocPropagator: done.
```

- [ ] **Step 2: Verify delombok output was patched**

```bash
grep -A2 "@param registrationNumber" \
    target/generated-sources/delombok/fr/fxjavadevblog/mvnlmbkjdoc/vehicules/Vehicule.java
```

Expected output:
```java
         * @param registrationNumber the registration plate; never {@code null}
         * @return this builder
```

```bash
grep "@return this builder" \
    target/generated-sources/delombok/fr/fxjavadevblog/mvnlmbkjdoc/vehicules/Vehicule.java
```

Expected: 4 matches (one per field: registrationNumber, registrationDate, brand, energy).

```bash
grep "@return {@code this}" \
    target/generated-sources/delombok/fr/fxjavadevblog/mvnlmbkjdoc/vehicules/Vehicule.java
```

Expected: **0 matches** (all replaced).

- [ ] **Step 3: Verify generated HTML**

```bash
grep -A5 'registrationNumber' \
    "target/reports/apidocs/fr/fxjavadevblog/mvnlmbkjdoc/vehicules/Vehicule.VehiculeBuilder.html" \
    | head -30
```

Expected: `@param registrationNumber` and its description appear in the HTML.

Alternatively open in browser:
```bash
xdg-open target/reports/apidocs/fr/fxjavadevblog/mvnlmbkjdoc/vehicules/Vehicule.VehiculeBuilder.html
```

The `registrationNumber(String)` method should show:
- Description: "Unique registration plate assigned by the national vehicle authority..."
- **Parameters:** `registrationNumber` — the registration plate; never `null`
- **Returns:** this builder

- [ ] **Step 4: Commit**

```bash
git add target/  # only if target/ is tracked — otherwise skip
git commit -m "Integration verified: builder setter Javadoc fully documented"
```

> If `target/` is in `.gitignore` (normal), only commit source changes if any were made in this task.

---

## Self-review against spec

| Spec requirement | Task |
|-----------------|------|
| `-- BUILDER --` class-level marker activates propagation | Task 1 (adds marker), Task 3 (detects it) |
| `-- BUILDER-PARAM --` field-level override | Task 3 (`extractBuilderParamOverrides`, test 5) |
| Groovy script in `scripts/` | Task 2+3 |
| gmavenplus `process-sources` between delombok and javadoc | Task 4 |
| `@param fieldName <text>` from getter `@return` | Task 3 (`extractGetterReturns`) |
| `@return this builder` added | Task 3 (`patchBuilderSetters`) |
| Normal build unaffected | Only in `javadoc` profile |
| Build succeeds | Task 5 step 1 |
| HTML shows full documentation | Task 5 step 3 |
