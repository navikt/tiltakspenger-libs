package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope

/**
 * Systemklokka hentes kun på composition root — én gang, ved oppstart (typisk `App.kt`; lokalt `LokalMain.kt`).
 * Alle andre steder skal `Clock` komme inn som parameter, og tester bruker `fixedClock`/`TikkendeKlokke`.
 * `Clock.system*` spredt utover gjør tidsavhengig oppførsel ikke-deterministisk og utestbar — samme begrunnelse som [IngenNowUtenClock] og [IngenClockDefault], se «Clock og tid» i AGENTS-backend.md.
 *
 * Deteksjonen er tekstbasert: `Clock.systemUTC()`, `Clock.systemDefaultZone()` og `Clock.system(sone)` flagges, mens kommentarer og strengliteraler hoppes over (se `kodelinjer`).
 *
 * [unntatteFilstier] er sti-suffikser som unntas — composition root-filene der kallet er selve definisjonen (f.eks. `App.kt` og `LokalMain.kt`).
 * Kalleren velger scope: typisk `scopeFromProject()`, siden regelen gjelder test- og produksjonskode likt.
 */
object IngenClockSystem {

    fun brudd(scope: KoScope, unntatteFilstier: Set<String> = emptySet()): List<String> = scope.kildefiler()
        .filterNot { file -> unntatteFilstier.any { sti -> file.path.endsWith(sti) } }
        .flatMap { file ->
            file.kodelinjer().mapNotNull { (linjenummer, kode) ->
                clockSystemRegex.find(kode)?.let { match -> "${file.path}:$linjenummer: ${match.value}" }
            }
        }

    fun assert(scope: KoScope, unntatteFilstier: Set<String> = emptySet()) = assertIngenBrudd(
        brudd(scope, unntatteFilstier),
        "Hent aldri systemklokka utenfor composition root — ta imot Clock som parameter; i test brukes fixedClock/TikkendeKlokke (se «Clock og tid» i AGENTS-backend.md).",
    )

    /** `Clock.systemUTC()`, `Clock.systemDefaultZone()` og `Clock.system(sone)`. */
    private val clockSystemRegex = Regex("""\bClock\.system(UTC|DefaultZone)?\(""")
}
