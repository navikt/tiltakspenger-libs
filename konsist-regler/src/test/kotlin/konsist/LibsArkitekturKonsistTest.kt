package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.Konsist
import org.junit.jupiter.api.Test

/**
 * Kjører de delte reglene på hele tiltakspenger-libs.
 * Konsist `scopeFromProject()`/`scopeFromTest()` skanner alle moduler, så disse testene dekker hele repoet.
 */
internal class LibsArkitekturKonsistTest {
    @Test
    fun `all kildekode bruker Jackson 3, ikke Jackson 2`() {
        IngenJackson2.assert(Konsist.scopeFromProject())
    }

    @Test
    fun `all testkode bruker JUnit 5, ikke JUnit 4`() {
        IngenJUnit4.assert(Konsist.scopeFromTest())
    }

    @Test
    fun `all testkode bruker Kotest assertions, ikke Jupiter Assertions`() {
        IngenJupiterAsserts.assert(Konsist.scopeFromTest())
    }

    @Test
    fun `ingen lokale Jackson-mappere utenfor json-modulen`() {
        IngenLokaleJacksonMappere.assert(Konsist.scopeFromProject())
    }

    @Test
    fun `kdoc og kommentarer har maks en setning per linje`() {
        EnSetningPerLinje.assertFlereSetningerIKommentarer(Konsist.scopeFromProject())
    }

    @Test
    fun `kdoc og kommentarer brekker ikke en setning over flere linjer`() {
        EnSetningPerLinje.assertBrukneSetningerIKommentarer(Konsist.scopeFromProject())
    }
}
