# JILT Javadoc Patcher Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Groovy script that patches Javadoc in JILT-generated builder files (`target/generated-sources/annotations/`), adding missing class-level documentation, factory method Javadoc, `build()` method Javadoc, and fixing setter method Javadoc (replacing wrong `@return` tags copied from fields with proper `@param` + `@return this builder` or `@return NextStage` for staged builders).

**Architecture:** A `JiltJavadocPatcher` Groovy class walks `target/generated-sources/annotations/` recursively, detects JILT-generated files via `@Generated("Jilt-` or `@JiltGenerated` annotations, identifies CLASSIC vs STAGED builder patterns, and applies appropriate Javadoc templates and transformations in-place. Integrated into Maven via gmavenplus-plugin at `process-classes` phase, after JILT compilation, in the `javadoc` profile.

**Tech Stack:** Groovy 4.x via gmavenplus-plugin 3.0.2, Maven lifecycle `process-classes`, existing `javadoc` Maven profile, existing `javadoc-utils.groovy` trait.

---

## Key observations from the actual JILT output

JILT generates two types of builder patterns in `target/generated-sources/annotations/`:

**CLASSIC builder** (`XxxBuilder.java` without `implements XxxBuilders`):
- Setter methods have Javadoc copied from field Javadoc
- The `@return` tag is copied verbatim from the field (semantically wrong: field return ≠ setter return)
- No `@param` tags on setters
- No class-level Javadoc on `XxxBuilder`
- No Javadoc on `build()` method
- Factory method may have no Javadoc

**STAGED builder** (`XxxBuilder.java` implementing `XxxBuilders.*`):
- Same issues as CLASSIC
- Additionally: setter return type is a nested stage interface (e.g., `Driver`, `Vehicule`), not `XxxBuilder`
- The `@return` must reference the next stage interface, not `this builder`

**Staged interface container** (`XxxBuilders.java`):
- Top-level interface has no Javadoc
- Each nested interface (one per required field) has no Javadoc

The script's job: detect file type, apply appropriate patches. CLASSIC vs STAGED detection is based on presence of `implements XxxBuilders` in the class declaration.

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `pom.xml` | Modify | Add `process-classes` execution to gmavenplus-plugin in `javadoc` profile |
| `scripts/jilt-javadoc-patcher.groovy` | Create | Full patcher logic + self-tests |
| `scripts/javadoc-utils.groovy` | Reuse | No change (trait implemented by patcher) |

---

## Task 1: Write the self-test section (TDD first)

**Files:**
- Create: `scripts/jilt-javadoc-patcher.groovy` (skeleton with self-tests only, no implementation yet)

- [ ] **Step 1: Create the script skeleton with failing self-tests**

Create `scripts/jilt-javadoc-patcher.groovy`:

