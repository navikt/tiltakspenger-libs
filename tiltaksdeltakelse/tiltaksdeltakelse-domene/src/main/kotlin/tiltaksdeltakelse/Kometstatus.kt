package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Deltakerstatus fra Komet (Deltakeroversikten).
 *
 * Kontrakt: https://github.com/navikt/mulighetsrommet/blob/main/common/domain/src/main/kotlin/no/nav/mulighetsrommet/model/DeltakerStatus.kt
 * Kildens egen definisjon: https://github.com/navikt/amt-deltakelser/blob/main/amt-lib/models/src/main/kotlin/no/nav/amt/lib/models/deltaker/DeltakerStatus.kt
 * Grupperinger og statustekster: https://github.com/navikt/amt-deltakelser/blob/main/amt-deltaker/src/main/kotlin/no/nav/amt/deltaker/extensions/DeltakerStatusExtensions.kt
 * Jobben som flytter status på dato: https://github.com/navikt/amt-deltakelser/blob/main/amt-deltaker/src/main/kotlin/no/nav/amt/deltaker/job/DeltakerProgresjonHandler.kt
 *
 * Komet er den eneste kilden som oppgir en **årsak** og et **tidspunkt** sammen med statusen, og derfor den eneste der [Kildestatus] bærer mer enn en kode.
 * Årsaken er ofte det saksbehandler faktisk vil vite: at noen har sluttet sier lite, at de sluttet fordi de fikk jobb sier mye.
 * Årsaken har sin egen kjent/ukjent-akse — se [Kometårsak].
 *
 * Navnene er identiske hos kilden og i kontrakten, så [kodeHosKilden] er den samme som enum-navnet.
 * Til forskjell fra Arena finnes det ikke noe oversettelsesledd å gå seg vill i.
 *
 * Komet grupperer selv statusene sine, og gruppene er nyttige når vi vurderer algoritmene våre.
 * *Avsluttende* er [Type.HAR_SLUTTET], [Type.IKKE_AKTUELL], [Type.FEILREGISTRERT], [Type.AVBRUTT], [Type.FULLFORT] og [Type.AVBRUTT_UTKAST].
 * *Venter på plass* er [Type.SOKT_INN], [Type.VURDERES], [Type.VENTELISTE] og [Type.PABEGYNT_REGISTRERING].
 * *Har ikke startet* er de fire over pluss [Type.VENTER_PA_OPPSTART].
 *
 * Statusene flyttes av en jobb hos kilden, som i tillegg lagrer framtidige statuser med gyldighetsvindu.
 * Den vi mottar er derfor et øyeblikksbilde, ikke en varig sannhet.
 * [Kjent.opprettet] sier når øyeblikksbildet ble tatt hos kilden.
 */
sealed interface Kometstatus : Kildestatus {
    /**
     * Årsaken kilden oppga, når den finnes — egen kjent/ukjent-akse, se [Kometårsak].
     * Leses uavhengig av om selve statustypen er kjent, siden den er et eget felt i kontraktens statusobjekt.
     */
    val årsak: Kometårsak?

    /**
     * Når kilden satte statusen — kildens eget tidspunkt, ikke når vi hentet.
     * Kontrakten kaller feltet `opprettetDato` og har varslet omdøping til `opprettetTidspunkt`.
     */
    val opprettet: LocalDateTime

    /** Statusen er en av de fjorten kodene vi kjenner fra kontrakten. */
    data class Kjent(
        val type: Type,
        override val årsak: Kometårsak?,
        override val opprettet: LocalDateTime,
    ) : Kometstatus,
        Kildestatus.Kjent {
        override val kilde: Tiltakskilde get() = Tiltakskilde.Komet

        override val kodeIKontrakten: String get() = type.name

        override val kodeHosKilden: String get() = type.name

        override fun deltakerstatus(fraOgMed: LocalDate?, påDato: LocalDate): Deltakerstatus =
            when (type) {
                // TODO: skal årsaken kunne overstyre typen her?
                // Komet modellerer «ikke møtt» som årsak, ikke som status — Arena har det som status, og der avklarte fag 2026-07-31 at det ikke er deltakelse.
                // En Komet-deltakelse med HAR_SLUTTET eller AVBRUTT og årsak IKKE_MOTT er trolig samme tilfelle, men behandles her som deltakelse.
                // Må avklares med fag, nå som vi endelig har årsaken tilgjengelig.
                Type.AVBRUTT,
                Type.DELTAR,
                Type.FULLFORT,
                Type.HAR_SLUTTET,
                -> Deltakerstatus.DeltarEllerHarDeltatt

                Type.VENTER_PA_OPPSTART -> Deltakerstatus.TildeltIkkeStartet

                Type.AVBRUTT_UTKAST,
                Type.FEILREGISTRERT,
                Type.IKKE_AKTUELL,
                Type.KLADD,
                Type.PABEGYNT_REGISTRERING,
                Type.SOKT_INN,
                Type.UTKAST_TIL_PAMELDING,
                Type.VENTELISTE,
                Type.VURDERES,
                -> Deltakerstatus.IkkeDeltatt
            }
    }

    /**
     * En kontraktsverdi for Komet vi ikke kjenner igjen — se [Kildestatus.Ukjent].
     * Årsaken og tidspunktet er egne felter i kontraktens statusobjekt, og leses selv om selve typen er ukjent.
     */
    data class Ukjent(
        override val kodeIKontrakten: String,
        override val årsak: Kometårsak?,
        override val opprettet: LocalDateTime,
    ) : Kometstatus,
        Kildestatus.Ukjent {
        init {
            require(kodeIKontrakten.isNotBlank()) { "En ukjent kildeverdi må bære kontraktens kode" }
        }

        override val kilde: Tiltakskilde get() = Tiltakskilde.Komet

        override val hva: String get() = "deltakerstatus fra Komet"
    }

