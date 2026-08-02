package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.tiltakspenger.libs.common.Tilknytningstittel
import no.nav.tiltakspenger.libs.common.Virksomhetsnavn
import no.nav.tiltakspenger.libs.periode.Periode
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

internal class TiltaksdeltakelseTest {
    private val start = LocalDate.of(2026, 3, 2)
    private val slutt = LocalDate.of(2026, 6, 30)
    private val statusOpprettet = LocalDateTime.of(2026, 2, 27, 12, 0)

    private fun deltakelse(
        tiltakstype: Tiltakstype = Tiltakstype.SomGirRett(tiltakskodeFraKilden = "INDOPPFAG", tiltakstype = TiltakstypeSomGirRett.OPPFØLGING),
        kildestatus: Kildestatus = Kometstatus.Kjent(Kometstatus.Type.DELTAR, årsak = null, opprettet = statusOpprettet),
        fraOgMed: LocalDate? = start,
        tilOgMed: LocalDate? = slutt,
        gjennomføringId: GjennomføringId? = GjennomføringId("6f3b1f52-9a1e-4a34-8f9a-1c2d3e4f5a6b"),
    ) = tiltaksdeltakelse(
        id = EksternDeltakelseId("TA1234567"),
        kildestatus = kildestatus,
        tiltakstype = tiltakstype,
        tiltakstypenavn = "Oppfølging",
        tittel = Tilknytningstittel("Oppfølging hos Arrangør AS"),
        arrangør = Arrangør(hovedenhet = Virksomhetsnavn("Arrangør AS"), underenhet = null),
        omfang = Deltakelsesomfang(deltakelsesprosent = 60f, dagerPerUke = 3f, deltidsprosentPåGjennomføring = null),
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed,
        gjennomføringId = gjennomføringId,
    )

    @Test
    fun `gir rett med begge datoer blir MedPeriode, og perioden er total`() {
        val resultat = deltakelse().shouldBeInstanceOf<Tiltaksdeltakelse.GirRett.MedPeriode>()

        resultat.periode shouldBe Periode(start, slutt)
        resultat.fraOgMed shouldBe start
        resultat.tilOgMed shouldBe slutt
        resultat.tiltakstype shouldBe TiltakstypeSomGirRett.OPPFØLGING
    }

    @Test
    fun `gir rett uten sluttdato blir UtenPeriode`() {
        val resultat = deltakelse(tilOgMed = null).shouldBeInstanceOf<Tiltaksdeltakelse.GirRett.UtenPeriode>()

        resultat.fraOgMed shouldBe start
        resultat.tilOgMed shouldBe null
    }

    @Test
    fun `gir rett uten startdato blir UtenPeriode`() {
        deltakelse(fraOgMed = null).shouldBeInstanceOf<Tiltaksdeltakelse.GirRett.UtenPeriode>().fraOgMed shouldBe null
    }

    @Test
    fun `gir rett uten datoer i det hele tatt blir UtenPeriode`() {
        val resultat = deltakelse(fraOgMed = null, tilOgMed = null)
            .shouldBeInstanceOf<Tiltaksdeltakelse.GirRett.UtenPeriode>()

        resultat.fraOgMed shouldBe null
        resultat.tilOgMed shouldBe null
    }

    /**
     * Kildens egen kode overlever også når vi har klart å tolke den til en enum.
     * Det er nettopp for tiltakene som gir rett at koden trengs: det er dem noen ringer og spør om, og da er `INDOPPFAG` det felles språket mot veileder og Arena — ikke vår `OPPFØLGING`.
     */
    @Test
    fun `tiltakskoden fra kilden bevares på alle variantene`() {
        deltakelse().tiltakskodeFraKilden shouldBe "INDOPPFAG"
        deltakelse(tiltakstype = Tiltakstype.SomIkkeGirRett("MENTOR")).tiltakskodeFraKilden shouldBe "MENTOR"
        deltakelse(tiltakstype = Tiltakstype.Ukjent("NOE_HELT_NYTT")).tiltakskodeFraKilden shouldBe "NOE_HELT_NYTT"
        deltakelse(fraOgMed = slutt, tilOgMed = start).tiltakskodeFraKilden shouldBe "INDOPPFAG"
        deltakelse(tilOgMed = null).tiltakskodeFraKilden shouldBe "INDOPPFAG"
    }

    @Test
    fun `kjent type uten rett bærer koden som streng`() {
        val resultat = deltakelse(tiltakstype = Tiltakstype.SomIkkeGirRett("MENTOR"))
            .shouldBeInstanceOf<Tiltaksdeltakelse.GirIkkeRett>()

        resultat.tiltakskodeFraKilden shouldBe "MENTOR"
    }

    /**
     * Før tok en ukjent Arena-kode ned hele oppslaget, fordi mappingen gjorde `valueOf` på fritekst.
     */
    @Test
    fun `ukjent type flyter inn i stedet for å velte oppslaget`() {
        val resultat = deltakelse(tiltakstype = Tiltakstype.Ukjent("NOE_HELT_NYTT"))
            .shouldBeInstanceOf<Tiltaksdeltakelse.UkjentTiltakstype>()

        resultat.tiltakskodeFraKilden shouldBe "NOE_HELT_NYTT"
    }