```groovy
import fr.fxjavadevblog.mvnlmbkjdoc.scripts.JavadocUtils

class JiltJavadocPatcher implements JavadocUtils {

    boolean isJiltGenerated(List<String> lines) {
        throw new UnsupportedOperationException("not implemented")
    }

    String detectBuilderType(List<String> lines) {
        // Returns: "CLASSIC", "STAGED", or "BUILDERS"
        throw new UnsupportedOperationException("not implemented")
    }

    Map<String, String> extractFieldDescriptions(List<String> lines) {
        // fieldName -> description body (from original field/builder Javadoc)
        throw new UnsupportedOperationException("not implemented")
    }

    String extractOuterClassName(List<String> lines) {
        throw new UnsupportedOperationException("not implemented")
    }

    String extractNextStageInterface(List<String> lines, String setterMethodSignature) {
        // For STAGED: extract return type from setter (e.g., "Driver", "Vehicule")
        throw new UnsupportedOperationException("not implemented")
    }

    List<String> patchClassJavadoc(List<String> lines, String outerClassName, String builderType) {
        throw new UnsupportedOperationException("not implemented")
    }

    List<String> patchFactoryMethod(List<String> lines, String outerClassName) {
        throw new UnsupportedOperationException("not implemented")
    }

    List<String> patchBuildMethod(List<String> lines, String outerClassName) {
        throw new UnsupportedOperationException("not implemented")
    }

    List<String> patchSetterMethods(List<String> lines, Map<String, String> fieldDescriptions, String builderType) {
        throw new UnsupportedOperationException("not implemented")
    }

    List<String> patchBuildersInterfaces(List<String> lines, String outerClassName) {
        throw new UnsupportedOperationException("not implemented")
    }

    void processFile(File file) {
        def lines = file.readLines('UTF-8')
        if (!isJiltGenerated(lines)) return
        
        def builderType = detectBuilderType(lines)
        def outerClassName = extractOuterClassName(lines)
        def fieldDescriptions = extractFieldDescriptions(lines)
        
        def patched = lines
        
        if (builderType == "BUILDERS") {
            patched = patchBuildersInterfaces(patched, outerClassName)
        } else {
            patched = patchClassJavadoc(patched, outerClassName, builderType)
            patched = patchFactoryMethod(patched, outerClassName)
            patched = patchBuildMethod(patched, outerClassName)
            patched = patchSetterMethods(patched, fieldDescriptions, builderType)
        }
        
        file.write(patched.join('\n') + '\n', 'UTF-8')
        println "  Patched: ${file.name} (${builderType})"
    }

    void runSelfTests() {
        // ── test data: CLASSIC builder ──────────────────────────────────
        def classicInput = [
            '/**',
            ' * Generated by JILT.',
            ' */',
            '@Generated("Jilt-1.0.0")',
            'public final class VehiculeBuilder {',
            '    /**',
            '     * Unique registration plate. Never null.',
            '     * @return the registration plate; never null',
            '     */',
            '    private String registrationNumber;',
            '',
            '    /**',
            '     * Unique registration plate. Never null.',
            '     * @return the registration plate; never null',
            '     */',
            '    @java.lang.SuppressWarnings("all")',
            '    public VehiculeBuilder registrationNumber(final String registrationNumber) {',
            '        this.registrationNumber = registrationNumber;',
            '        return this;',
            '    }',
            '',
            '    public Vehicule build() { return new Vehicule(registrationNumber); }',
            '',
            '    public static VehiculeBuilder vehicule() { return new VehiculeBuilder(); }',
            '}',
        ]

        // test 1: JILT detection
        assert isJiltGenerated(classicInput), "Should detect @Generated Jilt annotation"
        assert !isJiltGenerated(['public class Foo {}']), "Should NOT detect non-JILT file"

        // test 2: builder type detection
        assert detectBuilderType(classicInput) == "CLASSIC", "Should detect CLASSIC builder"

        // test 3: outer class extraction
        assert extractOuterClassName(classicInput) == "Vehicule", "Wrong outer class: ${extractOuterClassName(classicInput)}"

        // test 4: field descriptions extraction
        def descriptions = extractFieldDescriptions(classicInput)
        assert descriptions['registrationNumber'] != null, "Missing description for registrationNumber"
        assert descriptions['registrationNumber'].contains('Unique registration plate'), "Wrong description"

        // test 5: CLASSIC setter patching
        def classicOutput = patchSetterMethods(classicInput, descriptions, "CLASSIC")
        def classicText = classicOutput.join('\n')
        assert classicText.contains('@param registrationNumber Unique registration plate. Never null.'),
               "Missing @param in CLASSIC output:\n${classicText}"
        assert classicText.contains('@return this builder'),
               "Missing @return this builder in CLASSIC output"
        assert !classicText.contains('@return the registration plate'),
               "Old @return not replaced in CLASSIC output:\n${classicText}"

        // ── test data: STAGED builder ─────────────────────────────────
        def stagedInput = [
            '/**',
            ' * Generated by JILT.',
            ' */',
            '@JiltGenerated',
            'public final class VehiculeBuilder implements VehiculeBuilders.Driver, VehiculeBuilders.Vehicule {',
            '    /**',
            '     * Unique registration plate. Never null.',
            '     * @return the registration plate; never null',
            '     */',
            '    private String registrationNumber;',
            '',
            '    /**',
            '     * Unique registration plate. Never null.',
            '     * @return the registration plate; never null',
            '     */',
            '    @java.lang.SuppressWarnings("all")',
            '    public VehiculeBuilders.Driver registrationNumber(final String registrationNumber) {',
            '        this.registrationNumber = registrationNumber;',
            '        return this;',
            '    }',
            '',
            '    public Vehicule build() { return new Vehicule(registrationNumber); }',
            '',
            '    public static VehiculeBuilders.Driver vehicule() { return new VehiculeBuilder(); }',
            '}',
        ]

        // test 6: STAGED detection
        assert detectBuilderType(stagedInput) == "STAGED", "Should detect STAGED builder"

        // test 7: STAGED setter patching
        def stagedDescriptions = extractFieldDescriptions(stagedInput)
        def stagedOutput = patchSetterMethods(stagedInput, stagedDescriptions, "STAGED")
        def stagedText = stagedOutput.join('\n')
        assert stagedText.contains('@param registrationNumber Unique registration plate. Never null.'),
               "Missing @param in STAGED output:\n${stagedText}"
        assert stagedText.contains('@return {@link VehiculeBuilders.Driver} the next builder stage'),
               "Missing correct @return in STAGED output"
        assert !stagedText.contains('@return the registration plate'),
               "Old @return not replaced in STAGED output:\n${stagedText}"

        // ── test data: BUILDERS interface container ────────────────────
        def buildersInput = [
            '/**',
            ' * Generated by JILT.',
            ' */',
            '@Generated("Jilt-1.0.0")',
            'public final class VehiculeBuilders {',
            '    public interface Driver {',
            '        Vehicule registrationNumber(String registrationNumber);',
            '    }',
            '',
            '    public interface Vehicule extends Driver {',
            '        Vehicule build();',
            '    }',
            '}',
        ]

        // test 8: BUILDERS detection
        assert detectBuilderType(buildersInput) == "BUILDERS", "Should detect BUILDERS type"

        // test 9: BUILDERS patching
        def buildersOutput = patchBuildersInterfaces(buildersInput, "Vehicule")
        def buildersText = buildersOutput.join('\n')
        assert buildersText.contains('Staged builder interfaces for {@link Vehicule}'),
               "Missing top-level Javadoc in BUILDERS output"
        assert buildersText.contains('Builder step: set the {@code registrationNumber} field of {@link Vehicule}'),
               "Missing nested interface Javadoc in BUILDERS output"

        // ── test data: class-level Javadoc patching ──────────────────────
        def classicForClassPatch = [
            '/**',
            ' * Generated by JILT.',
            ' */',
            '@Generated("Jilt-1.0.0")',
            'public final class VehiculeBuilder {',
            '    public Vehicule build() { return new Vehicule(); }',
            '}',
        ]
        def classPatched = patchClassJavadoc(classicForClassPatch, "Vehicule", "CLASSIC")
        def classText = classPatched.join('\n')
        assert classText.contains('Builder for {@link Vehicule}'), "Missing class Javadoc"
        assert classText.contains('Generated by <a href="https://github.com/skinny85/jilt">JILT</a>'), "Missing JILT link"

        // ── test data: factory method patching ────────────────────────────
        def factoryInput = [
            '    /**',
            '     * @return {@code this}',
            '     */',
            '    public static VehiculeBuilder vehicule() { return new VehiculeBuilder(); }',
        ]
        def factoryOutput = patchFactoryMethod(factoryInput, "Vehicule")
        def factoryText = factoryOutput.join('\n')
        assert factoryText.contains('@return a new builder instance to construct a {@link Vehicule}; never {@code null}'),
               "Wrong factory Javadoc"

        // ── test data: build method patching ──────────────────────────────
        def buildInput = [
            '    /**',
            '     */',
            '    public Vehicule build() { return new Vehicule(); }',
        ]
        def buildOutput = patchBuildMethod(buildInput, "Vehicule")
        def buildText = buildOutput.join('\n')
        assert buildText.contains('Builds and returns a new {@link Vehicule} instance with the values set on this builder.'),
               "Missing build method description"
        assert buildText.contains('@return new {@link Vehicule} instance; never {@code null}'),
               "Missing build method @return"

        println "Self-tests: PASSED"
    }
}

if (binding.hasVariable('project')) {
    def jiltDir = new File(project.build.directory, 'generated-sources/annotations')
    println "JiltJavadocPatcher: scanning ${jiltDir}"
    def patcher = new JiltJavadocPatcher()
    jiltDir.eachFileRecurse { file ->
        if (file.name.endsWith('.java')) patcher.processFile(file)
    }
    println "JiltJavadocPatcher: done."
} else {
    new JiltJavadocPatcher().runSelfTests()
}
```

