class LombokJavadocPropagator {

    // Returns true if the file's class-level Javadoc contains the -- BUILDER -- marker.
    boolean hasBuilderMarker(List<String> lines) {
        lines.any { it.trim() == '* -- BUILDER --' }
    }

    // Extracts @return text from getter methods.
    // Returns: {fieldName -> returnText}  e.g. {"registrationNumber" -> "the plate; never null"}
    Map<String, String> extractGetterReturns(List<String> lines) {
        def result = [:]
        for (int i = 0; i < lines.size(); i++) {
            def m = (lines[i].trim() =~ /^public\s+\S+\s+(?:get|is)([A-Z]\w*)\s*\(\)/)
            if (!m) continue
            def raw = m[0][1]
            def fieldName = raw[0].toLowerCase() + (raw.size() > 1 ? raw[1..-1] : '')
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

    // Extracts -- BUILDER-PARAM -- override sections from field Javadoc.
    // Returns: {fieldName -> overrideContent}
    Map<String, String> extractBuilderParamOverrides(List<String> lines) {
        def result = [:]
        for (int i = 0; i < lines.size(); i++) {
            if (lines[i].trim() != '* -- BUILDER-PARAM --') continue
            def overrideLines = []
            int j = i + 1
            while (j < lines.size() && lines[j].trim() != '*/') {
                overrideLines << lines[j].trim().replaceFirst(/^\*\s?/, '')
                j++
            }
            int k = j + 1
            while (k < lines.size() && lines[k].trim().startsWith('@')) k++
            if (k < lines.size()) {
                def fm = (lines[k].trim() =~ /^private\s+(?:final\s+)?[\w<>?,.\[\] ]+\s+(\w+)\s*[;=]/)
                if (fm) result[fm[0][1]] = overrideLines.findAll { it }.join('\n')
            }
        }
        return result
    }

    // Patches builder setter Javadoc:
    // - Replaces `@return {@code this}.` with `@param fieldName <text>` + `@return this builder`
    // - If field has a -- BUILDER-PARAM -- override, replaces entire Javadoc with override content
    List<String> patchBuilderSetters(List<String> lines,
                                      Map<String, String> getterReturns,
                                      Map<String, String> overrides) {
        def result = []
        for (int i = 0; i < lines.size(); i++) {
            def line    = lines[i]
            def trimmed = line.trim()

            // Match builder setter: public *Builder methodName(final Type methodName)
            // (method name and parameter name must be identical)
            def m = (trimmed =~ /^public\s+\S+Builder\s+(\w+)\s*\(final\s+.+\s+(\w+)\s*\)/)
            if (m && m[0][1] == m[0][2]) {
                def fieldName = m[0][1]
                def override  = overrides[fieldName]
                def indent    = line.replaceFirst(/\S.*/, '')

                // Pop preceding annotations (@SuppressWarnings, @lombok.Generated, etc.)
                def annotations = []
                while (result && result.last().trim().startsWith('@')) {
                    annotations.add(0, result.removeLast())
                }

                if (override != null) {
                    // Remove existing Javadoc block and replace with override
                    if (result && result.last().trim() == '*/') {
                        while (result && !result.last().trim().startsWith('/**')) result.removeLast()
                        if (result) result.removeLast()
                    }
                    result << "${indent}/**"
                    override.split('\n').each { result << "${indent} * ${it}" }
                    result << "${indent} */"
                } else {
                    // Replace `@return {@code this}.` with @param + @return this builder
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

    void processFile(File file) {
        def lines = file.readLines('UTF-8')
        if (!hasBuilderMarker(lines)) return
        def getterReturns = extractGetterReturns(lines)
        def overrides     = extractBuilderParamOverrides(lines)
        def patched       = patchBuilderSetters(lines, getterReturns, overrides)
        file.write(patched.join('\n') + '\n', 'UTF-8')
        println "  Patched: ${file.name} (${getterReturns.size()} getters found)"
    }

    void runSelfTests() {
        // ── Test 1-4: basic case (body + @return -> @param + @return this builder) ──
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

        assert hasBuilderMarker(input), "Should detect -- BUILDER -- marker"
        assert !hasBuilderMarker(['/** no marker */', 'public class Bar {}']),
               "Should NOT detect marker when absent"

        def getterReturns = extractGetterReturns(input)
        assert getterReturns['name'] == 'the name; never null',
               "Wrong getter return: '${getterReturns['name']}'"

        def overrides = extractBuilderParamOverrides(input)
        assert overrides.isEmpty(), "Should have no overrides"

        def output = patchBuilderSetters(input, getterReturns, overrides)
        def text = output.join('\n')
        assert text.contains('@param name the name; never null'),
               "Missing @param in output:\n${text}"
        assert text.contains('@return this builder'),
               "Missing @return this builder"
        assert !text.contains('@return {@code this}'),
               "Old @return not replaced"

        // ── Test 5: -- BUILDER-PARAM -- override ────────────────────────
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
        assert ov2['count'].contains('must be positive'), "Override content wrong: '${ov2['count']}'"
        def out2 = patchBuilderSetters(overrideInput, [:], ov2)
        def txt2 = out2.join('\n')
        assert txt2.contains('Sets the count (must be positive)'), "Override not applied:\n${txt2}"
        assert txt2.contains('@param count positive integer'), "Override @param missing"
        assert !txt2.contains('@return {@code this}'), "Old @return not replaced in override case"

        println "Self-tests: PASSED"
    }
}

// When invoked via Maven (project binding is set), process delombok output.
// When invoked standalone (groovy scripts/...), run self-tests.
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
