// Run from project root: groovy scripts/lombok-javadoc-propagator-tests.groovy
import groovy.transform.Field

@Field def p   // initialized below after classloader setup
@Field def AC  // AnnotationContext class, loaded from gcl

def thisDir = new File(getClass().protectionDomain.codeSource.location.toURI()).parentFile
def gcl = new GroovyClassLoader(getClass().classLoader)
['source-analyzer', 'javadoc-utils', 'builder-javadoc-patcher',
 'equals-hashcode-javadoc-patcher', 'lombok-javadoc-propagator'].each { name ->
    gcl.parseClass(new File(thisDir, "${name}.groovy"))
}
p  = gcl.loadClass('LombokJavadocPropagator').newInstance()
AC = gcl.loadClass('AnnotationContext')

// ─── Fixtures ─────────────────────────────────────────────────────────────────

def builderFooInput() {
    [
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
        '',
        '        @java.lang.Override',
        '        @java.lang.SuppressWarnings("all")',
        '        @lombok.Generated',
        '        public java.lang.String toString() { return "FooBuilder(name=" + this.name + ")"; }',
        '    }',
        '',
        '    @java.lang.SuppressWarnings("all")',
        '    @lombok.Generated',
        '    public static FooBuilder builder() { return new FooBuilder(); }',
        '}',
    ]
}

