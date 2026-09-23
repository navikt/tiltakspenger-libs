package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope

/**
 * Bruk `nå(clock)` fra libs-common i stedet for `LocalDateTime.now(...)`.
 * Hjelperen trunkerer til mikrosekunder, som gir stabile verdier gjennom Postgres-rundturer.
 * Se «Clock og tid» i AGENTS-backend.md i monorepo-rota.
 *
 * Deteksjonen er tekstbasert: alle `LocalDateTime.now(`-kall flagges, mens kommentarlinjer og trailing-kommentarer hoppes over.
 * No-arg `LocalDateTime.now()` flagges også av [IngenNowUtenClock] — overlappen er bevisst, begge meldingene er riktige.
 *
 * [unntatteFilstier] er sti-suffikser som unntas — typisk fila som definerer `nå(clock)` og derfor legitimt kaller `LocalDateTime.now(clock)`.
 */
object IngenLocalDateTimeNow {

    fun brudd(scope: KoScope, unntatteFilstier: Set<String> = emptySet()): List<String> = scope.kildefiler()
        .filterNot { file -> unntatteFilstier.any { sti -> file.path.endsWith(sti) } }
        .flatMap { file ->
            file.kodelinjer().mapNotNull { (linjenummer, kode) ->
                localDateTimeNowRegex.find(kode)?.let { match -> "${file.path}:$linjenummer: ${match.value}" }
            }
        }

    fun assert(scope: KoScope, unntatteFilstier: Set<String> = emptySet()) = assertIngenBrudd(
        brudd(scope, unntatteFilstier),
        "Bruk nå(clock) fra libs-common i stedet for LocalDateTime.now(...) (se «Clock og tid» i AGENTS-backend.md).",
    )

    private val localDateTimeNowRegex = Regex("""\bLocalDateTime\.now\([^)]*\)""")
}
