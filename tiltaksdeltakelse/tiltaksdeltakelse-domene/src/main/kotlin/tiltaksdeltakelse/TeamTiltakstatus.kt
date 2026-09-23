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
 * `Status.fra(avtale)` i kildesystemet regner den ut i denne rekkefølgen: annullert-tidspunkt satt gir [Type.ANNULLERT]; ellers inngått avtale med passert sluttdato gir [Type.AVSLUTTET]; ellers inngått avtale med passert startdato gir [Type.GJENNOMFORES]; ellers inngått avtale gir [Type.KLAR_FOR_OPPSTART]; ellers alle felter utfylt gir [Type.MANGLER_GODKJENNING]; ellers [Type.PAABEGYNT].
 *
 * To konsekvenser er verdt å merke seg.
 * Skillet mellom [Type.KLAR_FOR_OPPSTART], [Type.GJENNOMFORES] og [Type.AVSLUTTET] er rent datostyrt, akkurat som Arena sin `GJENNOMFORES` — men her regnes det ut hos kilden, så statusen vi mottar er et øyeblikksbilde fra hentetidspunktet.
 * Og [Type.AVSLUTTET] sier bare at avtalen var inngått og at sluttdatoen har passert; det er ikke en bekreftelse på at personen faktisk møtte opp.
 *
 * NB: kilden har også en grunn på annulleringen, som vi ikke henter inn.
 */
sealed interface TeamTiltakstatus : Kildestatus {
    /** Statusen er en av de sju kodene vi kjenner fra kontrakten. */
    data class Kjent(
        val type: Type,
    ) : TeamTiltakstatus,
        Kildestatus.Kjent {
        override val kilde: Tiltakskilde get() = Tiltakskilde.TeamTiltak

        override val kodeIKontrakten: String get() = type.name

        override val kodeHosKilden: String get() = type.kodeHosKilden

        override fun deltakerstatus(fraOgMed: LocalDate?, påDato: LocalDate): Deltakerstatus = type.deltakerstatus(fraOgMed, påDato)
    }

    /** En kontraktsverdi for Team Tiltak vi ikke kjenner igjen — se [Kildestatus.Ukjent]. */
    data class Ukjent(
        override val kodeIKontrakten: String,
    ) : TeamTiltakstatus,
        Kildestatus.Ukjent {
        init {
            require(kodeIKontrakten.isNotBlank()) { "En ukjent kildeverdi må bære kontraktens kode" }
        }

        override val kilde: Tiltakskilde get() = Tiltakskilde.TeamTiltak

        override val hva: String get() = "avtalestatus fra Team Tiltak"
    }

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
