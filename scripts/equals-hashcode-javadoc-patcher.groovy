class EqualsHashCodeJavadocPatcher extends JavadocUtils {

    private static final TOSTRING_METHOD  = /^public\s+java\.lang\.String\s+toString\s*\(\s*\)\s*\{/
    private static final EQUALS_METHOD    = /^public\s+boolean\s+equals\s*\(/
    private static final CAN_EQUAL_METHOD = /^protected\s+boolean\s+canEqual\s*\(/
    private static final HASHCODE_METHOD  = /^public\s+int\s+hashCode\s*\(\s*\)\s*\{/
    private static final JAVADOC_TAG_LINE = /^\*\s*@/

    // ─── @ToString / @EqualsAndHashCode method Javadoc injection ─────────────

    private void injectToStringJavadoc(List<String> result, String indent,
                                        String className, List<String> fields, Map params) {
        if (hasPrecedingJavadoc(result)) return
        def annotations = collectPrecedingAnnotations(result)
        def fieldRefs   = fields.collect { field -> "{@code ${field}}" }.join(', ')
        def format      = params.includeFieldNames
            ? "${className}(" + fields.collect { field -> "${field}=…" }.join(', ') + ")"
            : "${className}(" + fields.collect { _ -> '…' }.join(', ') + ")"
        result << "${indent}/**"
        result << "${indent} * Returns a string representation of this instance."
        result << "${indent} * Includes: ${fieldRefs}."
        if (!params.includeFieldNames)
            result << "${indent} * Values only, no field names."
        result << "${indent} * Format: {@code ${format}}."
        if (params.callSuper)
            result << "${indent} * Includes fields from superclass."
        result << "${indent} *"
        result << "${indent} * @return string representation; never {@code null}"
        result << "${indent} */"
        result.addAll(annotations)
    }

    private void injectEqualsJavadoc(List<String> result, String indent,
                                      List<String> fields, Map params) {
        if (hasPrecedingJavadoc(result)) return
        def annotations = collectPrecedingAnnotations(result)
        def fieldRefs   = fields.collect { field -> "{@code ${field}}" }
        result << "${indent}/**"
        if (fields.size() == 1) {
            result << "${indent} * Two instances are equal when ${fieldRefs[0]} is equal."
        } else {
            result << "${indent} * Two instances are equal when all these fields are equal:"
            result << "${indent} * ${fieldRefs.join(', ')}."
        }
        if (params.callSuper)
            result << "${indent} * Includes fields from superclass."
        result << "${indent} *"
        result << "${indent} * @param o the object to compare with; may be {@code null}"
        if (fields.size() == 1) {
            result << "${indent} * @return {@code true} if ${fieldRefs[0]} is equal; {@code false} otherwise"
        } else {
            result << "${indent} * @return {@code true} when all fields match; {@code false} otherwise"
        }
        result << "${indent} */"
        result.addAll(annotations)
    }

    private void injectCanEqualJavadoc(List<String> result, String indent, String className) {
        if (hasPrecedingJavadoc(result)) return
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

    private void injectHashCodeJavadoc(List<String> result, String indent, List<String> fields) {
        if (hasPrecedingJavadoc(result)) return
        def annotations = collectPrecedingAnnotations(result)
        def fieldRefs   = fields.collect { field -> "{@code ${field}}" }.join(', ')
        result << "${indent}/**"
        result << "${indent} * Returns a hash code consistent with {@link #equals}."
        result << "${indent} * Based on: ${fieldRefs}."
        result << "${indent} *"
        result << "${indent} * @return the computed hash code"
        result << "${indent} */"
        result.addAll(annotations)
    }

    // Injects Javadoc on toString(), equals(), canEqual(), hashCode() at outer class level only.
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
                if (tsParams && tsFields && (trimmed =~ TOSTRING_METHOD)) {
                    injectToStringJavadoc(result, indent, className, tsFields, tsParams)
                    result << line
                    continue
                }

                if (eqParams && eqFields && (trimmed =~ EQUALS_METHOD)) {
                    injectEqualsJavadoc(result, indent, eqFields, eqParams)
                    result << line
                    continue
                }

                if (eqParams && (trimmed =~ CAN_EQUAL_METHOD)) {
                    injectCanEqualJavadoc(result, indent, className)
                    result << line
                    continue
                }

                if (eqParams && eqFields && (trimmed =~ HASHCODE_METHOD)) {
                    injectHashCodeJavadoc(result, indent, eqFields)
                    result << line
                    continue
                }
            }

            result << line
        }
        return result
    }

    // ─── Class-level Javadoc update ───────────────────────────────────────────

    private int findClassDeclarationIndex(List<String> lines, String className) {
        int depth = 0
        for (int i = 0; i < lines.size(); i++) {
            depth += lines[i].trim().count('{') - lines[i].trim().count('}')
            if (depth == 1 && lines[i] =~ /^\s*public\s+(?:(?:final|abstract)\s+)?class\s+${className}\b/)
                return i
        }
        return -1
    }

    // Returns [start, end] indices of the /** ... */ block preceding classIdx, or null if absent.
    private List<Integer> findClassJavadocBounds(List<String> lines, int classIdx) {
        int end = -1
        for (int i = classIdx - 1; i >= 0; i--) {
            def t = lines[i].trim()
            if (t.isEmpty() || t.startsWith('@')) continue
            if (t == '*/') { end = i; break }
            break
        }
        if (end < 0) return null
        int start = -1
        for (int i = end - 1; i >= 0; i--) {
            if (lines[i].trim().startsWith('/**')) { start = i; break }
            if (!lines[i].trim().startsWith('*')) break
        }
        return start < 0 ? null : [start, end]
    }

    private List<String> removeStaleClassJavadocParagraphs(List<String> javadocLines) {
        javadocLines = removeParagraphsMatching(javadocLines) { line ->
            def lower = line.toLowerCase()
            lower.contains('tostring') || lower.contains('#tostring')
        }
        return removeParagraphsMatching(javadocLines) { line ->
            def lower = line.toLowerCase()
            lower.contains('equality') || lower.contains('hash-code') ||
            lower.contains('hashcode') || (lower.contains('equal') && lower.contains('based'))
        }
    }

    private List<String> buildClassLevelParagraphs(String indent,
                                                    List<String> eqFields, Map eqParams,
                                                    List<String> tsFields, Map tsParams) {
        def paras = []
        if (eqParams != null && eqFields) {
            def refs = eqFields.collect { field -> "{@code ${field}}" }
            def text = eqFields.size() == 1
                ? "Equality and hash code based solely on ${refs[0]}."
                : "Equality and hash code based on: ${refs.join(', ')}."
            if (eqParams.callSuper) text += " Includes superclass fields."
            paras << "${indent} * <p>${text}</p>"
        }
        if (tsParams != null && tsFields) {
            def refs = tsFields.collect { field -> "{@code ${field}}" }.join(', ')
            def text = tsParams.includeFieldNames
                ? "{@link #toString()} includes: ${refs}."
                : "{@link #toString()} includes values of: ${refs} (no field names)."
            if (tsParams.callSuper) text += " Includes fields from superclass."
            paras << "${indent} * <p>${text}</p>"
        }
        return paras
    }

    private List<String> insertParagraphsIntoJavadoc(List<String> javadocLines,
                                                      List<String> newParas, String indent) {
        int insertIdx = javadocLines.size() - 1
        boolean inPre = false
        for (int i = 1; i < javadocLines.size() - 1; i++) {
            def t = javadocLines[i].trim()
            if (t.contains('<pre>'))  inPre = true
            if (t.contains('</pre>')) inPre = false
            if (!inPre && t =~ JAVADOC_TAG_LINE) { insertIdx = i; break }
        }
        if (javadocLines[insertIdx - 1].trim() != '*') javadocLines.add(insertIdx++, "${indent} *")
        newParas.eachWithIndex { para, idx -> javadocLines.add(insertIdx + idx, para) }
        insertIdx += newParas.size()
        if (javadocLines[insertIdx].trim() != '*' && javadocLines[insertIdx].trim() != '*/')
            javadocLines.add(insertIdx, "${indent} *")
        return javadocLines
    }

    // Replaces stale toString/equals <p> paragraphs in the class Javadoc and injects fresh ones.
    List<String> patchClassJavadoc(List<String> lines,
                                    List<String> tsFields, Map tsParams,
                                    List<String> eqFields, Map eqParams,
                                    String className) {
        int classIdx = findClassDeclarationIndex(lines, className)
        if (classIdx < 0) return lines

        def bounds = findClassJavadocBounds(lines, classIdx)
        if (bounds == null) return lines

        def (javadocStart, javadocEnd) = bounds
        def indent       = lines[javadocStart].replaceFirst(/\S.*/, '')
        def javadocLines = new ArrayList<>(lines[javadocStart..javadocEnd])

        javadocLines = removeStaleClassJavadocParagraphs(javadocLines)
        javadocLines = collapseBlankJavadocLines(javadocLines)

        def newParas = buildClassLevelParagraphs(indent, eqFields, eqParams, tsFields, tsParams)
        if (newParas)
            javadocLines = insertParagraphsIntoJavadoc(javadocLines, newParas, indent)

        def out = []
        out.addAll(lines[0..<javadocStart])
        out.addAll(javadocLines)
        out.addAll(lines[(javadocEnd + 1)..<lines.size()])
        return out
    }
}

null
