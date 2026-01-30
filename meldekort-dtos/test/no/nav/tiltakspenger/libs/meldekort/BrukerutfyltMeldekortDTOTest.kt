package no.nav.tiltakspenger.libs.meldekort

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.json.deserialize
import no.nav.tiltakspenger.libs.json.serialize
import no.nav.tiltakspenger.libs.meldekort.BrukerutfyltMeldekortDTO.Status.FRAVÆR_ANNET
import no.nav.tiltakspenger.libs.meldekort.BrukerutfyltMeldekortDTO.Status.FRAVÆR_SYK
import no.nav.tiltakspenger.libs.meldekort.BrukerutfyltMeldekortDTO.Status.FRAVÆR_SYKT_BARN
import no.nav.tiltakspenger.libs.meldekort.BrukerutfyltMeldekortDTO.Status.IKKE_BESVART
import no.nav.tiltakspenger.libs.periode.Periode
import no.nav.tiltakspenger.libs.periode.PeriodeDTO
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class BrukerutfyltMeldekortDTOTest {

    @Test
    fun `test serialization and deserialization of BrukerutfyltMeldekortDTO`() {
        val now = LocalDateTime.of(2024, 1, 23, 12, 0)
        val startDate = LocalDate.of(2024, 1, 1)
        val endDate = LocalDate.of(2024, 1, 14)
        val periode = Periode(startDate, endDate)
        val journalpostId = "12345"

        val dager: Map<LocalDate, BrukerutfyltMeldekortDTO.Status> = buildMap {
            put(periode.fraOgMed, BrukerutfyltMeldekortDTO.Status.DELTATT_UTEN_LØNN_I_TILTAKET)
            put(periode.fraOgMed.plusDays(1), BrukerutfyltMeldekortDTO.Status.DELTATT_UTEN_LØNN_I_TILTAKET)
            put(periode.fraOgMed.plusDays(2), FRAVÆR_ANNET)
            put(periode.fraOgMed.plusDays(3), FRAVÆR_SYKT_BARN)
            put(periode.fraOgMed.plusDays(4), FRAVÆR_SYK)
            put(periode.fraOgMed.plusDays(5), IKKE_BESVART)
            put(periode.fraOgMed.plusDays(6), IKKE_BESVART)
            put(periode.fraOgMed.plusDays(7), BrukerutfyltMeldekortDTO.Status.DELTATT_UTEN_LØNN_I_TILTAKET)
            put(periode.fraOgMed.plusDays(8), IKKE_BESVART)
            put(periode.fraOgMed.plusDays(9), BrukerutfyltMeldekortDTO.Status.DELTATT_UTEN_LØNN_I_TILTAKET)
            put(periode.fraOgMed.plusDays(10), IKKE_BESVART)
            put(periode.fraOgMed.plusDays(11), IKKE_BESVART)
            put(periode.fraOgMed.plusDays(12), IKKE_BESVART)
            put(periode.tilOgMed, IKKE_BESVART)
        }

        val meldekort = BrukerutfyltMeldekortDTO(
            id = "01HMWNSTVP8XB3QR6GYKR2E7AE",
            meldeperiodeId = "01HMWNSTVP8XB3QR6GYKR2E7AF",
            sakId = "01HMWNSTVP8XB3QR6GYKR2E7AG",
            periode = PeriodeDTO(
                fraOgMed = startDate.toString(),
                tilOgMed = endDate.toString(),
            ),
            mottatt = now,
            dager = dager,
            journalpostId = journalpostId,
        )

        val expectedJson = """
            {
                "id": "01HMWNSTVP8XB3QR6GYKR2E7AE",
                "meldeperiodeId": "01HMWNSTVP8XB3QR6GYKR2E7AF",
                "sakId": "01HMWNSTVP8XB3QR6GYKR2E7AG",
                "periode": {
                    "fraOgMed": "2024-01-01",
                    "tilOgMed": "2024-01-14"
                },
                "mottatt": "2024-01-23T12:00:00",
                "dager": {
                    "2024-01-01": "DELTATT_UTEN_LØNN_I_TILTAKET",
                    "2024-01-02": "DELTATT_UTEN_LØNN_I_TILTAKET",
                    "2024-01-03": "FRAVÆR_ANNET",
                    "2024-01-04": "FRAVÆR_SYKT_BARN",
                    "2024-01-05": "FRAVÆR_SYK",
                    "2024-01-06": "IKKE_BESVART",
                    "2024-01-07": "IKKE_BESVART",
                    "2024-01-08": "DELTATT_UTEN_LØNN_I_TILTAKET",
                    "2024-01-09": "IKKE_BESVART",
                    "2024-01-10": "DELTATT_UTEN_LØNN_I_TILTAKET",
                    "2024-01-11": "IKKE_BESVART",
                    "2024-01-12": "IKKE_BESVART",
                    "2024-01-13": "IKKE_BESVART",
                    "2024-01-14": "IKKE_BESVART"
                  },
                "journalpostId": "$journalpostId"
            }
        """.trimIndent()

        val serialized = serialize(meldekort)
        serialized.shouldEqualJson(expectedJson)

        deserialize<BrukerutfyltMeldekortDTO>(serialized).shouldBe(meldekort)
    }
}