def builderBarOverrideInput() {
    [
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
}

def equalsHashCodeDelombokInput() {
    [
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
}

// ─── @Builder tests ───────────────────────────────────────────────────────────

def testBuilderAnnotationDetection() {
    assert p.hasBuilderAnnotation(builderFooInput()), "Should detect @Builder"
    assert !p.hasBuilderAnnotation(['public class Bar {}']), "Should NOT detect without @Builder"
}

def testGetterReturnsExtraction() {
    def gr = p.extractGetterReturns(builderFooInput())
    assert gr['name'] == 'the name; never null', "Wrong getter return: '${gr['name']}'"
}

def testBuilderParamOverrideExtraction() {
    assert p.extractBuilderParamOverrides(builderFooInput()).isEmpty(), "Should have no overrides"
    def ov = p.extractBuilderParamOverrides(builderBarOverrideInput())
    assert ov['count']?.contains('Sets the count'), "Override not extracted: ${ov}"
}

def testBuilderSetterPatching() {
    def gr  = p.extractGetterReturns(builderFooInput())
    def txt = p.patchBuilderSetters(builderFooInput(), gr, [:]).join('\n')
    assert txt.contains('@param name the name; never null'), "Missing @param:\n${txt}"
    assert txt.contains('@return this builder'), "Missing @return this builder"
    assert !txt.contains('@return {@code this}'), "Old @return not replaced"
}

def testBuilderFactoryAndToStringJavadoc() {
    def gr  = p.extractGetterReturns(builderFooInput())
    def txt = p.patchBuilderSetters(builderFooInput(), gr, [:]).join('\n')
    assert txt.contains('Creates a new {@link FooBuilder}'), "builder() Javadoc missing:\n${txt}"
    assert txt.contains('Returns a string representation of the current FooBuilder state.'),
        "builder toString() Javadoc missing:\n${txt}"
}

def testBuilderParamOverrideApplication() {
    def ov  = p.extractBuilderParamOverrides(builderBarOverrideInput())
    def txt = p.patchBuilderSetters(builderBarOverrideInput(), [:], ov).join('\n')
    assert txt.contains('Sets the count (must be positive)'), "Override not applied:\n${txt}"
    assert txt.contains('@param count positive integer'), "Override @param missing"
    assert !txt.contains('@return {@code this}'), "Old @return not replaced in override"
}

// ─── Annotation parsing tests ─────────────────────────────────────────────────

def testToStringAnnotationParsing() {
    def tsDefault = p.parseToStringParams(['@ToString', 'public class A {}'])
    assert tsDefault != null, "@ToString not detected"
    assert tsDefault.of.isEmpty(), "Default of should be empty"
    assert tsDefault.includeFieldNames == true, "Default includeFieldNames should be true"

    def tsOf = p.parseToStringParams(['@ToString(of = "name")', 'public class A {}'])
    assert tsOf.of == ['name'], "of= single not parsed: ${tsOf.of}"

    def tsOfMulti = p.parseToStringParams(['@ToString(of = {"a", "b"})', 'public class A {}'])
    assert tsOfMulti.of == ['a', 'b'], "of= multi not parsed: ${tsOfMulti.of}"

    def tsExclude = p.parseToStringParams(['@ToString(exclude = {"secret"})', 'public class A {}'])
    assert tsExclude.exclude == ['secret'], "exclude not parsed: ${tsExclude.exclude}"

    def tsNoNames = p.parseToStringParams(['@ToString(includeFieldNames = false)', 'public class A {}'])
    assert tsNoNames.includeFieldNames == false, "includeFieldNames=false not parsed"
}

def testEqualsHashCodeAnnotationParsing() {
    def eqOf = p.parseEqualsHashCodeParams(['@EqualsAndHashCode(of = "id")', 'public class A {}'])
    assert eqOf != null, "@EqualsAndHashCode not detected"
    assert eqOf.of == ['id'], "EqualsAndHashCode of= not parsed: ${eqOf.of}"
    assert p.parseEqualsHashCodeParams(['public class A {}']) == null, "Should be null without annotation"
}

// ─── Field extraction tests ───────────────────────────────────────────────────

def testAllFieldExtraction() {
    def fields = p.extractAllFieldNames([
        'public class X {',
        '    private final String name;',
        '    private int age;',
        '    private static final int CONST = 0;',
        '}',
    ])
    assert fields == ['name', 'age'], "Fields: ${fields}"
}

def testFieldLevelAnnotations() {
    def src = [
        '    @ToString.Exclude',
        '    private final String secret;',
        '    @EqualsAndHashCode.Include',
        '    private final String id;',
        '    private final String name;',
    ]
    def tsAnns = p.extractFieldLevelAnnotations(src, 'ToString')
    assert tsAnns.excludes == ['secret'], "TS excludes: ${tsAnns.excludes}"

    def eqAnns = p.extractFieldLevelAnnotations(src, 'EqualsAndHashCode')
    assert eqAnns.includes == ['id'], "EQ includes: ${eqAnns.includes}"
}

// ─── Effective field computation tests ────────────────────────────────────────

def testComputeEffectiveFields() {
    def allF   = ['a', 'b', 'c', 'd']
    def noAnns = [excludes: [], includes: []]

    def eff1 = p.computeEffectiveFields([of: ['b'], exclude: [], onlyExplicit: false], noAnns, allF)
    assert eff1 == ['b'], "of= should win: ${eff1}"

    def eff2 = p.computeEffectiveFields([of: [], exclude: ['c'], onlyExplicit: false], noAnns, allF)
    assert eff2 == ['a', 'b', 'd'], "exclude= should drop c: ${eff2}"

    def eff3 = p.computeEffectiveFields([of: [], exclude: [], onlyExplicit: true], [excludes: [], includes: ['a', 'd']], allF)
    assert eff3 == ['a', 'd'], "onlyExplicit should use includes: ${eff3}"

    def eff4 = p.computeEffectiveFields([of: [], exclude: [], onlyExplicit: false], [excludes: ['b'], includes: []], allF)
    assert eff4 == ['a', 'c', 'd'], "@Exclude on field: ${eff4}"
}

// ─── Method Javadoc injection tests ──────────────────────────────────────────

def testEqualsHashCodeToStringPatching() {
    def eqP   = [of: ['id'], exclude: [], onlyExplicit: false, callSuper: false]
    def tsP   = [of: [], exclude: [], onlyExplicit: false, callSuper: false, includeFieldNames: true]
    def tsCtx = AC.of(['id'], tsP)
    def eqCtx = AC.of(['id'], eqP)
    def txt   = p.patchToStringEqualsHashCode(equalsHashCodeDelombokInput(), tsCtx, eqCtx, 'Baz').join('\n')

    assert txt.contains('Two instances are equal when {@code id} is equal.'), "equals Javadoc missing:\n${txt}"
    assert txt.contains('Returns a hash code consistent with {@link #equals}.'), "hashCode Javadoc missing:\n${txt}"
    assert txt.contains('Returns a string representation of this instance.'), "toString Javadoc missing:\n${txt}"
    assert txt.contains('Includes: {@code id}.'), "toString field list missing"
}

def testCanEqualPatching() {
    def eqP   = [of: ['id'], exclude: [], onlyExplicit: false, callSuper: false]
    def tsP   = [of: [], exclude: [], onlyExplicit: false, callSuper: false, includeFieldNames: true]
    def tsCtx = AC.of(['id'], tsP)
    def eqCtx = AC.of(['id'], eqP)
    def txt   = p.patchToStringEqualsHashCode(equalsHashCodeDelombokInput(), tsCtx, eqCtx, 'Baz').join('\n')

    assert txt.contains('Returns whether another object can be considered equal'), "canEqual Javadoc missing:\n${txt}"
    assert txt.contains('@param other the object to test'), "canEqual @param missing"
}

// ─── Paragraph removal tests ──────────────────────────────────────────────────

def testRemoveParagraphsMatching() {
    def javadoc = [
        ' * /**',
        ' * <p>Equality based on id.</p>',
        ' * <p>Other paragraph.</p>',
        ' */',
    ]
    def cleaned = p.removeParagraphsMatching(javadoc) { line -> line.toLowerCase().contains('equality') }
    assert !cleaned.any { it.contains('Equality') }, "Should remove equality para"
    assert cleaned.any { it.contains('Other paragraph') }, "Should keep other para"
}

// ─── Run all tests ────────────────────────────────────────────────────────────

testBuilderAnnotationDetection()
testGetterReturnsExtraction()
testBuilderParamOverrideExtraction()
testBuilderSetterPatching()
testBuilderFactoryAndToStringJavadoc()
testBuilderParamOverrideApplication()
testToStringAnnotationParsing()
testEqualsHashCodeAnnotationParsing()
testAllFieldExtraction()
testFieldLevelAnnotations()
testComputeEffectiveFields()
testEqualsHashCodeToStringPatching()
testCanEqualPatching()
testRemoveParagraphsMatching()

println "Self-tests: PASSED"
