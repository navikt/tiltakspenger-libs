package no.nav.tiltakspenger.libs.meldekort

import java.time.LocalDate
import java.time.LocalDateTime

/**
 *  Sak med meldeperioder som sendes fra saksbehandling-api til meldekort-api når det fattes et nytt vedtak på saken
 *  @param meldeperioder Gjeldende versjon av alle meldeperioder på saken
 * */
data class SakTilMeldekortApiDTO(
    val fnr: String,
    val sakId: String,
    val saksnummer: String,
    val meldeperioder: List<Meldeperiode>,
    val harSoknadUnderBehandling: Boolean,
    val kanSendeInnHelgForMeldekort: Boolean = false,
) {
    data class Meldeperiode(
        val id: String,
        val kjedeId: String,
        val versjon: Int,
        val opprettet: LocalDateTime,
        val fraOgMed: LocalDate,
        val tilOgMed: LocalDate,
        val antallDagerForPeriode: Int,
        val girRett: Map<LocalDate, Boolean>,
    )
}
