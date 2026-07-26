package no.nav.tiltakspenger.libs.lokal

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.tiltakspenger.libs.common.TikkendeKlokke
import no.nav.tiltakspenger.libs.common.getOrFail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

internal class LokalPostgresTest {

    @Test
    fun `rører ingenting når databasen allerede svarer`(@TempDir katalog: Path) {
        val kommandokjører = FakeKommandokjører { vellykket() }

        val resultat = start(katalog, kommandokjører, FakeDatabaseprobe(svarer)).getOrFail()

        resultat.jdbcUrl shouldBe "jdbc:postgresql://127.0.0.1:5435/meldekort?user=postgres&password=test"
        resultat.modus shouldBe LokalDatabaseModus.DockerCompose
        resultat.beskrivelse shouldContain "allerede oppe"
        kommandokjører.kall shouldBe emptyList()
    }

    @Test
    fun `finner compose-fila i katalogen over og starter tjenesten`(@TempDir rot: Path) {
        val composefil = rot.medComposefil()
        val subrepo = Files.createDirectory(rot.resolve("tiltakspenger-meldekort-api"))
        val kommandokjører = FakeKommandokjører { kommando ->
            when {
                "config" in kommando -> vellykket("postgresSaksbehandling\npostgresMeldekort\n")
                else -> vellykket()
            }
        }

        val resultat = start(subrepo, kommandokjører, FakeDatabaseprobe(svarerIkke(), svarer)).getOrFail()

        resultat.beskrivelse shouldContain composefil.toString()
        kommandokjører.kall.map { it.vist } shouldBe listOf(
            "docker compose version",
            "docker compose -f docker-compose.yml config --services",
            "docker compose -f docker-compose.yml up -d postgresMeldekort",
        )
        // Compose kjøres fra katalogen fila ligger i, ellers treffer ikke relative volum og build-contexter.
        kommandokjører.kall.last().arbeidskatalog shouldBe rot
    }

    @Test
    fun `faller tilbake til docker-compose når docker compose ikke finnes`(@TempDir rot: Path) {
        rot.medComposefil()
        val kommandokjører = FakeKommandokjører { kommando ->
            when {
                kommando.first() == "docker" -> mislyktes("docker: 'compose' is not a docker command.")
                "config" in kommando -> vellykket("postgresMeldekort\n")
                else -> vellykket()
            }
        }

        start(rot, kommandokjører, FakeDatabaseprobe(svarerIkke(), svarer)).getOrFail()

        kommandokjører.kall.map { it.vist } shouldBe listOf(
            "docker compose version",
            "docker-compose version",
            "docker-compose -f docker-compose.yml config --services",
            "docker-compose -f docker-compose.yml up -d postgresMeldekort",
        )
    }

    @Test
    fun `sier fra når docker ikke er installert`(@TempDir rot: Path) {
        rot.medComposefil()
        val kommandokjører = FakeKommandokjører {
            Kommandofeil.KunneIkkeStarte(IOException("Cannot run program \"docker\"")).left()
        }

        val feil = start(rot, kommandokjører, FakeDatabaseprobe(svarerIkke())).feilen()

        feil.shouldBeInstanceOf<LokalPostgresFeil.DockerMangler>()
        feil.prøvdeKommandoer shouldBe listOf("docker compose", "docker-compose")
        feil.somMelding() shouldContain "LOKAL_DB_MODUS=testcontainers"
    }

    @Test
    fun `sier fra når docker-demonen ikke svarer`(@TempDir rot: Path) {
        rot.medComposefil()
        val kommandokjører = FakeKommandokjører { kommando ->
            when {
                "version" in kommando -> vellykket()
                "config" in kommando -> vellykket("postgresMeldekort\n")
                else -> mislyktes("Cannot connect to the Docker daemon at unix:///var/run/docker.sock.")
            }
        }

        val feil = start(rot, kommandokjører, FakeDatabaseprobe(svarerIkke())).feilen()

        feil.shouldBeInstanceOf<LokalPostgresFeil.DockerDemonSvarerIkke>()
        feil.somMelding() shouldContain "colima start"
    }

    @Test
    fun `sier fra når det ikke finnes noen compose-fil`(@TempDir rot: Path) {
        val feil = start(rot, FakeKommandokjører { vellykket() }, FakeDatabaseprobe(svarerIkke())).feilen()

        feil.shouldBeInstanceOf<LokalPostgresFeil.FantIngenComposefil>()
        feil.somMelding() shouldContain "docker-compose.yml"
    }

    @Test
    fun `lister opp tjenestene som faktisk finnes når vi ikke fant vår`(@TempDir rot: Path) {
        rot.medComposefil()
        val kommandokjører = FakeKommandokjører { kommando ->
            when {
                "config" in kommando -> vellykket("postgresSaksbehandling\nauthserver\n")
                else -> vellykket()
            }
        }

        val feil = start(rot, kommandokjører, FakeDatabaseprobe(svarerIkke())).feilen()

        feil.shouldBeInstanceOf<LokalPostgresFeil.FantIkkeTjenesten>()
        feil.somMelding() shouldContain "postgresSaksbehandling, authserver"
    }

    @Test
    fun `sier fra når compose-fila ikke lar seg lese`(@TempDir rot: Path) {
        val composefil = rot.medComposefil()
        val kommandokjører = FakeKommandokjører { kommando ->
            when {
                "config" in kommando -> mislyktes("yaml: line 3: did not find expected key")
                else -> vellykket()
            }
        }

        val feil = start(rot, kommandokjører, FakeDatabaseprobe(svarerIkke())).feilen()

        feil.shouldBeInstanceOf<LokalPostgresFeil.ComposefilKunneIkkeLeses>()
        feil.composefil shouldBe composefil
        feil.somMelding() shouldContain "did not find expected key"
    }

