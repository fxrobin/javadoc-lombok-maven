@groovy.transform.Canonical
class AnnotationContext {
    List<String>        fields = []
    Map<String, Object> params = null   // null means annotation absent

    static AnnotationContext of(List<String> fields, Map<String, Object> params) {
        new AnnotationContext(fields, params)
    }

    static AnnotationContext absent() { new AnnotationContext() }
    boolean isPresent()               { params != null }
    boolean hasFields()               { !fields.isEmpty() }
}

trait SourceAnalyzer {

    // ── Regex Constants ───────────────────────────────────────────────────────

    private static final BUILDER_ANNOTATION  = /^@(lombok\.)?Builder(\(.*\))?$/
    private static final GETTER_METHOD       = /^public\s+\S+\s+(?:get|is)([A-Z]\w*)\s*\(\)/ 
    private static final FIELD_DECLARATION    = /^private\s+(?:final\s+)?[\w<>?,\[\]. ]+\s+(\w+)\s*[;=]/
    private static final NON_STATIC_FIELD_DECL = /^private\s+(?!static\s)(?:final\s+)?[\w<>?,\[\]. ]+\s+(\w+)\s*[;=]/
    private static final CLASS_DECLARATION     = /^\s*public\s+(?:(?:final|abstract)\s+)?class\s+(\w+)/
    private static final QUOTED_WORD          = /"(\w+)"/
    private static final JAVADOC_STAR_PREFIX   = /^\*\s?/

    // ─── @Builder helpers ─────────────────────────────────────────────────────

    boolean hasBuilderAnnotation(List<String> lines) {
        lines.any { line -> line.trim() =~ BUILDER_ANNOTATION }
    }

    // Extracts @return text from getter methods in delombok output.
    // Returns: {fieldName -> returnText}
    Map<String, String> extractGetterReturns(List<String> lines) {
        def result = [:]
        for (int i = 0; i < lines.size(); i++) {
            def m = (lines[i].trim() =~ GETTER_METHOD)
            if (!m) continue
            def raw = m[0][1]
            def fieldName = raw[0].toLowerCase() + (raw.size() > 1 ? raw[1..-1] : '')
            for (int j = i - 1; j >= 0; j--) {
                def content = lines[j].trim().replaceFirst(JAVADOC_STAR_PREFIX, '')
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
                overrideLines << lines[j].trim().replaceFirst(JAVADOC_STAR_PREFIX, '')
                j++
            }
            int k = j + 1
            while (k < lines.size() && lines[k].trim().startsWith('@')) k++
            if (k < lines.size()) {
                def fm = (lines[k].trim() =~ FIELD_DECLARATION)
                if (fm) result[fm[0][1]] = overrideLines.findAll { line -> !line.isEmpty() }.join('\n')
            }
        }
        return result
    }

    // ─── Annotation parsing ───────────────────────────────────────────────────

    // Collects the full annotation text (handles multi-line annotations).
    String findAnnotationLine(List<String> lines, String name) {
        for (int i = 0; i < lines.size(); i++) {
            def t = lines[i].trim()
            if (!(t =~ /^@(lombok\.)?${name}\b/)) continue
            if (t.count('(') == t.count(')')) return t
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
        return (m[0][1] =~ QUOTED_WORD).collect { match -> match[1] }
    }

    boolean parseBoolParam(String ann, String param, boolean defaultVal = false) {
        if (!ann) return defaultVal
        def m = ann =~ /\b${param}\s*=\s*(true|false)/
        return m ? m[0][1] == 'true' : defaultVal
    }

    // Returns null if @ToString is absent; otherwise a map of params.
    Map<String, Object> parseToStringParams(List<String> lines) {
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
    Map<String, Object> parseEqualsHashCodeParams(List<String> lines) {
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
            def m = line.trim() =~ NON_STATIC_FIELD_DECL
            if (m) result << m[0][1]
        }
        return result
    }

    // annotationType: 'ToString' or 'EqualsAndHashCode'
    private boolean isFieldLevelAnnotation(String line, String annotationType) {
        def t = line.trim()
        return t =~ /^@(?:lombok\.)?${annotationType}\.Exclude\b/ ||
               t =~ /^@(?:lombok\.)?${annotationType}\.Include\b/
    }

    private boolean isExcludeAnnotation(String line, String annotationType) {
        def t = line.trim()
        return t =~ /^@(?:lombok\.)?${annotationType}\.Exclude\b/
    }

    private String extractFieldNameFromDeclaration(String line) {
        def m = line.trim() =~ FIELD_DECLARATION
        return m ? m[0][1] : null
    }

    private static final OPEN_BRACE = /^\{/

    private boolean isClassOrMethodBoundary(String line) {
        def t = line.trim()
        return t =~ OPEN_BRACE || t.startsWith('public ') || t.startsWith('protected ')
    }

    private String findFieldAfterAnnotation(List<String> lines, int startIdx, String annotationType) {
        for (int j = startIdx; j < lines.size(); j++) {
            def fieldName = extractFieldNameFromDeclaration(lines[j])
            if (fieldName) return fieldName
            if (isClassOrMethodBoundary(lines[j])) break
        }
        return null
    }

    // annotationType: 'ToString' or 'EqualsAndHashCode'
    // Returns [excludes: [...], includes: [...]] from field-level annotations.
    Map<String, List<String>> extractFieldLevelAnnotations(List<String> lines, String annotationType) {
        def excludes = []
        def includes = []
        for (int i = 0; i < lines.size(); i++) {
            if (!isFieldLevelAnnotation(lines[i], annotationType)) continue
            boolean isExclude = isExcludeAnnotation(lines[i], annotationType)
            def fieldName = findFieldAfterAnnotation(lines, i + 1, annotationType)
            if (fieldName) {
                if (isExclude) excludes << fieldName
                else includes << fieldName
            }
        }
        return [excludes: excludes, includes: includes]
    }

    List<String> computeEffectiveFields(Map<String, Object> params, Map<String, List<String>> fieldAnns, List<String> allFields) {
        if (!params.of.isEmpty())    return new ArrayList<>(params.of)
        if (params.onlyExplicit)     return new ArrayList<>(fieldAnns.includes)
        def result = new ArrayList<>(allFields)
        result.removeAll(params.exclude)
        result.removeAll(fieldAnns.excludes)
        return result
    }

    String extractClassName(List<String> lines) {
        for (def line : lines) {
            def m = line =~ CLASS_DECLARATION
            if (m) return m[0][1]
        }
        return 'Unknown'
    }
}

null
