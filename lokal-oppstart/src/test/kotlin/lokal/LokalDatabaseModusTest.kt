package no.nav.tiltakspenger.libs.lokal

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.tiltakspenger.libs.common.getOrFail
import org.junit.jupiter.api.Test

internal class LokalDatabaseModusTest {

    @Test
    fun `docker compose når miljøvariabelen ikke er satt`() {
        LokalDatabaseModus.fraMiljø { null }.getOrFail() shouldBe LokalDatabaseModus.DockerCompose
        LokalDatabaseModus.fraMiljø { "  " }.getOrFail() shouldBe LokalDatabaseModus.DockerCompose
    }

    @Test
    fun `godtar de skrivemåtene folk faktisk bruker`() {
        mapOf(
            "compose" to LokalDatabaseModus.DockerCompose,
            "DOCKER-COMPOSE" to LokalDatabaseModus.DockerCompose,
            " docker " to LokalDatabaseModus.DockerCompose,
            "testcontainers" to LokalDatabaseModus.Testcontainers,
            "TC" to LokalDatabaseModus.Testcontainers,
        ).forEach { (verdi, forventet) ->
            LokalDatabaseModus.fraMiljø { verdi }.getOrFail() shouldBe forventet
        }
    }

    @Test
    fun `en ukjent verdi er en feil, ikke et stille tilbakefall`() {
        val feil = LokalDatabaseModus.fraMiljø { "docker-desktop" }.feilen()

        feil.verdi shouldBe "docker-desktop"
        feil.somMelding() shouldContain "compose, docker-compose, docker, testcontainers, tc"
    }
}
