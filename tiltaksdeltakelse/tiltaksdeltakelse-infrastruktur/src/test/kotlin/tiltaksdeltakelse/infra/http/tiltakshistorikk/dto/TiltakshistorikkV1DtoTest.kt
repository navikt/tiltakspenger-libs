package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.FnrGenerator
import no.nav.tiltakspenger.libs.json.deserialize
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class TiltakshistorikkV1DtoTest {
    private val fnr = FnrGenerator().generer().verdi
    private val historiskFnr = FnrGenerator(start = 1).generer().verdi

    /**
     * JSON-en er pinnet mot kontraktens wire-format, inkludert feltene vi bevisst utelater fra kopien (`organisasjonsnummer`, `gjennomforing.navn`).
     * At de står i JSON-en og ikke i forventningsobjektet beviser at utelatelsene tolereres i stedet for å velte deserialiseringen.
     */
    @Test
    fun `deserialiserer kontraktens tre deltakelsesformer felt for felt`() {
        val json = """
            {
              "historikk": [
                {
                  "type": "ArenaDeltakelse",
                  "norskIdent": "$fnr",
                  "startDato": "2024-01-01",
                  "sluttDato": "2024-06-30",
                  "id": "019018e5-6461-74a0-9d66-70d0bf3d0b8b",
                  "tittel": "Oppfølging hos Arrangør AS",
                  "arenaId": 142536,
                  "status": "GJENNOMFORES",
                  "tiltakstype": { "tiltakskode": "INDOPPFAG", "navn": "Oppfølging" },
                  "gjennomforing": { "id": "0190c9a2-1111-7000-8000-000000000001", "navn": "Oppfølging 2024", "deltidsprosent": 50.0 },
                  "arrangor": {
                    "hovedenhet": { "organisasjonsnummer": "987654321", "navn": "Arrangør AS" },
                    "underenhet": { "organisasjonsnummer": "912345678", "navn": "Arrangør AS avd Strandveien" }
                  },
                  "deltidsprosent": 100.0,
                  "dagerPerUke": 5.0
                },
                {
                  "type": "TeamKometDeltakelse",
                  "norskIdent": "$fnr",
                  "startDato": "2024-03-04",
                  "sluttDato": null,
                  "id": "0190c9a2-2222-7000-8000-000000000002",
                  "tittel": "Arbeidsforberedende trening hos Arrangør AS",
                  "status": { "type": "DELTAR", "aarsak": null, "opprettetDato": "2024-03-01T09:30:00" },
                  "tiltakstype": { "tiltakskode": "ARBEIDSFORBEREDENDE_TRENING", "navn": "Arbeidsforberedende trening" },
                  "gjennomforing": { "id": "0190c9a2-3333-7000-8000-000000000003", "navn": null, "deltidsprosent": null },
                  "arrangor": {
                    "hovedenhet": null,
                    "underenhet": { "organisasjonsnummer": "912345678", "navn": null }
                  },
                  "deltidsprosent": 60.0,
                  "dagerPerUke": null
                },
                {
                  "type": "TeamTiltakAvtale",
                  "norskIdent": "$historiskFnr",
                  "startDato": "2025-01-01",
                  "sluttDato": null,
                  "id": "0190c9a2-4444-7000-8000-000000000004",
                  "tittel": "Arbeidstrening hos Butikken AS",
                  "tiltakstype": { "tiltakskode": "ARBEIDSTRENING", "navn": "Arbeidstrening" },
                  "status": "GJENNOMFORES",
                  "stillingsprosent": 50.0,
                  "dagerPerUke": 4.0,
                  "arbeidsgiver": { "organisasjonsnummer": "999888777", "navn": "Butikken AS" }
                }
              ]
            }
        """.trimIndent()

        deserialize<TiltakshistorikkV1Response>(json) shouldBe TiltakshistorikkV1Response(
            historikk = listOf(
                TiltakshistorikkV1Dto.ArenaDeltakelse(
                    norskIdent = NorskIdentDto(fnr),
                    startDato = LocalDate.of(2024, 1, 1),
                    sluttDato = LocalDate.of(2024, 6, 30),
                    id = UUID.fromString("019018e5-6461-74a0-9d66-70d0bf3d0b8b"),
                    tittel = "Oppfølging hos Arrangør AS",
                    arenaId = 142536,
                    status = "GJENNOMFORES",
                    tiltakstype = TiltakshistorikkV1Dto.Tiltakstype(tiltakskode = "INDOPPFAG", navn = "Oppfølging"),
                    gjennomforing = TiltakshistorikkV1Dto.Gjennomforing(
                        id = UUID.fromString("0190c9a2-1111-7000-8000-000000000001"),
                        deltidsprosent = 50.0f,
                    ),
                    arrangor = TiltakshistorikkV1Dto.Arrangor(
                        hovedenhet = TiltakshistorikkV1Dto.Virksomhet(navn = "Arrangør AS"),
                        underenhet = TiltakshistorikkV1Dto.Virksomhet(navn = "Arrangør AS avd Strandveien"),
                    ),
                    deltidsprosent = 100.0f,
                    dagerPerUke = 5.0f,
                ),
                TiltakshistorikkV1Dto.TeamKometDeltakelse(
                    norskIdent = NorskIdentDto(fnr),
                    startDato = LocalDate.of(2024, 3, 4),
                    sluttDato = null,
                    id = UUID.fromString("0190c9a2-2222-7000-8000-000000000002"),
                    tittel = "Arbeidsforberedende trening hos Arrangør AS",
                    status = TiltakshistorikkV1Dto.TeamKometDeltakelse.Status(
                        type = "DELTAR",
                        aarsak = null,
                        opprettetDato = LocalDateTime.of(2024, 3, 1, 9, 30, 0),
                    ),
                    tiltakstype = TiltakshistorikkV1Dto.Tiltakstype(
                        tiltakskode = "ARBEIDSFORBEREDENDE_TRENING",
                        navn = "Arbeidsforberedende trening",
                    ),
                    gjennomforing = TiltakshistorikkV1Dto.Gjennomforing(
                        id = UUID.fromString("0190c9a2-3333-7000-8000-000000000003"),
                        deltidsprosent = null,
                    ),
                    arrangor = TiltakshistorikkV1Dto.Arrangor(
                        hovedenhet = null,
                        underenhet = TiltakshistorikkV1Dto.Virksomhet(navn = null),
                    ),
                    deltidsprosent = 60.0f,
                    dagerPerUke = null,
                ),
                TiltakshistorikkV1Dto.TeamTiltakAvtale(
                    norskIdent = NorskIdentDto(historiskFnr),
                    startDato = LocalDate.of(2025, 1, 1),
                    sluttDato = null,
                    id = UUID.fromString("0190c9a2-4444-7000-8000-000000000004"),
                    tittel = "Arbeidstrening hos Butikken AS",
                    tiltakstype = TiltakshistorikkV1Dto.Tiltakstype(tiltakskode = "ARBEIDSTRENING", navn = "Arbeidstrening"),
                    status = "GJENNOMFORES",
                    stillingsprosent = 50.0f,
                    dagerPerUke = 4.0f,
                    arbeidsgiver = TiltakshistorikkV1Dto.Virksomhet(navn = "Butikken AS"),
                ),
            ),
        )
    }

    @Test
    fun `ukjente statuser, koder, årsaker og felter flyter gjennom uten å velte`() {
        val json = """
            {
              "historikk": [
                {
                  "type": "TeamKometDeltakelse",
                  "norskIdent": "$fnr",
                  "startDato": null,
                  "sluttDato": null,
                  "id": "0190c9a2-5555-7000-8000-000000000005",
                  "tittel": "Nytt tiltak hos Arrangør AS",
                  "status": { "type": "HELT_NY_STATUS", "aarsak": "HELT_NY_AARSAK", "opprettetDato": "2026-01-15T12:00:00" },
                  "tiltakstype": { "tiltakskode": "HELT_NY_TILTAKSKODE", "navn": "Nytt tiltak" },
                  "gjennomforing": { "id": "0190c9a2-6666-7000-8000-000000000006", "deltidsprosent": null },
                  "arrangor": { "hovedenhet": null, "underenhet": { "navn": null } },
                  "deltidsprosent": null,
                  "dagerPerUke": null,
                  "nyttFeltViIkkeKjenner": true
                }
              ]
            }
        """.trimIndent()

        val respons = deserialize<TiltakshistorikkV1Response>(json)

        val deltakelse = respons.historikk.single() as TiltakshistorikkV1Dto.TeamKometDeltakelse
        deltakelse.status.type shouldBe "HELT_NY_STATUS"
        deltakelse.status.aarsak shouldBe "HELT_NY_AARSAK"
        deltakelse.tiltakstype.tiltakskode shouldBe "HELT_NY_TILTAKSKODE"
    }

    @Test
    fun `en ny deltakelsesform blir UkjentDeltakelse med diskriminatoren i behold`() {
        val json = """
            {
              "historikk": [
                { "type": "NyDeltakelsesform", "id": "0190c9a2-7777-7000-8000-000000000007", "noeAnnet": 42 }
              ]
            }
        """.trimIndent()

        deserialize<TiltakshistorikkV1Response>(json).historikk.single() shouldBe
            TiltakshistorikkV1Dto.UkjentDeltakelse(type = "NyDeltakelsesform")
    }

    @Test
    fun `en deltakelse uten type-felt blir UkjentDeltakelse uten diskriminator`() {
        val json = """
            {
              "historikk": [
                { "id": "0190c9a2-8888-7000-8000-000000000008" }
              ]
            }
        """.trimIndent()

        deserialize<TiltakshistorikkV1Response>(json).historikk.single() shouldBe
            TiltakshistorikkV1Dto.UkjentDeltakelse(type = null)
    }

    @Test
    fun `komet-tidspunktet leses fra både opprettetDato og det varslede opprettetTidspunkt`() {
        fun kometJson(tidspunktfelt: String) = """
            {
              "type": "TeamKometDeltakelse",
              "norskIdent": "$fnr",
              "startDato": null,
              "sluttDato": null,
              "id": "0190c9a2-9999-7000-8000-000000000009",
              "tittel": "Avklaring hos Arrangør AS",
              "status": { "type": "VENTER_PA_OPPSTART", "aarsak": null, "$tidspunktfelt": "2026-02-01T08:00:00" },
              "tiltakstype": { "tiltakskode": "AVKLARING", "navn": "Avklaring" },
              "gjennomforing": { "id": "0190c9a2-aaaa-7000-8000-00000000000a", "deltidsprosent": null },
              "arrangor": { "hovedenhet": null, "underenhet": { "navn": null } },
              "deltidsprosent": null,
              "dagerPerUke": null
            }
        """.trimIndent()

        val forventet = LocalDateTime.of(2026, 2, 1, 8, 0, 0)
        listOf("opprettetDato", "opprettetTidspunkt").forEach { felt ->
            val deltakelse = deserialize<TiltakshistorikkV1Dto>(kometJson(felt)) as TiltakshistorikkV1Dto.TeamKometDeltakelse
            deltakelse.status.opprettetDato shouldBe forventet
        }
    }
}
