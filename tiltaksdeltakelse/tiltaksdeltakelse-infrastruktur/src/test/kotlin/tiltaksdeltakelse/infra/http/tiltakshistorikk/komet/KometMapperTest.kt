package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.komet

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.FnrGenerator
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Kometstatus
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Kometårsak
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto.NorskIdentDto
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto.TiltakshistorikkV1Dto
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.felles.UgyldigKontraktsverdi
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class KometMapperTest {

    private val deltakelseId = UUID.fromString("0190c9a2-1111-7000-8000-000000000001")
    private val gjennomføringId = UUID.fromString("0190c9a2-2222-7000-8000-000000000002")
    private val opprettet = LocalDateTime.of(2026, 1, 15, 9, 30)
    private val fnr = FnrGenerator().generer().verdi

    private fun kometDto(
        status: String = "DELTAR",
        aarsak: String? = null,
        tiltakskode: String = "ARBEIDSFORBEREDENDE_TRENING",
        hovedenhetsnavn: String? = null,
    ) = TiltakshistorikkV1Dto.TeamKometDeltakelse(
        norskIdent = NorskIdentDto(fnr),
        startDato = LocalDate.of(2024, 3, 4),
        sluttDato = null,
        id = deltakelseId,
        tittel = "Arbeidsforberedende trening hos Arrangør AS",
        status = TiltakshistorikkV1Dto.TeamKometDeltakelse.Status(type = status, aarsak = aarsak, opprettetDato = opprettet),
        tiltakstype = TiltakshistorikkV1Dto.Tiltakstype(tiltakskode = tiltakskode, navn = "Arbeidsforberedende trening"),
        gjennomforing = TiltakshistorikkV1Dto.Gjennomforing(id = gjennomføringId, deltidsprosent = null),
        arrangor = TiltakshistorikkV1Dto.Arrangor(
            hovedenhet = hovedenhetsnavn?.let { TiltakshistorikkV1Dto.Virksomhet(navn = it) },
            underenhet = TiltakshistorikkV1Dto.Virksomhet(navn = null),
        ),
        deltidsprosent = 60.0f,
        dagerPerUke = null,
    )

    @Test
    fun `kladd flyter inn i stedet for å filtreres bort`() {
        val resultat = kometDto(status = "KLADD").tilTiltaksdeltakelse().getOrFail()

        resultat.kildestatus shouldBe Kometstatus.Kjent(Kometstatus.Type.KLADD, null, opprettet)
    }

    @Test
    fun `komet-årsaken følger med, kjent eller ukjent`() {
        kometDto(status = "HAR_SLUTTET", aarsak = "FATT_JOBB").tilTiltaksdeltakelse().getOrFail().kildestatus shouldBe
            Kometstatus.Kjent(Kometstatus.Type.HAR_SLUTTET, Kometårsak.Kjent(Kometstatus.Årsak.FATT_JOBB), opprettet)

        kometDto(status = "HAR_SLUTTET", aarsak = "HELT_NY_AARSAK", hovedenhetsnavn = "Arrangør AS").tilTiltaksdeltakelse().getOrFail().kildestatus shouldBe
            Kometstatus.Kjent(Kometstatus.Type.HAR_SLUTTET, Kometårsak.Ukjent("HELT_NY_AARSAK"), opprettet)
    }

    @Test
    fun `blank status, kode eller årsak feller hele raden som ugyldig kontraktsverdi`() {
        kometDto(status = " ").tilTiltaksdeltakelse() shouldBe
            UgyldigKontraktsverdi("Blank deltakerstatus fra Komet kan ikke bæres som ukjent kildeverdi").left()
        kometDto(tiltakskode = " ").tilTiltaksdeltakelse() shouldBe
            UgyldigKontraktsverdi("Blank tiltakskode fra Komet kan ikke bæres som ukjent kildeverdi").left()
        kometDto(aarsak = " ").tilTiltaksdeltakelse() shouldBe
            UgyldigKontraktsverdi("Blank årsak fra Komet kan ikke bæres som ukjent kildeverdi").left()
    }
}
