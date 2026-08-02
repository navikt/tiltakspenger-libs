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
 *
 * Alle tre settene er hentet fra `tiltakshistorikk`, som er kontrakten vi faktisk mottar:
 * https://github.com/navikt/mulighetsrommet/blob/main/common/tiltakshistorikk-client/src/main/kotlin/no/nav/tiltak/historikk/TiltakshistorikkV1Dto.kt
 *
 * En kildeverdi vi ikke kjenner igjen skal ikke tvinges inn i noen av disse — den hører hjemme i en egen variant av tiltaksdeltakelsen.
 *
 * @see Arenastatus
 * @see Kometstatus
 * @see TeamTiltakstatus
 */
sealed interface Kildestatus {
    val kilde: Tiltakskilde

    /**
     * Koden slik kildesystemet selv skriver den.
     *
     * Dette er ikke alltid det samme som navnet på enum-verdien.
     * `tiltakshistorikk` er et mellomledd som har døpt om Arenas koder: Arena sier `JATAKK`, kontrakten sier `TAKKET_JA_TIL_TILBUD`.
     * Snakker du med noen som jobber i kildesystemet, er det denne verdien dere har felles.
     *
     * **Kun til visning og gjenkjenning.**
     * Diskriminering skal skje på enum-verdien, ikke på denne strengen.
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
     * Statusen hos kilden kan være feilregistrert, og i noen tilfeller er det for omstendelig å få rettet den der.
     * Da må saksbehandler likevel kunne innvilge — og bruker fortsatt kunne søke — selv om statusen normalt ikke gir rett til det.
     * Det løses aldri ved å justere denne mappingen; den leser kilden ærlig, og forgjengerens `IKKE_MOTT → Avbrutt` i tiltak-dtos viste hvordan en mapping som bøyes for behovet gjør valget usynlig.
     * Håndteringen bor hos konsumentene, som har kildestatusen i behold: en overstyringsabstraksjon i `tiltakspenger-saksbehandling-api` for innvilgelse, og et unntak i søknadsguarden i `tiltakspenger-soknad-api` for hvem som kan søke.
     */
    fun deltakerstatus(fraOgMed: LocalDate?, påDato: LocalDate): Deltakerstatus
}
