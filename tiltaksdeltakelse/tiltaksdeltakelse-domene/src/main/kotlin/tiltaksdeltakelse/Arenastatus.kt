package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import java.time.LocalDate

/**
 * Deltakerstatus fra Arena.
 *
 * Kontrakt: https://github.com/navikt/mulighetsrommet/blob/main/common/domain/src/main/kotlin/no/nav/mulighetsrommet/model/ArenaDeltakerStatus.kt
 * Omdøpingen fra Arenas koder: https://github.com/navikt/mulighetsrommet/blob/main/mulighetsrommet-arena-adapter/src/main/kotlin/no/nav/mulighetsrommet/arena/adapter/models/arena/ArenaTiltakdeltakelse.kt
 *
 * **Navnene her er `tiltakshistorikk` sine, ikke Arenas.**
 * Mellomleddet har døpt om kodene: Arena sier `JATAKK`, kontrakten sier `TAKKET_JA_TIL_TILBUD`.
 * Oversettelsen står i `ArenaTiltakdeltakerStatus` i arena-adapteren over, der Arenas kode er `@SerialName` og det lange navnet er enum-verdien.
 * [kodeHosKilden]-tabellen under er verifisert mot den fila 2026-07-31.
 * Vi følger kontrakten i enum-navnet, fordi det er den vi deserialiserer, og bærer Arenas egen kode i [kodeHosKilden].
 * Snakker du med noen som jobber i Arena, er det [kodeHosKilden] dere har felles — hver verdi under viser begge.
 *
 * Arena er under utfasing, og deltakelser flyttes gradvis til Komet.
 * Kodene har ingen offentlig dokumentasjon; enumet med sine norske beskrivelser er den praktiske kontrakten.
 *
 * Løpet i grove trekk:
 * [Type.AKTUELL] og [Type.INFORMASJONSMOTE] er tidlig interesse, [Type.VENTELISTE] er kvalifisert uten plass, og [Type.TILBUD] er tilbudt plass.
 * [Type.TAKKET_JA_TIL_TILBUD] og [Type.TAKKET_NEI_TIL_TILBUD] er brukerens svar på tilbudet.
 * [Type.GJENNOMFORES] er selve deltakelsen, som avsluttes med [Type.FULLFORT], [Type.DELTAKELSE_AVBRUTT] eller [Type.IKKE_MOTT].
 * [Type.AVSLAG], [Type.IKKE_AKTUELL], [Type.GJENNOMFORING_AVLYST] og [Type.FEILREGISTRERT] er utganger der deltakelsen aldri ble noe av.
 */
sealed interface Arenastatus : Kildestatus {
    /** Statusen er en av de femten kodene vi kjenner fra kontrakten. */
    data class Kjent(
        val type: Type,
    ) : Arenastatus,
        Kildestatus.Kjent {
        override val kilde: Tiltakskilde get() = Tiltakskilde.Arena

        override val kodeIKontrakten: String get() = type.name

        override val kodeHosKilden: String get() = type.kodeHosKilden

        override fun deltakerstatus(fraOgMed: LocalDate?, påDato: LocalDate): Deltakerstatus = type.deltakerstatus(fraOgMed, påDato)
    }

    /** En kontraktsverdi for Arena vi ikke kjenner igjen — se [Kildestatus.Ukjent]. */
    data class Ukjent(
        override val kodeIKontrakten: String,
    ) : Arenastatus,
        Kildestatus.Ukjent {
        init {
            require(kodeIKontrakten.isNotBlank()) { "En ukjent kildeverdi må bære kontraktens kode" }
        }

        override val kilde: Tiltakskilde get() = Tiltakskilde.Arena
    }

