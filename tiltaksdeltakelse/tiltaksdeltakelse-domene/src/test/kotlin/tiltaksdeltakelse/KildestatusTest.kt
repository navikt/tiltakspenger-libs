package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Paritetstester mot dagens oppførsel.
 *
 * Fasit er dagens kjede: kildens `toDeltakerStatusDTO()` til den flate 11-verdis statusen, og derfra `rettTilÅSøke` for søknadsguarden og `deltarEllerHarDeltatt()` for innvilgelse.
 * Utgangspunktet er at omskrivingen ikke skal endre hvem som får søke eller bli innvilget.
 *
 * Bevisste avvik fra dagens oppførsel skal ha egen test som sier at det er et avvik, hvem som har avklart det og når.
 * Så langt gjelder det Arena `IKKE_MOTT`, avklart med fag 2026-07-31.
 * Uten den regelen ville et avvik sett ut som paritet i en tabell ingen leser nøye.
 */
internal class KildestatusTest {
    private val idag = LocalDate.of(2026, 7, 30)
    private val igår = idag.minusDays(1)
    private val imorgen = idag.plusDays(1)
    private val statusOpprettet = LocalDateTime.of(2026, 7, 29, 12, 0)

    private fun Arenastatus.Type.status(fraOgMed: LocalDate? = igår) =
        Arenastatus.Kjent(this).deltakerstatus(fraOgMed = fraOgMed, påDato = idag)

    private fun Kometstatus.Type.status(fraOgMed: LocalDate? = igår) =
        Kometstatus.Kjent(type = this, årsak = null, opprettet = statusOpprettet).deltakerstatus(fraOgMed = fraOgMed, påDato = idag)

    private fun TeamTiltakstatus.Type.status(fraOgMed: LocalDate? = igår) =
        TeamTiltakstatus.Kjent(this).deltakerstatus(fraOgMed = fraOgMed, påDato = idag)

    @Test
    fun `kilden utledes fra statusen`() {
        Arenastatus.Kjent(Arenastatus.Type.GJENNOMFORES).kilde shouldBe Tiltakskilde.Arena
        Kometstatus.Kjent(Kometstatus.Type.DELTAR, årsak = null, opprettet = statusOpprettet).kilde shouldBe Tiltakskilde.Komet
        TeamTiltakstatus.Kjent(TeamTiltakstatus.Type.GJENNOMFORES).kilde shouldBe Tiltakskilde.TeamTiltak
    }

    /**
     * Et tillegg hos kilden skal flyte inn som ukjent i stedet for å velte deserialiseringen — beslutning D.
     * Tolkning er urepresenterbar for en ukjent verdi: `deltakerstatus` finnes bare på `Kildestatus.Kjent`.
     * Det er kontraktens verdi som er ukjent, og det er den som bæres — kildens egen kode er ukjennbar til mappingen finnes.
     */
    @Test
    fun `en ukjent kontraktsverdi bærer kontraktens kode ordrett og kilden sin`() {
        Arenastatus.Ukjent("NY_KONTRAKTSVERDI").kilde shouldBe Tiltakskilde.Arena
        Kometstatus.Ukjent("NY_KOMET_KODE", årsak = Kometårsak.Ukjent("NY_ÅRSAK"), opprettet = statusOpprettet).kilde shouldBe Tiltakskilde.Komet
        TeamTiltakstatus.Ukjent("NY_AVTALESTATUS").kilde shouldBe Tiltakskilde.TeamTiltak
    }

    /**
     * For en kjent verdi kjenner vi begge språkene — kontraktens og kildesystemets.
     * For en ukjent finnes bare kontraktens, og derfor finnes ikke `kodeHosKilden` på `Ukjent`.
     */
    @Test
    fun `arena - en kjent verdi bærer både kontraktens og Arenas språk`() {
        val kjent = Arenastatus.Kjent(Arenastatus.Type.TAKKET_JA_TIL_TILBUD)

        kjent.kodeIKontrakten shouldBe "TAKKET_JA_TIL_TILBUD"
        kjent.kodeHosKilden shouldBe "JATAKK"
    }

