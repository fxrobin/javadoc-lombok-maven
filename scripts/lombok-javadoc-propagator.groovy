class LombokJavadocPropagator {

    // ─── @Builder helpers ─────────────────────────────────────────────────────

    boolean hasBuilderAnnotation(List<String> lines) {
        lines.any { it.trim() =~ /^@(lombok\.)?Builder(\(.*\))?$/ }
    }

    // Extracts @return text from getter methods in delombok output.
    // Returns: {fieldName -> returnText}
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

    // Extracts -- BUILDER-PARAM -- override sections from field Javadoc in source.
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

    // Returns true if result buffer ends with */ (skipping blank lines and annotations).
    boolean hasPrecedingJavadoc(List<String> result) {
        for (int i = result.size() - 1; i >= 0; i--) {
            def t = result[i].trim()
            if (t.isEmpty() || t.startsWith('@')) continue
            return t == '*/'
        }
        return false
    }

    // Patches delombok builder output: class Javadoc, build() Javadoc, setter Javadoc.
    List<String> patchBuilderSetters(List<String> lines,
                                      Map<String, String> getterReturns,
                                      Map<String, String> overrides) {
        def result = []
        def outerClassName = null

        for (int i = 0; i < lines.size(); i++) {
            def line    = lines[i]
            def trimmed = line.trim()
            def indent  = line.replaceFirst(/\S.*/, '')

            // ── Builder class declaration ──────────────────────────────
            def classM = (trimmed =~ /^public static class (\w+)Builder\s*\{/)
            if (classM) {
                outerClassName = classM[0][1]
                if (!hasPrecedingJavadoc(result)) {
                    def annotations = collectPrecedingAnnotations(result)
                    result << "${indent}/**"
                    result << "${indent} * Builder class for {@link ${outerClassName}}."
                    result << "${indent} */"
                    result.addAll(annotations)
                }
                result << line
                continue
            }

            // ── build() termination method ─────────────────────────────
            if (outerClassName && (trimmed =~ /^public\s+\S+\s+build\s*\(\s*\)\s*\{/)) {
                def annotations = collectPrecedingAnnotations(result)
                if (!hasPrecedingJavadoc(result)) {
                    result << "${indent}/**"
                    result << "${indent} * Builds and returns a new {@link ${outerClassName}} instance."
                    result << "${indent} * @return new {@link ${outerClassName}} instance; never {@code null}"
                    result << "${indent} */"
                }
                result.addAll(annotations)
                result << line
                continue
            }

            // ── Builder setter methods ─────────────────────────────────
            def m = (trimmed =~ /^public\s+\S+Builder\s+(\w+)\s*\(final\s+.+\s+(\w+)\s*\)/)
            if (m && m[0][1] == m[0][2]) {
                def fieldName = m[0][1]
                def override  = overrides[fieldName]
                def annotations = collectPrecedingAnnotations(result)

                if (override != null) {
                    if (result && result.last().trim() == '*/') {
                        while (result && !result.last().trim().startsWith('/**')) result.removeLast()
                        if (result) result.removeLast()
                    }
                    result << "${indent}/**"
                    override.split('\n').each { result << "${indent} * ${it}" }
                    result << "${indent} */"
                } else {
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

    // ─── Annotation parsing ───────────────────────────────────────────────────

    // Collects the full annotation text (handles multi-line annotations).
    String findAnnotationLine(List<String> lines, String name) {
        for (int i = 0; i < lines.size(); i++) {
            def t = lines[i].trim()
            if (!(t =~ /^@(lombok\.)?${name}\b/)) continue
            // Balanced on this line?
            if (t.count('(') == t.count(')')) return t
            // Multi-line: collect until balanced
            def sb = new StringBuilder(t)
            int depth = t.count('(') - t.count(')')
            int j = i + 1
            while (depth > 0 && j < lines.size()) {
                def next = lines[j].trim()
                sb.append(' ').append(next)
                depth += next.count('(') - next.count(')')
                j++
            }
            return sb.toString()
        }
        return null
    }

    List<String> parseStringListParam(String ann, String param) {
        if (!ann) return []
        def m = ann =~ /\b${param}\s*=\s*"(\w+)"/
        if (m) return [m[0][1]]
        m = ann =~ /\b${param}\s*=\s*\{([^}]*)\}/
        if (!m) return []
        return (m[0][1] =~ /"(\w+)"/).collect { it[1] }
    }

    boolean parseBoolParam(String ann, String param, boolean defaultVal = false) {
        if (!ann) return defaultVal
        def m = ann =~ /\b${param}\s*=\s*(true|false)/
        return m ? m[0][1] == 'true' : defaultVal
    }

    // Returns null if @ToString is absent; otherwise a map of params.
    Map parseToStringParams(List<String> lines) {
        def ann = findAnnotationLine(lines, 'ToString')
        if (!ann) return null
        return [
            of               : parseStringListParam(ann, 'of'),
            exclude          : parseStringListParam(ann, 'exclude'),
            onlyExplicit     : parseBoolParam(ann, 'onlyExplicitlyIncluded'),
            callSuper        : parseBoolParam(ann, 'callSuper'),
            includeFieldNames: parseBoolParam(ann, 'includeFieldNames', true)
        ]
    }

    // Returns null if @EqualsAndHashCode is absent; otherwise a map of params.
    Map parseEqualsHashCodeParams(List<String> lines) {
        def ann = findAnnotationLine(lines, 'EqualsAndHashCode')
        if (!ann) return null
        return [
            of          : parseStringListParam(ann, 'of'),
            exclude     : parseStringListParam(ann, 'exclude'),
            onlyExplicit: parseBoolParam(ann, 'onlyExplicitlyIncluded'),
            callSuper   : parseBoolParam(ann, 'callSuper')
        ]
    }

    // ─── Field extraction ─────────────────────────────────────────────────────

    List<String> extractAllFieldNames(List<String> lines) {
        def result = []
        for (def line : lines) {
            def m = line.trim() =~ /^private\s+(?!static\s)(?:final\s+)?[\w<>?,\[\]. ]+\s+(\w+)\s*[;=]/
            if (m) result << m[0][1]
        }
        return result
    }

    // annotationType: 'ToString' or 'EqualsAndHashCode'
    // Returns [excludes: [...], includes: [...]] from field-level annotations.
    Map extractFieldLevelAnnotations(List<String> lines, String annotationType) {
        def excludes = []; def includes = []
        for (int i = 0; i < lines.size(); i++) {
            def t = lines[i].trim()
            boolean isExclude = t =~ /^@(?:lombok\.)?${annotationType}\.Exclude\b/
            boolean isInclude = t =~ /^@(?:lombok\.)?${annotationType}\.Include\b/
            if (!isExclude && !isInclude) continue
            for (int j = i + 1; j < lines.size(); j++) {
                def m = lines[j].trim() =~ /^private\s+(?:final\s+)?[\w<>?,\[\]. ]+\s+(\w+)\s*[;=]/
                if (m) {
                    if (isExclude) excludes << m[0][1]
                    else           includes << m[0][1]
                    break
                }
                def fLine = lines[j].trim()
                if (fLine =~ /^\{/ || fLine.startsWith('public ') || fLine.startsWith('protected ')) break
            }
        }
        return [excludes: excludes, includes: includes]
    }

    List<String> computeEffectiveFields(Map params, Map fieldAnns, List<String> allFields) {
        if (!params.of.isEmpty())    return new ArrayList<>(params.of)
        if (params.onlyExplicit)     return new ArrayList<>(fieldAnns.includes)
        def result = new ArrayList<>(allFields)
        result.removeAll(params.exclude)
        result.removeAll(fieldAnns.excludes)
        return result
    }

    String extractClassName(List<String> lines) {
        for (def line : lines) {
            def m = line =~ /^\s*public\s+(?:(?:final|abstract)\s+)?class\s+(\w+)/
            if (m) return m[0][1]
        }
        return 'Unknown'
    }

    // ─── Common injection helper ──────────────────────────────────────────────

    List<String> collectPrecedingAnnotations(List<String> result) {
        def annotations = []
        while (result && result.last().trim().startsWith('@')) {
            annotations.add(0, result.removeLast())
        }
        return annotations
    }

    // Removes <p>...</p> blocks (possibly multi-line) where any line matches predicate.
    List<String> removeParagraphsMatching(List<String> javadocLines, Closure<Boolean> predicate) {
        def result = []
        int i = 0
        while (i < javadocLines.size()) {
            def line = javadocLines[i]
            def t    = line.trim()
            if (t =~ /^\*\s*<p>/) {
                def paraLines = [line]
                boolean matches = predicate.call(line)
                if (t =~ /<\/p>/) {
                    // single-line paragraph
                    if (!matches) result << line
                    i++; continue
                }
                // multi-line paragraph: collect until </p>
                i++
                while (i < javadocLines.size()) {
                    def nl = javadocLines[i]
                    paraLines << nl
                    if (predicate.call(nl)) matches = true
                    i++
                    if (nl.trim() =~ /<\/p>/) break
                }
                if (!matches) result.addAll(paraLines)
                continue
            }
            result << line
            i++
        }
        return result
    }

    // ─── @ToString / @EqualsAndHashCode method Javadoc injection ─────────────

    // Injects Javadoc on toString(), equals(), hashCode() at outer class level only.
    // Uses brace-depth tracking to skip methods inside nested classes (e.g. XxxBuilder).
    List<String> patchToStringEqualsHashCode(List<String> lines,
                                              List<String> tsFields, Map tsParams,
                                              List<String> eqFields, Map eqParams,
                                              String className) {
        def result    = []
        int braceDepth = 0

        for (int i = 0; i < lines.size(); i++) {
            def line    = lines[i]
            def trimmed = line.trim()
            def indent  = line.replaceFirst(/\S.*/, '')

            int lineStartDepth = braceDepth
            braceDepth += trimmed.count('{') - trimmed.count('}')

            if (lineStartDepth == 1) {

                // ── toString() ────────────────────────────────────────
                if (tsParams && tsFields &&
                    trimmed =~ /^public\s+java\.lang\.String\s+toString\s*\(\s*\)\s*\{/) {
                    if (!hasPrecedingJavadoc(result)) {
                        def annotations = collectPrecedingAnnotations(result)
                        def fieldRefs   = tsFields.collect { "{@code ${it}}" }.join(', ')
                        def format      = tsParams.includeFieldNames
                            ? "${className}(" + tsFields.collect { "${it}=…" }.join(', ') + ")"
                            : "${className}(" + tsFields.collect { '…' }.join(', ') + ")"
                        result << "${indent}/**"
                        result << "${indent} * Returns a string representation of this instance."
                        result << "${indent} * Includes: ${fieldRefs}."
                        if (!tsParams.includeFieldNames)
                            result << "${indent} * Values only, no field names."
                        result << "${indent} * Format: {@code ${format}}."
                        if (tsParams.callSuper)
                            result << "${indent} * Includes fields from superclass."
                        result << "${indent} *"
                        result << "${indent} * @return string representation; never {@code null}"
                        result << "${indent} */"
                        result.addAll(annotations)
                    }
                    result << line; continue
                }

                // ── equals() ─────────────────────────────────────────
                if (eqParams && eqFields &&
                    trimmed =~ /^public\s+boolean\s+equals\s*\(/) {
                    if (!hasPrecedingJavadoc(result)) {
                        def annotations = collectPrecedingAnnotations(result)
                        def fieldRefs   = eqFields.collect { "{@code ${it}}" }
                        result << "${indent}/**"
                        if (eqFields.size() == 1) {
                            result << "${indent} * Two instances are equal when ${fieldRefs[0]} is equal."
                        } else {
                            result << "${indent} * Two instances are equal when all these fields are equal:"
                            result << "${indent} * ${fieldRefs.join(', ')}."
                        }
                        if (eqParams.callSuper)
                            result << "${indent} * Includes fields from superclass."
                        result << "${indent} *"
                        result << "${indent} * @param o the object to compare with; may be {@code null}"
                        if (eqFields.size() == 1) {
                            result << "${indent} * @return {@code true} if ${fieldRefs[0]} is equal; {@code false} otherwise"
                        } else {
                            result << "${indent} * @return {@code true} when all fields match; {@code false} otherwise"
                        }
                        result << "${indent} */"
                        result.addAll(annotations)
                    }
                    result << line; continue
                }

                // ── canEqual() ───────────────────────────────────────
                if (eqParams &&
                    trimmed =~ /^protected\s+boolean\s+canEqual\s*\(/) {
                    if (!hasPrecedingJavadoc(result)) {
                        def annotations = collectPrecedingAnnotations(result)
                        result << "${indent}/**"
                        result << "${indent} * Returns whether another object can be considered equal to this instance."
                        result << "${indent} * Used internally by {@link #equals} to support correct behavior with subclasses."
                        result << "${indent} *"
                        result << "${indent} * @param other the object to test; may be {@code null}"
                        result << "${indent} * @return {@code true} if {@code other} is an instance of {@link ${className}}; {@code false} otherwise"
                        result << "${indent} */"
                        result.addAll(annotations)
                    }
                    result << line; continue
                }

                // ── hashCode() ────────────────────────────────────────
                if (eqParams && eqFields &&
                    trimmed =~ /^public\s+int\s+hashCode\s*\(\s*\)\s*\{/) {
                    if (!hasPrecedingJavadoc(result)) {
                        def annotations = collectPrecedingAnnotations(result)
                        def fieldRefs   = eqFields.collect { "{@code ${it}}" }.join(', ')
                        result << "${indent}/**"
                        result << "${indent} * Returns a hash code consistent with {@link #equals}."
                        result << "${indent} * Based on: ${fieldRefs}."
                        result << "${indent} *"
                        result << "${indent} * @return the computed hash code"
                        result << "${indent} */"
                        result.addAll(annotations)
                    }
                    result << line; continue
                }
            }

            result << line
        }
        return result
    }

    // ─── Class-level Javadoc update ───────────────────────────────────────────

    // Collapses runs of blank (* only) lines in a Javadoc block to at most one.
    List<String> collapseBlankJavadocLines(List<String> lines) {
        def result = []
        boolean lastWasBlank = false
        for (def line : lines) {
            boolean isBlank = line.trim() == '*'
            if (isBlank && lastWasBlank) continue
            result << line
            lastWasBlank = isBlank
        }
        return result
    }

    // Replaces existing toString/equals <p> paragraphs in the class Javadoc
    // and injects computed ones derived from annotation params.
    List<String> patchClassJavadoc(List<String> lines,
                                    List<String> tsFields, Map tsParams,
                                    List<String> eqFields, Map eqParams,
                                    String className) {
        // Find the public class declaration (first, not nested)
        int classIdx = -1
        int depth = 0
        for (int i = 0; i < lines.size(); i++) {
            depth += lines[i].trim().count('{') - lines[i].trim().count('}')
            if (depth == 1 && lines[i] =~ /^\s*public\s+(?:(?:final|abstract)\s+)?class\s+${className}\b/) {
                classIdx = i; break
            }
        }
        if (classIdx < 0) return lines

        // Find preceding */
        int javadocEnd = -1
        for (int i = classIdx - 1; i >= 0; i--) {
            def t = lines[i].trim()
            if (t.isEmpty() || t.startsWith('@')) continue
            if (t == '*/') { javadocEnd = i; break }
            break
        }
        if (javadocEnd < 0) return lines

        // Find /**
        int javadocStart = -1
        for (int i = javadocEnd - 1; i >= 0; i--) {
            if (lines[i].trim().startsWith('/**')) { javadocStart = i; break }
            if (!lines[i].trim().startsWith('*')) break
        }
        if (javadocStart < 0) return lines

        def indent       = lines[javadocStart].replaceFirst(/\S.*/, '')
        def javadocLines = new ArrayList<>(lines[javadocStart..javadocEnd])

        // Remove existing computed paragraphs about toString and equals/hashCode
        javadocLines = removeParagraphsMatching(javadocLines) { line ->
            def lower = line.toLowerCase()
            lower.contains('tostring') || lower.contains('#tostring')
        }
        javadocLines = removeParagraphsMatching(javadocLines) { line ->
            def lower = line.toLowerCase()
            lower.contains('equality') || lower.contains('hash-code') ||
            lower.contains('hashcode') ||
            (lower.contains('equal') && lower.contains('based'))
        }

        // Build replacement paragraphs
        def newParas = []
        if (eqParams != null && eqFields) {
            def refs = eqFields.collect { "{@code ${it}}" }
            def text = eqFields.size() == 1
                ? "Equality and hash code based solely on ${refs[0]}."
                : "Equality and hash code based on: ${refs.join(', ')}."
            if (eqParams.callSuper) text += " Includes superclass fields."
            newParas << "${indent} * <p>${text}</p>"
        }
        if (tsParams != null && tsFields) {
            def refs = tsFields.collect { "{@code ${it}}" }.join(', ')
            def text = tsParams.includeFieldNames
                ? "{@link #toString()} includes: ${refs}."
                : "{@link #toString()} includes values of: ${refs} (no field names)."
            if (tsParams.callSuper) text += " Includes fields from superclass."
            newParas << "${indent} * <p>${text}</p>"
        }

        // Collapse consecutive blank (* only) lines left by removed paragraphs
        javadocLines = collapseBlankJavadocLines(javadocLines)

        if (newParas) {
            // Insert before the first @-tag line (not inside a <pre> block)
            int insertIdx = javadocLines.size() - 1  // default: before */
            boolean inPre = false
            for (int i = 1; i < javadocLines.size() - 1; i++) {
                def t = javadocLines[i].trim()
                if (t.contains('<pre>'))  inPre = true
                if (t.contains('</pre>')) inPre = false
                if (!inPre && t =~ /^\*\s*@/) { insertIdx = i; break }
            }
            // Ensure blank line before new paragraphs
            def prev = javadocLines[insertIdx - 1].trim()
            if (prev != '*') javadocLines.add(insertIdx++, "${indent} *")
            newParas.eachWithIndex { para, idx -> javadocLines.add(insertIdx + idx, para) }
            insertIdx += newParas.size()
            // Ensure blank line after new paragraphs
            def next = javadocLines[insertIdx].trim()
            if (next != '*' && next != '*/') javadocLines.add(insertIdx, "${indent} *")
        }

        def out = []
        out.addAll(lines[0..<javadocStart])
        out.addAll(javadocLines)
        out.addAll(lines[(javadocEnd + 1)..<lines.size()])
        return out
    }

    // ─── Entry point ──────────────────────────────────────────────────────────

    void processFile(File delombokFile, File delombokBaseDir, File sourceBaseDir) {
        def relPath     = delombokBaseDir.toPath().relativize(delombokFile.toPath()).toString()
        def sourceFile  = new File(sourceBaseDir, relPath)
        def sourceLines = sourceFile.exists() ? sourceFile.readLines('UTF-8') : []

        def tsParams  = parseToStringParams(sourceLines)
        def eqParams  = parseEqualsHashCodeParams(sourceLines)
        boolean hasBuilder  = hasBuilderAnnotation(sourceLines)
        boolean hasToString = tsParams != null
        boolean hasEqHash   = eqParams != null

        if (!hasBuilder && !hasToString && !hasEqHash) return

        def patched = delombokFile.readLines('UTF-8')

        if (hasBuilder) {
            def getterReturns = extractGetterReturns(patched)
            def overrides     = extractBuilderParamOverrides(sourceLines)
            patched = patchBuilderSetters(patched, getterReturns, overrides)
        }

        if (hasToString || hasEqHash) {
            def allFields   = extractAllFieldNames(sourceLines)
            def tsFieldAnns = extractFieldLevelAnnotations(sourceLines, 'ToString')
            def eqFieldAnns = extractFieldLevelAnnotations(sourceLines, 'EqualsAndHashCode')
            def tsFields    = tsParams ? computeEffectiveFields(tsParams, tsFieldAnns, allFields) : []
            def eqFields    = eqParams ? computeEffectiveFields(eqParams, eqFieldAnns, allFields) : []
            def className   = extractClassName(patched)

            patched = patchToStringEqualsHashCode(patched, tsFields, tsParams, eqFields, eqParams, className)
            patched = patchClassJavadoc(patched, tsFields, tsParams, eqFields, eqParams, className)
        }

        delombokFile.write(patched.join('\n') + '\n', 'UTF-8')
        def tags = [hasBuilder ? "@Builder" : null,
                    hasToString ? "@ToString" : null,
                    hasEqHash ? "@EqualsAndHashCode" : null].findAll { it }.join(', ')
        println "  Patched: ${delombokFile.name} [${tags}]"
    }

    // ─── Self-tests ───────────────────────────────────────────────────────────

    void runSelfTests() {
        // ── @Builder tests (existing) ────────────────────────────────────────
        def input = [
            '@Builder',
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

        assert hasBuilderAnnotation(input), "Should detect @Builder"
        assert !hasBuilderAnnotation(['public class Bar {}']), "Should NOT detect without @Builder"

        def gr = extractGetterReturns(input)
        assert gr['name'] == 'the name; never null', "Wrong getter return: '${gr['name']}'"

        def ov = extractBuilderParamOverrides(input)
        assert ov.isEmpty(), "Should have no overrides"

        def out = patchBuilderSetters(input, gr, ov)
        def txt = out.join('\n')
        assert txt.contains('@param name the name; never null'), "Missing @param:\n${txt}"
        assert txt.contains('@return this builder'), "Missing @return this builder"
        assert !txt.contains('@return {@code this}'), "Old @return not replaced"

        // ── BUILDER-PARAM override ───────────────────────────────────────────
        def ovInput = [
            '@Builder',
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
        def ov2   = extractBuilderParamOverrides(ovInput)
        def out2  = patchBuilderSetters(ovInput, [:], ov2)
        def txt2  = out2.join('\n')
        assert txt2.contains('Sets the count (must be positive)'), "Override not applied:\n${txt2}"
        assert txt2.contains('@param count positive integer'), "Override @param missing"
        assert !txt2.contains('@return {@code this}'), "Old @return not replaced in override"

        // ── Annotation parsing ───────────────────────────────────────────────
        def tsDefault = parseToStringParams(['@ToString', 'public class A {}'])
        assert tsDefault != null, "@ToString not detected"
        assert tsDefault.of.isEmpty(), "Default of should be empty"
        assert tsDefault.includeFieldNames == true, "Default includeFieldNames should be true"

        def tsOf = parseToStringParams(['@ToString(of = "name")', 'public class A {}'])
        assert tsOf.of == ['name'], "of= single not parsed: ${tsOf.of}"

        def tsOfMulti = parseToStringParams(['@ToString(of = {"a", "b"})', 'public class A {}'])
        assert tsOfMulti.of == ['a', 'b'], "of= multi not parsed: ${tsOfMulti.of}"

        def tsExclude = parseToStringParams(['@ToString(exclude = {"secret"})', 'public class A {}'])
        assert tsExclude.exclude == ['secret'], "exclude not parsed: ${tsExclude.exclude}"

        def tsNoNames = parseToStringParams(['@ToString(includeFieldNames = false)', 'public class A {}'])
        assert tsNoNames.includeFieldNames == false, "includeFieldNames=false not parsed"

        def eqOf = parseEqualsHashCodeParams(['@EqualsAndHashCode(of = "id")', 'public class A {}'])
        assert eqOf != null, "@EqualsAndHashCode not detected"
        assert eqOf.of == ['id'], "EqualsAndHashCode of= not parsed: ${eqOf.of}"

        def eqDefault = parseEqualsHashCodeParams(['public class A {}'])
        assert eqDefault == null, "Should be null without annotation"

        // ── Field extraction ─────────────────────────────────────────────────
        def fieldSrc = [
            'public class X {',
            '    private final String name;',
            '    private int age;',
            '    private static final int CONST = 0;',  // static: not matched
            '}',
        ]
        def fields = extractAllFieldNames(fieldSrc)
        assert fields == ['name', 'age'], "Fields: ${fields}"

        def fieldLevelSrc = [
            '    @ToString.Exclude',
            '    private final String secret;',
            '    @EqualsAndHashCode.Include',
            '    private final String id;',
            '    private final String name;',
        ]
        def tsAnns = extractFieldLevelAnnotations(fieldLevelSrc, 'ToString')
        assert tsAnns.excludes == ['secret'], "TS excludes: ${tsAnns.excludes}"
        def eqAnns = extractFieldLevelAnnotations(fieldLevelSrc, 'EqualsAndHashCode')
        assert eqAnns.includes == ['id'], "EQ includes: ${eqAnns.includes}"

        // ── computeEffectiveFields ────────────────────────────────────────────
        def allF = ['a', 'b', 'c', 'd']
        def eff1 = computeEffectiveFields([of: ['b'], exclude: [], onlyExplicit: false], [excludes: [], includes: []], allF)
        assert eff1 == ['b'], "of= should win: ${eff1}"

        def eff2 = computeEffectiveFields([of: [], exclude: ['c'], onlyExplicit: false], [excludes: [], includes: []], allF)
        assert eff2 == ['a', 'b', 'd'], "exclude= should drop c: ${eff2}"

        def eff3 = computeEffectiveFields([of: [], exclude: [], onlyExplicit: true], [excludes: [], includes: ['a', 'd']], allF)
        assert eff3 == ['a', 'd'], "onlyExplicit should use includes: ${eff3}"

        def eff4 = computeEffectiveFields([of: [], exclude: [], onlyExplicit: false], [excludes: ['b'], includes: []], allF)
        assert eff4 == ['a', 'c', 'd'], "@Exclude on field: ${eff4}"

        // ── patchToStringEqualsHashCode ───────────────────────────────────────
        def delombokInput = [
            'public class Baz {',
            '    private final String id;',
            '',
            '    @java.lang.Override',
            '    @java.lang.SuppressWarnings("all")',
            '    @lombok.Generated',
            '    public boolean equals(final java.lang.Object o) { return false; }',
            '',
            '    @java.lang.SuppressWarnings("all")',
            '    @lombok.Generated',
            '    protected boolean canEqual(final java.lang.Object other) { return false; }',
            '',
            '    @java.lang.Override',
            '    @java.lang.SuppressWarnings("all")',
            '    @lombok.Generated',
            '    public int hashCode() { return 0; }',
            '',
            '    @java.lang.Override',
            '    @java.lang.SuppressWarnings("all")',
            '    @lombok.Generated',
            '    public java.lang.String toString() { return ""; }',
            '}',
        ]
        def eqP  = [of: ['id'], exclude: [], onlyExplicit: false, callSuper: false]
        def tsP  = [of: [], exclude: [], onlyExplicit: false, callSuper: false, includeFieldNames: true]
        def patched = patchToStringEqualsHashCode(delombokInput, ['id'], tsP, ['id'], eqP, 'Baz')
        def patchedTxt = patched.join('\n')
        assert patchedTxt.contains('Two instances are equal when {@code id} is equal.'),
            "equals Javadoc missing:\n${patchedTxt}"
        assert patchedTxt.contains('Returns a hash code consistent with {@link #equals}.'),
            "hashCode Javadoc missing:\n${patchedTxt}"
        assert patchedTxt.contains('Returns a string representation of this instance.'),
            "toString Javadoc missing:\n${patchedTxt}"
        assert patchedTxt.contains('Includes: {@code id}.'), "toString field list missing"
        assert patchedTxt.contains('Returns whether another object can be considered equal'),
            "canEqual Javadoc missing:\n${patchedTxt}"
        assert patchedTxt.contains('@param other the object to test'), "canEqual @param missing"

        // ── removeParagraphsMatching ──────────────────────────────────────────
        def javadoc = [
            ' * /**',
            ' * <p>Equality based on id.</p>',
            ' * <p>Other paragraph.</p>',
            ' */',
        ]
        def cleaned = removeParagraphsMatching(javadoc) { it.toLowerCase().contains('equality') }
        assert !cleaned.any { it.contains('Equality') }, "Should remove equality para"
        assert cleaned.any { it.contains('Other paragraph') }, "Should keep other para"

        println "Self-tests: PASSED"
    }
}

// When invoked via Maven (project binding is set), process delombok output.
// When invoked standalone (groovy scripts/...), run self-tests.
if (binding.hasVariable('project')) {
    def delombokDir = new File(project.build.directory, 'generated-sources/delombok')
    def sourceDir   = new File(project.build.sourceDirectory)
    println "LombokJavadocPropagator: scanning ${delombokDir}"
    def propagator = new LombokJavadocPropagator()
    delombokDir.eachFileRecurse { file ->
        if (file.name.endsWith('.java')) propagator.processFile(file, delombokDir, sourceDir)
    }
    println "LombokJavadocPropagator: done."
} else {
    new LombokJavadocPropagator().runSelfTests()
}
