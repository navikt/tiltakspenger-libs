package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import java.time.LocalDate

/**
 * Statusen kildesystemet selv oppgir, bevart ordrett.
 *
 * **Unntak fra navnekonvensjonene våre:** enum-verdiene beholder stavingen fra kontrakten vi mottar, uten æøå og uten norsk ordlyd.
 * Det er bevisst, slik at en verdi kan søkes opp og kjennes igjen på tvers av vår kode, kontrakten og feilsøking i kildesystemene.
 * Vår egen ordlyd ligger i [Deltakerstatus].
 *
 * Saksbehandler skal kunne se hva kilden faktisk sa, ikke bare vår tolkning av det.
 * Normaliseringen til [Deltakerstatus] er en faglig vurdering, ikke en teknisk oversettelse, og ligger derfor i domenet der den kan granskes og testes.
 *
 * At statusen er sealed per kilde gjør at kilde og status ikke kan komme i utakt: en Komet-deltakelse kan ikke bære en Arena-status.
 * Derfor utledes også [kilde] herfra i stedet for å bæres som et eget felt.
 * Det gjelder også de ukjente verdiene — en kode vi ikke kjenner igjen er fortsatt en Arena-, Komet- eller Team Tiltak-kode.
 *
 * Alle tre settene er hentet fra `tiltakshistorikk`, som er kontrakten vi faktisk mottar:
 * https://github.com/navikt/mulighetsrommet/blob/main/common/tiltakshistorikk-client/src/main/kotlin/no/nav/tiltak/historikk/TiltakshistorikkV1Dto.kt
 *
 * En kildeverdi vi ikke kjenner igjen tvinges ikke inn i noen av de kjente — den flyter inn som kildens egen [Ukjent]-variant, bæres ordrett, og blokkerer tolkning til den er mappet.
 *
 * @see Arenastatus
 * @see Kometstatus
 * @see TeamTiltakstatus
 */
sealed interface Kildestatus {
    val kilde: Tiltakskilde

    /**
     * Verdien slik kontrakten skriver den — det som faktisk sto på wiren fra `tiltakshistorikk`.
     *
     * For kjente verdier er dette navnet på enum-verdien; for [Ukjent] er det nettopp denne strengen vi ikke kjenner igjen.
     * Snakker du med noen i mulighetsrommet, er det denne verdien dere har felles — kildesystemets eget språk er [Kjent.kodeHosKilden].
     *
     * **Kun til visning og gjenkjenning.**
     * Diskriminering skal skje på enum-verdien, ikke på denne strengen.
     */
    val kodeIKontrakten: String

    /**
     * Statusen er en verdi vi kjenner igjen, og som kan tolkes.
     */
    sealed interface Kjent : Kildestatus {
        /**
         * Koden slik kildesystemet selv skriver den.
         *
         * Dette er ikke alltid det samme som [kodeIKontrakten].
         * `tiltakshistorikk` er et mellomledd som har døpt om Arenas koder: Arena sier `JATAKK`, kontrakten sier `TAKKET_JA_TIL_TILBUD`.
         * Snakker du med noen som jobber i kildesystemet, er det denne verdien dere har felles.
         * Oversettelsen finnes bare for kjente verdier — for en [Ukjent] kontraktsverdi kjenner vi ikke kildens egen kode, og feltet finnes derfor ikke der.
         */
        val kodeHosKilden: String

        /**
         * Hva kildens status betyr for oss.
         *
         * @param fraOgMed deltakelsens startdato, som kan mangle.
         * @param påDato datoen spørsmålet stilles for.
         *
         * De fleste statusene svarer det samme uansett dato.
         * Unntaket er Arena sin `GJENNOMFORES`, som ikke skiller mellom «tildelt, ikke startet» og «deltar» — der er datoen det eneste vi har å gå på.
         * Å ta datoen som parameter i stedet for å lese klokka gjør avhengigheten synlig og svaret reproduserbart.
         *
         * Svaret er en spørring, ikke en lagret sannhet: det regnes ut ved lesing, og skal aldri persisteres som om det var kildedata.
         *
         * Statusen hos kilden kan være feilregistrert, og i noen tilfeller er det for omstendelig å få rettet den der.
         * Da må saksbehandler likevel kunne innvilge — og bruker fortsatt kunne søke — selv om statusen normalt ikke gir rett til det.
         * Det løses aldri ved å justere denne mappingen; den leser kilden ærlig, og forgjengerens `IKKE_MOTT → Avbrutt` i tiltak-dtos viste hvordan en mapping som bøyes for behovet gjør valget usynlig.
         * Håndteringen bor hos konsumentene, som har kildestatusen i behold: en overstyringsabstraksjon i `tiltakspenger-saksbehandling-api` for innvilgelse, og et unntak i søknadsguarden i `tiltakspenger-soknad-api` for hvem som kan søke.
         */
        fun deltakerstatus(fraOgMed: LocalDate?, påDato: LocalDate): Deltakerstatus
    }

    /**
     * En verdi i kontrakten vi ikke kjenner igjen — typisk et tillegg hos kilden som kodetabellene våre ikke har tatt igjen ennå.
     *
     * Det er **kontraktens** verdi som er ukjent, og den bæres ordrett i [kodeIKontrakten].
     * Kildesystemets egen kode er ukjennbar til mappingen finnes — derfor har varianten ikke [Kjent.kodeHosKilden].
     * Verdien lagres ordrett hos konsumentene, slik at den tolkes riktig ved neste lesing når mappingen tar den igjen — uten migrering.
     * Den kan ikke tolkes: [Kjent.deltakerstatus] finnes ikke her, så spørsmålet er urepresenterbart i stedet for å få et uærlig svar.
     * Uttrekk og guards utelater den til den er mappet — det er et brukbarhetsspørsmål, aldri et utfall — og den skal varsles på, ikke forsvinne stille.
     * Varslingen og visningen leser den gjennom [UkjentKildeverdi]-flaten.
     */
    sealed interface Ukjent :
        Kildestatus,
        UkjentKildeverdi
}