    @Test
    fun `en ukjent kildeverdi uten kode er en programmererfeil`() {
        shouldThrow<IllegalArgumentException> { Arenastatus.Ukjent("   ") }
        shouldThrow<IllegalArgumentException> { Kometstatus.Ukjent("", årsak = null, opprettet = statusOpprettet) }
        shouldThrow<IllegalArgumentException> { TeamTiltakstatus.Ukjent("") }
        shouldThrow<IllegalArgumentException> { Kometårsak.Ukjent("") }
    }

    @Test
    fun `arena - hvem som er i gang eller gjennomført`() {
        Arenastatus.Type.entries.filter { it.status() == Deltakerstatus.DeltarEllerHarDeltatt }.toSet() shouldBe
            setOf(
                Arenastatus.Type.DELTAKELSE_AVBRUTT,
                Arenastatus.Type.GJENNOMFORES,
                Arenastatus.Type.GJENNOMFORING_AVBRUTT,
                Arenastatus.Type.FULLFORT,
                // Videreført fra dagens mapping, men ikke bekreftet av fag ennå — se TODO i Kildestatus.
                Arenastatus.Type.TAKKET_JA_TIL_TILBUD,
            )
    }

    /**
     * Avklart med fag 2026-07-31: «Ikke møtt» betyr at personen skulle møtt, men aldri kom, og datoen har passert.
     * Den skal derfor ikke gi rett til innvilgelse.
     * Dette er en bevisst endring fra dagens mapping (IKKE_MOTT -> Avbrutt -> deltarEllerHarDeltatt), ikke en videreføring.
     */
    @Test
    fun `arena - ikke møtt gir ikke rett til innvilgelse`() {
        Arenastatus.Type.IKKE_MOTT.status() shouldBe Deltakerstatus.IkkeDeltatt
        Arenastatus.Type.IKKE_MOTT.status().deltarEllerHarDeltatt shouldBe false
        Arenastatus.Type.IKKE_MOTT.status().girRettTilÅSøke shouldBe false
    }

    @Test
    fun `arena - hvem som har fått tildelt plass uten å ha startet`() {
        Arenastatus.Type.entries.filter { it.status() == Deltakerstatus.TildeltIkkeStartet }.toSet() shouldBe
            setOf(Arenastatus.Type.TILBUD)
    }

    /**
     * Arena skiller ikke mellom «tildelt» og «deltar» — `GJENNOMFORES` dekker begge, og startdatoen er det eneste skillet.
     * Dagens kode leser klokka her; vi tar datoen som parameter, men svarer likt.
     */
    @Test
    fun `arena - GJENNOMFORES avhenger av startdato`() {
        Arenastatus.Type.GJENNOMFORES.status(fraOgMed = igår) shouldBe Deltakerstatus.DeltarEllerHarDeltatt
        Arenastatus.Type.GJENNOMFORES.status(fraOgMed = idag) shouldBe Deltakerstatus.DeltarEllerHarDeltatt
        Arenastatus.Type.GJENNOMFORES.status(fraOgMed = imorgen) shouldBe Deltakerstatus.TildeltIkkeStartet
        Arenastatus.Type.GJENNOMFORES.status(fraOgMed = null) shouldBe Deltakerstatus.TildeltIkkeStartet
    }

    @Test
    fun `arena - resten har ikke deltatt`() {
        Arenastatus.Type.entries.filter { it.status() == Deltakerstatus.IkkeDeltatt }.toSet() shouldBe
            setOf(
                Arenastatus.Type.AKTUELL,
                Arenastatus.Type.AVSLAG,
                Arenastatus.Type.FEILREGISTRERT,
                Arenastatus.Type.GJENNOMFORING_AVLYST,
                Arenastatus.Type.IKKE_AKTUELL,
                Arenastatus.Type.IKKE_MOTT,
                Arenastatus.Type.INFORMASJONSMOTE,
                Arenastatus.Type.TAKKET_NEI_TIL_TILBUD,
                Arenastatus.Type.VENTELISTE,
            )
    }

    @Test
    fun `komet - hvem som er i gang eller gjennomført`() {
        Kometstatus.Type.entries.filter { it.status() == Deltakerstatus.DeltarEllerHarDeltatt }.toSet() shouldBe
            setOf(
                Kometstatus.Type.AVBRUTT,
                Kometstatus.Type.DELTAR,
                Kometstatus.Type.FULLFORT,
                Kometstatus.Type.HAR_SLUTTET,
            )
    }

