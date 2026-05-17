class JavadocUtils extends SourceAnalyzer {

    // Returns true if result buffer ends with */ (skipping blank lines and annotations).
    boolean hasPrecedingJavadoc(List<String> result) {
        for (int i = result.size() - 1; i >= 0; i--) {
            def t = result[i].trim()
            if (t.isEmpty() || t.startsWith('@')) continue
            return t == '*/'
        }
        return false
    }

    List<String> collectPrecedingAnnotations(List<String> result) {
        def annotations = []
        while (result && result.last().trim().startsWith('@')) {
            annotations.add(0, result.removeLast())
        }
        return annotations
    }

    // Injects /** ... */ unless one already precedes; always re-parks trailing annotations.
    void injectJavadocIfMissing(List<String> result, String indent, List<String> bodyLines) {
        def annotations = collectPrecedingAnnotations(result)
        if (!hasPrecedingJavadoc(result)) {
            result << "${indent}/**"
            bodyLines.each { bodyLine -> result << (bodyLine.isEmpty() ? "${indent} *" : "${indent} * ${bodyLine}") }
            result << "${indent} */"
        }
        result.addAll(annotations)
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
                    if (!matches) result << line
                    i++
                    continue
                }
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
}

null
