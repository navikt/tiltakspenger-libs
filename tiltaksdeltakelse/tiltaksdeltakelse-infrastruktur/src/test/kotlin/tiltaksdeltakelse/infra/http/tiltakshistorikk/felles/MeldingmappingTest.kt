package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.felles

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakshistorikkmelding
import org.junit.jupiter.api.Test

class MeldingmappingTest {

    @Test
    fun `meldinger klassifiseres som kjent eller ukjent, og blank feiler`() {
        tiltakshistorikkmelding("MANGLER_HISTORIKK_FRA_TEAM_TILTAK").getOrFail() shouldBe
            Tiltakshistorikkmelding.ManglerHistorikkFraTeamTiltak
        tiltakshistorikkmelding("HELT_NY_MELDING").getOrFail() shouldBe
            Tiltakshistorikkmelding.Ukjent("HELT_NY_MELDING")
        tiltakshistorikkmelding(" ") shouldBe
            UgyldigKontraktsverdi("Blank melding fra tiltakshistorikk kan ikke bæres som ukjent kildeverdi").left()
    }
}
