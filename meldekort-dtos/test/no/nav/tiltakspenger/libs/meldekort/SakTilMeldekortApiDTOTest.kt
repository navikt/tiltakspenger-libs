package no.nav.tiltakspenger.libs.meldekort

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.json.deserialize
import no.nav.tiltakspenger.libs.json.serialize
import no.nav.tiltakspenger.libs.periodisering.Periode
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SakTilMeldekortApiDTOTest {

    @Test
    fun `test serialization and deserialization of SakTilMeldekortApiDTO`() {
        val now = LocalDateTime.of(2024, 1, 23, 12, 0)
        val startDate = LocalDate.of(2024, 1, 1)
        val endDate = LocalDate.of(2024, 1, 14)
        val periode = Periode(startDate, endDate)
        val antallDagerForPeriode = 10

        val girRettMap: Map<LocalDate, Boolean> = buildMap {
            put(periode.fraOgMed, false)
            (1 until 13).forEach { day ->
                put(periode.fraOgMed.plusDays(day.toLong()), true)
            }
            put(periode.tilOgMed, false)
        }

        val meldeperiode = SakTilMeldekortApiDTO.Meldeperiode(
            id = "01HMWNSTVP8XB3QR6GYKR2E7AE",
            kjedeId = "2024-01-01/2024-01-14",
            versjon = 1,
            opprettet = now,
            fraOgMed = startDate,
            tilOgMed = endDate,
            antallDagerForPeriode = antallDagerForPeriode,
            girRett = girRettMap,
        )

        val sak = SakTilMeldekortApiDTO(
            fnr = "12345678910",
            sakId = "SAKID456",
            saksnummer = "SAK123",
            meldeperioder = listOf(meldeperiode),
            harSoknadUnderBehandling = false,
        )
        // language=JSON
        val expectedJson = """
            {
                "fnr": "12345678910",
                "sakId": "SAKID456",
                "saksnummer": "SAK123",
                "meldeperioder": [{
                    "id": "01HMWNSTVP8XB3QR6GYKR2E7AE",
                    "kjedeId": "2024-01-01/2024-01-14",
                    "versjon": 1,
                    "opprettet": "2024-01-23T12:00:00",
                    "fraOgMed": "2024-01-01",
                    "tilOgMed": "2024-01-14",
                    "antallDagerForPeriode": 10,
                    "girRett": {
                        "2024-01-01": false,
                        "2024-01-02": true,
                        "2024-01-03": true,
                        "2024-01-04": true,
                        "2024-01-05": true,
                        "2024-01-06": true,
                        "2024-01-07": true,
                        "2024-01-08": true,
                        "2024-01-09": true,
                        "2024-01-10": true,
                        "2024-01-11": true,
                        "2024-01-12": true,
                        "2024-01-13": true,
                        "2024-01-14": false
                    }
                }],
                "harSoknadUnderBehandling": false
            }
        """.trimIndent()

        val serialized = serialize(sak)
        serialized.shouldEqualJson(expectedJson)

        deserialize<SakTilMeldekortApiDTO>(serialized) shouldBe (sak)
    }
}
