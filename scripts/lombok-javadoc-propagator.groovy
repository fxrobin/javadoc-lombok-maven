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
            patched = builderPatcher.patchBuilderSetters(patched, getterReturns, overrides)
        }

        if (hasToString || hasEqHash) {
            def allFields   = extractAllFieldNames(sourceLines)
            def tsFieldAnns = extractFieldLevelAnnotations(sourceLines, 'ToString')
            def eqFieldAnns = extractFieldLevelAnnotations(sourceLines, 'EqualsAndHashCode')
            def tsCtx       = tsParams ? AnnotationContext.of(computeEffectiveFields(tsParams, tsFieldAnns, allFields), tsParams)
                                       : AnnotationContext.absent()
            def eqCtx       = eqParams ? AnnotationContext.of(computeEffectiveFields(eqParams, eqFieldAnns, allFields), eqParams)
                                       : AnnotationContext.absent()
            def className   = extractClassName(patched)

            patched = eqPatcher.patchToStringEqualsHashCode(patched, tsCtx, eqCtx, className)
            patched = eqPatcher.patchClassJavadoc(patched, tsCtx, eqCtx, className)
        }

        delombokFile.write(patched.join('\n') + '\n', 'UTF-8')
        def tags = [hasBuilder ? "@Builder" : null,
                    hasToString ? "@ToString" : null,
                    hasEqHash ? "@EqualsAndHashCode" : null].findAll { tag -> tag != null }.join(', ')
        println "  Patched: ${delombokFile.name} [${tags}]"
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
