class LombokJavadocPropagator implements SourceAnalyzer, JavadocUtils {

    private final BuilderJavadocPatcher        builderPatcher = new BuilderJavadocPatcher()
    private final EqualsHashCodeJavadocPatcher eqPatcher      = new EqualsHashCodeJavadocPatcher()

    // Delegations exposed for test compatibility.
    List<String> patchBuilderSetters(List<String> lines,
                                      Map<String, String> getterReturns,
                                      Map<String, String> overrides) {
        builderPatcher.patchBuilderSetters(lines, getterReturns, overrides)
    }

    List<String> patchToStringEqualsHashCode(List<String> lines,
                                              AnnotationContext tsCtx,
                                              AnnotationContext eqCtx,
                                              String className) {
        eqPatcher.patchToStringEqualsHashCode(lines, tsCtx, eqCtx, className)
    }

    // ─── Entry point ──────────────────────────────────────────────────────────

    private List<String> applyBuilderPatching(List<String> lines, List<String> sourceLines) {
        def getterReturns = extractGetterReturns(lines)
        def overrides     = extractBuilderParamOverrides(sourceLines)
        return builderPatcher.patchBuilderSetters(lines, getterReturns, overrides)
    }

    private List<String> applyEqualsHashCodePatching(List<String> lines, List<String> sourceLines,
                                                      Map<String, Object> tsParams,
                                                      Map<String, Object> eqParams) {
        def allFields   = extractAllFieldNames(sourceLines)
        def tsFieldAnns = extractFieldLevelAnnotations(sourceLines, 'ToString')
        def eqFieldAnns = extractFieldLevelAnnotations(sourceLines, 'EqualsAndHashCode')
        def tsCtx       = tsParams ? AnnotationContext.of(computeEffectiveFields(tsParams, tsFieldAnns, allFields), tsParams)
                                   : AnnotationContext.absent()
        def eqCtx       = eqParams ? AnnotationContext.of(computeEffectiveFields(eqParams, eqFieldAnns, allFields), eqParams)
                                   : AnnotationContext.absent()
        def className   = extractClassName(lines)
        lines = eqPatcher.patchToStringEqualsHashCode(lines, tsCtx, eqCtx, className)
        return eqPatcher.patchClassJavadoc(lines, tsCtx, eqCtx, className)
    }

    private void logPatched(File delombokFile, boolean hasBuilder, boolean hasToString, boolean hasEqHash) {
        def tags = [hasBuilder ? "@Builder" : null,
                    hasToString ? "@ToString" : null,
                    hasEqHash ? "@EqualsAndHashCode" : null].findAll { tag -> tag != null }.join(', ')
        println "  Patched: ${delombokFile.name} [${tags}]"
    }

    void processFile(File delombokFile, File delombokBaseDir, File sourceBaseDir) {
        def sourceLines = new File(sourceBaseDir, delombokBaseDir.toPath().relativize(delombokFile.toPath()).toString())
                              .with { f -> f.exists() ? f.readLines('UTF-8') : [] }
        def tsParams        = parseToStringParams(sourceLines)
        def eqParams        = parseEqualsHashCodeParams(sourceLines)
        boolean hasBuilder  = hasBuilderAnnotation(sourceLines)
        boolean hasToString = tsParams != null
        boolean hasEqHash   = eqParams != null
        if (!hasBuilder && !hasToString && !hasEqHash) return
        def patched = delombokFile.readLines('UTF-8')
        if (hasBuilder)            patched = applyBuilderPatching(patched, sourceLines)
        if (hasToString || hasEqHash) patched = applyEqualsHashCodePatching(patched, sourceLines, tsParams, eqParams)
        delombokFile.write(patched.join('\n') + '\n', 'UTF-8')
        logPatched(delombokFile, hasBuilder, hasToString, hasEqHash)
    }
}

// Invoked via Maven (project binding is set): process delombok output.
// Self-tests: groovy scripts/lombok-javadoc-propagator-tests.groovy
if (binding.hasVariable('project')) {
    def delombokDir = new File(project.build.directory, 'generated-sources/delombok')
    def sourceDir   = new File(project.build.sourceDirectory)
    println "LombokJavadocPropagator: scanning ${delombokDir}"
    def propagator = new LombokJavadocPropagator()
    delombokDir.eachFileRecurse { file ->
        if (file.name.endsWith('.java')) propagator.processFile(file, delombokDir, sourceDir)
    }
    println "LombokJavadocPropagator: done."
}