    @Test
    fun `komet - hvem som har fått tildelt plass uten å ha startet`() {
        Kometstatus.Type.entries.filter { it.status() == Deltakerstatus.TildeltIkkeStartet }.toSet() shouldBe
            setOf(Kometstatus.Type.VENTER_PA_OPPSTART)
    }

    /**
     * KLADD er et utkast veileder ikke har sendt til bruker.
     * Den ble tidligere silt bort før mappingen, som kastet på den; nå bæres den og regnes som ikke deltatt.
     */
    @Test
    fun `komet - resten har ikke deltatt, inkludert kladd`() {
        Kometstatus.Type.entries.filter { it.status() == Deltakerstatus.IkkeDeltatt }.toSet() shouldBe
            setOf(
                Kometstatus.Type.AVBRUTT_UTKAST,
                Kometstatus.Type.FEILREGISTRERT,
                Kometstatus.Type.IKKE_AKTUELL,
                Kometstatus.Type.KLADD,
                Kometstatus.Type.PABEGYNT_REGISTRERING,
                Kometstatus.Type.SOKT_INN,
                Kometstatus.Type.UTKAST_TIL_PAMELDING,
                Kometstatus.Type.VENTELISTE,
                Kometstatus.Type.VURDERES,
            )
    }

    @Test
    fun `team tiltak - hvem som er i gang eller gjennomført`() {
        TeamTiltakstatus.Type.entries.filter { it.status() == Deltakerstatus.DeltarEllerHarDeltatt }.toSet() shouldBe
            setOf(
                TeamTiltakstatus.Type.AVBRUTT,
                TeamTiltakstatus.Type.AVSLUTTET,
                TeamTiltakstatus.Type.GJENNOMFORES,
            )
    }

    @Test
    fun `team tiltak - hvem som har fått tildelt plass uten å ha startet`() {
        TeamTiltakstatus.Type.entries.filter { it.status() == Deltakerstatus.TildeltIkkeStartet }.toSet() shouldBe
            setOf(TeamTiltakstatus.Type.KLAR_FOR_OPPSTART)
    }

    /**
     * Kafka-varianten skiller ANNULLERT i feilregistrert og ikke aktuell med et eget flagg, mens HTTP-API-et mangler flagget.
     * Skillet forsvinner her, siden begge uansett er IkkeDeltatt — asymmetrien mellom de to veiene inn slutter dermed å bety noe.
     */
    @Test
    fun `team tiltak - resten har ikke deltatt`() {
        TeamTiltakstatus.Type.entries.filter { it.status() == Deltakerstatus.IkkeDeltatt }.toSet() shouldBe
            setOf(
                TeamTiltakstatus.Type.ANNULLERT,
                TeamTiltakstatus.Type.MANGLER_GODKJENNING,
                TeamTiltakstatus.Type.PAABEGYNT,
            )
    }

    /**
     * `tiltakshistorikk` har døpt om Arenas koder, så enum-navnet vårt er ikke det Arena-folk kjenner igjen.
     * Denne tabellen er oversettelsen, og den er poenget med `kodeHosKilden`: uten den kan vi ikke snakke samme språk som dem som jobber i kilden.
     */
    @Test
    fun `arena - kodeHosKilden er Arenas egen kode, ikke kontraktens navn`() {
        Arenastatus.Type.entries.associate { it.name to Arenastatus.Kjent(it).kodeHosKilden } shouldBe
            mapOf(
                "AKTUELL" to "AKTUELL",
                "AVSLAG" to "AVSLAG",
                "DELTAKELSE_AVBRUTT" to "DELAVB",
                "FEILREGISTRERT" to "FEILREG",
                "FULLFORT" to "FULLF",
                "GJENNOMFORES" to "GJENN",
                "GJENNOMFORING_AVBRUTT" to "GJENN_AVB",
                "GJENNOMFORING_AVLYST" to "GJENN_AVL",
                "IKKE_AKTUELL" to "IKKAKTUELL",
                "IKKE_MOTT" to "IKKEM",
                "INFORMASJONSMOTE" to "INFOMOETE",
                "TAKKET_JA_TIL_TILBUD" to "JATAKK",
                "TAKKET_NEI_TIL_TILBUD" to "NEITAKK",
                "TILBUD" to "TILBUD",
                "VENTELISTE" to "VENTELISTE",
            )
    }