- [ ] **Step 2: Run the script to confirm it fails**

```bash
groovy scripts/jilt-javadoc-patcher.groovy
```

Expected output: exception `UnsupportedOperationException: not implemented` from `isJiltGenerated`.

---

## Task 2: Implement the patcher

**Files:**
- Modify: `scripts/jilt-javadoc-patcher.groovy` (replace stubs with real implementations)

- [ ] **Step 1: Implement `isJiltGenerated`**

Replace the stub:
```groovy
boolean isJiltGenerated(List<String> lines) {
    lines.any { it.contains('@Generated("Jilt-') || it.contains('@JiltGenerated') }
}
```

- [ ] **Step 2: Implement `detectBuilderType`**

Replace the stub:
```groovy
String detectBuilderType(List<String> lines) {
    if (lines.any { it.contains('public final class') && it.contains('Builders') && !it.contains('implements') }) {
        return "BUILDERS"
    }
    if (lines.any { it.contains('implements') && it.contains('Builders.') }) {
        return "STAGED"
    }
    if (lines.any { it.contains('public final class') && it.contains('Builder') }) {
        return "CLASSIC"
    }
    return "UNKNOWN"
}
```

- [ ] **Step 3: Implement `extractOuterClassName`**

Replace the stub:
```groovy
String extractOuterClassName(List<String> lines) {
    // Find class declaration line
    def classLine = lines.find { it.contains('public final class') && (it.contains('Builder') || it.contains('Builders')) }
    if (!classLine) return ""
    
    // Extract class name: "public final class VehiculeBuilder" -> "VehiculeBuilder"
    def m = (classLine =~ /public\s+final\s+class\s+(\w+)/)
    if (!m) return ""
    
    def className = m[0][1]
    // Remove "Builder" or "Builders" suffix
    if (className.endsWith("Builders")) {
        return className - "Builders"
    }
    if (className.endsWith("Builder")) {
        return className - "Builder"
    }
    return className
}
```

