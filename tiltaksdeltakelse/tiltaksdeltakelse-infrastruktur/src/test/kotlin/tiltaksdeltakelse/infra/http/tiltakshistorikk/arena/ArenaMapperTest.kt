package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.arena

import arrow.core.left
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.tilknytningstittel
import no.nav.tiltakspenger.libs.common.virksomhetsnavn
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Arenastatus
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Arrangør
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Deltakelsesomfang
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.EksternDeltakelseId
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.GjennomføringId
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltaksdeltakelse
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakstype
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.TiltakstypeSomGirRett
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto.NorskIdentDto
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto.TiltakshistorikkV1Dto
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.felles.UgyldigKontraktsverdi
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.tiltaksdeltakelse
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class ArenaMapperTest {

    private val deltakelseId = UUID.fromString("0190c9a2-1111-7000-8000-000000000001")
    private val gjennomføringId = UUID.fromString("0190c9a2-2222-7000-8000-000000000002")

    private fun arenaDto(
        startDato: LocalDate? = LocalDate.of(2024, 1, 1),
        sluttDato: LocalDate? = LocalDate.of(2024, 6, 30),
        status: String = "GJENNOMFORES",
        tiltakskode: String = "INDOPPFAG",
        hovedenhetsnavn: String? = "Arrangør AS",
    ) = TiltakshistorikkV1Dto.ArenaDeltakelse(
        norskIdent = NorskIdentDto("12345678901"),
        startDato = startDato,
        sluttDato = sluttDato,
        id = deltakelseId,
        tittel = "Oppfølging hos Arrangør AS",
        arenaId = 142536,
        status = status,
        tiltakstype = TiltakshistorikkV1Dto.Tiltakstype(tiltakskode = tiltakskode, navn = "Oppfølging"),
        gjennomforing = TiltakshistorikkV1Dto.Gjennomforing(id = gjennomføringId, deltidsprosent = 50.0f),
        arrangor = TiltakshistorikkV1Dto.Arrangor(
            hovedenhet = hovedenhetsnavn?.let { TiltakshistorikkV1Dto.Virksomhet(navn = it) },
            underenhet = TiltakshistorikkV1Dto.Virksomhet(navn = "Arrangør AS avd Strandveien"),
        ),
        deltidsprosent = 100.0f,
        dagerPerUke = 5.0f,
    )

    @Test
    fun `arena-raden mappes felt for felt og bærer gjennomføringen`() {
        val resultat = arenaDto().tilTiltaksdeltakelse().getOrFail()

        resultat shouldBe tiltaksdeltakelse(
            id = EksternDeltakelseId("TA142536"),
            kildestatus = Arenastatus.Kjent(Arenastatus.Type.GJENNOMFORES),
            tiltakstype = Tiltakstype.SomGirRett("INDOPPFAG", TiltakstypeSomGirRett.OPPFØLGING),
            tiltakstypenavn = "Oppfølging",
            tittel = tilknytningstittel("Oppfølging hos Arrangør AS"),
            arrangør = Arrangør(
                hovedenhet = virksomhetsnavn("Arrangør AS"),
                underenhet = virksomhetsnavn("Arrangør AS avd Strandveien"),
            ),
            omfang = Deltakelsesomfang(
                deltakelsesprosent = 100.0f,
                dagerPerUke = 5.0f,
                deltidsprosentPåGjennomføring = 50.0f,
            ),
            fraOgMed = LocalDate.of(2024, 1, 1),
            tilOgMed = LocalDate.of(2024, 6, 30),
            gjennomføringId = GjennomføringId(gjennomføringId.toString()),
        )
        resultat.shouldBeInstanceOf<Tiltaksdeltakelse.GirRett.MedPeriode>()
    }

    @Test
    fun `arena-rad med ukjent kode flyter inn som ukjent tiltakstype`() {
        val resultat = arenaDto(tiltakskode = "HELT_NY_KODE", hovedenhetsnavn = null, startDato = null, sluttDato = null)
            .tilTiltaksdeltakelse()
            .getOrFail()

        resultat.shouldBeInstanceOf<Tiltaksdeltakelse.UkjentTiltakstype>()
        resultat.tiltakskodeFraKilden shouldBe "HELT_NY_KODE"
        resultat.arrangør shouldBe Arrangør(hovedenhet = null, underenhet = virksomhetsnavn("Arrangør AS avd Strandveien"))
    }

    @Test
    fun `datoer som ikke danner periode blir Ugyldig i stedet for å filtreres bort`() {
        val resultat = arenaDto(startDato = LocalDate.of(2024, 6, 30), sluttDato = LocalDate.of(2024, 1, 1))
            .tilTiltaksdeltakelse()
            .getOrFail()

        resultat.shouldBeInstanceOf<Tiltaksdeltakelse.Ugyldig>()
    }

    @Test
    fun `blank status eller kode feller hele raden som ugyldig kontraktsverdi`() {
        arenaDto(status = " ").tilTiltaksdeltakelse() shouldBe
            UgyldigKontraktsverdi("Blank deltakerstatus fra Arena kan ikke bæres som ukjent kildeverdi").left()
        arenaDto(tiltakskode = " ").tilTiltaksdeltakelse() shouldBe
            UgyldigKontraktsverdi("Blank tiltakskode fra Arena kan ikke bæres som ukjent kildeverdi").left()
    }
}
