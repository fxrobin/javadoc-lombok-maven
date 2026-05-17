class JavadocUtils extends SourceAnalyzer {

    private static final PARA_OPEN  = /^\*\s*<p>/
    private static final PARA_CLOSE = /<\/p>/

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
            if (!(line.trim() =~ PARA_OPEN)) { result << line; i++; continue }
            def para = collectParagraph(javadocLines, i, predicate)
            if (!para.matches) result.addAll(para.lines)
            i = para.nextIdx
        }
        return result
    }

    private Map<String, Object> collectParagraph(List<String> javadocLines, int startIdx, Closure<Boolean> predicate) {
        def line = javadocLines[startIdx]
        def t    = line.trim()
        if (t =~ PARA_CLOSE)
            return [lines: [line], matches: predicate.call(line), nextIdx: startIdx + 1]
        def paraLines = [line]
        boolean matches = predicate.call(line)
        int i = startIdx + 1
        while (i < javadocLines.size()) {
            def nl = javadocLines[i]
            paraLines << nl
            if (predicate.call(nl)) matches = true
            i++
            if (nl.trim() =~ PARA_CLOSE) break
        }
        return [lines: paraLines, matches: matches, nextIdx: i]
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
