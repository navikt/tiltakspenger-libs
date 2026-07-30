package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope

/**
 * Mocking som muterer global JVM-tilstand er forbudt i testkode: `mockkStatic`, `mockkObject`, `mockkConstructor` og de tilhørende unmockk-/clear-funksjonene.
 * Alle tester skal tåle å kjøre parallelt i vilkårlig rekkefølge, og global mocking lekker mellom tester uansett hvor pent den ryddes opp — opprydding med `clearAllMocks`/`unmockkAll` er selv en global mutasjon.
 * Vanlig `mockk`/`spyk` bygget inne i hver test er fortsatt tillatt; fakes foretrekkes, men det håndheves ikke her.
 *
 * Deteksjonen har to lag: importer av funksjonene fra `io.mockk`, og kall funnet via [no.nav.tiltakspenger.libs.konsist.kodelinjer] (kommentarer og strengliteraler teller ikke, import-linjer hoppes over).
 * En fil som både importerer og kaller en funksjon rapporteres derfor én gang per linje som må endres.
 *
 * Kalleren sender typisk `scopeFromTest()`; produksjonskode skal uansett ikke ha mockk på classpathen.
 */
object IngenGlobalMocking {

    fun brudd(scope: KoScope, unntatteFilstier: Set<String> = emptySet()): List<String> = scope
        .kildefiler()
        .filterNot { file -> unntatteFilstier.any { sti -> file.path.endsWith(sti) } }
        .flatMap { file ->
            val importbrudd = file.imports
                .filter { import -> import.name.startsWith("io.mockk.") && import.name.removePrefix("io.mockk.") in globaleMockkFunksjoner }
                .map { import -> "${file.path}: ${import.name}" }
            val kallbrudd = file
                .kodelinjer()
                .filterNot { (_, kode) -> kode.trimStart().startsWith("import ") }
                .mapNotNull { (linjenummer, kode) ->
                    kallRegex.find(kode)?.let { match -> "${file.path}:$linjenummer: ${match.groupValues[1]}" }
                }
            importbrudd + kallbrudd
        }

    fun assert(scope: KoScope, unntatteFilstier: Set<String> = emptySet()) = assertIngenBrudd(
        brudd(scope, unntatteFilstier),
        "Ingen global mocking i tester — mockkStatic/mockkObject/mockkConstructor muterer JVM-tilstand som lekker mellom parallelle tester. Bruk mockk/spyk per test, eller helst en fake.",
    )

    /** Funksjonene i mockk som muterer global JVM-tilstand, inkludert oppryddingsvariantene. */
    private val globaleMockkFunksjoner = setOf(
        "mockkStatic",
        "mockkObject",
        "mockkConstructor",
        "unmockkStatic",
        "unmockkObject",
        "unmockkConstructor",
        "unmockkAll",
        "clearAllMocks",
        "clearStaticMockk",
        "clearConstructorMockk",
    )

    private val kallRegex = Regex("""\b(${globaleMockkFunksjoner.joinToString("|")})\s*\(""")
}