- [ ] **Step 4: Implement `extractFieldDescriptions`**

Replace the stub:
```groovy
Map<String, String> extractFieldDescriptions(List<String> lines) {
    def result = [:]
    def inJavadoc = false
    def currentField = null
    def descriptionLines = []
    
    for (int i = 0; i < lines.size(); i++) {
        def line = lines[i].trim()
        
        if (line.startsWith('/**')) {
            inJavadoc = true
            descriptionLines = []
            continue
        }
        
        if (inJavadoc) {
            if (line.startsWith('*/')) {
                inJavadoc = false
                // Process collected Javadoc
                def body = descriptionLines.findAll { !it.startsWith('@') }.join(' ').trim()
                if (currentField && body) {
                    result[currentField] = body
                }
                currentField = null
                continue
            }
            
            // Remove leading * and space
            def content = line.replaceFirst(/^\*\s?/, '')
            
            // Check for field declaration after Javadoc
            if (!currentField) {
                // Look ahead for field declaration
                for (int j = i + 1; j < Math.min(i + 5, lines.size()); j++) {
                    def nextLine = lines[j].trim()
                    if (nextLine.startsWith('private') || nextLine.startsWith('public') || nextLine.startsWith('@')) {
                        continue
                    }
                    // Try to match field name from declaration
                    def m = (nextLine =~ /^\s*private\s+\S+\s+(\w+)\s*;/)
                    if (m) {
                        currentField = m[0][1]
                        break
                    }
                }
            }
            
            // Collect description lines (excluding @ tags)
            if (!content.startsWith('@')) {
                descriptionLines << content
            }
        }
    }
    
    return result
}
```

