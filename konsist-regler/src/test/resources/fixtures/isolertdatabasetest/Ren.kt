package fixtures.isolertdatabasetest

import org.junit.jupiter.api.Test

internal class Ren {

    // Omtale av runIsolated = true i en kommentar flagges ikke.
    @Test
    @IsolatedDatabaseTest
    fun `isolert med annotasjon og begrunnelse`() {
        // Aggregert spørring på tvers av saker; må kjøre isolert.
        withMigratedDb(runIsolated = true) {
        }
    }

    @Test
    @IsolatedDatabaseTest
    fun `isolert med exit-plan`() {
        // TODO: Kan flippes til runIsolated = false når spørringen får limit som parameter.
        withMigratedDb(runIsolated = true) {
        }
    }

    @Test
    fun `vanlig test uten isolasjon`() {
        withMigratedDb(runIsolated = false) {
        }
        val omtale = "runIsolated = true i en strengliteral flagges ikke"
        println(omtale)
    }
}
