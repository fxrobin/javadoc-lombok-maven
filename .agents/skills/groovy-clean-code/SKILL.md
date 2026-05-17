---
name: groovy-clean-code
description: >
  Use when writing, reviewing, or refactoring any Groovy code. Triggers on: method longer than
  20 lines, inline logic mixed with dispatch, God-method smell, monolithic script over 200 lines,
  missing single-responsibility, regex defined inline at call site, or any Groovy code requiring
  craftsmanship review. Apply extreme Clean Code discipline.
---

# Groovy Clean Code & Craftsmanship

Extreme discipline: every method does one thing. Every name reveals intent. No excuses.

---

## Core Laws

1. **Single Responsibility** — one method, one reason to change
2. **Intention-Revealing Names** — the name makes the comment unnecessary
3. **20-Line Hard Cap** — methods longer than 20 lines get extracted immediately, no negotiation
4. **No Inline Regex at Call Site** — always assign to a named variable first
5. **Dispatcher vs Worker** — routing logic and business logic never share a method

---

## The 20-Line Law

Count lines before writing. When a method hits 20, stop and extract.

Extraction is not optional. It is not "premature abstraction". It is the discipline.

```groovy
// ❌ God-method: routes AND processes AND formats — 60+ lines
List<String> process(List<String> lines) {
    def result = []
    for (int i = 0; i < lines.size(); i++) {
        def line    = lines[i]
        def trimmed = line.trim()
        def indent  = line.replaceFirst(/\S.*/, '')
        if (trimmed.startsWith('class ')) {
            // 15 lines of class handling inline
        }
        if (trimmed.startsWith('def ')) {
            // 15 lines of method handling inline
        }
        // ...more blocks
    }
}

// ✅ Thin dispatcher + focused workers — each method < 20 lines
List<String> process(List<String> lines) {
    def result = []
    for (int i = 0; i < lines.size(); i++) {
        def line    = lines[i]
        def trimmed = line.trim()
        def indent  = line.replaceFirst(/\S.*/, '')
        if (trimmed.startsWith('class ')) { handleClassDeclaration(result, indent, trimmed); continue }
        if (trimmed.startsWith('def '))   { handleMethodDeclaration(result, indent, trimmed); continue }
        result << line
    }
    return result
}

private void handleClassDeclaration(List<String> result, String indent, String trimmed) { ... }
private void handleMethodDeclaration(List<String> result, String indent, String trimmed) { ... }
```

---

## Dispatcher vs Worker

Two kinds of methods. Never mix them.

| Kind | Responsibility | Contains |
|------|---------------|----------|
| **Dispatcher** | Routing | `if`, `switch`, `continue`, calls to workers |
| **Worker** | One concern | Business logic, no routing |

A dispatcher that processes is a God-method. A worker that routes is a dispatcher.

---

## Naming Rules

Names must reveal intent. No abbreviations. No generic names (`data`, `process`, `handle`).

| Context | Pattern | Example |
|---------|---------|---------|
| Transform lines | `patch{What}` | `patchMethodSignatures` |
| Find position | `find{What}{How}` | `findClassDeclarationIndex` |
| Extract data | `extract{What}` | `extractFieldNames` |
| Boolean test | `has{Condition}` / `is{State}` | `hasPrecedingComment`, `isAbstract` |
| Parse structured input | `parse{Type}` | `parseAnnotationParams` |
| Remove content | `remove{What}` | `removeStaleEntries` |
| Build output | `build{What}` | `buildSummaryLines` |
| Inject content | `inject{What}` | `injectDefaultImplementation` |

Groovy closures: name the parameter, even in one-liners.

```groovy
// ❌ Opaque
lines.findAll { it.trim().startsWith('@') }

// ✅ Intent visible
lines.findAll { line -> line.trim().startsWith('@') }
```

---

## Regex Discipline

Named match variable. Always.

```groovy
// ❌ Anonymous — unreadable, result silently discarded
if (trimmed =~ /^public\s+(?:final\s+)?class\s+(\w+)\s*\{/)

// ✅ Named — explicit intent, groups accessible, step-debuggable
def classMatch = (trimmed =~ /^public\s+(?:final\s+)?class\s+(\w+)\s*\{/)
if (classMatch) {
    def className = classMatch[0][1]
    handleClass(result, indent, className)
}
```

Complex regex: extract to a named constant or static field with a comment on the pattern intent.

```groovy
static final FIELD_DECL = /^private\s+(?:final\s+)?[\w<>?,\[\] ]+\s+(\w+)\s*[;=]/

// Usage — pattern name documents itself
def fieldMatch = (line.trim() =~ FIELD_DECL)
```

---

## Guard Clause over Nesting