- [ ] **Step 5: Implement `extractNextStageInterface`**

Replace the stub:
```groovy
String extractNextStageInterface(List<String> lines, String setterMethodSignature) {
    // Extract return type from method signature
    // e.g., "public VehiculeBuilders.Driver registrationNumber(final String registrationNumber)"
    def m = (setterMethodSignature =~ /^\s*public\s+(\S+)\s+\w+/)
    if (m) {
        return m[0][1]
    }
    return ""
}
```

- [ ] **Step 6: Implement `patchClassJavadoc`**

Replace the stub:
```groovy
List<String> patchClassJavadoc(List<String> lines, String outerClassName, String builderType) {
    def result = []
    def foundClassDeclaration = false
    
    for (int i = 0; i < lines.size(); i++) {
        def line = lines[i]
        def trimmed = line.trim()
        
        // Detect class declaration line
        if (trimmed.contains('public final class') && (trimmed.contains('Builder') || trimmed.contains('Builders'))) {
            // Check if there's already a Javadoc
            def hasJavadoc = false
            if (i > 0 && lines[i - 1].trim().startsWith('*/')) {
                hasJavadoc = true
            }
            
            if (!hasJavadoc) {
                // Insert class Javadoc before the class declaration
                def indent = line.replaceFirst(/\S.*/, '')
                if (builderType == "BUILDERS") {
                    result << "${indent}/**"
                    result << "${indent} * Staged builder interfaces for {@link ${outerClassName}}."
                    result << "${indent} * Each interface represents one required construction step."
                    result << "${indent} */"
                } else {
                    result << "${indent}/**"
                    result << "${indent} * Builder for {@link ${outerClassName}}."
                    result << "${indent} * Generated by <a href=\"https://github.com/skinny85/jilt\">JILT</a>."
                    result << "${indent} * Use the {@link #${outerClassName.toLowerCase()}()} factory method to obtain an instance."
                    result << "${indent} */"
                }
            }
        }
        
        result << line
    }
    
    return result
}
```

- [ ] **Step 7: Implement `patchFactoryMethod`**

Replace the stub:
```groovy
List<String> patchFactoryMethod(List<String> lines, String outerClassName) {
    def result = []
    
    for (int i = 0; i < lines.size(); i++) {
        def line = lines[i]
        
        // Detect factory method: public static XxxBuilder xxx()
        def trimmed = line.trim()
        def m = (trimmed =~ /^public\s+static\s+\S+\s+(\w+)\(/)
        if (m) {
            def methodName = m[0][1]
            // Check if it's a factory method (lowercase first char, matches outer class)
            if (methodName.equalsIgnoreCase(outerClassName.toLowerCase())) {
                // Check if there's a Javadoc on the previous lines
                def hasJavadoc = false
                for (int j = Math.max(0, i - 5); j < i; j++) {
                    if (lines[j].trim().startsWith('/**')) {
                        hasJavadoc = true
                        break
                    }
                }
                
                if (!hasJavadoc) {
                    // Insert factory Javadoc
                    def indent = line.replaceFirst(/\S.*/, '')
                    result << "${indent}/**"
                    result << "${indent} * @return a new builder instance to construct a {@link ${outerClassName}}; never {@code null}"
                    result << "${indent} */"
                }
            }
        }
        
        result << line
    }
    
    return result
}
```

- [ ] **Step 8: Implement `patchBuildMethod`**