    /**
     * Løpet går fra utkast ([KLADD], [UTKAST_TIL_PAMELDING], [AVBRUTT_UTKAST]) via innsøking ([SOKT_INN], [VURDERES], [VENTELISTE]) til tildelt plass ([VENTER_PA_OPPSTART]) og deltakelse ([DELTAR]).
     * Deltakelsen avsluttes med [HAR_SLUTTET], [FULLFORT] eller [AVBRUTT], avhengig av om tiltaket er løpende eller et kurs.
     */
    enum class Type {
        /** Deltakeren deltok på kurs, men sluttet før kurset var ferdig. */
        AVBRUTT,

        /** Utkastet ble avbrutt før det ble en påmelding. */
        AVBRUTT_UTKAST,

        /** Deltakeren deltar nå. */
        DELTAR,

        /** Registreringen skulle ikke vært der. */
        FEILREGISTRERT,

        /** Deltakeren deltok kurset ut. */
        FULLFORT,

        /** Deltakeren deltok på et løpende tiltak og har sluttet. */
        HAR_SLUTTET,

        /** Deltakeren ble vurdert, men skal ikke delta. */
        IKKE_AKTUELL,

        /**
         * «Kladden er ikke delt».
         * Veileder har begynt å fylle ut, men ingenting er sendt til bruker.
         */
        KLADD,

        /**
         * Påbegynt registrering.
         * Eldre variant av utkastløpet, under utfasing.
         */
        PABEGYNT_REGISTRERING,

        /** Veileder har meldt deltakeren inn, men utvelgelsen er ikke gjort. */
        SOKT_INN,

        /** «Utkastet er delt og venter på godkjenning» fra bruker. */
        UTKAST_TIL_PAMELDING,

        /** Deltakeren er vurdert som kvalifisert, men har ikke fått plass. */
        VENTELISTE,

        /** Plass er tildelt, deltakelsen har ikke startet. */
        VENTER_PA_OPPSTART,

        /** Arrangøren vurderer om deltakeren er kvalifisert. */
        VURDERES,
    }

    /**
     * Hvorfor deltakelsen endte som den gjorde.
     *
     * Settet følger **kontrakten**, ikke kildens egen enum.
     * Kilden har elleve verdier, kontrakten fjorten: [FEILREGISTRERT], [FERDIG] og [OPPFYLLER_IKKE_KRAVENE] finnes kun i kontrakten, og er trolig historiske verdier som fortsatt ligger i data.
     *
     * Kilden har i tillegg et fritekstfelt på årsaken, kun tillatt for [ANNET].
     * Det når ikke oss gjennom kontrakten — like greit, siden fritekst fra saksbehandlere kan inneholde personopplysninger.
     */
    enum class Årsak {
        /**
         * Fri årsak.
         * Kilden har en fritekstbeskrivelse her som ikke når oss.
         */
        ANNET,

        /** Kontrakten mellom Nav og arrangør ble avlyst. */
        AVLYST_KONTRAKT,

        /** Deltakeren kom i jobb. */
        FATT_JOBB,

        /** Historisk verdi, finnes ikke i kildens enum lenger. */
        FEILREGISTRERT,

        /** Historisk verdi, finnes ikke i kildens enum lenger. */
        FERDIG,

        /** Det var ikke plass til deltakeren. */
        FIKK_IKKE_PLASS,

        /**
         * «Møter ikke opp».
         * Merk at Arena har dette som *status*, ikke som årsak.
         */
        IKKE_MOTT,

        /** Historisk verdi, finnes ikke i kildens enum lenger. */
        OPPFYLLER_IKKE_KRAVENE,

        /** Samarbeidet med arrangøren ble avbrutt. */
        SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT,

        /** Deltakeren ble syk. */
        SYK,

        /** Deltakeren trenger en annen form for støtte. */
        TRENGER_ANNEN_STOTTE,

        /** Deltakeren begynte å studere. */
        UTDANNING,

        /** Kravene for deltakelse er ikke oppfylt. */
        KRAV_IKKE_OPPFYLT,

        /** Kurset var fullt. */
        KURS_FULLT,
    }
}

/**
 * Årsaken Komet oppga, kjent eller ikke.
 *
 * Egen akse: en kjent status kan komme med en ukjent årsak, og omvendt.
 * Årsaken driver ingen guards — den er informasjon til saksbehandler — så en ukjent årsak blokkerer ingenting, men skal varsles på.
 */
sealed interface Kometårsak {
    /**
     * Koden slik kontrakten skriver den; hos Komet er kildens og kontraktens navn de samme.
     * Kun til visning og gjenkjenning — diskriminering skjer på [Kjent.årsak].
     */
    val kodeIKontrakten: String

    /** Årsaken er en av de fjorten verdiene vi kjenner fra kontrakten. */
    data class Kjent(
        val årsak: Kometstatus.Årsak,
    ) : Kometårsak {
        override val kodeIKontrakten: String get() = årsak.name
    }

    /** En årsakskode i kontrakten vi ikke kjenner igjen — bæres ordrett, og varsles på gjennom [UkjentKildeverdi]. */
    data class Ukjent(
        override val kodeIKontrakten: String,
    ) : Kometårsak,
        UkjentKildeverdi {
        init {
            require(kodeIKontrakten.isNotBlank()) { "En ukjent kildeverdi må bære kontraktens kode" }
        }

        override val hva: String get() = "årsak fra Komet"
    }
}
