package no.nav.tiltakspenger.libs.lokal

import java.nio.file.Path
import kotlin.time.Duration

/**
 * Alt som kan gå galt når [startLokalPostgres] skal skaffe en lokal database.
 * Hver variant bærer konteksten som trengs for å forstå og fikse feilen, og oversetter det underliggende problemet — som regel en exception fra docker, JDBC eller Testcontainers — til noe en utvikler kan handle på.
 *
 * [hva] beskriver problemet, [løsning] hva du gjør med det.
 * Bruk [somMelding] når feilen skal logges.
 */
sealed interface LokalPostgresFeil {
    /** Hva som er galt. */
    val hva: String

    /** Hva du gjør for å komme videre. */
    val løsning: String

    /** Den underliggende exceptionen, når feilen kommer fra noe som kastet. */
    val årsak: Throwable?

    /** [LokalDatabaseModus.MILJØVARIABEL] er satt til noe vi ikke kjenner igjen. */
    data class UgyldigModus(
        val verdi: String,
        val gyldigeVerdier: List<String>,
    ) : LokalPostgresFeil {
        override val hva = "${LokalDatabaseModus.MILJØVARIABEL}=$verdi er ikke en modus vi kjenner."
        override val løsning = "Gyldige verdier: ${gyldigeVerdier.joinToString(", ")}. Fjern variabelen for å bruke docker compose."
        override val årsak: Throwable? = null
    }

    /** Jdbc-url-en vi skulle utlede konfigurasjonen fra ga ikke mening. */
    data class UgyldigJdbcUrl(
        val jdbcUrl: String,
        val begrunnelse: String,
    ) : LokalPostgresFeil {
        override val hva = "Klarte ikke å utlede lokal database-config fra jdbc-url-en «${jdbcUrl.utenPassord()}»: $begrunnelse."
        override val løsning =
            "Url-en må ha formen jdbc:postgresql://<vert>:<port>/<database>?user=<bruker>&password=<passord>. Alternativt kan du bygge LokalPostgresConfig direkte med port, database, brukernavn og passord."
        override val årsak: Throwable? = null
    }

    /** Vi fant ingen docker-kommando å kjøre. */
    data class DockerMangler(
        val prøvdeKommandoer: List<String>,
        override val årsak: Throwable?,
    ) : LokalPostgresFeil {
        override val hva = "Fant ingen docker compose-kommando å kjøre (prøvde: ${prøvdeKommandoer.joinToString(", ")})."
        override val løsning =
            "Installer Docker (Docker Desktop, Colima, Rancher Desktop e.l.) og sjekk at `docker compose version` virker i samme skall som du starter appen fra. Trenger du ikke compose-oppsettet, kjør heller med ${LokalDatabaseModus.MILJØVARIABEL}=testcontainers."
    }

    /** Docker-klienten finnes, men får ikke kontakt med demonen. */
    data class DockerDemonSvarerIkke(
        val kommando: String,
        val utdata: String,
    ) : LokalPostgresFeil {
        override val hva = "Docker-demonen svarer ikke — `$kommando` kom ikke gjennom."
        override val løsning =
            "Start Docker og prøv igjen: `colima start` (eller `brew services start colima`), eventuelt Docker Desktop. Bruker du Colima, sjekk at DOCKER_HOST peker på colima-socketen. Utdata fra docker:\n$utdata"
        override val årsak: Throwable? = null
    }

    /** Vi lette oppover i katalogtreet, men fant ingen compose-fil. */
    data class FantIngenComposefil(
        val startkatalog: Path,
        val filnavn: List<String>,
        val maksNivåerOpp: Int,
    ) : LokalPostgresFeil {
        override val hva =
            "Fant ingen compose-fil (${filnavn.joinToString(", ")}) i $startkatalog eller inntil $maksNivåerOpp nivåer over."
        override val løsning =
            "Start appen fra et sub-repo som ligger i monorepoet, der `docker-compose.yml` ligger i rota. Ligger repoet et annet sted, sett `composefil` i LokalPostgresConfig eller kjør med ${LokalDatabaseModus.MILJØVARIABEL}=testcontainers."
        override val årsak: Throwable? = null
    }

    /** Compose-filene vi fant har ikke tjenesten vi leter etter. */
    data class FantIkkeTjenesten(
        val tjeneste: String,
        val tjenesterPerFil: Map<Path, List<String>>,
    ) : LokalPostgresFeil {
        override val hva = "Fant ingen tjeneste som heter «$tjeneste» i compose-filene vi undersøkte."
        override val løsning = buildString {
            append("Sjekk navnet mot compose-fila. Tjenester vi fant:")
            tjenesterPerFil.forEach { (fil, tjenester) ->
                append("\n  $fil: ${tjenester.joinToString(", ").ifBlank { "(ingen)" }}")
            }
        }
        override val årsak: Throwable? = null
    }