Early return removes nesting. Flat is readable. Nested is not.

```groovy
// ❌ Arrow code
private void process(List<String> result, String input) {
    if (isValid(input)) {
        if (!alreadyProcessed(result)) {
            def data = parse(input)
            if (data) {
                result << format(data)
            }
        }
    }
}

// ✅ Guards + flat logic
private void process(List<String> result, String input) {
    if (!isValid(input))          return
    if (alreadyProcessed(result)) return
    def data = parse(input)
    if (!data)                    return
    result << format(data)
}
```

---

## Traits over Inheritance — Prefer Composition

When classes share behavior, use Groovy `trait`, not `extends`. Inheritance couples types; traits share capabilities.

```groovy
// ❌ Inheritance — DomainProcessor IS-A BaseUtils (false claim)
class BaseUtils {
    boolean hasPrecedingComment(List<String> lines) { ... }
}
class DomainProcessor extends BaseUtils { ... }

// ✅ Traits — DomainProcessor HAS commenting capability (true claim)
trait CommentUtils {
    boolean hasPrecedingComment(List<String> lines) { ... }
}
class DomainProcessor implements CommentUtils { ... }
```

Multiple traits, no diamond problem:

```groovy
trait SourceAnalyzer  { Map<String, Object> parseParams(...) { ... } }
trait JavadocUtils    { boolean hasPrecedingJavadoc(...) { ... } }

class MyProcessor implements SourceAnalyzer, JavadocUtils { ... }
```

Composition for collaborators — hold instances, delegate calls:

```groovy
class Orchestrator implements SourceAnalyzer {
    private final BuilderPatcher builder = new BuilderPatcher()
    private final EqualsPatcher  equals  = new EqualsPatcher()

    void process(File f) {
        def patched = builder.patch(...)
        patched = equals.patch(patched, ...)
    }
}
```

Load order still matters: trait file before class file that implements it.

---

## Script Splitting

When a Groovy script exceeds 200 lines:

1. Identify cohesive clusters of methods
2. Move each cluster to its own trait or class file
3. Connect via traits (shared behavior) or composition (collaborators)
4. Load files in dependency order (trait → implementing class)

Size thresholds:

| Lines | Action |
|-------|--------|
| < 100 | Fine |
| 100–200 | Watch for cohesion problems |
| > 200 | Split now |
| > 400 | Already a problem — split immediately |

---

## Groovy-Specific Pitfalls

**`def` abuse** — use concrete types at method boundaries, `def` inside method body only.

```groovy
// ❌ Opaque signature
def process(def input, def config) { ... }

// ✅ Self-documenting
List<String> process(List<String> lines, Map<String, String> config) { ... }
```

**Implicit return** — fine for single-expression methods, dangerous for multi-branch.

```groovy
// ✅ OK — single expression, intent clear
boolean isEmpty(List<String> lines) { lines.every { it.trim().isEmpty() } }

// ❌ Risky — last expression returned silently, branch unclear
String resolve(Map config) {
    if (config.override) config.override
    computeDefault(config)           // implicit return — easy to miss
}
// ✅ Explicit
String resolve(Map config) {
    if (config.override) return config.override
    return computeDefault(config)
}
```

**GString in regex** — interpolation works but kills readability. Use plain String + concat or `sprintf`.

---

## Self-Test Discipline

Every script that transforms text must have self-tests. Keep tests in a sibling file (`*-tests.groovy`).
Tests run standalone — no build tool required.

```groovy
// my-processor-tests.groovy
def loader = new GroovyClassLoader()
loader.parseClass(new File('scripts/base-utils.groovy'))
def Processor = loader.parseClass(new File('scripts/my-processor.groovy'))
def proc = Processor.newInstance()

def input  = ['line one', 'line two']
def result = proc.process(input)
assert result.contains('expected output') : "Missing expected output. Got: ${result}"
println "All tests passed."
```

One assertion per logical case. Assert message must say what was expected.

---

## Quick Checklist

- [ ] No method > 20 lines
- [ ] Dispatcher methods contain only routing (`if` / `switch` / `continue` / calls)
- [ ] Worker methods contain only one concern
- [ ] Regex assigned to named variable before use
- [ ] Complex regex extracted to named constant with intent comment
- [ ] Closure parameters named (not implicit `it` in multi-step chains)
- [ ] Guard clauses replace nesting (max 2 levels deep)
- [ ] Method signatures use concrete types, not bare `def`
- [ ] Implicit returns only for single-expression methods
- [ ] Self-tests cover each code path
- [ ] Shared behavior extracted to `trait`, not base class (`extends`)
- [ ] Collaborators injected via composition (held as fields), not inherited
- [ ] Script > 200 lines has been split
