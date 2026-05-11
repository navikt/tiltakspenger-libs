package no.nav.tiltakspenger.libs.meldekort

import no.nav.tiltakspenger.libs.periode.PeriodeDTO
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 *  Sak med meldeperioder og meldekortvedtak som sendes fra saksbehandling-api til meldekort-api når det fattes et nytt vedtak på saken eller det er en søknad under behandling.
 *  @param meldeperioder Gjeldende versjon av alle meldeperioder på saken
 *  @param meldekortvedtak Alle meldekortvedtak på saken. Brukes til visning og varsling i meldekort-api.
 *      Default `emptyList()` for å være bakoverkompatibel med eldre saksbehandling-api-versjoner.
 * */
data class SakTilMeldekortApiDTO(
    val fnr: String,
    val sakId: String,
    val saksnummer: String,
    val meldeperioder: List<MeldeperiodeDTO>,
    val harSoknadUnderBehandling: Boolean,
    val kanSendeInnHelgForMeldekort: Boolean = false,
    val meldekortvedtak: List<MeldekortvedtakDTO> = emptyList(),
) {
    data class MeldeperiodeDTO(
        val id: String,
        val kjedeId: String,
        val versjon: Int,
        val opprettet: LocalDateTime,
        val periodeDTO: PeriodeDTO,
        val antallDagerForPeriode: Int,
        val girRett: Map<LocalDate, Boolean>,
    ) {
        /**
         * Bakoverkompatibilitet: meldekort-api / andre konsumenter som ikke er oppgradert
         * leser fortsatt `fraOgMed`/`tilOgMed` flatt. Slik unngår vi koordinerte deploys.
         * Fjernes når alle konsumenter har gått over til [periodeDTO].
         */
        @Deprecated("Bruk periodeDTO.fraOgMed", ReplaceWith("periodeDTO.fraOgMed"))
        val fraOgMed: String = periodeDTO.fraOgMed

        @Deprecated("Bruk periodeDTO.tilOgMed", ReplaceWith("periodeDTO.tilOgMed"))
        val tilOgMed: String = periodeDTO.tilOgMed

        init {
            val (fraOgMed, tilOgMed) = validerMeldeperiode(periodeDTO)
            val forventedeDatoer = (0L..13L).map { fraOgMed.plusDays(it) }.toSet()
            require(girRett.keys == forventedeDatoer) {
                "girRett må inneholde nøyaktig de 14 datoene i meldeperioden $fraOgMed - $tilOgMed, men var ${girRett.keys.sorted()}"
            }
        }
    }

    /**
     * Et iverksatt meldekortvedtak. Meldekortvedtak er immutable etter iverksettelse,
     * så meldekort-api kan trygt deduplisere på [id].
     *
     * Et vedtak gjelder én underliggende meldekortbehandling som kan omfatte én eller flere
     * sammenhengende [MeldeperiodebehandlingDTO]. Felter fra selve meldekortbehandlingen er lagt
     * flatt på vedtaket. Beløp og total-periode utelates — meldekort-api kan regne dem ut fra
     * meldeperiodebehandlingene ved behov.
     *
     * @param id VedtakId (ULID).
     * @param meldeperiodebehandlinger Sammenhengende og sortert ikke-tom liste av meldeperiodebehandlinger som inngår i vedtaket.
     */
    data class MeldekortvedtakDTO(
        val id: String,
        val opprettet: LocalDateTime,
        val erKorrigering: Boolean,
        val erAutomatiskBehandlet: Boolean,
        val meldeperiodebehandlinger: List<MeldeperiodebehandlingDTO>,
    ) {
        init {
            require(meldeperiodebehandlinger.isNotEmpty()) {
                "Et meldekortvedtak må ha minst én meldeperiodebehandling"
            }
            require(
                meldeperiodebehandlinger.zipWithNext().all { (a, b) ->
                    LocalDate.parse(a.periodeDTO.tilOgMed).plusDays(1) == LocalDate.parse(b.periodeDTO.fraOgMed)
                },
            ) {
                "Meldeperiodebehandlinger må være sammenhengende og sortert, men var ${meldeperiodebehandlinger.map { it.periodeDTO }}"
            }
        }

        /**
         * Én meldeperiodebehandling inne i et meldekortvedtak. Hver meldeperiodebehandling
         * gjelder nøyaktig én meldeperiode (mandag-søndag, 14 dager).
         *
         * @param brukersMeldekortId Id-en til brukers innsendte meldekort som denne behandlingen
         *      tok utgangspunkt i, eller `null` dersom behandlingen ikke er knyttet til et brukers meldekort
         *      (f.eks. ved automatisk behandling uten innsendt meldekort, eller manuell behandling før bruker har sendt inn).
         *      Lar meldekort-api vise hvilke overstyringer saksbehandler har gjort sammenliknet med brukers innsendte verdier.
         */
        data class MeldeperiodebehandlingDTO(
            val meldeperiodeId: String,
            val meldeperiodeKjedeId: String,
            val brukersMeldekortId: String?,
            val periodeDTO: PeriodeDTO,
            val dager: List<DagDTO>,
        ) {
            init {
                val (fraOgMed, tilOgMed) = validerMeldeperiode(periodeDTO)
                require(dager.all { it.dato in fraOgMed..tilOgMed }) {
                    "Alle dager må ligge innenfor meldeperioden $fraOgMed - $tilOgMed, men var ${dager.map { it.dato }}"
                }
            }

            data class DagDTO(
                val dato: LocalDate,
                val status: Status,
                val reduksjon: Reduksjon,
                val beløp: Int,
                val beløpBarnetillegg: Int,
            ) {
                init {
                    require(beløp >= 0) {
                        "beløp må være >= 0, men var $beløp"
                    }
                    require(beløpBarnetillegg >= 0) {
                        "beløpBarnetillegg må være >= 0, men var $beløpBarnetillegg"
                    }
                }
            }
        }

        enum class Status {
            DELTATT_UTEN_LØNN_I_TILTAKET,
            DELTATT_MED_LØNN_I_TILTAKET,
            FRAVÆR_SYK,
            FRAVÆR_SYKT_BARN,
            FRAVÆR_GODKJENT_AV_NAV,
            FRAVÆR_STERKE_VELFERDSGRUNNER_ELLER_JOBBINTERVJU,
            FRAVÆR_ANNET,
            IKKE_BESVART,
            IKKE_TILTAKSDAG,
            IKKE_RETT_TIL_TILTAKSPENGER,
        }

        enum class Reduksjon {
            INGEN_REDUKSJON,
            REDUKSJON,
            YTELSEN_FALLER_BORT,
        }
    }
}

/**
 * En meldeperiode må starte på mandag, slutte på søndag og være nøyaktig 14 dager.
 * Returnerer parset (fraOgMed, tilOgMed).
 */
private fun validerMeldeperiode(periodeDTO: PeriodeDTO): Pair<LocalDate, LocalDate> {
    val fraOgMed = LocalDate.parse(periodeDTO.fraOgMed)
    val tilOgMed = LocalDate.parse(periodeDTO.tilOgMed)
    require(fraOgMed.dayOfWeek == DayOfWeek.MONDAY) {
        "fraOgMed må være en mandag, men var $fraOgMed (${fraOgMed.dayOfWeek})"
    }
    require(tilOgMed.dayOfWeek == DayOfWeek.SUNDAY) {
        "tilOgMed må være en søndag, men var $tilOgMed (${tilOgMed.dayOfWeek})"
    }
    require(tilOgMed == fraOgMed.plusDays(13)) {
        "En meldeperiode må være 14 dager, men var fraOgMed=$fraOgMed, tilOgMed=$tilOgMed"
    }
    return fraOgMed to tilOgMed
}
