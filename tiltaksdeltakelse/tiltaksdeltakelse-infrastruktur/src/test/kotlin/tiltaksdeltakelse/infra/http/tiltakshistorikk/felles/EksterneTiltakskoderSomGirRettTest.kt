package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.felles

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakskilde
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.TiltakstypeSomGirRett
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.arena.arenakoderSomGirRett
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.komet.kometkoderSomGirRett
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.teamtiltak.teamTiltakkoderSomGirRett
import org.junit.jupiter.api.Test

class EksterneTiltakskoderSomGirRettTest {

    @Test
    fun `lista inneholder én rad per tiltakskode fra hver kilde`() {
        eksterneTiltakskoderSomGirRett shouldHaveSize arenakoderSomGirRett.size + kometkoderSomGirRett.size + teamTiltakkoderSomGirRett.size
        eksterneTiltakskoderSomGirRett.groupBy { it.kilde }.mapValues { (_, koder) -> koder.associate { it.eksternTiltakskode to it.tiltakstype } } shouldBe mapOf(
            Tiltakskilde.Arena to arenakoderSomGirRett,
            Tiltakskilde.Komet to kometkoderSomGirRett,
            Tiltakskilde.TeamTiltak to teamTiltakkoderSomGirRett,
        )
    }

    @Test
    fun `samme tiltakskode fra to kilder gir to rader`() {
        eksterneTiltakskoderSomGirRett shouldContainAll listOf(
            EksternTiltakskodeSomGirRett(Tiltakskilde.Arena, "STUDIESPESIALISERING", TiltakstypeSomGirRett.STUDIESPESIALISERING),
            EksternTiltakskodeSomGirRett(Tiltakskilde.Komet, "STUDIESPESIALISERING", TiltakstypeSomGirRett.STUDIESPESIALISERING),
        )
    }

    @Test
    fun `alle tiltakstyper som gir rett har minst én tiltakskode`() {
        eksterneTiltakskoderSomGirRett.map { it.tiltakstype }.toSet() shouldBe TiltakstypeSomGirRett.entries.toSet()
    }
}
