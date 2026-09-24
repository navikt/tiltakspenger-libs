package no.nav.tiltakspenger.libs.lokal

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.tiltakspenger.libs.common.getOrFail
import org.junit.jupiter.api.Test

internal class LokalPostgresConfigTest {

    @Test
    fun `utleder port database og bruker fra appens egen jdbc-url`() {
        val config = LokalPostgresConfig.fraJdbcUrl(
            jdbcUrl = "jdbc:postgresql://localhost:5435/meldekort?user=postgres&password=test",
            composeTjeneste = "postgresMeldekort",
        ).getOrFail()

        config.vert shouldBe "localhost"
        config.port shouldBe 5435
        config.database shouldBe "meldekort"
        config.brukernavn shouldBe "postgres"
        config.passord shouldBe "test"
        config.composeTjeneste shouldBe "postgresMeldekort"
        config.jdbcUrl shouldBe "jdbc:postgresql://localhost:5435/meldekort?user=postgres&password=test"
    }

    @Test
    fun `sier hva som mangler i en jdbc-url vi ikke kan bruke`() {
        val feil = LokalPostgresConfig.fraJdbcUrl(
            jdbcUrl = "jdbc:postgresql://localhost/meldekort?user=postgres&password=test",
            composeTjeneste = "postgresMeldekort",
        ).feilen()

        feil.begrunnelse shouldBe "den mangler port"
        feil.somMelding() shouldContain "jdbc:postgresql://<vert>:<port>/<database>"
    }

    @Test
    fun `maskerer passordet i feilmeldingen`() {
        val feil = LokalPostgresConfig.fraJdbcUrl(
            jdbcUrl = "jdbc:postgresql://localhost:5435/meldekort?password=hemmelig",
            composeTjeneste = "postgresMeldekort",
        ).feilen()

        feil.begrunnelse shouldContain "user"
        feil.somMelding() shouldContain "password=*****"
    }
}
