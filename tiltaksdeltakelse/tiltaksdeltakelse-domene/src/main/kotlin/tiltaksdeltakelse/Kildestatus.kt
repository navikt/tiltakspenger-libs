package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import java.time.LocalDate

/**
 * Statusen kildesystemet selv oppgir, bevart ordrett.
 *
 * Saksbehandler skal kunne se hva kilden faktisk sa, ikke bare vår tolkning av det.
 * Normaliseringen til [Deltakerstatus] er en faglig vurdering, ikke en teknisk oversettelse, og ligger derfor i domenet der den kan granskes og testes.
 *
 * At statusen er sealed per kilde gjør at kilde og status ikke kan komme i utakt: en Komet-deltakelse kan ikke bære en Arena-status.
 * Derfor utledes også [kilde] herfra i stedet for å bæres som et eget felt.
 *
 * Infrastrukturen eier wire-formatet og mapper begge Arena-stavemåtene (tiltakshistorikk sine lange navn og Arenas egne koder fra Kafka, `DELAVB`, `GJENN`, …) inn på `Arena` her.
 * En kildeverdi vi ikke kjenner igjen skal ikke tvinges inn i noen av disse — den hører hjemme i en egen variant av tiltaksdeltakelsen.
 */
sealed interface Kildestatus {
    val kilde: Tiltakskilde

    /**
     * Hva kildens status betyr for oss.
     *
     * @param fraOgMed deltakelsens startdato, som kan mangle.
     * @param påDato datoen spørsmålet stilles for.
     *
     * De fleste statusene svarer det samme uansett dato.
     * Unntaket er Arena sin `GJENNOMFORES`, som ikke skiller mellom «tildelt, ikke startet» og «deltar» — der er datoen det eneste vi har å gå på.
     * Å ta datoen som parameter i stedet for å lese klokka gjør avhengigheten synlig og svaret reproduserbart.
     */
    fun deltakerstatus(fraOgMed: LocalDate?, påDato: LocalDate): Deltakerstatus

    /**
     * Arena, slik `tiltakshistorikk` staver statusene.
     */
    enum class Arena : Kildestatus {
        AKTUELL,
        AVSLAG,
        DELTAKELSE_AVBRUTT,
        FEILREGISTRERT,
        FULLFORT,
        GJENNOMFORES,
        GJENNOMFORING_AVBRUTT,
        GJENNOMFORING_AVLYST,
        IKKE_AKTUELL,
        IKKE_MOTT,
        INFORMASJONSMOTE,
        TAKKET_JA_TIL_TILBUD,
        TAKKET_NEI_TIL_TILBUD,
        TILBUD,
        VENTELISTE,
        ;

        override val kilde: Tiltakskilde = Tiltakskilde.Arena

        override fun deltakerstatus(fraOgMed: LocalDate?, påDato: LocalDate): Deltakerstatus =
            when (this) {
                // Arena skiller ikke mellom tildelt og påbegynt; startdatoen er det eneste skillet vi har.
                GJENNOMFORES ->
                    if (fraOgMed == null || fraOgMed.isAfter(påDato)) {
                        Deltakerstatus.TildeltIkkeStartet
                    } else {
                        Deltakerstatus.DeltarEllerHarDeltatt
                    }

                DELTAKELSE_AVBRUTT,
                GJENNOMFORING_AVBRUTT,
                FULLFORT,
                -> Deltakerstatus.DeltarEllerHarDeltatt

                // TODO: er dette riktig?
                // «Ikke møtt» betyr at personen aldri startet, men behandles her som å ha deltatt, og gir dermed rett til innvilgelse.
                // Videreført fra dagens mapping (IKKE_MOTT -> Avbrutt) for å bevare oppførsel.
                // Må avklares med fag.
                IKKE_MOTT -> Deltakerstatus.DeltarEllerHarDeltatt

                // TODO: er dette riktig?
                // Å ha takket ja til et tilbud er ikke det samme som å delta, men behandles her som deltakelse.
                // Videreført fra dagens mapping (TAKKET_JA_TIL_TILBUD -> Deltar) for å bevare oppførsel.
                // Må avklares med fag.
                TAKKET_JA_TIL_TILBUD -> Deltakerstatus.DeltarEllerHarDeltatt

                TILBUD -> Deltakerstatus.TildeltIkkeStartet

                AKTUELL,
                AVSLAG,
                FEILREGISTRERT,
                GJENNOMFORING_AVLYST,
                IKKE_AKTUELL,
                INFORMASJONSMOTE,
                TAKKET_NEI_TIL_TILBUD,
                VENTELISTE,
                -> Deltakerstatus.IngenPlass
            }
    }

    /**
     * Komet (Deltakeroversikten).
     */
    enum class Komet : Kildestatus {
        AVBRUTT,
        AVBRUTT_UTKAST,
        DELTAR,
        FEILREGISTRERT,
        FULLFORT,
        HAR_SLUTTET,
        IKKE_AKTUELL,
        KLADD,
        PABEGYNT_REGISTRERING,
        SOKT_INN,
        UTKAST_TIL_PAMELDING,
        VENTELISTE,
        VENTER_PA_OPPSTART,
        VURDERES,
        ;

        override val kilde: Tiltakskilde = Tiltakskilde.Komet

        override fun deltakerstatus(fraOgMed: LocalDate?, påDato: LocalDate): Deltakerstatus =
            when (this) {
                AVBRUTT,
                DELTAR,
                FULLFORT,
                HAR_SLUTTET,
                -> Deltakerstatus.DeltarEllerHarDeltatt

                VENTER_PA_OPPSTART -> Deltakerstatus.TildeltIkkeStartet

                // KLADD er et utkast veileder ikke har sendt til bruker, og er ikke en reell deltakelse.
                // Den ble tidligere silt bort før mappingen, som kastet på den; nå bæres den eksplisitt.
                AVBRUTT_UTKAST,
                FEILREGISTRERT,
                IKKE_AKTUELL,
                KLADD,
                PABEGYNT_REGISTRERING,
                SOKT_INN,
                UTKAST_TIL_PAMELDING,
                VENTELISTE,
                VURDERES,
                -> Deltakerstatus.IngenPlass
            }
    }

    /**
     * Team Tiltak (avtaler med arbeidsgiver).
     */
    enum class TeamTiltak : Kildestatus {
        ANNULLERT,
        AVBRUTT,
        AVSLUTTET,
        GJENNOMFORES,
        KLAR_FOR_OPPSTART,
        MANGLER_GODKJENNING,
        PAABEGYNT,
        ;

        override val kilde: Tiltakskilde = Tiltakskilde.TeamTiltak

        override fun deltakerstatus(fraOgMed: LocalDate?, påDato: LocalDate): Deltakerstatus =
            when (this) {
                AVBRUTT,
                AVSLUTTET,
                GJENNOMFORES,
                -> Deltakerstatus.DeltarEllerHarDeltatt

                KLAR_FOR_OPPSTART -> Deltakerstatus.TildeltIkkeStartet

                // Kafka-varianten skiller ANNULLERT i feilregistrert og ikke aktuell med et eget flagg, mens HTTP-API-et ikke har flagget.
                // Skillet forsvinner her, siden begge uansett er IngenPlass.
                ANNULLERT,
                MANGLER_GODKJENNING,
                PAABEGYNT,
                -> Deltakerstatus.IngenPlass
            }
    }
}
