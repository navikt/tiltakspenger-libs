package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Paritetstester mot dagens oppførsel.
 *
 * Fasit er dagens kjede: kildens `toDeltakerStatusDTO()` til den flate 11-verdis statusen, og derfra `rettTilÅSøke` for søknadsguarden og `deltarEllerHarDeltatt()` for innvilgelse.
 * Så lenge disse står, kan ikke omskrivingen endre hvem som får søke eller bli innvilget.
 */
internal class KildestatusTest {
    private val idag = LocalDate.of(2026, 7, 30)
    private val igår = idag.minusDays(1)
    private val imorgen = idag.plusDays(1)

    private fun Kildestatus.status(fraOgMed: LocalDate? = igår) = deltakerstatus(fraOgMed = fraOgMed, påDato = idag)

    @Test
    fun `kilden utledes fra statusen`() {
        Kildestatus.Arena.GJENNOMFORES.kilde shouldBe Tiltakskilde.Arena
        Kildestatus.Komet.DELTAR.kilde shouldBe Tiltakskilde.Komet
        Kildestatus.TeamTiltak.GJENNOMFORES.kilde shouldBe Tiltakskilde.TeamTiltak
    }

    @Test
    fun `arena - hvem som er i gang eller gjennomført`() {
        Kildestatus.Arena.entries.filter { it.status() == Deltakerstatus.DeltarEllerHarDeltatt }.toSet() shouldBe
            setOf(
                Kildestatus.Arena.DELTAKELSE_AVBRUTT,
                Kildestatus.Arena.GJENNOMFORES,
                Kildestatus.Arena.GJENNOMFORING_AVBRUTT,
                Kildestatus.Arena.FULLFORT,
                // De to under er videreført fra dagens mapping og skal avklares med fag, se TODO i Kildestatus.
                Kildestatus.Arena.IKKE_MOTT,
                Kildestatus.Arena.TAKKET_JA_TIL_TILBUD,
            )
    }

    @Test
    fun `arena - hvem som har fått tildelt plass uten å ha startet`() {
        Kildestatus.Arena.entries.filter { it.status() == Deltakerstatus.TildeltIkkeStartet }.toSet() shouldBe
            setOf(Kildestatus.Arena.TILBUD)
    }

    /**
     * Arena skiller ikke mellom «tildelt» og «deltar» — `GJENNOMFORES` dekker begge, og startdatoen er det eneste skillet.
     * Dagens kode leser klokka her; vi tar datoen som parameter, men svarer likt.
     */
    @Test
    fun `arena - GJENNOMFORES avhenger av startdato`() {
        Kildestatus.Arena.GJENNOMFORES.status(fraOgMed = igår) shouldBe Deltakerstatus.DeltarEllerHarDeltatt
        Kildestatus.Arena.GJENNOMFORES.status(fraOgMed = idag) shouldBe Deltakerstatus.DeltarEllerHarDeltatt
        Kildestatus.Arena.GJENNOMFORES.status(fraOgMed = imorgen) shouldBe Deltakerstatus.TildeltIkkeStartet
        Kildestatus.Arena.GJENNOMFORES.status(fraOgMed = null) shouldBe Deltakerstatus.TildeltIkkeStartet
    }

    @Test
    fun `arena - resten gir ingen plass`() {
        Kildestatus.Arena.entries.filter { it.status() == Deltakerstatus.IngenPlass }.toSet() shouldBe
            setOf(
                Kildestatus.Arena.AKTUELL,
                Kildestatus.Arena.AVSLAG,
                Kildestatus.Arena.FEILREGISTRERT,
                Kildestatus.Arena.GJENNOMFORING_AVLYST,
                Kildestatus.Arena.IKKE_AKTUELL,
                Kildestatus.Arena.INFORMASJONSMOTE,
                Kildestatus.Arena.TAKKET_NEI_TIL_TILBUD,
                Kildestatus.Arena.VENTELISTE,
            )
    }

    @Test
    fun `komet - hvem som er i gang eller gjennomført`() {
        Kildestatus.Komet.entries.filter { it.status() == Deltakerstatus.DeltarEllerHarDeltatt }.toSet() shouldBe
            setOf(
                Kildestatus.Komet.AVBRUTT,
                Kildestatus.Komet.DELTAR,
                Kildestatus.Komet.FULLFORT,
                Kildestatus.Komet.HAR_SLUTTET,
            )
    }

    @Test
    fun `komet - hvem som har fått tildelt plass uten å ha startet`() {
        Kildestatus.Komet.entries.filter { it.status() == Deltakerstatus.TildeltIkkeStartet }.toSet() shouldBe
            setOf(Kildestatus.Komet.VENTER_PA_OPPSTART)
    }

    /**
     * KLADD er et utkast veileder ikke har sendt til bruker.
     * Den ble tidligere silt bort før mappingen, som kastet på den; nå bæres den og gir ingen plass.
     */
    @Test
    fun `komet - resten gir ingen plass, inkludert kladd`() {
        Kildestatus.Komet.entries.filter { it.status() == Deltakerstatus.IngenPlass }.toSet() shouldBe
            setOf(
                Kildestatus.Komet.AVBRUTT_UTKAST,
                Kildestatus.Komet.FEILREGISTRERT,
                Kildestatus.Komet.IKKE_AKTUELL,
                Kildestatus.Komet.KLADD,
                Kildestatus.Komet.PABEGYNT_REGISTRERING,
                Kildestatus.Komet.SOKT_INN,
                Kildestatus.Komet.UTKAST_TIL_PAMELDING,
                Kildestatus.Komet.VENTELISTE,
                Kildestatus.Komet.VURDERES,
            )
    }

    @Test
    fun `team tiltak - hvem som er i gang eller gjennomført`() {
        Kildestatus.TeamTiltak.entries.filter { it.status() == Deltakerstatus.DeltarEllerHarDeltatt }.toSet() shouldBe
            setOf(
                Kildestatus.TeamTiltak.AVBRUTT,
                Kildestatus.TeamTiltak.AVSLUTTET,
                Kildestatus.TeamTiltak.GJENNOMFORES,
            )
    }

    @Test
    fun `team tiltak - hvem som har fått tildelt plass uten å ha startet`() {
        Kildestatus.TeamTiltak.entries.filter { it.status() == Deltakerstatus.TildeltIkkeStartet }.toSet() shouldBe
            setOf(Kildestatus.TeamTiltak.KLAR_FOR_OPPSTART)
    }

    /**
     * Kafka-varianten skiller ANNULLERT i feilregistrert og ikke aktuell med et eget flagg, mens HTTP-API-et mangler flagget.
     * Skillet forsvinner her, siden begge uansett gir ingen plass — asymmetrien mellom de to veiene inn slutter dermed å bety noe.
     */
    @Test
    fun `team tiltak - resten gir ingen plass`() {
        Kildestatus.TeamTiltak.entries.filter { it.status() == Deltakerstatus.IngenPlass }.toSet() shouldBe
            setOf(
                Kildestatus.TeamTiltak.ANNULLERT,
                Kildestatus.TeamTiltak.MANGLER_GODKJENNING,
                Kildestatus.TeamTiltak.PAABEGYNT,
            )
    }

    /**
     * Antallet per kilde skal stemme med kildesystemenes egne sett.
     * Legger en kilde til en verdi, skal den inn her og få en vurdering — ikke forsvinne stille.
     */
    @Test
    fun `kildene har det antallet statuser vi kjenner`() {
        Kildestatus.Arena.entries.size shouldBe 15
        Kildestatus.Komet.entries.size shouldBe 14
        Kildestatus.TeamTiltak.entries.size shouldBe 7
    }
}
