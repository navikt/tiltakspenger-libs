package fixtures.isolertdatabasetest

import org.junit.jupiter.api.Test

internal class EgenBegrunnelse {

    // Begrunnelsen er repoets egen kategori, og godtas først når den legges til standardsettet.
    @Test
    @IsolatedDatabaseTest
    fun `isolert med en begrunnelse repoet definerer selv`() {
        // Migrering pågår i nabotabellen.
        withMigratedDb(runIsolated = true) {
        }
    }
}
