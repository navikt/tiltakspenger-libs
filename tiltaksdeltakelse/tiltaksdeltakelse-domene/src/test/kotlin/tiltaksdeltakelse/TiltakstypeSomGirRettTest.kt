package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class TiltakstypeSomGirRettTest {
    /**
     * Testen pinner antallet, ikke navnene.
     * Navnene er domenets egne og står fritt til å endres — konsumentene eier sine egne databasetyper og mapper til og fra dem.
     * Settet er derimot en faglig størrelse: får en tiltakstype rett, eller mister den den, må endringen følges opp av mappingen til `StønadTypeTiltakspenger` i utbetaling og av databasetypene i konsumentene.
     * Da skal denne testen feile og tvinge fram det blikket.
     */
    @Test
    fun `settet av tiltakstyper som gir rett er 22 - endres det, må konsumentene følge etter`() {
        TiltakstypeSomGirRett.entries.size shouldBe 22
    }
}
