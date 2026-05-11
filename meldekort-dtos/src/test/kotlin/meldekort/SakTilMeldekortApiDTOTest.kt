package no.nav.tiltakspenger.libs.meldekort

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.json.deserialize
import no.nav.tiltakspenger.libs.json.serialize
import no.nav.tiltakspenger.libs.periode.Periode
import no.nav.tiltakspenger.libs.periode.toDTO
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SakTilMeldekortApiDTOTest {

    private val now = LocalDateTime.of(2024, 1, 23, 12, 0)

    // En meldeperiode (mandag-søndag, 14 dager)
    private val periode1 = Periode(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 14))

    // Påfølgende meldeperiode for tester med flere meldeperiodebehandlinger
    private val periode2 = Periode(LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 28))

    @Test
    fun `test serialization and deserialization of SakTilMeldekortApiDTO`() {
        val girRettMap: Map<LocalDate, Boolean> = buildMap {
            put(periode1.fraOgMed, false)
            (1 until 13).forEach { day ->
                put(periode1.fraOgMed.plusDays(day.toLong()), true)
            }
            put(periode1.tilOgMed, false)
        }

        val meldeperiode = SakTilMeldekortApiDTO.MeldeperiodeDTO(
            id = "01HMWNSTVP8XB3QR6GYKR2E7AE",
            kjedeId = "2024-01-01/2024-01-14",
            versjon = 1,
            opprettet = now,
            periodeDTO = periode1.toDTO(),
            antallDagerForPeriode = 10,
            girRett = girRettMap,
        )

        val sak = SakTilMeldekortApiDTO(
            fnr = "12345678910",
            sakId = "SAKID456",
            saksnummer = "SAK123",
            meldeperioder = listOf(meldeperiode),
            harSoknadUnderBehandling = false,
            kanSendeInnHelgForMeldekort = false,
            meldekortvedtak = listOf(
                SakTilMeldekortApiDTO.MeldekortvedtakDTO(
                    id = "01HMWNT0Q4Z2X0VJ7G7Q4Z9JKM",
                    opprettet = now,
                    erKorrigering = false,
                    erAutomatiskBehandlet = true,
                    meldeperiodebehandlinger = listOf(
                        SakTilMeldekortApiDTO.MeldekortvedtakDTO.MeldeperiodebehandlingDTO(
                            meldeperiodeId = "01HMWNSTVP8XB3QR6GYKR2E7AE",
                            meldeperiodeKjedeId = "2024-01-01/2024-01-14",
                            brukersMeldekortId = "01HMWNVBRUKERSMELDEKORTXYZ",
                            periodeDTO = periode1.toDTO(),
                            dager = listOf(
                                SakTilMeldekortApiDTO.MeldekortvedtakDTO.MeldeperiodebehandlingDTO.DagDTO(
                                    dato = LocalDate.of(2024, 1, 2),
                                    status = SakTilMeldekortApiDTO.MeldekortvedtakDTO.Status.DELTATT_UTEN_LØNN_I_TILTAKET,
                                    reduksjon = SakTilMeldekortApiDTO.MeldekortvedtakDTO.Reduksjon.INGEN_REDUKSJON,
                                    beløp = 285,
                                    beløpBarnetillegg = 100,
                                ),
                                SakTilMeldekortApiDTO.MeldekortvedtakDTO.MeldeperiodebehandlingDTO.DagDTO(
                                    dato = LocalDate.of(2024, 1, 3),
                                    status = SakTilMeldekortApiDTO.MeldekortvedtakDTO.Status.FRAVÆR_SYK,
                                    reduksjon = SakTilMeldekortApiDTO.MeldekortvedtakDTO.Reduksjon.REDUKSJON,
                                    beløp = 0,
                                    beløpBarnetillegg = 0,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
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
                    "periodeDTO": {
                        "fraOgMed": "2024-01-01",
                        "tilOgMed": "2024-01-14"
                    },
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
                "harSoknadUnderBehandling": false,
                "kanSendeInnHelgForMeldekort": false,
                "meldekortvedtak": [{
                    "id": "01HMWNT0Q4Z2X0VJ7G7Q4Z9JKM",
                    "opprettet": "2024-01-23T12:00:00",
                    "erKorrigering": false,
                    "erAutomatiskBehandlet": true,
                    "meldeperiodebehandlinger": [{
                        "meldeperiodeId": "01HMWNSTVP8XB3QR6GYKR2E7AE",
                        "meldeperiodeKjedeId": "2024-01-01/2024-01-14",
                        "brukersMeldekortId": "01HMWNVBRUKERSMELDEKORTXYZ",
                        "periodeDTO": {
                            "fraOgMed": "2024-01-01",
                            "tilOgMed": "2024-01-14"
                        },
                        "dager": [
                            {
                                "dato": "2024-01-02",
                                "status": "DELTATT_UTEN_LØNN_I_TILTAKET",
                                "reduksjon": "INGEN_REDUKSJON",
                                "beløp": 285,
                                "beløpBarnetillegg": 100
                            },
                            {
                                "dato": "2024-01-03",
                                "status": "FRAVÆR_SYK",
                                "reduksjon": "REDUKSJON",
                                "beløp": 0,
                                "beløpBarnetillegg": 0
                            }
                        ]
                    }]
                }]
            }
        """.trimIndent()

        val serialized = serialize(sak)
        serialized.shouldEqualJson(expectedJson)

        deserialize<SakTilMeldekortApiDTO>(serialized) shouldBe (sak)
    }

    @Test
    fun `bakoverkompatibel - JSON uten meldekortvedtak kan fortsatt deserialiseres`() {
        // language=JSON
        val gammelJson = """
            {
                "fnr": "12345678910",
                "sakId": "SAKID456",
                "saksnummer": "SAK123",
                "meldeperioder": [],
                "harSoknadUnderBehandling": false,
                "kanSendeInnHelgForMeldekort": false
            }
        """.trimIndent()

        val deserialized = deserialize<SakTilMeldekortApiDTO>(gammelJson)
        deserialized.meldekortvedtak shouldBe emptyList()
    }

    // -- MeldeperiodeDTO invarianter --

    private fun meldeperiode(
        fraOgMed: LocalDate = periode1.fraOgMed,
        tilOgMed: LocalDate = periode1.tilOgMed,
        girRett: Map<LocalDate, Boolean> = (0L..13L).associate { fraOgMed.plusDays(it) to true },
    ) = SakTilMeldekortApiDTO.MeldeperiodeDTO(
        id = "01HMWNSTVP8XB3QR6GYKR2E7AE",
        kjedeId = "kjede",
        versjon = 1,
        opprettet = now,
        periodeDTO = Periode(fraOgMed, tilOgMed).toDTO(),
        antallDagerForPeriode = 10,
        girRett = girRett,
    )

    @Test
    fun `meldeperiode må starte på mandag`() {
        val tirsdag = LocalDate.of(2024, 1, 2)
        shouldThrow<IllegalArgumentException> {
            meldeperiode(fraOgMed = tirsdag, tilOgMed = tirsdag.plusDays(13))
        }.message shouldBe "fraOgMed må være en mandag, men var 2024-01-02 (TUESDAY)"
    }

    @Test
    fun `meldeperiode må slutte på søndag`() {
        val mandag = LocalDate.of(2024, 1, 1)
        val lørdag = mandag.plusDays(12)
        shouldThrow<IllegalArgumentException> {
            meldeperiode(fraOgMed = mandag, tilOgMed = lørdag)
        }.message shouldBe "tilOgMed må være en søndag, men var 2024-01-13 (SATURDAY)"
    }

    @Test
    fun `meldeperiode må være 14 dager`() {
        val mandag = LocalDate.of(2024, 1, 1)
        val søndag = mandag.plusDays(20)
        shouldThrow<IllegalArgumentException> {
            meldeperiode(fraOgMed = mandag, tilOgMed = søndag)
        }.message shouldBe "En meldeperiode må være 14 dager, men var fraOgMed=2024-01-01, tilOgMed=2024-01-21"
    }

    @Test
    fun `girRett må inneholde alle 14 datoene i meldeperioden`() {
        val mandag = LocalDate.of(2024, 1, 1)
        val søndag = mandag.plusDays(13)
        val ufullstendigGirRett = (0L..12L).associate { mandag.plusDays(it) to true }
        shouldThrow<IllegalArgumentException> {
            meldeperiode(fraOgMed = mandag, tilOgMed = søndag, girRett = ufullstendigGirRett)
        }.message shouldBe "girRett må inneholde nøyaktig de 14 datoene i meldeperioden 2024-01-01 - 2024-01-14, men var ${ufullstendigGirRett.keys.sorted()}"
    }

    // -- DagDTO invarianter --

    private fun dag(
        dato: LocalDate = LocalDate.of(2024, 1, 2),
        beløp: Int = 100,
        beløpBarnetillegg: Int = 50,
    ) = SakTilMeldekortApiDTO.MeldekortvedtakDTO.MeldeperiodebehandlingDTO.DagDTO(
        dato = dato,
        status = SakTilMeldekortApiDTO.MeldekortvedtakDTO.Status.DELTATT_UTEN_LØNN_I_TILTAKET,
        reduksjon = SakTilMeldekortApiDTO.MeldekortvedtakDTO.Reduksjon.INGEN_REDUKSJON,
        beløp = beløp,
        beløpBarnetillegg = beløpBarnetillegg,
    )

    @Test
    fun `dag beløp må være større eller lik 0`() {
        shouldThrow<IllegalArgumentException> {
            dag(beløp = -1)
        }.message shouldBe "beløp må være >= 0, men var -1"
    }

    @Test
    fun `dag beløpBarnetillegg må være større eller lik 0`() {
        shouldThrow<IllegalArgumentException> {
            dag(beløpBarnetillegg = -1)
        }.message shouldBe "beløpBarnetillegg må være >= 0, men var -1"
    }

    // -- MeldeperiodebehandlingDTO invarianter --

    private fun meldeperiodebehandling(
        periode: Periode = periode1,
        dager: List<SakTilMeldekortApiDTO.MeldekortvedtakDTO.MeldeperiodebehandlingDTO.DagDTO> = emptyList(),
        brukersMeldekortId: String? = null,
    ) = SakTilMeldekortApiDTO.MeldekortvedtakDTO.MeldeperiodebehandlingDTO(
        meldeperiodeId = "meldeperiodeId-${periode.fraOgMed}",
        meldeperiodeKjedeId = "${periode.fraOgMed}/${periode.tilOgMed}",
        brukersMeldekortId = brukersMeldekortId,
        periodeDTO = periode.toDTO(),
        dager = dager,
    )

    @Test
    fun `meldeperiodebehandling må ha mandag-søndag, 14-dagers periode`() {
        val tirsdag = LocalDate.of(2024, 1, 2)
        shouldThrow<IllegalArgumentException> {
            meldeperiodebehandling(periode = Periode(tirsdag, tirsdag.plusDays(13)))
        }.message shouldBe "fraOgMed må være en mandag, men var 2024-01-02 (TUESDAY)"
    }

    @Test
    fun `meldeperiodebehandling sine dager må ligge innenfor perioden`() {
        val utenforDag = dag(dato = LocalDate.of(2024, 2, 1))
        shouldThrow<IllegalArgumentException> {
            meldeperiodebehandling(periode = periode1, dager = listOf(utenforDag))
        }.message shouldBe "Alle dager må ligge innenfor meldeperioden 2024-01-01 - 2024-01-14, men var [2024-02-01]"
    }

    // -- MeldekortvedtakDTO invarianter --

    private fun meldekortvedtak(
        meldeperiodebehandlinger: List<SakTilMeldekortApiDTO.MeldekortvedtakDTO.MeldeperiodebehandlingDTO> =
            listOf(meldeperiodebehandling(dager = listOf(dag()))),
    ) = SakTilMeldekortApiDTO.MeldekortvedtakDTO(
        id = "01HMWNT0Q4Z2X0VJ7G7Q4Z9JKM",
        opprettet = now,
        erKorrigering = false,
        erAutomatiskBehandlet = true,
        meldeperiodebehandlinger = meldeperiodebehandlinger,
    )

    @Test
    fun `meldekortvedtak må ha minst én meldeperiodebehandling`() {
        shouldThrow<IllegalArgumentException> {
            SakTilMeldekortApiDTO.MeldekortvedtakDTO(
                id = "01HMWNT0Q4Z2X0VJ7G7Q4Z9JKM",
                opprettet = now,
                erKorrigering = false,
                erAutomatiskBehandlet = true,
                meldeperiodebehandlinger = emptyList(),
            )
        }.message shouldBe "Et meldekortvedtak må ha minst én meldeperiodebehandling"
    }

    @Test
    fun `meldeperiodebehandlinger må være sammenhengende og sortert`() {
        // periode1 og periode3 — hopper over periode2 — ikke sammenhengende
        val periode3 = Periode(LocalDate.of(2024, 1, 29), LocalDate.of(2024, 2, 11))
        shouldThrow<IllegalArgumentException> {
            meldekortvedtak(
                meldeperiodebehandlinger = listOf(
                    meldeperiodebehandling(periode = periode1),
                    meldeperiodebehandling(periode = periode3),
                ),
            )
        }.message shouldBe "Meldeperiodebehandlinger må være sammenhengende og sortert, men var [${periode1.toDTO()}, ${periode3.toDTO()}]"
    }

    @Test
    fun `meldekortvedtak kan ha flere sammenhengende meldeperiodebehandlinger`() {
        val vedtak = meldekortvedtak(
            meldeperiodebehandlinger = listOf(
                meldeperiodebehandling(periode = periode1, dager = listOf(dag(dato = LocalDate.of(2024, 1, 2), beløp = 100, beløpBarnetillegg = 50))),
                meldeperiodebehandling(periode = periode2, dager = listOf(dag(dato = LocalDate.of(2024, 1, 16), beløp = 200, beløpBarnetillegg = 0))),
            ),
        )
        vedtak.meldeperiodebehandlinger.size shouldBe 2
    }
}
