package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import java.time.LocalDate

/**
 * Avtalestatus fra Team Tiltak (avtaler med arbeidsgiver).
 *
 * Kontrakt: https://github.com/navikt/mulighetsrommet/blob/main/common/domain/src/main/kotlin/no/nav/mulighetsrommet/model/ArbeidsgiverAvtaleStatus.kt
 * Kildesystem: https://github.com/navikt/tiltaksgjennomforing-api/blob/master/src/main/java/no/nav/tag/tiltaksgjennomforing/avtale/Status.java
 * Kafka-meldingen statusen sendes på: https://github.com/navikt/tiltaksgjennomforing-api/blob/master/src/main/java/no/nav/tag/tiltaksgjennomforing/datadeling/AvtaleMelding.java
 *
 * Kilden skriver to av verdiene med æøå (`PÅBEGYNT`, `GJENNOMFØRES`), mens kontrakten har strippet dem.
 * [kodeHosKilden] gir kildens egen staving.
 *
 * **Statusen er ikke registrert, den er utledet.**
 * `Status.fra(avtale)` i kildesystemet regner den ut i denne rekkefølgen: annullert-tidspunkt satt gir [ANNULLERT]; ellers inngått avtale med passert sluttdato gir [AVSLUTTET]; ellers inngått avtale med passert startdato gir [GJENNOMFORES]; ellers inngått avtale gir [KLAR_FOR_OPPSTART]; ellers alle felter utfylt gir [MANGLER_GODKJENNING]; ellers [PAABEGYNT].
 *
 * To konsekvenser er verdt å merke seg.
 * Skillet mellom [KLAR_FOR_OPPSTART], [GJENNOMFORES] og [AVSLUTTET] er rent datostyrt, akkurat som Arena sin `GJENNOMFORES` — men her regnes det ut hos kilden, så statusen vi mottar er et øyeblikksbilde fra hentetidspunktet.
 * Og [AVSLUTTET] sier bare at avtalen var inngått og at sluttdatoen har passert; det er ikke en bekreftelse på at personen faktisk møtte opp.
 *
 * NB: kilden har også en grunn på annulleringen, som vi ikke henter inn.
 */
data class TeamTiltakstatus(
    val type: Type,
) : Kildestatus {
    override val kilde: Tiltakskilde get() = Tiltakskilde.TeamTiltak

    override val kodeHosKilden: String get() = type.kodeHosKilden

    override fun deltakerstatus(fraOgMed: LocalDate?, påDato: LocalDate): Deltakerstatus = type.deltakerstatus(fraOgMed, påDato)

    enum class Type(
        val kodeHosKilden: String,
    ) {
        /**
         * «Annullert».
         * Tiltaket ble aldri noe av.
         */
        ANNULLERT("ANNULLERT"),

        /**
         * «Avbrutt».
         * Finnes i kontrakten, men **ikke** i kildesystemets egen statusenum, som kun har de seks andre.
         * Vi tar den imot for ikke å velte på en verdi kontrakten tillater, men vet ikke når den faktisk sendes.
         */
        AVBRUTT("AVBRUTT"),

        /**
         * «Avsluttet».
         * Avtalen var inngått og sluttdatoen har passert.
         */
        AVSLUTTET("AVSLUTTET"),

        /**
         * «Gjennomføres».
         * Avtalen er inngått og startdatoen har passert.
         */
        GJENNOMFORES("GJENNOMFØRES"),

        /**
         * «Klar for oppstart».
         * Avtalen er inngått, men startdatoen er fram i tid.
         */
        KLAR_FOR_OPPSTART("KLAR_FOR_OPPSTART"),

        /**
         * «Mangler godkjenning».
         * Alle felter er utfylt, men avtalen er ikke inngått ennå.
         */
        MANGLER_GODKJENNING("MANGLER_GODKJENNING"),

        /**
         * «Påbegynt».
         * Avtalen mangler fortsatt data som kreves for å kunne gjennomføres — i praksis en kladd.
         */
        PAABEGYNT("PÅBEGYNT"),
        ;

        internal fun deltakerstatus(fraOgMed: LocalDate?, påDato: LocalDate): Deltakerstatus =
            when (this) {
                AVSLUTTET,
                GJENNOMFORES,
                -> Deltakerstatus.DeltarEllerHarDeltatt

                // TODO: er dette riktig?
                // Kildesystemet har ingen AVBRUTT i sin egen statusenum, så vi vet ikke om den betyr avbrutt før eller etter oppstart.
                // Videreført fra dagens mapping (AVBRUTT -> Avbrutt -> deltarEllerHarDeltatt) for å bevare oppførsel.
                // Må avklares med fag, og helst med Team Tiltak.
                AVBRUTT -> Deltakerstatus.DeltarEllerHarDeltatt

                KLAR_FOR_OPPSTART -> Deltakerstatus.TildeltIkkeStartet

                // Kafka-varianten skiller ANNULLERT i feilregistrert og ikke aktuell med et eget flagg, mens HTTP-API-et mangler flagget.
                // Skillet forsvinner her, siden begge uansett er IkkeDeltatt.
                ANNULLERT,
                MANGLER_GODKJENNING,
                PAABEGYNT,
                -> Deltakerstatus.IkkeDeltatt
            }
    }
}