    /** Compose-fila er oppgitt eksplisitt i config, men finnes ikke. */
    data class ComposefilFinnesIkke(
        val composefil: Path,
    ) : LokalPostgresFeil {
        override val hva = "Compose-fila $composefil finnes ikke."
        override val løsning = "Rett `composefil` i LokalPostgresConfig, eller la den stå tom så leter vi oppover fra arbeidskatalogen."
        override val årsak: Throwable? = null
    }

    /** Compose kunne ikke lese fila — typisk ugyldig yaml eller en variabel som ikke er satt. */
    data class ComposefilKunneIkkeLeses(
        val composefil: Path,
        val kommando: String,
        val utdata: String,
    ) : LokalPostgresFeil {
        override val hva = "Docker compose klarte ikke å lese $composefil."
        override val løsning = "Kjør `$kommando` selv for å se hva som er galt med fila. Utdata:\n$utdata"
        override val årsak: Throwable? = null
    }

    /** `docker compose up` feilet. */
    data class ComposeKommandoFeilet(
        val kommando: String,
        val exitkode: Int,
        val utdata: String,
    ) : LokalPostgresFeil {
        override val hva = "`$kommando` avsluttet med exitkode $exitkode."
        override val løsning = "Kjør kommandoen selv for å se hele bildet. Utdata:\n$utdata"
        override val årsak: Throwable? = null
    }

    /** En docker-kommando brukte for lang tid. */
    data class KommandoTidsavbrutt(
        val kommando: String,
        val timeout: Duration,
    ) : LokalPostgresFeil {
        override val hva = "`$kommando` brukte mer enn $timeout og ble avbrutt."
        override val løsning =
            "Første oppstart laster ned imaget og kan ta tid på en treg linje — prøv igjen, eller kjør kommandoen selv og se hvor den henger. Du kan øke `kommandotimeout` i LokalPostgresConfig."
        override val årsak: Throwable? = null
    }

    /** Postgres-driveren ligger ikke på klassestien. */
    data class JdbcDriverMangler(
        override val årsak: Throwable,
    ) : LokalPostgresFeil {
        override val hva = "Fant ikke postgres-driveren (org.postgresql.Driver) på klassestien."
        override val løsning = "Legg til `org.postgresql:postgresql` som avhengighet i appen — vi bruker den for å sjekke at databasen faktisk svarer."
    }

    /** Containeren ble startet, men databasen svarte aldri. */
    data class DatabaseSvarteIkke(
        val jdbcUrl: String,
        val composeTjeneste: String,
        val port: Int,
        val ventet: Duration,
        val portenSvarer: Boolean,
        val sisteFeil: String?,
    ) : LokalPostgresFeil {
        override val hva = "Postgres på ${jdbcUrl.utenPassord()} svarte ikke innen $ventet."
        override val løsning = when {
            portenSvarer ->
                "Noe lytter på port $port, men svarer ikke som postgres — sannsynligvis en annen tjeneste på samme port. Sjekk med `lsof -nP -iTCP:$port -sTCP:LISTEN`.${sisteFeilLinje()}"

            else ->
                "Sjekk at containeren kjører og hva den sier: `docker compose ps $composeTjeneste` og `docker compose logs $composeTjeneste`.${sisteFeilLinje()}"
        }
        override val årsak: Throwable? = null

        private fun sisteFeilLinje(): String = sisteFeil?.let { "\nSiste feil fra tilkoblingsforsøket: $it" }.orEmpty()
    }

    /** Testcontainers klarte ikke å starte containeren. */
    data class TestcontainersFeilet(
        val postgresImage: String,
        override val årsak: Throwable,
    ) : LokalPostgresFeil {
        override val hva = "Testcontainers klarte ikke å starte $postgresImage: ${årsak.message ?: årsak::class.simpleName}."
        override val løsning =
            "Sjekk at Docker kjører (`docker version`). Testcontainers finner docker via DOCKER_HOST eller ~/.testcontainers.properties — bruker du Colima, må DOCKER_HOST peke på colima-socketen."
    }

    /** Tråden ble avbrutt mens vi ventet. */
    data class Avbrutt(
        val steg: String,
    ) : LokalPostgresFeil {
        override val hva = "Oppstarten ble avbrutt under $steg."
        override val løsning = "Start appen på nytt."
        override val årsak: Throwable? = null
    }
}

/** Feilen som én loggbar tekst: hva som er galt, og hva du gjør med det. */
fun LokalPostgresFeil.somMelding(): String = """
    Kunne ikke skaffe en lokal postgres-database.
    $hva
    $løsning
""".trimIndent()

/** Passordet er en lokal testverdi, men det har ingenting i en loggmelding å gjøre. */
internal fun String.utenPassord(): String = replace(Regex("(?<=password=)[^&]*"), "*****")
