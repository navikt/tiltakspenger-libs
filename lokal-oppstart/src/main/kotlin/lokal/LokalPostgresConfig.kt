package no.nav.tiltakspenger.libs.lokal

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import java.net.URI
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Alt [startLokalPostgres] trenger for å skaffe en lokal database.
 * Bygg den helst med [fraJdbcUrl] fra appens egen lokal-konfigurasjon, så kan ikke port, database og bruker komme ut av takt med det appen faktisk kobler seg til.
 *
 * Standardverdiene er de vi bruker i monorepoets `docker-compose.yml`.
 */
data class LokalPostgresConfig(
    /** Navnet på tjenesten i compose-fila, f.eks. `postgresMeldekort`. */
    val composeTjeneste: String,
    /** Databasenavnet, f.eks. `meldekort`. */
    val database: String,
    /** Porten compose publiserer på verten, f.eks. 5435. */
    val port: Int,
    val vert: String = "127.0.0.1",
    val brukernavn: String = "postgres",
    val passord: String = "test",
    /** Imaget som brukes i [LokalDatabaseModus.Testcontainers]; ubrukt i compose-modus, der compose-fila bestemmer. */
    val postgresImage: String = "postgres:17-alpine",
    /** Settes kun av apper som trenger logisk replikering (`wal_level=logical`) lokalt; ubrukt i compose-modus. */
    val walLevel: String? = null,
    /** Gjenbruk av testcontaineren mellom kjøringer krever i tillegg `TESTCONTAINERS_REUSE_ENABLE=true` i miljøet. */
    val gjenbrukTestcontainer: Boolean = true,
    /** Sett denne for å peke rett på en compose-fil i stedet for å lete etter den. */
    val composefil: Path? = null,
    /** Katalogen vi begynner å lete etter compose-fila i, og går oppover fra. */
    val startkatalog: Path = Path.of(System.getProperty("user.dir")),
    val composefilnavn: List<String> = listOf("docker-compose.yml", "docker-compose.yaml", "compose.yml", "compose.yaml"),
    /** Hvor mange nivåer over [startkatalog] vi leter i før vi gir opp. */
    val maksNivåerOpp: Int = 6,
    /** Hvor lenge vi venter på at databasen svarer etter at containeren er startet — første gang må imaget lastes ned. */
    val oppstartstimeout: Duration = 3.minutes,
    /** Hvor lenge vi venter på hvert enkelt tilkoblingsforsøk. */
    val tilkoblingstimeout: Duration = 2.seconds,
    /** Hvor lenge vi venter mellom tilkoblingsforsøkene. */
    val pollintervall: Duration = 500.milliseconds,
    /** Hvor lenge en enkelt docker-kommando får bruke. */
    val kommandotimeout: Duration = 5.minutes,
    /** Overstyrer modus i kode og hopper over [LokalDatabaseModus.MILJØVARIABEL]. */
    val modusOverstyring: LokalDatabaseModus? = null,
    /** Sømmen mot miljøet, slik at modus-valget kan testes uten å sette miljøvariabler. */
    val lesMiljøvariabel: (String) -> String? = { navn -> System.getenv(navn) ?: System.getProperty(navn) },
) {
    /** Url-en appen skal koble seg til i [LokalDatabaseModus.DockerCompose]. */
    val jdbcUrl: String = "jdbc:postgresql://$vert:$port/$database?user=$brukernavn&password=$passord"

    companion object {
        /**
         * Utleder port, database, bruker og passord fra appens egen jdbc-url, typisk `Configuration.database()` i LOCAL-profilen.
         * Da finnes verdiene ett sted, og de kan ikke drifte fra hverandre.
         */
        fun fraJdbcUrl(
            jdbcUrl: String,
            composeTjeneste: String,
        ): Either<LokalPostgresFeil.UgyldigJdbcUrl, LokalPostgresConfig> = either {
            val uri = Either.catch { URI(jdbcUrl.removePrefix("jdbc:")) }
                .mapLeft { LokalPostgresFeil.UgyldigJdbcUrl(jdbcUrl, "den kan ikke tolkes som en URI") }
                .bind()
            val vert = uri.host
            ensureNotNull(vert) { LokalPostgresFeil.UgyldigJdbcUrl(jdbcUrl, "den mangler vertsnavn") }
            ensure(uri.port > 0) { LokalPostgresFeil.UgyldigJdbcUrl(jdbcUrl, "den mangler port") }
            val database = uri.path.orEmpty().removePrefix("/")
            ensure(database.isNotBlank()) { LokalPostgresFeil.UgyldigJdbcUrl(jdbcUrl, "den mangler databasenavn") }
            val parametre = uri.query.orEmpty()
                .split("&")
                .filter { it.contains("=") }
                .associate { parameter -> parameter.substringBefore("=") to parameter.substringAfter("=") }
            val brukernavn = parametre["user"]
            ensureNotNull(brukernavn) { LokalPostgresFeil.UgyldigJdbcUrl(jdbcUrl, "den mangler `user`-parameteren") }
            val passord = parametre["password"]
            ensureNotNull(passord) { LokalPostgresFeil.UgyldigJdbcUrl(jdbcUrl, "den mangler `password`-parameteren") }
            LokalPostgresConfig(
                composeTjeneste = composeTjeneste,
                database = database,
                port = uri.port,
                vert = vert,
                brukernavn = brukernavn,
                passord = passord,
            )
        }
    }
}
