package no.nav.tiltakspenger.libs.lokal

import arrow.core.Either
import arrow.core.flatMap
import java.net.InetSocketAddress
import java.net.Socket
import java.sql.DriverManager
import kotlin.time.Duration

/** Hvorfor et tilkoblingsforsøk mot postgres ikke gikk. */
internal sealed interface Tilkoblingsfeil {
    /** Driveren finnes ikke på klassestien — det hjelper ikke å prøve igjen. */
    data class DriverMangler(val årsak: Throwable) : Tilkoblingsfeil

    /** Databasen svarte ikke (ennå). */
    data class Mislyktes(val melding: String) : Tilkoblingsfeil
}

/** Finnes for at oppstartslogikken skal kunne testes uten postgres. */
internal interface Databaseprobe {
    /** Åpner og lukker en tilkobling for å se om databasen faktisk svarer. */
    fun prøvTilkobling(jdbcUrl: String, timeout: Duration): Either<Tilkoblingsfeil, Unit>

    /** Om noe i det hele tatt lytter på porten — skiller «ingen container» fra «feil tjeneste på porten». */
    fun portenSvarer(vert: String, port: Int, timeout: Duration): Boolean
}

internal class JdbcDatabaseprobe : Databaseprobe {
    override fun prøvTilkobling(jdbcUrl: String, timeout: Duration): Either<Tilkoblingsfeil, Unit> =
        Either.catch { Class.forName(DRIVERKLASSE) }
            .mapLeft { Tilkoblingsfeil.DriverMangler(it) }
            .flatMap {
                Either.catch { DriverManager.getConnection(medTimeout(jdbcUrl, timeout)).close() }
                    .mapLeft { Tilkoblingsfeil.Mislyktes(it.message?.trim() ?: it.toString()) }
            }

    override fun portenSvarer(vert: String, port: Int, timeout: Duration): Boolean =
        Either.catch {
            Socket().use { it.connect(InetSocketAddress(vert, port), timeout.inWholeMilliseconds.toInt()) }
        }.isRight()

    /** Uten disse arver forsøket driverens standardtimeout, og en hengende port gir oss ingen kontroll over hvor lenge vi blokkerer. */
    private fun medTimeout(jdbcUrl: String, timeout: Duration): String {
        val sekunder = maxOf(1, timeout.inWholeSeconds)
        val skille = if (jdbcUrl.contains("?")) "&" else "?"
        return "$jdbcUrl${skille}connectTimeout=$sekunder&socketTimeout=$sekunder&loginTimeout=$sekunder"
    }

    private companion object {
        private const val DRIVERKLASSE = "org.postgresql.Driver"
    }
}

/** Finnes for at tester skal slippe å sove. */
internal fun interface Venting {
    fun vent(varighet: Duration, steg: String): Either<LokalPostgresFeil.Avbrutt, Unit>
}

internal class TrådsøvnVenting : Venting {
    override fun vent(varighet: Duration, steg: String): Either<LokalPostgresFeil.Avbrutt, Unit> =
        Either.catch { Thread.sleep(varighet.inWholeMilliseconds) }
            .mapLeft {
                Thread.currentThread().interrupt()
                LokalPostgresFeil.Avbrutt(steg)
            }
}