Replace the stub:
```groovy
List<String> patchBuildMethod(List<String> lines, String outerClassName) {
    def result = []
    
    for (int i = 0; i < lines.size(); i++) {
        def line = lines[i]
        
        // Detect build method: public Xxx build()
        def trimmed = line.trim()
        if (trimmed.matches(/^public\s+\S+\s+build\s*\(\s*\)/)) {
            // Check if there's a Javadoc
            def hasJavadoc = false
            for (int j = Math.max(0, i - 5); j < i; j++) {
                if (lines[j].trim().startsWith('/**')) {
                    hasJavadoc = true
                    break
                }
            }
            
            if (!hasJavadoc) {
                def indent = line.replaceFirst(/\S.*/, '')
                result << "${indent}/**"
                result << "${indent} * Builds and returns a new {@link ${outerClassName}} instance with the values set on this builder."
                result << "${indent} * @return new {@link ${outerClassName}} instance; never {@code null}"
                result << "${indent} */"
            }
        }
        
        result << line
    }
    
    return result
}
```

- [ ] **Step 9: Implement `patchSetterMethods`**

Replace the stub:
```groovy
List<String> patchSetterMethods(List<String> lines, Map<String, String> fieldDescriptions, String builderType) {
    def result = []
    
    for (int i = 0; i < lines.size(); i++) {
        def line = lines[i]
        def trimmed = line.trim()
        
        // Detect setter method: public (Type) methodName(final Type methodName)
        // where method name matches parameter name
        def m = (trimmed =~ /^public\s+(\S+)\s+(\w+)\s*\(\s*final\s+\S+\s+(\w+)\s*\)/)
        if (m && m[0][2] == m[0][3]) {
            def returnType = m[0][1]
            def methodName = m[0][2]
            def fieldName = m[0][3]
            
            // This is a setter method
            def indent = line.replaceFirst(/\S.*/, '')
            
            // Look back for existing Javadoc
            def javadocStart = -1
            def javadocEnd = -1
            for (int j = Math.max(0, i - 1); j >= Math.max(0, i - 20); j--) {
                if (lines[j].trim().startsWith('/**')) {
                    javadocStart = j
                    break
                }
            }
            if (javadocStart >= 0) {
                for (int j = javadocStart; j <= i; j++) {
                    if (lines[j].trim().startsWith('*/')) {
                        javadocEnd = j
                        break
                    }
                }
            }
            
            // Collect annotations before Javadoc
            def annotations = []
            def annotationStart = javadocStart >= 0 ? javadocStart : i
            for (int j = Math.max(0, annotationStart - 1); j < annotationStart; j--) {
                if (lines[j].trim().startsWith('@')) {
                    annotations.add(0, lines[j])
                } else {
                    break
                }
            }
            
            // Remove old Javadoc if present
            if (javadocStart >= 0 && javadocEnd >= 0) {
                // Keep annotations, remove Javadoc
                for (int j = 0; j < annotationStart; j++) {
                    result << lines[j]
                }
                i = javadocEnd  // Skip old Javadoc
            }
            
            // Determine return description based on builder type
            def returnDescription = (builderType == "STAGED") 
                ? "{@link ${returnType}} the next builder stage"
                : "this builder"
            
            // Get field description
            def fieldDescription = fieldDescriptions.get(fieldName, "")
            
            // Add annotations
            annotations.each { result << it }
            
            // Add new Javadoc
            result << "${indent}/**"
            if (fieldDescription) {
                // Split description into lines and wrap
                def descLines = fieldDescription.split('\\. ')
                descLines.each { descLine ->
                    result << "${indent} * ${descLine}"
                }
            }
            result << "${indent} * @param ${fieldName} ${fieldDescription}"
            result << "${indent} * @return ${returnDescription}"
            result << "${indent} */"
        }
        
        result << line
    }
    
    return result
}
```

- [ ] **Step 10: Implement `patchBuildersInterfaces`**

