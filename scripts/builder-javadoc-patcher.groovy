class BuilderJavadocPatcher extends JavadocUtils {

    private static final BUILDER_CLASS_DECL = /^public static class (\w+)Builder\s*\{/
    private static final BUILD_METHOD       = /^public\s+\S+\s+build\s*\(\s*\)\s*\{/
    private static final BUILDER_TOSTRING   = /^public\s+java\.lang\.String\s+toString\s*\(\s*\)\s*\{/
    private static final BUILDER_FACTORY    = /^public\s+static\s+\S+\s+builder\s*\(\s*\)\s*\{/
    private static final BUILDER_SETTER     = /^public\s+\S+Builder\s+(\w+)\s*\(final\s+.+\s+(\w+)\s*\)/

    private void injectBuilderClassJavadoc(List<String> result, String indent, String outerClassName) {
        if (!hasPrecedingJavadoc(result)) {
            def annotations = collectPrecedingAnnotations(result)
            result << "${indent}/**"
            result << "${indent} * Builder class for {@link ${outerClassName}}."
            result << "${indent} */"
            result.addAll(annotations)
        }
    }

    private void injectBuildMethodJavadoc(List<String> result, String indent, String outerClassName) {
        injectJavadocIfMissing(result, indent, [
            "Builds and returns a new {@link ${outerClassName}} instance.",
            "@return new {@link ${outerClassName}} instance; never {@code null}"
        ])
    }

    private void injectBuilderToStringJavadoc(List<String> result, String indent, String outerClassName) {
        injectJavadocIfMissing(result, indent, [
            "Returns a string representation of the current ${outerClassName}Builder state.",
            "Shows all field values set so far; useful for debugging.",
            "",
            "@return string representation of this builder; never {@code null}"
        ])
    }

    private void injectBuilderFactoryJavadoc(List<String> result, String indent, String outerClassName) {
        injectJavadocIfMissing(result, indent, [
            "Creates a new {@link ${outerClassName}Builder} to build a {@link ${outerClassName}} instance.",
            "",
            "@return a new {@link ${outerClassName}Builder}; never {@code null}"
        ])
    }

    private void injectBuilderSetterJavadoc(List<String> result, String indent,
                                             String fieldName, Map getterReturns, Map overrides) {
        def override    = overrides[fieldName]
        def annotations = collectPrecedingAnnotations(result)
        if (override != null) {
            if (result && result.last().trim() == '*/') {
                while (result && !result.last().trim().startsWith('/**')) result.removeLast()
                if (result) result.removeLast()
            }
            result << "${indent}/**"
            override.split('\n').each { overrideLine -> result << "${indent} * ${overrideLine}" }
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

    // Patches delombok builder output: injects Javadoc on class, build(), toString(), builder(), setters.
    List<String> patchBuilderSetters(List<String> lines,
                                      Map<String, String> getterReturns,
                                      Map<String, String> overrides) {
        def result = []
        def outerClassName = null
        int builderDepth = 0

        for (int i = 0; i < lines.size(); i++) {
            def line    = lines[i]
            def trimmed = line.trim()
            def indent  = line.replaceFirst(/\S.*/, '')

            if (builderDepth > 0)
                builderDepth += trimmed.count('{') - trimmed.count('}')

            def classM = (trimmed =~ BUILDER_CLASS_DECL)
            if (classM) {
                outerClassName = classM[0][1]
                builderDepth = 1
                injectBuilderClassJavadoc(result, indent, outerClassName)
                result << line
                continue
            }

            if (outerClassName && (trimmed =~ BUILD_METHOD)) {
                injectBuildMethodJavadoc(result, indent, outerClassName)
                result << line
                continue
            }

            if (builderDepth > 0 && outerClassName && (trimmed =~ BUILDER_TOSTRING)) {
                injectBuilderToStringJavadoc(result, indent, outerClassName)
                result << line
                continue
            }

            if (outerClassName && (trimmed =~ BUILDER_FACTORY)) {
                injectBuilderFactoryJavadoc(result, indent, outerClassName)
                result << line
                continue
            }

            def m = (trimmed =~ BUILDER_SETTER)
            if (m && m[0][1] == m[0][2])
                injectBuilderSetterJavadoc(result, indent, m[0][1], getterReturns, overrides)

            result << line
        }
        return result
    }
}

null