    /**
     * Disse ble tidligere silt bort i stillhet, så ingen oppdaget at kilden hadde korrupte rader.
     */
    @Test
    fun `sluttdato før startdato blir Ugyldig`() {
        val resultat = deltakelse(fraOgMed = slutt, tilOgMed = start)
            .shouldBeInstanceOf<Tiltaksdeltakelse.Ugyldig>()

        resultat.grunn shouldBe Ugyldiggrunn.SluttFørStart
        resultat.fraOgMed shouldBe slutt
        resultat.tilOgMed shouldBe start
    }

    /**
     * Ugyldige datoer slår ut først, uansett hvor fin tiltakstypen er.
     * Klassifiseringen bæres likevel: en korrupt rad som ville gitt rett er den mest handlingsutløsende å varsle på.
     */
    @Test
    fun `ugyldige datoer vinner over tiltakstypen, men klassifiseringen bæres`() {
        deltakelse(
            tiltakstype = Tiltakstype.SomGirRett(tiltakskodeFraKilden = "JOBBK", tiltakstype = TiltakstypeSomGirRett.JOBBKLUBB),
            fraOgMed = slutt,
            tilOgMed = start,
        ).shouldBeInstanceOf<Tiltaksdeltakelse.Ugyldig>()
            .tiltakstype shouldBe Tiltakstype.SomGirRett(tiltakskodeFraKilden = "JOBBK", tiltakstype = TiltakstypeSomGirRett.JOBBKLUBB)

        deltakelse(
            tiltakstype = Tiltakstype.Ukjent("NOE_HELT_NYTT"),
            fraOgMed = slutt,
            tilOgMed = start,
        ).shouldBeInstanceOf<Tiltaksdeltakelse.Ugyldig>()
            .tiltakstype shouldBe Tiltakstype.Ukjent("NOE_HELT_NYTT")
    }

    @Test
    fun `samme fra- og til-dato er en gyldig periode på én dag`() {
        deltakelse(fraOgMed = start, tilOgMed = start)
            .shouldBeInstanceOf<Tiltaksdeltakelse.GirRett.MedPeriode>()
            .periode shouldBe Periode(start, start)
    }

    /**
     * `LocalDate.MAX` som start og `LocalDate.MIN` som slutt er tekniske yttergrenser `Periode` ikke kan bære.
     * Uten denne ruten ville fabrikken kastet, stikk i strid med totalitetsløftet sitt.
     */
    @Test
    fun `datoer på tekniske yttergrenser blir Ugyldig i stedet for å velte fabrikken`() {
        deltakelse(fraOgMed = LocalDate.MAX, tilOgMed = LocalDate.MAX)
            .shouldBeInstanceOf<Tiltaksdeltakelse.Ugyldig>()
            .grunn shouldBe Ugyldiggrunn.DatoPåYttergrense

        deltakelse(fraOgMed = LocalDate.MIN, tilOgMed = LocalDate.MIN)
            .shouldBeInstanceOf<Tiltaksdeltakelse.Ugyldig>()
            .grunn shouldBe Ugyldiggrunn.DatoPåYttergrense
    }

    @Test
    fun `periodeFraKilden er null når datoene ligger på tekniske yttergrenser`() {
        deltakelse(fraOgMed = LocalDate.MAX, tilOgMed = LocalDate.MAX).periodeFraKilden shouldBe null
        deltakelse(fraOgMed = LocalDate.MIN, tilOgMed = LocalDate.MIN).periodeFraKilden shouldBe null
    }

    /**
     * Fabrikken er ikke den eneste veien inn, så variantene håndhever sine egne påstander.
     */
    @Test
    fun `UtenPeriode kan ikke konstrueres med begge datoene på plass`() {
        shouldThrow<IllegalArgumentException> { utenPeriode(fraOgMed = start, tilOgMed = slutt) }
    }

    @Test
    fun `Ugyldig krever at grunnen stemmer med datoene`() {
        shouldThrow<IllegalArgumentException> { ugyldig(fraOgMed = start, tilOgMed = slutt, grunn = Ugyldiggrunn.SluttFørStart) }
        shouldThrow<IllegalArgumentException> { ugyldig(fraOgMed = start, tilOgMed = slutt, grunn = Ugyldiggrunn.DatoPåYttergrense) }
    }

    private fun utenPeriode(fraOgMed: LocalDate?, tilOgMed: LocalDate?) = Tiltaksdeltakelse.GirRett.UtenPeriode(
        id = EksternDeltakelseId("TA1234567"),
        kildestatus = Kometstatus.Kjent(Kometstatus.Type.DELTAR, årsak = null, opprettet = statusOpprettet),
        tiltakstype = TiltakstypeSomGirRett.OPPFØLGING,
        tiltakstypenavn = "Oppfølging",
        tiltakskodeFraKilden = "INDOPPFAG",
        tittel = Tilknytningstittel("Oppfølging hos Arrangør AS"),
        arrangør = Arrangør(hovedenhet = Virksomhetsnavn("Arrangør AS"), underenhet = null),
        omfang = Deltakelsesomfang(deltakelsesprosent = 60f, dagerPerUke = 3f, deltidsprosentPåGjennomføring = null),
        gjennomføringId = null,
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed,
    )

