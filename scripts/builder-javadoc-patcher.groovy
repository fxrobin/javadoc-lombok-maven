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

    private void replaceWithOverrideJavadoc(List<String> result, String indent, String override) {
        if (result && result.last().trim() == '*/') {
            while (result && !result.last().trim().startsWith('/**')) result.removeLast()
            if (result) result.removeLast()
        }
        result << "${indent}/**"
        override.split('\n').each { overrideLine -> result << "${indent} * ${overrideLine}" }
        result << "${indent} */"
    }

    private void patchReturnTagInLastWindow(List<String> result, String fieldName, String returnText) {
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

    private void injectBuilderSetterJavadoc(List<String> result, String indent,
                                             String fieldName, Map<String, String> getterReturns,
                                             Map<String, String> overrides) {
        def override    = overrides[fieldName]
        def annotations = collectPrecedingAnnotations(result)
        if (override != null)
            replaceWithOverrideJavadoc(result, indent, override)
        else
            patchReturnTagInLastWindow(result, fieldName, getterReturns[fieldName] ?: '')
        result.addAll(annotations)
    }

    private boolean dispatchBuilderMemberJavadoc(List<String> result, String line, String trimmed,
                                                  String indent, Map<String, Object> state,
                                                  Map<String, String> getterReturns,
                                                  Map<String, String> overrides) {
        if (trimmed =~ BUILD_METHOD) {
            injectBuildMethodJavadoc(result, indent, state.outerClassName)
            result << line; return true
        }
        if (state.builderDepth > 0 && (trimmed =~ BUILDER_TOSTRING)) {
            injectBuilderToStringJavadoc(result, indent, state.outerClassName)
            result << line; return true
        }
        if (trimmed =~ BUILDER_FACTORY) {
            injectBuilderFactoryJavadoc(result, indent, state.outerClassName)
            result << line; return true
        }
        def m = (trimmed =~ BUILDER_SETTER)
        if (m && m[0][1] == m[0][2])
            injectBuilderSetterJavadoc(result, indent, m[0][1], getterReturns, overrides)
        return false
    }

    private boolean dispatchBuilderLineJavadoc(List<String> result, String line, String trimmed,
                                                String indent, Map<String, Object> state,
                                                Map<String, String> getterReturns,
                                                Map<String, String> overrides) {
        def classM = (trimmed =~ BUILDER_CLASS_DECL)
        if (classM) {
            state.outerClassName = classM[0][1]
            state.builderDepth   = 1
            injectBuilderClassJavadoc(result, indent, state.outerClassName)
            result << line
            return true
        }
        if (!state.outerClassName) return false
        return dispatchBuilderMemberJavadoc(result, line, trimmed, indent, state, getterReturns, overrides)
    }

    // Patches delombok builder output: injects Javadoc on class, build(), toString(), builder(), setters.
    List<String> patchBuilderSetters(List<String> lines,
                                      Map<String, String> getterReturns,
                                      Map<String, String> overrides) {
        def result = []
        def state  = [outerClassName: null, builderDepth: 0]
        for (int i = 0; i < lines.size(); i++) {
            def line    = lines[i]
            def trimmed = line.trim()
            def indent  = line.replaceFirst(/\S.*/, '')
            if (state.builderDepth > 0)
                state.builderDepth += trimmed.count('{') - trimmed.count('}')
            if (!dispatchBuilderLineJavadoc(result, line, trimmed, indent, state, getterReturns, overrides))
                result << line
        }
        return result
    }
}

null
