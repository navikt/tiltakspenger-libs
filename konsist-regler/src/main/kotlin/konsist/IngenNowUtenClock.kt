package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope

/**
 * Produksjonskode skal aldri hente nåtid uten en `Clock`: kall `now(clock)` (eller `nå(clock)` fra libs-common), ikke `now()`.
 * No-arg `now()` gjør koden ikke-deterministisk og utestbar med `fixedClock`/`TikkendeKlokke`.
 * Se «Clock og tid» i AGENTS-backend.md i monorepo-rota.
 *
 * Deteksjonen er tekstbasert: no-arg `now()` på java.time-typene flagges, mens kommentarlinjer og trailing-kommentarer hoppes over.
 * Bevisst akseptert hull: `now(ZoneId)` er også uten `Clock`, men skilles ikke fra `now(clock)` tekstlig — fanges i review.
 *
 * Kalleren velger scope: typisk `scopeFromProduction()`, siden tester bruker `fixedClock`/`TikkendeKlokke` som klokke, ikke som unnskyldning for `now()`.
 */
object IngenNowUtenClock {

    fun brudd(scope: KoScope, ekstraTyper: Set<String> = emptySet()): List<String> {
        val regex = nowUtenClockRegex(standardTyper + ekstraTyper)
        return scope.kildefiler().flatMap { file ->
            file.kodelinjer().mapNotNull { (linjenummer, kode) ->
                regex.find(kode)?.let { match -> "${file.path}:$linjenummer: ${match.value}" }
            }
        }
    }

    fun assert(scope: KoScope, ekstraTyper: Set<String> = emptySet()) = assertIngenBrudd(
        brudd(scope, ekstraTyper),
        "Hent aldri nåtid uten Clock — bruk now(clock) eller nå(clock) fra libs-common (se «Clock og tid» i AGENTS-backend.md).",
    )

    /** Typene i java.time som har en no-arg `now()`; et repo med en egen tidstype som følger samme mønster legger navnet til. */
    val standardTyper = setOf(
        "Instant",
        "LocalDate",
        "LocalDateTime",
        "LocalTime",
        "OffsetDateTime",
        "OffsetTime",
        "ZonedDateTime",
        "YearMonth",
        "Year",
        "MonthDay",
    )

    /** No-arg `now()` på typene i settet. */
    private fun nowUtenClockRegex(typer: Set<String>) =
        Regex("""\b(${typer.joinToString("|") { type -> Regex.escape(type) }})\.now\(\)""")
}