    @Test
    fun `tar med utdata når compose up feiler`(@TempDir rot: Path) {
        rot.medComposefil()
        val kommandokjører = FakeKommandokjører { kommando ->
            when {
                "up" in kommando -> mislyktes("Bind for 127.0.0.1:5435 failed: port is already allocated", exitkode = 125)
                "config" in kommando -> vellykket("postgresMeldekort\n")
                else -> vellykket()
            }
        }

        val feil = start(rot, kommandokjører, FakeDatabaseprobe(svarerIkke())).feilen()

        feil.shouldBeInstanceOf<LokalPostgresFeil.ComposeKommandoFeilet>()
        feil.exitkode shouldBe 125
        feil.somMelding() shouldContain "port is already allocated"
    }

    @Test
    fun `sier fra når en docker-kommando henger`(@TempDir rot: Path) {
        rot.medComposefil()
        val kommandokjører = FakeKommandokjører { Kommandofeil.Tidsavbrutt.left() }

        val feil = start(rot, kommandokjører, FakeDatabaseprobe(svarerIkke())).feilen()

        feil.shouldBeInstanceOf<LokalPostgresFeil.KommandoTidsavbrutt>()
        feil.kommando shouldBe "docker compose version"
    }

    @Test
    fun `gir opp med hint om portkonflikt når noe annet lytter på porten`(@TempDir rot: Path) {
        rot.medComposefil()
        val kommandokjører = FakeKommandokjører { kommando ->
            when {
                "config" in kommando -> vellykket("postgresMeldekort\n")
                else -> vellykket()
            }
        }
        val probe = FakeDatabaseprobe(svarerIkke("FATAL: password authentication failed"), portenSvarer = true)

        val feil = start(rot, kommandokjører, probe).feilen()

        feil.shouldBeInstanceOf<LokalPostgresFeil.DatabaseSvarteIkke>()
        feil.portenSvarer shouldBe true
        feil.somMelding() shouldContain "lsof -nP -iTCP:5435"
        feil.somMelding() shouldContain "password authentication failed"
        // Passordet skal ikke ligge i loggen selv om det er en lokal testverdi.
        feil.somMelding() shouldContain "password=*****"
    }

    @Test
    fun `sier fra når postgres-driveren mangler i stedet for å starte containere`(@TempDir rot: Path) {
        rot.medComposefil()
        val kommandokjører = FakeKommandokjører { vellykket() }
        val probe = FakeDatabaseprobe(Tilkoblingsfeil.DriverMangler(ClassNotFoundException("org.postgresql.Driver")).left())

        val feil = start(rot, kommandokjører, probe).feilen()

        feil.shouldBeInstanceOf<LokalPostgresFeil.JdbcDriverMangler>()
        kommandokjører.kall shouldBe emptyList()
    }

    @Test
    fun `bruker testcontainers når miljøvariabelen sier det`(@TempDir rot: Path) {
        val kommandokjører = FakeKommandokjører { vellykket() }
        val probe = FakeDatabaseprobe(svarerIkke())

        val resultat = start(
            startkatalog = rot,
            kommandokjører = kommandokjører,
            databaseprobe = probe,
            miljø = mapOf(LokalDatabaseModus.MILJØVARIABEL to "testcontainers"),
        ).getOrFail()

        resultat.modus shouldBe LokalDatabaseModus.Testcontainers
        resultat.jdbcUrl shouldContain "54321"
        kommandokjører.kall shouldBe emptyList()
        probe.antallForsøk shouldBe 0
    }

    @Test
    fun `godtar ikke en modus vi ikke kjenner`(@TempDir rot: Path) {
        val feil = start(
            startkatalog = rot,
            kommandokjører = FakeKommandokjører { vellykket() },
            databaseprobe = FakeDatabaseprobe(svarer),
            miljø = mapOf(LokalDatabaseModus.MILJØVARIABEL to "postgres-i-skyen"),
        ).feilen()

        feil.shouldBeInstanceOf<LokalPostgresFeil.UgyldigModus>()
        feil.somMelding() shouldContain "testcontainers"
    }

    private fun start(
        startkatalog: Path,
        kommandokjører: Kommandokjører,
        databaseprobe: Databaseprobe,
        miljø: Map<String, String> = emptyMap(),
    ) = startLokalPostgres(
        config = LokalPostgresConfig(
            composeTjeneste = "postgresMeldekort",
            database = "meldekort",
            port = 5435,
            startkatalog = startkatalog,
            maksNivåerOpp = 1,
            oppstartstimeout = 3.seconds,
            lesMiljøvariabel = { navn -> miljø[navn] },
        ),
        clock = TikkendeKlokke(),
        kommandokjører = kommandokjører,
        databaseprobe = databaseprobe,
        venting = ingenVenting,
        testcontainerpostgres = { config ->
            LokalPostgres(
                jdbcUrl = "jdbc:postgresql://localhost:54321/${config.database}?user=postgres&password=test",
                modus = LokalDatabaseModus.Testcontainers,
                beskrivelse = "fake testcontainer",
            ).right()
        },
    )

    private fun Path.medComposefil(): Path =
        Files.writeString(resolve("docker-compose.yml"), "services:\n  postgresMeldekort:\n    image: postgres:17\n")
}
