class JiltJavadocPatcher {

    boolean isJiltGenerated(List<String> lines) {
        lines.any { it.contains('@Generated("Jilt-') || it.contains('@JiltGenerated') }
    }

    String detectBuilderType(List<String> lines) {
        if (lines.any { (it.contains('public final class') || it.contains('public class') || it.contains('public interface')) && it.contains('Builders') && !it.contains('implements') }) {
            return "BUILDERS"
        }
        if (lines.any { it.contains('implements') && it.contains('Builders.') }) {
            return "STAGED"
        }
        if (lines.any { (it.contains('public final class') || it.contains('public class') || it.contains('public interface')) && it.contains('Builder') }) {
            return "CLASSIC"
        }
        return "UNKNOWN"
    }

    Map<String, String> extractFieldDescriptions(List<String> lines) {
        def result = [:]
        def inJavadoc = false
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
                    // Look ahead for field or setter declaration after this Javadoc
                    def fieldName = null
                    for (int j = i + 1; j < Math.min(i + 10, lines.size()); j++) {
                        def nextLine = lines[j].trim()
                        // Try field declaration: private Type name;
                        def m = (nextLine =~ /private\s+\S+\s+(\w+)\s*;/)
                        if (m) {
                            fieldName = m[0][1]
                            break
                        }
                        // Try setter method: public ReturnType methodName(final Type paramName) or (Type paramName)
                        m = (nextLine =~ /public\s+\S+\s+(\w+)\s*\(\s*(final\s+)?\S+\s+\w+/)
                        if (m) {
                            fieldName = m[0][1]
                            break
                        }
                        // Skip annotations and empty lines
                        if (nextLine.startsWith('@') || nextLine.isEmpty()) {
                            continue
                        }
                        // If we hit something else, stop looking
                        break
                    }
                    
                    if (fieldName) {
                        def body = descriptionLines.findAll { !it.startsWith('@') }.join(' ').trim()
                        if (body) {
                            result[fieldName] = body
                        }
                    }
                    continue
                }
                
                def content = line.replaceFirst(/^\*\s?/, '')
                if (!content.startsWith('@')) {
                    descriptionLines << content
                }
            }
        }
        
        return result
    }

    String extractOuterClassName(List<String> lines) {
        def classLine = lines.find { (it.contains('public final class') || it.contains('public class') || it.contains('public interface')) && (it.contains('Builder') || it.contains('Builders')) }
        if (!classLine) return ""
        
        def m = (classLine =~ /public\s+(?:final\s+)?(?:class|interface)\s+(\w+)/)
        if (!m) return ""
        
        def className = m[0][1]
        if (className.endsWith("Builders")) {
            return className - "Builders"
        }
        if (className.endsWith("Builder")) {
            return className - "Builder"
        }
        return className
    }

    String extractNextStageInterface(List<String> lines, String setterMethodSignature) {
        def m = (setterMethodSignature =~ /^\s*public\s+(\S+)\s+\w+/)
        if (m) {
            return m[0][1]
        }
        return ""
    }

    List<String> patchClassJavadoc(List<String> lines, String outerClassName, String builderType) {
        def result = []
        for (int i = 0; i < lines.size(); i++) {
            def line = lines[i]
            def trimmed = line.trim()
            
            if ((trimmed.contains('public final class') || trimmed.contains('public class') || trimmed.contains('public interface')) && (trimmed.contains('Builder') || trimmed.contains('Builders'))) {
                def hasJavadoc = false
                if (i > 0 && lines[i - 1].trim() == '*/') {
                    hasJavadoc = true
                }
                
                if (!hasJavadoc) {
                    def indent = line.replaceFirst(/\S.*/, '')
                    // Collect preceding annotations to reorder: Javadoc -> annotations -> class
                    def precedingAnnotations = []
                    for (int j = i - 1; j >= Math.max(0, i - 5); j--) {
                        if (lines[j].trim().startsWith('@')) {
                            precedingAnnotations.add(0, lines[j])
                        } else if (lines[j].trim().isEmpty()) {
                            // Keep empty lines before annotations
                            precedingAnnotations.add(0, lines[j])
                        } else {
                            break
                        }
                    }
                    
                    // Remove annotations from result (they were already added)
                    def annotationsCount = precedingAnnotations.size()
                    for (int k = 0; k < annotationsCount; k++) {
                        if (result && result.size() > 0) {
                            result.removeLast()
                        }
                    }
                    
                    // Add Javadoc first, then annotations
                    if (builderType == "BUILDERS") {
                        result << "${indent}/**"
                        result << "${indent} * Staged builder interfaces for ${outerClassName}."
                        result << "${indent} * Each interface represents one required construction step."
                        result << "${indent} * "
                        result << "${indent} * @see ${outerClassName}"
                        result << "${indent} */"
                    } else {
                        result << "${indent}/**"
                        result << "${indent} * Builder for {@link ${outerClassName}}."
                        result << "${indent} * Generated by <a href=\"https://github.com/skinny85/jilt\">JILT</a>."
                        // JILT uses camelCase for factory method: first letter lowercase
                        def factoryMethod = outerClassName[0].toLowerCase() + (outerClassName.length() > 1 ? outerClassName[1..-1] : '')
                        result << "${indent} * Use the {@link #${factoryMethod}()} factory method to obtain an instance."
                        result << "${indent} */"
                    }
                    
                    // Add annotations back
                    precedingAnnotations.each { result << it }
                }
            }
            
            result << line
        }
        return result
    }

    List<String> patchFactoryMethod(List<String> lines, String outerClassName) {
        def result = []
        def skipUntil = -1
        
        for (int i = 0; i < lines.size(); i++) {
            if (i <= skipUntil) {
                i++
                continue
            }
            
            def line = lines[i]
            def trimmed = line.trim()
            
            def m = (trimmed =~ /^public\s+static\s+\S+\s+(\w+)\(/)
            if (m) {
                def methodName = m[0][1]
                if (methodName.equalsIgnoreCase(outerClassName.toLowerCase())) {
                    def javadocStart = -1
                    def javadocEnd = -1
                    for (int j = i - 1; j >= Math.max(0, i - 5); j--) {
                        if (lines[j].trim().startsWith('/**')) {
                            javadocStart = j
                            break
                        }
                    }
                    if (javadocStart >= 0) {
                        for (int j = javadocStart; j < i; j++) {
                            if (lines[j].trim().startsWith('*/')) {
                                javadocEnd = j
                                break
                            }
                        }
                    }
                    
                    def indent = line.replaceFirst(/\S.*/, '')
                    result << "${indent}/**"
                    result << "${indent} * @return a new builder instance to construct a {@link ${outerClassName}}; never {@code null}"
                    result << "${indent} */"
                    
                    if (javadocStart >= 0 && javadocEnd >= 0) {
                        skipUntil = javadocEnd
                    }
                }
            }
            
            result << line
        }
        return result
    }

    List<String> patchBuildMethod(List<String> lines, String outerClassName) {
        def result = []
        def skipUntil = -1
        
        for (int i = 0; i < lines.size(); i++) {
            if (i <= skipUntil) {
                i++
                continue
            }
            
            def line = lines[i]
            def trimmed = line.trim()
            
            if (trimmed.contains('public') && trimmed.contains('build()')) {
                def javadocStart = -1
                def javadocEnd = -1
                for (int j = i - 1; j >= Math.max(0, i - 5); j--) {
                    if (lines[j].trim().startsWith('/**')) {
                        javadocStart = j
                        break
                    }
                }
                if (javadocStart >= 0) {
                    for (int j = javadocStart; j < i; j++) {
                        if (lines[j].trim().startsWith('*/')) {
                            javadocEnd = j
                            break
                        }
                    }
                }
                
                def indent = line.replaceFirst(/\S.*/, '')
                result << "${indent}/**"
                result << "${indent} * Builds and returns a new {@link ${outerClassName}} instance with the values set on this builder."
                result << "${indent} * @return new {@link ${outerClassName}} instance; never {@code null}"
                result << "${indent} */"
                
                if (javadocStart >= 0 && javadocEnd >= 0) {
                    skipUntil = javadocEnd
                }
            }
            
            result << line
        }
        return result
    }

    List<String> patchSetterMethods(List<String> lines, Map<String, String> fieldDescriptions, String builderType) {
        // First pass: find all setters and their Javadoc ranges
        def setterInfos = []
        for (int i = 0; i < lines.size(); i++) {
            def line = lines[i]
            def trimmed = line.trim()
            
            def m = (trimmed =~ /^public\s+(\S+)\s+(\w+)\s*\(\s*(final\s+)?\S+\s+(\w+)\s*\)/)
            if (m && m[0][2] == m[0][4]) {
                def returnType = m[0][1]
                def fieldName = m[0][4]
                def indent = line.replaceFirst(/\S.*/, '')
                
                // Look back for existing Javadoc
                def javadocStart = -1
                def javadocEnd = -1
                for (int j = i - 1; j >= Math.max(0, i - 20); j--) {
                    if (lines[j].trim().startsWith('/**')) {
                        javadocStart = j
                        break
                    }
                }
                if (javadocStart >= 0) {
                    for (int j = javadocStart; j < i; j++) {
                        if (lines[j].trim().startsWith('*/')) {
                            javadocEnd = j
                            break
                        }
                    }
                }
                
                setterInfos << [
                    index: i,
                    returnType: returnType,
                    fieldName: fieldName,
                    indent: indent,
                    javadocStart: javadocStart,
                    javadocEnd: javadocEnd
                ]
            }
        }
        
        // Second pass: build result with new Javadoc
        def result = []
        def setterIndex = 0
        def currentSetter = setterInfos.size() > 0 ? setterInfos[0] : null
        
        for (int i = 0; i < lines.size(); i++) {
            // Check if we need to inject new Javadoc before this line
            if (currentSetter && i == currentSetter.index) {
                def returnDescription = (builderType == "STAGED")
                    ? "{@link ${currentSetter.returnType}} the next builder stage"
                    : "this builder"
                def fieldDescription = fieldDescriptions.get(currentSetter.fieldName, "")
                
                result << "${currentSetter.indent}/**"
                if (fieldDescription) {
                    result << "${currentSetter.indent} * ${fieldDescription}"
                }
                result << "${currentSetter.indent} * @param ${currentSetter.fieldName} ${fieldDescription}"
                result << "${currentSetter.indent} * @return ${returnDescription}"
                result << "${currentSetter.indent} */"
                
                // Move to next setter
                setterIndex++
                currentSetter = setterIndex < setterInfos.size() ? setterInfos[setterIndex] : null
            }
            
            // Check if this line is part of old Javadoc to skip
            def shouldSkip = currentSetter && i >= currentSetter.javadocStart && i <= currentSetter.javadocEnd
            // Also check previous setter's Javadoc range
            if (!shouldSkip && setterIndex > 0 && setterIndex - 1 < setterInfos.size()) {
                def prevSetter = setterInfos[setterIndex - 1]
                shouldSkip = i >= prevSetter.javadocStart && i <= prevSetter.javadocEnd
            }
            
            if (!shouldSkip) {
                result << lines[i]
            }
        }
        return result
    }

    List<String> patchBuildersInterfaces(List<String> lines, String outerClassName) {
        def result = []
        def inTopLevelJavadoc = false
        def currentInterfaceName = null
        def buildersClassIndex = -1
        
        // Find the builders class/index interface line
        for (int i = 0; i < lines.size(); i++) {
            def trimmed = lines[i].trim()
            if ((trimmed.contains('public final class') || trimmed.contains('public class') || trimmed.contains('public interface')) && trimmed.contains('Builders')) {
                buildersClassIndex = i
                break
            }
        }
        
        for (int i = 0; i < lines.size(); i++) {
            def line = lines[i]
            def trimmed = line.trim()
            
            // Check if this is the builders class line and we need to inject top-level Javadoc
            // and add a marker member so Javadoc renders the interface description
            if (i == buildersClassIndex && buildersClassIndex > 0) {
                def indent = line.replaceFirst(/\S.*/, '')
                
                // Collect preceding annotations to reorder: Javadoc -> annotations -> class
                def precedingAnnotations = []
                def annotationsStart = -1
                for (int j = i - 1; j >= Math.max(0, i - 5); j--) {
                    if (lines[j].trim().startsWith('@')) {
                        if (annotationsStart < 0) annotationsStart = j
                        precedingAnnotations.add(0, lines[j])
                    } else if (lines[j].trim().isEmpty()) {
                        precedingAnnotations.add(0, lines[j])
                    } else {
                        break
                    }
                }
                
                // Remove annotations from result (they were already added)
                def annotationsCount = precedingAnnotations.size()
                for (int k = 0; k < annotationsCount; k++) {
                    if (result && result.size() > 0) {
                        result.removeLast()
                    }
                }
                
                // Check if there's already a Javadoc before this line
                def hasPrecedingJavadoc = false
                for (int j = Math.max(0, i - 10); j < i; j++) {
                    if (lines[j].trim().startsWith('/**')) {
                        hasPrecedingJavadoc = true
                        break
                    }
                }
                
                if (!hasPrecedingJavadoc) {
                    // Add Javadoc first
                    result << "${indent}/**"
                    result << "${indent} * Staged builder interfaces for the ${outerClassName} class."
                    result << "${indent} * This interface container and its nested interfaces are generated by"
                    result << "${indent} * <a href=\"https://github.com/skinny85/jilt\">JILT</a> to implement"
                    result << "${indent} * the staged builder pattern."
                    result << "${indent} * "
                    result << "${indent} */"
                }
                
                // Add annotations back
                precedingAnnotations.each { result << it }
                
                // Add the interface line
                result << line
                
                // Check if this is the builders interface declaration line (ends with {)
                if (trimmed.endsWith('{') && trimmed.contains('Builders')) {
                    // Add a constant field so Javadoc renders the top-level description
                    // Interfaces can have public static final constants
                    result << "${indent}    /**"
                    result << "${indent}     * Marker constant to ensure Javadoc renders the interface description."
                    result << "${indent}     */"
                    result << "${indent}    public static final boolean JILT_GENERATED = true;"
                }
                continue
            }
            
            if (trimmed.startsWith('/**') && !inTopLevelJavadoc) {
                def hasClassDeclaration = false
                for (int j = i + 1; j < Math.min(i + 5, lines.size()); j++) {
                    if ((lines[j].trim().contains('public final class') || lines[j].trim().contains('public class') || lines[j].trim().contains('public interface')) && lines[j].trim().contains('Builders')) {
                        hasClassDeclaration = true
                        break
                    }
                }
                
                if (hasClassDeclaration) {
                    inTopLevelJavadoc = true
                    def indent = line.replaceFirst(/\S.*/, '')
                    result << "${indent}/**"
                    result << "${indent} * Staged builder interfaces for {@link ${outerClassName}}."
                    result << "${indent} * Each interface represents one required construction step."
                    result << "${indent} */"
                    
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
            
            // Match both top-level public interfaces and nested interfaces
            def m = (trimmed =~ /(public\s+)?interface\s+(\w+)\b/)
            if (m) {
                def interfaceName = m[0][2]
                // Skip the top-level Builders interface (e.g., RentalContractBuilders)
                // and also skip if this is the line where we already injected top-level Javadoc
                if (!interfaceName.equals("${outerClassName}Builders") && i != buildersClassIndex) {
                    currentInterfaceName = interfaceName
                    def indent = line.replaceFirst(/\S.*/, '')
                    result << "${indent}/**"
                    result << "${indent} * Builder step: set the {@code ${currentInterfaceName.toLowerCase()}} field of {@link ${outerClassName}}."
                    result << "${indent} */"
                }
            }
            
            result << line
        }
        return result
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
        // test data: CLASSIC builder
        def classicInput = [
            '/**',
            ' * Generated by JILT.',
            ' */',
            '@Generated("Jilt-1.0.0")',
            'public final class VehiculeBuilder {',
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

        // test data: STAGED builder
        def stagedInput = [
            '/**',
            ' * Generated by JILT.',
            ' */',
            '@JiltGenerated',
            'public final class VehiculeBuilder implements VehiculeBuilders.Driver, VehiculeBuilders.Vehicule {',
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

        // test data: BUILDERS interface container
        def buildersInput = [
            '@Generated("Jilt-1.0.0")',
            'public interface VehiculeBuilders {',
            '    interface Driver {',
            '        Vehicule registrationNumber(String registrationNumber);',
            '    }',
            '',
            '    interface Vehicule extends Driver {',
            '        Vehicule build();',
            '    }',
            '}',
        ]

        // test 8: BUILDERS detection
        assert detectBuilderType(buildersInput) == "BUILDERS", "Should detect BUILDERS type"

        // test 9: BUILDERS patching
        def buildersOutput = patchBuildersInterfaces(buildersInput, "Vehicule")
        def buildersText = buildersOutput.join('\n')
        assert buildersText.contains('Staged builder interfaces for the Vehicule class.'),
               "Missing top-level Javadoc in BUILDERS output"
        assert buildersText.contains('Builder step: set the {@code driver} field of {@link Vehicule}'),
               "Missing nested interface Javadoc in BUILDERS output"
        assert buildersText.contains('public static final boolean JILT_GENERATED = true'),
               "Missing marker constant in BUILDERS output"

        // test data: class-level Javadoc patching
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

        // test data: factory method patching
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

        // test data: build method patching
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
