package fixtures.isolertdatabasetest

import org.junit.jupiter.api.Test

internal class Brudd {

    private fun hjelperUtenforTest() {
        withMigratedDb(runIsolated = true) {
        }
    }

    @Test
    fun `isolert uten annotasjon`() {
        withMigratedDb(runIsolated = true) {
        }
    }

    @Test
    @IsolatedDatabaseTest
    fun `isolert uten begrunnelse`() {
        withMigratedDb(runIsolated = true) {
        }
    }
}
