package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.teamtiltak

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.tilknytningstittel
import no.nav.tiltakspenger.libs.common.virksomhetsnavn
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Arrangør
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Deltakelsesomfang
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.EksternDeltakelseId
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.TeamTiltakstatus
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakstype
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.TiltakstypeSomGirRett
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto.NorskIdentDto
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto.TiltakshistorikkV1Dto
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.felles.UgyldigKontraktsverdi
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.tiltaksdeltakelse
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class TeamTiltakMapperTest {

    private val deltakelseId = UUID.fromString("0190c9a2-4444-7000-8000-000000000004")

    private fun teamTiltakDto(
        status: String = "GJENNOMFORES",
        tiltakskode: String = "ARBEIDSTRENING",
    ) = TiltakshistorikkV1Dto.TeamTiltakAvtale(
        norskIdent = NorskIdentDto("10987654321"),
        startDato = LocalDate.of(2025, 1, 1),
        sluttDato = null,
        id = deltakelseId,
        tittel = "Arbeidstrening hos Butikken AS",
        tiltakstype = TiltakshistorikkV1Dto.Tiltakstype(tiltakskode = tiltakskode, navn = "Arbeidstrening"),
        status = status,
        stillingsprosent = 50.0f,
        dagerPerUke = 4.0f,
        arbeidsgiver = TiltakshistorikkV1Dto.Virksomhet(navn = "Butikken AS"),
    )

    @Test
    fun `team tiltak-avtalen får arbeidsgiver som underenhet og ingen gjennomføring`() {
        val resultat = teamTiltakDto().tilTiltaksdeltakelse().getOrFail()

        resultat shouldBe tiltaksdeltakelse(
            id = EksternDeltakelseId(deltakelseId.toString()),
            kildestatus = TeamTiltakstatus.Kjent(TeamTiltakstatus.Type.GJENNOMFORES),
            tiltakstype = Tiltakstype.SomGirRett("ARBEIDSTRENING", TiltakstypeSomGirRett.ARBEIDSTRENING),
            tiltakstypenavn = "Arbeidstrening",
            tittel = tilknytningstittel("Arbeidstrening hos Butikken AS"),
            arrangør = Arrangør(hovedenhet = null, underenhet = virksomhetsnavn("Butikken AS")),
            omfang = Deltakelsesomfang(
                deltakelsesprosent = 50.0f,
                dagerPerUke = 4.0f,
                deltidsprosentPåGjennomføring = null,
            ),
            fraOgMed = LocalDate.of(2025, 1, 1),
            tilOgMed = null,
            gjennomføringId = null,
        )
    }

    @Test
    fun `blank status eller kode feller hele raden som ugyldig kontraktsverdi`() {
        teamTiltakDto(status = " ").tilTiltaksdeltakelse() shouldBe
            UgyldigKontraktsverdi("Blank avtalestatus fra Team Tiltak kan ikke bæres som ukjent kildeverdi").left()
        teamTiltakDto(tiltakskode = " ").tilTiltaksdeltakelse() shouldBe
            UgyldigKontraktsverdi("Blank tiltakskode fra Team Tiltak kan ikke bæres som ukjent kildeverdi").left()
    }
}
