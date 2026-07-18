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

    fun brudd(scope: KoScope): List<String> = scope.kildefiler().flatMap { file ->
        file.kodelinjer().mapNotNull { (linjenummer, kode) ->
            nowUtenClockRegex.find(kode)?.let { match -> "${file.path}:$linjenummer: ${match.value}" }
        }
    }

    fun assert(scope: KoScope) = assertIngenBrudd(
        brudd(scope),
        "Hent aldri nåtid uten Clock — bruk now(clock) eller nå(clock) fra libs-common (se «Clock og tid» i AGENTS-backend.md).",
    )

    /** No-arg `now()` på java.time-typene. */
    private val nowUtenClockRegex =
        Regex("""\b(Instant|LocalDate|LocalDateTime|LocalTime|OffsetDateTime|OffsetTime|ZonedDateTime|YearMonth|Year|MonthDay)\.now\(\)""")
}
