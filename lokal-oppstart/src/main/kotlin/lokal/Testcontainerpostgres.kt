package no.nav.tiltakspenger.libs.lokal

import arrow.core.Either
import io.github.oshai.kotlinlogging.KotlinLogging
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.TestcontainersConfiguration

private val log = KotlinLogging.logger {}

/** Finnes for at modusvalget skal kunne testes uten å starte en container. */
internal fun interface Testcontainerpostgres {
    fun start(config: LokalPostgresConfig): Either<LokalPostgresFeil, LokalPostgres>
}

internal class TestcontainersOppstart : Testcontainerpostgres {
    override fun start(config: LokalPostgresConfig): Either<LokalPostgresFeil, LokalPostgres> {
        advarOmGjenbrukSomIkkeVirker(config)
        log.info { "Starter lokal postgres med Testcontainers (${config.postgresImage})." }
        return Either.catch { startContainer(config) }
            .mapLeft { LokalPostgresFeil.TestcontainersFeilet(config.postgresImage, it) }
            .map { container ->
                val port = container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
                LokalPostgres(
                    jdbcUrl = "jdbc:postgresql://${container.host}:$port/${config.database}?user=${config.brukernavn}&password=${config.passord}",
                    modus = LokalDatabaseModus.Testcontainers,
                    beskrivelse = "Testcontainers-container ${config.postgresImage} på ${container.host}:$port",
                )
            }
    }

    private fun startContainer(config: LokalPostgresConfig): PostgreSQLContainer =
        PostgreSQLContainer(config.postgresImage)
            .withDatabaseName(config.database)
            .withUsername(config.brukernavn)
            .withPassword(config.passord)
            .let { container -> config.walLevel?.let { container.withCommand("postgres", "-c", "wal_level=$it") } ?: container }
            .withReuse(config.gjenbrukTestcontainer)
            .also { it.start() }

    /**
     * Gjenbruk må slås på i miljøet i tillegg til i koden.
     * Uten den beskjeden framstår det som en feil at databasen er tom etter hver omstart.
     */
    private fun advarOmGjenbrukSomIkkeVirker(config: LokalPostgresConfig) {
        if (config.gjenbrukTestcontainer && !TestcontainersConfiguration.getInstance().environmentSupportsReuse()) {
            log.warn {
                "Testcontainers gjenbruker ikke containeren, så databasen er tom ved hver oppstart. Sett TESTCONTAINERS_REUSE_ENABLE=true (eller testcontainers.reuse.enable=true i ~/.testcontainers.properties) for å beholde dataene."
            }
        }
    }
}
