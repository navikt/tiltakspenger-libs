package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration

/**
 * Testklasser skal ikke ha muterbar tilstand i felter — verken `var`/`lateinit var` eller `val` med kjent muterbar initialisator (mocks, køer, tellere).
 * Alle tester skal tåle å kjøre parallelt i vilkårlig rekkefølge, og med `per_class`-livssyklus deles én instans mellom alle testmetodene i klassen, så et muterbart felt er en race.
 * Toppnivå-properties i testfiler er JVM-globale og vurderes på samme måte.
 * Alternativet er å bygge tilstanden inne i hver test, typisk via en kontekst-klasse som instansieres per test.
 *
 * En testklasse er en klasse med minst én funksjon annotert med en JUnit-testannotasjon (`@Test`, `@ParameterizedTest`, `@RepeatedTest`, `@TestFactory`, `@TestTemplate`).
 * Immutable verdiobjekter i `val`-felter (identer, DTO-fixtures, scopes) er fortsatt tillatt.
 * Bevisst aksepterte hull: muterbare typer uten gjenkjent initialisator, `by lazy`-delegater, companion objects og konstruktørparametre.
 *
 * Kalleren sender `scopeFromTest()`.
 */
object IngenMuterbareTestfelter {

    fun brudd(
        scope: KoScope,
        unntatteFilstier: Set<String> = emptySet(),
        ekstraMuterbareInitialisatorer: Set<String> = emptySet(),
    ): List<String> {
        val regex = muterbarInitialisatorRegex(standardMuterbareInitialisatorer + ekstraMuterbareInitialisatorer)
        return scope
            .kildefiler()
            .filterNot { file -> unntatteFilstier.any { sti -> file.path.endsWith(sti) } }
            .flatMap { file -> file.toppnivåbrudd(regex) + file.testklassebrudd(regex) }
    }

    fun assert(
        scope: KoScope,
        unntatteFilstier: Set<String> = emptySet(),
        ekstraMuterbareInitialisatorer: Set<String> = emptySet(),
    ) = assertIngenBrudd(
        brudd(scope, unntatteFilstier, ekstraMuterbareInitialisatorer),
        "Ingen muterbar tilstand i testklassers felter — med per_class-livssyklus og parallelle testmetoder er feltet en race. Bygg tilstanden inne i hver test.",
    )

    private fun KoFileDeclaration.toppnivåbrudd(regex: Regex): List<String> =
        properties(includeNested = false).mapNotNull { property -> property.somBrudd(regex) }

    private fun KoFileDeclaration.testklassebrudd(regex: Regex): List<String> =
        classes(includeNested = true)
            .filter { klasse -> klasse.erTestklasse() }
            .flatMap { klasse -> klasse.properties(includeNested = false).mapNotNull { property -> property.somBrudd(regex) } }

    private fun KoClassDeclaration.erTestklasse(): Boolean =
        functions().any { funksjon -> funksjon.annotations.any { annotasjon -> annotasjon.name in testannotasjoner } }

    private fun KoPropertyDeclaration.somBrudd(regex: Regex): String? = when {
        isVar -> "$location: feltet $name er var og deler tilstand mellom tester"
        regex.containsMatchIn(text) -> "$location: feltet $name initialiseres med muterbar tilstand som deles mellom tester"
        else -> null
    }

    private val testannotasjoner = setOf("Test", "ParameterizedTest", "RepeatedTest", "TestFactory", "TestTemplate")

    /**
     * Initialisatorer som beviselig gir muterbar tilstand: mocks (som muteres av verify-/answers-oppsett og kall-opptak), muterbare collections og atomics.
     * Lista er bevisst kort og presis framfor komplett — heller et akseptert hull enn falske positiver på immutable verdiobjekter.
     * Et repo med en egen muterbar testtype (en kø, en teller, en fake med opptak) legger navnet til med `ekstraMuterbareInitialisatorer`.
     */
    val standardMuterbareInitialisatorer = setOf(
        "mockk",
        "spyk",
        "mutableListOf",
        "mutableMapOf",
        "mutableSetOf",
        "ArrayDeque",
        "LinkedList",
        "AtomicBoolean",
        "AtomicInteger",
        "AtomicLong",
        "AtomicReference",
    )

    /** Regexen bygges av navnesettet: `= <navn>(` eller `= <navn><`, slik at et tillegg fra kalleren treffer på lik linje. */
    private fun muterbarInitialisatorRegex(initialisatorer: Set<String>) =
        Regex("""=\s*(${initialisatorer.joinToString("|") { navn -> Regex.escape(navn) }})\s*[<(]""")
}
