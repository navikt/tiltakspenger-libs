package no.nav.tiltakspenger.libs.meldekort

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.json.deserialize
import no.nav.tiltakspenger.libs.json.serialize
import no.nav.tiltakspenger.libs.meldekort.BrukerutfyltMeldekortDTO.Status.DELTATT
import no.nav.tiltakspenger.libs.meldekort.BrukerutfyltMeldekortDTO.Status.FRAVÆR_ANNET
import no.nav.tiltakspenger.libs.meldekort.BrukerutfyltMeldekortDTO.Status.FRAVÆR_SYK
import no.nav.tiltakspenger.libs.meldekort.BrukerutfyltMeldekortDTO.Status.FRAVÆR_SYKT_BARN
import no.nav.tiltakspenger.libs.meldekort.BrukerutfyltMeldekortDTO.Status.IKKE_DELTATT
import no.nav.tiltakspenger.libs.meldekort.BrukerutfyltMeldekortDTO.Status.IKKE_REGISTRERT
import no.nav.tiltakspenger.libs.meldekort.BrukerutfyltMeldekortDTO.Status.IKKE_RETT_TIL_TILTAKSPENGER
import no.nav.tiltakspenger.libs.periodisering.Periode
import no.nav.tiltakspenger.libs.periodisering.PeriodeDTO
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

        val dager: Map<LocalDate, BrukerutfyltMeldekortDTO.Status> = buildMap {
            put(periode.fraOgMed, IKKE_RETT_TIL_TILTAKSPENGER)
            put(periode.fraOgMed.plusDays(1), DELTATT)
            put(periode.fraOgMed.plusDays(2), FRAVÆR_ANNET)
            put(periode.fraOgMed.plusDays(3), FRAVÆR_SYKT_BARN)
            put(periode.fraOgMed.plusDays(4), FRAVÆR_SYK)
            put(periode.fraOgMed.plusDays(5), IKKE_REGISTRERT)
            put(periode.fraOgMed.plusDays(6), IKKE_REGISTRERT)
            put(periode.fraOgMed.plusDays(7), DELTATT)
            put(periode.fraOgMed.plusDays(8), IKKE_DELTATT)
            put(periode.fraOgMed.plusDays(9), DELTATT)
            put(periode.fraOgMed.plusDays(10), IKKE_DELTATT)
            put(periode.fraOgMed.plusDays(11), IKKE_DELTATT)
            put(periode.fraOgMed.plusDays(12), IKKE_REGISTRERT)
            put(periode.tilOgMed, IKKE_RETT_TIL_TILTAKSPENGER)
        }

        val meldekort = BrukerutfyltMeldekortDTO(
            id = "01HMWNSTVP8XB3QR6GYKR2E7AE",
            meldeperiodeId = "01HMWNSTVP8XB3QR6GYKR2E7AF",
            periode = PeriodeDTO(
                fraOgMed = startDate.toString(),
                tilOgMed = endDate.toString(),
            ),
            mottatt = now,
            dager = dager,
        )

        val expectedJson = """
            {
                "id": "01HMWNSTVP8XB3QR6GYKR2E7AE",
                "meldeperiodeId": "01HMWNSTVP8XB3QR6GYKR2E7AF",
                "periode": {
                    "fraOgMed": "2024-01-01",
                    "tilOgMed": "2024-01-14"
                },
                "mottatt": "2024-01-23T12:00:00",
                "dager": {
                    "2024-01-01": "IKKE_RETT_TIL_TILTAKSPENGER",
                    "2024-01-02": "DELTATT",
                    "2024-01-03": "FRAVÆR_ANNET",
                    "2024-01-04": "FRAVÆR_SYKT_BARN",
                    "2024-01-05": "FRAVÆR_SYK",
                    "2024-01-06": "IKKE_REGISTRERT",
                    "2024-01-07": "IKKE_REGISTRERT",
                    "2024-01-08": "DELTATT",
                    "2024-01-09": "IKKE_DELTATT",
                    "2024-01-10": "DELTATT",
                    "2024-01-11": "IKKE_DELTATT",
                    "2024-01-12": "IKKE_DELTATT",
                    "2024-01-13": "IKKE_REGISTRERT",
                    "2024-01-14": "IKKE_RETT_TIL_TILTAKSPENGER"
                  }
            }
        """.trimIndent()

        val serialized = serialize(meldekort)
        serialized.shouldEqualJson(expectedJson)

        deserialize<BrukerutfyltMeldekortDTO>(serialized).shouldBe(meldekort)
    }
}