    private fun ugyldig(fraOgMed: LocalDate, tilOgMed: LocalDate, grunn: Ugyldiggrunn) = Tiltaksdeltakelse.Ugyldig(
        id = EksternDeltakelseId("TA1234567"),
        kildestatus = Kometstatus.Kjent(Kometstatus.Type.DELTAR, årsak = null, opprettet = statusOpprettet),
        tiltakstypenavn = "Oppfølging",
        tiltakstype = Tiltakstype.SomGirRett(tiltakskodeFraKilden = "INDOPPFAG", tiltakstype = TiltakstypeSomGirRett.OPPFØLGING),
        tittel = Tilknytningstittel("Oppfølging hos Arrangør AS"),
        arrangør = Arrangør(hovedenhet = Virksomhetsnavn("Arrangør AS"), underenhet = null),
        omfang = Deltakelsesomfang(deltakelsesprosent = 60f, dagerPerUke = 3f, deltidsprosentPåGjennomføring = null),
        gjennomføringId = null,
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed,
        grunn = grunn,
    )

    @Test
    fun `kilden utledes fra kildestatusen`() {
        deltakelse(kildestatus = Arenastatus.Kjent(Arenastatus.Type.GJENNOMFORES)).kilde shouldBe Tiltakskilde.Arena
        deltakelse(kildestatus = Kometstatus.Kjent(Kometstatus.Type.DELTAR, årsak = null, opprettet = statusOpprettet)).kilde shouldBe Tiltakskilde.Komet
        deltakelse(kildestatus = TeamTiltakstatus.Kjent(TeamTiltakstatus.Type.GJENNOMFORES)).kilde shouldBe Tiltakskilde.TeamTiltak
    }

    /**
     * Tolkningen finnes bare på kjente kildestatuser, så kallere må narrowe først.
     * Det er en brukbarhetsforgrening (F-unntaket), ikke et utfall: en ukjent kode kan ikke tolkes.
     */
    @Test
    fun `tolkningen krever kjent kildestatus, og tar deltakelsens startdato`() {
        val deltakelse = deltakelse(kildestatus = Arenastatus.Kjent(Arenastatus.Type.GJENNOMFORES))

        val kjent = deltakelse.kildestatus.shouldBeInstanceOf<Kildestatus.Kjent>()

        kjent.deltakerstatus(fraOgMed = deltakelse.fraOgMed, påDato = slutt) shouldBe Deltakerstatus.DeltarEllerHarDeltatt
        kjent.deltakerstatus(fraOgMed = deltakelse.fraOgMed, påDato = start.minusDays(1)) shouldBe Deltakerstatus.TildeltIkkeStartet
    }

    /**
     * En ukjent kildestatus påvirker ikke variantvalget — kvalitetsaksene er uavhengige.
     * Deltakelsen flyter inn, og blokkeringen skjer der tolkningen trengs, ikke ved henting.
     */
    @Test
    fun `ukjent kildestatus flyter inn uten å endre varianten`() {
        val resultat = deltakelse(kildestatus = Arenastatus.Ukjent("NY_ARENA_KODE"))
            .shouldBeInstanceOf<Tiltaksdeltakelse.GirRett.MedPeriode>()

        resultat.kildestatus shouldBe Arenastatus.Ukjent("NY_ARENA_KODE")
    }

    /**
     * Virker på alle varianter, slik at kallere slipper å narrowe først.
     */
    @Test
    fun `periodeFraKilden gir perioden når datoene henger sammen`() {
        deltakelse().periodeFraKilden shouldBe Periode(start, slutt)
        deltakelse(tiltakstype = Tiltakstype.SomIkkeGirRett("MENTOR")).periodeFraKilden shouldBe Periode(start, slutt)
    }

    @Test
    fun `periodeFraKilden er null når en dato mangler eller datoene ikke henger sammen`() {
        deltakelse(tilOgMed = null).periodeFraKilden shouldBe null
        deltakelse(fraOgMed = null).periodeFraKilden shouldBe null
        deltakelse(fraOgMed = null, tilOgMed = null).periodeFraKilden shouldBe null
        deltakelse(fraOgMed = slutt, tilOgMed = start).periodeFraKilden shouldBe null
    }

    /**
     * Tittelen inneholder arrangørnavnet og er derfor stedsinformasjon.
     * Maskeringen arves fra typen, så deltakelsen trenger ingen egen `toString()`.
     */
    @Test
    fun `toString lekker hverken tittel eller arrangørnavn`() {
        val tekst = deltakelse().toString()

        tekst.contains("Arrangør AS") shouldBe false
        tekst.contains("*****") shouldBe true
    }
}