    /**
     * Team Tiltak skriver to av verdiene med æøå; kontrakten har strippet dem.
     * Kontrastparet viser skillet: `kodeIKontrakten` er den strippede formen på wiren, `kodeHosKilden` kildens egen.
     */
    @Test
    fun `team tiltak - kodeHosKilden beholder kildens æøå`() {
        TeamTiltakstatus.Kjent(TeamTiltakstatus.Type.GJENNOMFORES).kodeHosKilden shouldBe "GJENNOMFØRES"
        TeamTiltakstatus.Kjent(TeamTiltakstatus.Type.GJENNOMFORES).kodeIKontrakten shouldBe "GJENNOMFORES"
        TeamTiltakstatus.Kjent(TeamTiltakstatus.Type.PAABEGYNT).kodeHosKilden shouldBe "PÅBEGYNT"
        TeamTiltakstatus.Type.entries.filter { TeamTiltakstatus.Kjent(it).kodeHosKilden != it.name }.toSet() shouldBe
            setOf(TeamTiltakstatus.Type.GJENNOMFORES, TeamTiltakstatus.Type.PAABEGYNT)
    }

    /**
     * Hos Komet er kildens og kontraktens språk det samme, så begge feltene svarer likt.
     * Literalen pinner kontraktsnavnet: kildestatus-enumene følger kontrakten og kan ikke omdøpes fritt.
     */
    @Test
    fun `komet - kildens og kontraktens språk er det samme`() {
        val kjent = Kometstatus.Kjent(Kometstatus.Type.VENTER_PA_OPPSTART, årsak = null, opprettet = statusOpprettet)

        kjent.kodeIKontrakten shouldBe "VENTER_PA_OPPSTART"
        kjent.kodeHosKilden shouldBe "VENTER_PA_OPPSTART"
    }

    /**
     * Komet er den eneste kilden som oppgir en årsak, og settet følger **kontrakten**, ikke kildens egen enum.
     * Kilden har elleve verdier; kontrakten fjorten.
     * De tre ekstra er historiske verdier som fortsatt kan ligge i data, og som vi derfor må kunne ta imot.
     */
    @Test
    fun `komet - årsakssettet følger kontrakten, som har tre verdier kilden ikke lenger har`() {
        Kometstatus.Årsak.entries.size shouldBe 14

        setOf(
            Kometstatus.Årsak.FEILREGISTRERT,
            Kometstatus.Årsak.FERDIG,
            Kometstatus.Årsak.OPPFYLLER_IKKE_KRAVENE,
        ).all { it in Kometstatus.Årsak.entries } shouldBe true
    }

    /**
     * Årsaken påvirker ikke utledningen i dag, men den skal bæres uendret.
     * Se TODO-en i Kometstatus: «ikke møtt» er årsak hos Komet og status hos Arena, og fag har allerede avklart Arena-siden.
     */
    @Test
    fun `komet - årsaken bæres, men endrer ikke utledningen ennå`() {
        val medÅrsak = Kometstatus.Kjent(type = Kometstatus.Type.HAR_SLUTTET, årsak = Kometårsak.Kjent(Kometstatus.Årsak.IKKE_MOTT), opprettet = statusOpprettet)

        medÅrsak.årsak shouldBe Kometårsak.Kjent(Kometstatus.Årsak.IKKE_MOTT)
        medÅrsak.årsak?.kodeIKontrakten shouldBe "IKKE_MOTT"
        medÅrsak.deltakerstatus(fraOgMed = igår, påDato = idag) shouldBe Deltakerstatus.DeltarEllerHarDeltatt
    }

    /**
     * Antallet per kilde skal stemme med kildesystemenes egne sett.
     * Legger en kilde til en verdi, skal den inn her og få en vurdering — ikke forsvinne stille.
     */
    @Test
    fun `kildene har det antallet statuser vi kjenner`() {
        Arenastatus.Type.entries.size shouldBe 15
        Kometstatus.Type.entries.size shouldBe 14
        TeamTiltakstatus.Type.entries.size shouldBe 7
    }
}