    enum class Type(
        val kodeHosKilden: String,
    ) {
        /**
         * Arena: `AKTUELL` — «Aktuell».
         * Veileder har meldt brukeren som interessert, men utvelgelsen er ikke startet.
         */
        AKTUELL("AKTUELL"),

        /**
         * Arena: `AVSLAG` — «Fått avslag».
         * Brukeren ble vurdert, men fikk ikke plass.
         */
        AVSLAG("AVSLAG"),

        /**
         * Arena: `DELAVB` — «Deltakelse avbrutt».
         * Brukeren deltok og sluttet før tiltaket var ferdig.
         */
        DELTAKELSE_AVBRUTT("DELAVB"),

        /**
         * Arena: `FEILREG` — «Feilregistrert».
         * Registreringen skulle ikke vært der.
         */
        FEILREGISTRERT("FEILREG"),

        /**
         * Arena: `FULLF` — «Fullført».
         * Brukeren deltok tiltaket ut.
         */
        FULLFORT("FULLF"),

        /**
         * Arena: `GJENN` — «Gjennomføres».
         * Dekker både «plass tildelt, ikke startet» og «deltar nå» — Arena skiller ikke.
         * Startdatoen er det eneste vi har til å avgjøre hvilken av dem det er.
         */
        GJENNOMFORES("GJENN"),

        /**
         * Arena: `GJENN_AVB` — «Gjennomføring avbrutt».
         * Selve tiltaket ble avbrutt, ikke bare brukerens deltakelse.
         */
        GJENNOMFORING_AVBRUTT("GJENN_AVB"),

        /**
         * Arena: `GJENN_AVL` — «Gjennomføring avlyst».
         * Tiltaket ble avlyst før det startet.
         */
        GJENNOMFORING_AVLYST("GJENN_AVL"),

        /**
         * Arena: `IKKAKTUELL` — «Ikke aktuell».
         * Brukeren ble vurdert, men skal ikke delta.
         */
        IKKE_AKTUELL("IKKAKTUELL"),

        /**
         * Arena: `IKKEM` — «Ikke møtt».
         * Brukeren hadde plass og skulle møtt, men kom aldri, og datoen har passert.
         * Avklart med fag 2026-07-31: dette er ikke deltakelse.
         * Statusen kan være feilregistrert, og i praksis er det for omstendelig å få den rettet i Arena.
         * Da skal konsumentene håndtere det — overstyring ved innvilgelse i `tiltakspenger-saksbehandling-api`, unntak i søknadsguarden i `tiltakspenger-soknad-api` — aldri denne mappingen.
         */
        IKKE_MOTT("IKKEM"),

        /**
         * Arena: `INFOMOETE` — «Informasjonsmøte».
         * Brukeren er invitert til informasjonsmøte, ikke tildelt plass.
         */
        INFORMASJONSMOTE("INFOMOETE"),

        /**
         * Arena: `JATAKK` — «Takket ja til tilbud».
         * Brukeren har akseptert plassen, men deltakelsen er ikke registrert som i gang.
         */
        TAKKET_JA_TIL_TILBUD("JATAKK"),

        /**
         * Arena: `NEITAKK` — «Takket nei til tilbud».
         * Brukeren avslo plassen.
         */
        TAKKET_NEI_TIL_TILBUD("NEITAKK"),

        /**
         * Arena: `TILBUD` — «Godkjent tiltaksplass».
         * Plass er tildelt og tilbudt, brukeren har ikke svart ennå.
         */
        TILBUD("TILBUD"),

        /**
         * Arena: `VENTELISTE` — «Venteliste».
         * Brukeren er vurdert som kvalifisert, men har ikke fått plass.
         */
        VENTELISTE("VENTELISTE"),
        ;

        internal fun deltakerstatus(fraOgMed: LocalDate?, påDato: LocalDate): Deltakerstatus {
            return when (this) {
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

                // Avklart med fag 2026-07-31: «Ikke møtt» er ikke deltakelse, og gir ikke rett til innvilgelse.
                // Bevisst avvik fra dagens mapping (IKKEM -> Avbrutt -> deltarEllerHarDeltatt).
                IKKE_MOTT -> Deltakerstatus.IkkeDeltatt

                // TODO: er dette riktig?
                // Å ha takket ja til et tilbud er ikke det samme som å delta, men behandles her som deltakelse.
                // Videreført fra dagens mapping (JATAKK -> Deltar) for å bevare oppførsel.
                // Fag sier Komet mapper denne til deltatt, men er usikker på hvor det skjer.
                // Merk at vi mottar verdien distinkt fra tiltakshistorikk uansett, så valget er vårt.
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
                -> Deltakerstatus.IkkeDeltatt
            }
        }
    }
}
