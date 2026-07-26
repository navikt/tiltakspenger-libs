package no.nav.tiltakspenger.libs.lokal

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Clock
import java.time.Instant

private val log = KotlinLogging.logger {}

/** Den lokale databasen appen kan koble seg til. */
data class LokalPostgres(
    /** Url-en appen skal bruke — sett den som `DB_JDBC_URL` før konfigurasjonen leses. */
    val jdbcUrl: String,
    val modus: LokalDatabaseModus,
    /** Kort beskrivelse av hvor databasen kom fra, til loggen. */
    val beskrivelse: String,
)

/**
 * Skaffer en lokal postgres-database, slik at `LokalMain` kan startes uten at utvikleren først må kjøre kommandoer i et terminalvindu.
 *
 * I [LokalDatabaseModus.DockerCompose] (standard) sjekker vi først om databasen allerede svarer.
 * Gjør den det, rører vi ingenting.
 * Ellers leter vi opp compose-fila, starter tjenesten og venter til databasen svarer.
 *
 * I [LokalDatabaseModus.Testcontainers] starter vi en egen container og returnerer jdbc-url-en til den.
 *
 * Alt som kan gå galt er modellert i [LokalPostgresFeil] — logg den med [somMelding] og avslutt appen, i stedet for å la den falle over på en tilkoblingsfeil.
 */
fun startLokalPostgres(
    config: LokalPostgresConfig,
    clock: Clock,
): Either<LokalPostgresFeil, LokalPostgres> = startLokalPostgres(
    config = config,
    clock = clock,
    kommandokjører = ProsessKommandokjører(),
    databaseprobe = JdbcDatabaseprobe(),
    venting = TrådsøvnVenting(),
    testcontainerpostgres = TestcontainersOppstart(),
)

internal fun startLokalPostgres(
    config: LokalPostgresConfig,
    clock: Clock,
    kommandokjører: Kommandokjører,
    databaseprobe: Databaseprobe,
    venting: Venting,
    testcontainerpostgres: Testcontainerpostgres,
): Either<LokalPostgresFeil, LokalPostgres> = either {
    val modus = config.modusOverstyring ?: LokalDatabaseModus.fraMiljø(config.lesMiljøvariabel).bind()
    when (modus) {
        LokalDatabaseModus.DockerCompose -> medDockerCompose(config, clock, kommandokjører, databaseprobe, venting).bind()
        LokalDatabaseModus.Testcontainers -> testcontainerpostgres.start(config).bind()
    }
}

private fun medDockerCompose(
    config: LokalPostgresConfig,
    clock: Clock,
    kommandokjører: Kommandokjører,
    databaseprobe: Databaseprobe,
    venting: Venting,
): Either<LokalPostgresFeil, LokalPostgres> {
    when (val alleredeOppe = databaseprobe.prøvTilkobling(config.jdbcUrl, config.tilkoblingstimeout)) {
        is Either.Right -> {
            log.info { "Lokal postgres svarer allerede på ${config.vert}:${config.port}." }
            return LokalPostgres(
                jdbcUrl = config.jdbcUrl,
                modus = LokalDatabaseModus.DockerCompose,
                beskrivelse = "allerede oppe på ${config.vert}:${config.port}",
            ).right()
        }

        is Either.Left -> when (val feil = alleredeOppe.value) {
            // Uten driver kommer vi ingen vei, og en container til hjelper ikke.
            is Tilkoblingsfeil.DriverMangler -> return LokalPostgresFeil.JdbcDriverMangler(feil.årsak).left()

            is Tilkoblingsfeil.Mislyktes ->
                log.info { "Lokal postgres svarer ikke på ${config.vert}:${config.port} (${feil.melding}) — starter den." }
        }
    }
    return DockerCompose(config, kommandokjører).startTjeneste().flatMap { composefil ->
        ventPåDatabasen(config, clock, databaseprobe, venting).map {
            LokalPostgres(
                jdbcUrl = config.jdbcUrl,
                modus = LokalDatabaseModus.DockerCompose,
                beskrivelse = "tjenesten «${config.composeTjeneste}» startet fra $composefil",
            )
        }
    }
}

/**
 * Containeren er startet, men postgres tar noen sekunder på å ta imot tilkoblinger — og første gang må imaget lastes ned.
 * Vi prøver til [LokalPostgresConfig.oppstartstimeout] er brukt opp, og tar vare på siste feil til feilmeldingen.
 */
private fun ventPåDatabasen(
    config: LokalPostgresConfig,
    clock: Clock,
    databaseprobe: Databaseprobe,
    venting: Venting,
): Either<LokalPostgresFeil, Unit> {
    val frist: Instant = clock.instant().plusMillis(config.oppstartstimeout.inWholeMilliseconds)
    while (true) {
        val sisteFeil = when (val resultat = databaseprobe.prøvTilkobling(config.jdbcUrl, config.tilkoblingstimeout)) {
            is Either.Right -> return Unit.right()

            is Either.Left -> when (val tilkoblingsfeil = resultat.value) {
                is Tilkoblingsfeil.DriverMangler -> return LokalPostgresFeil.JdbcDriverMangler(tilkoblingsfeil.årsak).left()
                is Tilkoblingsfeil.Mislyktes -> tilkoblingsfeil.melding
            }
        }
        if (clock.instant() >= frist) {
            return LokalPostgresFeil.DatabaseSvarteIkke(
                jdbcUrl = config.jdbcUrl,
                composeTjeneste = config.composeTjeneste,
                port = config.port,
                ventet = config.oppstartstimeout,
                portenSvarer = databaseprobe.portenSvarer(config.vert, config.port, config.tilkoblingstimeout),
                sisteFeil = sisteFeil,
            ).left()
        }
        venting.vent(config.pollintervall, "venting på at postgres skal svare").onLeft { return it.left() }
    }
}
