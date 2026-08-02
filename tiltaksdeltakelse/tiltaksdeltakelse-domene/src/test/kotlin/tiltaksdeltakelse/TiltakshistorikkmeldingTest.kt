package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class TiltakshistorikkmeldingTest {
    /**
     * Kontraktskoden er det felles språket mot mulighetsrommet, og skal ikke drive fra domenenavnet vårt.
     */
    @Test
    fun `kontraktskoden er spikret`() {
        Tiltakshistorikkmelding.ManglerHistorikkFraTeamTiltak.kode shouldBe "MANGLER_HISTORIKK_FRA_TEAM_TILTAK"
    }

    @Test
    fun `tolkningen peker på kilden som mangler`() {
        Tiltakshistorikkmelding.ManglerHistorikkFraTeamTiltak.manglendeKilde shouldBe Tiltakskilde.TeamTiltak
        Tiltakshistorikkmelding.Ukjent("NOE_HELT_NYTT").manglendeKilde shouldBe null
    }
}
