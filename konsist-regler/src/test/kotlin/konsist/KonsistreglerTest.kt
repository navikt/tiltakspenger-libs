package no.nav.tiltakspenger.libs.konsist

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.junit.jupiter.api.Test

/**
 * Tester av det delte fundamentet i `Konsistregler.kt`, som alle reglene går via.
 */
internal class KonsistreglerTest {

    /**
     * Et git-arbeidstre lagt under repo-rota (`.worktrees/<gren>/`) plukkes opp av `Konsist.scopeFromProject()` som om det var en egen modul.
     * Da ville en regel som nettopp er skjerpet på hovedgrenen feilet på et arbeidstre som ennå ikke er rebaset, og blokkert hovedtreet for kode det ikke eier.
     */
    @Test
    fun `filer fra en annen utsjekk er ikke kildefiler`() {
        val scope = fixtureScope("utsjekker")

        // Scopet må faktisk se begge filene, ellers ville testen passert selv om fixturen aldri ble kopiert.
        scope.files.map { fil -> fil.name } shouldContainExactlyInAnyOrder listOf("Egen", "AnnenGren")

        scope.kildefiler().map { fil -> fil.name } shouldContainExactly listOf("Egen")
    }
}