Replace the stub:
```groovy
List<String> patchBuildersInterfaces(List<String> lines, String outerClassName) {
    def result = []
    def inTopLevelJavadoc = false
    def inInterface = false
    def currentInterfaceName = null
    
    for (int i = 0; i < lines.size(); i++) {
        def line = lines[i]
        def trimmed = line.trim()
        
        // Detect top-level class Javadoc
        if (trimmed.startsWith('/**') && !inTopLevelJavadoc) {
            // Check if next non-empty line is the class declaration
            def hasClassDeclaration = false
            for (int j = i + 1; j < Math.min(i + 5, lines.size()); j++) {
                if (lines[j].trim().contains('public final class') && lines[j].trim().contains('Builders')) {
                    hasClassDeclaration = true
                    break
                }
            }
            
            if (hasClassDeclaration) {
                inTopLevelJavadoc = true
                // Replace the Javadoc
                def indent = line.replaceFirst(/\S.*/, '')
                result << "${indent}/**"
                result << "${indent} * Staged builder interfaces for {@link ${outerClassName}}."
                result << "${indent} * Each interface represents one required construction step."
                result << "${indent} */"
                
                // Skip old Javadoc lines
                while (i < lines.size() && !lines[i].trim().startsWith('*/')) {
                    i++
                }
                if (i < lines.size() && lines[i].trim().startsWith('*/')) {
                    i++
                }
                continue
            }
        }
        
        if (inTopLevelJavadoc && trimmed.startsWith('*/')) {
            inTopLevelJavadoc = false
        }
        
        // Detect interface declarations
        if (trimmed.matches(/^\s*public\s+interface\s+(\w+)\s*\{/)) {
            def m = (trimmed =~ /^\s*public\s+interface\s+(\w+)\s*\{/)
            if (m) {
                currentInterfaceName = m[0][1]
                inInterface = true
                
                // Add Javadoc before interface
                def indent = line.replaceFirst(/\S.*/, '')
                result << "${indent}/**"
                result << "${indent} * Builder step: set the {@code ${currentInterfaceName.toLowerCase()}} field of {@link ${outerClassName}}."
                result << "${indent} */"
            }
        }
        
        if (inInterface && trimmed.startsWith('}')) {
            inInterface = false
            currentInterfaceName = null
        }
        
        result << line
    }
    
    return result
}
```

- [ ] **Step 11: Run self-tests**

```bash
groovy scripts/jilt-javadoc-patcher.groovy
```

Expected output:
```
Self-tests: PASSED
```

If any assertion fails, fix the implementation before proceeding.

- [ ] **Step 12: Commit**

```bash
git add scripts/jilt-javadoc-patcher.groovy
git commit -m "Add Groovy JILT Javadoc patcher with self-tests"
```

---

## Task 3: Wire up Maven (gmavenplus-plugin)

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add new execution to gmavenplus-plugin in `javadoc` profile**

In `pom.xml`, inside the `javadoc` profile, find the existing gmavenplus-plugin and add a new execution:

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
        <!-- existing delombok-related executions -->
        
        <execution>
            <id>patch-jilt-javadoc</id>
            <phase>process-classes</phase>
            <goals>
                <goal>execute</goal>
            </goals>
            <configuration>
                <scripts>
                    <script>${project.basedir}/scripts/javadoc-utils.groovy</script>
                    <script>${project.basedir}/scripts/jilt-javadoc-patcher.groovy</script>
                </scripts>
            </configuration>
        </execution>
    </executions>
</plugin>
```

> **Note:** The script must run in `process-classes` phase (after JILT annotation processing) and after any existing `process-sources` executions.

- [ ] **Step 2: Verify pom.xml is valid**

```bash
mvn validate -Pjavadoc
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "Wire gmavenplus-plugin for JILT Javadoc patching in process-classes phase"
```

---

## Task 4: Integration test

**Files:** None modified

- [ ] **Step 1: Run full javadoc build**

```bash
mvn clean compile javadoc:javadoc -Pjavadoc
```

Expected: `BUILD SUCCESS`

Watch the output for:
```
JiltJavadocPatcher: scanning .../target/generated-sources/annotations
  Patched: VehiculeBuilder.java (CLASSIC)
  Patched: GarageBuilder.java (STAGED)
  Patched: VehiculeBuilders.java (BUILDERS)
JiltJavadocPatcher: done.
```

- [ ] **Step 2: Verify JILT output was patched**

```bash
grep -A2 "@param registrationNumber" \
    target/generated-sources/annotations/fr/fxjavadevblog/mvnlmbkjdoc/vehicules/VehiculeBuilder.java
