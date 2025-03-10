package no.nav.tiltakspenger.libs.meldekort

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * @param id Unik id (ULID/UUID) for denne versjonen av meldeperioden
 * @param kjedeId Identifiserer en meldeperiode, på tvers av saker. For øyeblikket på formatet yyyy-mm-dd/yyyy-mm-dd. Vil være unik sammen med [sakId]/[saksnummer] og [versjon]
 * @param versjon Versjon av meldeperioden. Øker med 1 for hvert nye vedtak dersom det påvirker meldeperioden. Unik sammen med [sakId]/[saksnummer] og [kjedeId]
 * @param antallDagerForPeriode Maks antall dager en bruker har rett til å melde for meldeperioden.
 * @param girRett En map med datoer og om de gir rett til tiltakspenger. Datoene er fra og med [fraOgMed] til og med [tilOgMed]
 */
data class MeldeperiodeDTO(
    val id: String,
    val kjedeId: String,
    val versjon: Int,
    val fnr: String,
    val saksnummer: String,
    val sakId: String,
    val opprettet: LocalDateTime,
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val antallDagerForPeriode: Int,
    val girRett: Map<LocalDate, Boolean>,
)