```

Expected output:
```java
         * @param registrationNumber Unique registration plate. Never null.
         * @return this builder
```

```bash
grep "@return this builder" \
    target/generated-sources/annotations/fr/fxjavadevblog/mvnlmbkjdoc/vehicules/VehiculeBuilder.java
```

Expected: matches for all setter methods.

```bash
grep "@return {@link" \
    target/generated-sources/annotations/fr/fxjavadevblog/mvnlmbkjdoc/vehicules/VehiculeBuilder.java
```

Expected: matches for STAGED builder setters with next stage interface references.

```bash
grep "Builder for {@link" \
    target/generated-sources/annotations/fr/fxjavadevblog/mvnlmbkjdoc/vehicules/VehiculeBuilder.java
```

Expected: 1 match (class-level Javadoc).

```bash
grep "Staged builder interfaces for {@link" \
    target/generated-sources/annotations/fr/fxjavadevblog/mvnlmbkjdoc/vehicules/VehiculeBuilders.java
```

Expected: 1 match (top-level interface Javadoc).

- [ ] **Step 3: Verify generated HTML**

```bash
grep -A5 'registrationNumber' \
    "target/reports/apidocs/fr/fxjavadevblog/mvnlmbkjdoc/vehicules/VehiculeBuilder.html" \
    | head -30
```

Expected: `@param registrationNumber` and its description appear in the HTML.

Alternatively open in browser:
```bash
xdg-open target/reports/apidocs/fr/fxjavadevblog/mvnlmbkjdoc/vehicules/VehiculeBuilder.html
```

The `registrationNumber(String)` method should show:
- Description: field description text
- **Parameters:** `registrationNumber` — description from field Javadoc
- **Returns:** this builder (or next stage interface for STAGED)

The `build()` method should show:
- Description: "Builds and returns a new Vehicule instance..."
- **Returns:** new Vehicule instance; never null

- [ ] **Step 4: Commit**

```bash
git add target/  # only if target/ is tracked — otherwise skip
git commit -m "Integration verified: JILT builder Javadoc fully documented"
```

> If `target/` is in `.gitignore` (normal), only commit source changes if any were made in this task.

---

## Self-review against spec

| Spec requirement | Task |
|-----------------|------|
| Script file: `scripts/jilt-javadoc-patcher.groovy` | Task 2 (implementation) |
| Maven phase: `process-classes` | Task 3 (pom.xml configuration) |
| Maven profile: `javadoc` | Task 3 (inside javadoc profile) |
| Detects `@Generated("Jilt-` and `@JiltGenerated` | Task 2 (`isJiltGenerated`) |
| CLASSIC builder detection | Task 2 (`detectBuilderType`) |
| STAGED builder detection (implements XxxBuilders) | Task 2 (`detectBuilderType`) |
| BUILDERS interface container detection | Task 2 (`detectBuilderType`) |
| Class-level Javadoc on builders | Task 2 (`patchClassJavadoc`) |
| Factory method Javadoc | Task 2 (`patchFactoryMethod`) |
| `build()` method Javadoc | Task 2 (`patchBuildMethod`) |
| Setter `@param fieldName <text>` from field Javadoc | Task 2 (`patchSetterMethods`) |
| Setter `@return this builder` (CLASSIC) | Task 2 (`patchSetterMethods`) |
| Setter `@return NextStage the next builder stage` (STAGED) | Task 2 (`patchSetterMethods`) |
| BUILDERS top-level interface Javadoc | Task 2 (`patchBuildersInterfaces`) |
| BUILDERS nested interface Javadoc | Task 2 (`patchBuildersInterfaces`) |
| No changes to existing Lombok scripts | Confirmed (new file only) |
| No patching of `src/main/java` | Confirmed (operates on target/ only) |
| Normal build unaffected | Confirmed (only in javadoc profile) |
| Build succeeds | Task 4 step 1 |
| HTML shows full documentation | Task 4 step 3 |
