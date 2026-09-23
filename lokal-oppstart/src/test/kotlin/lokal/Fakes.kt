package no.nav.tiltakspenger.libs.lokal

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.assertions.AssertionErrorBuilder.Companion.fail
import java.nio.file.Path
import kotlin.time.Duration

internal data class Kall(val kommando: List<String>, val arbeidskatalog: Path?) {
    val vist: String = kommando.joinToString(" ")
}

internal class FakeKommandokjører(
    private val svar: (List<String>) -> Either<Kommandofeil, Kommandoresultat>,
) : Kommandokjører {
    val kall = mutableListOf<Kall>()

    override fun kjør(
        kommando: List<String>,
        arbeidskatalog: Path?,
        timeout: Duration,
    ): Either<Kommandofeil, Kommandoresultat> {
        kall += Kall(kommando, arbeidskatalog)
        return svar(kommando)
    }
}

internal class FakeDatabaseprobe(
    private vararg val svar: Either<Tilkoblingsfeil, Unit>,
    private val portenSvarer: Boolean = false,
) : Databaseprobe {
    private val forsøk = mutableListOf<String>()
    val antallForsøk: Int get() = forsøk.size

    override fun prøvTilkobling(jdbcUrl: String, timeout: Duration): Either<Tilkoblingsfeil, Unit> {
        forsøk += jdbcUrl
        return svar[minOf(forsøk.size - 1, svar.lastIndex)]
    }

    override fun portenSvarer(vert: String, port: Int, timeout: Duration): Boolean = portenSvarer
}

/** Tester skal ikke sove. */
internal val ingenVenting = Venting { _, _ -> Unit.right() }

internal fun vellykket(standardUt: String = ""): Either<Kommandofeil, Kommandoresultat> =
    Kommandoresultat(exitkode = 0, standardUt = standardUt, standardFeil = "").right()

internal fun mislyktes(standardFeil: String, exitkode: Int = 1): Either<Kommandofeil, Kommandoresultat> =
    Kommandoresultat(exitkode = exitkode, standardUt = "", standardFeil = standardFeil).right()

internal fun svarerIkke(melding: String = "Connection refused"): Either<Tilkoblingsfeil, Unit> =
    Tilkoblingsfeil.Mislyktes(melding).left()

internal val svarer: Either<Tilkoblingsfeil, Unit> = Unit.right()

/** Motstykket til `getOrFail()` i test-common: pakker ut feilen, eller feiler testen hvis alt gikk bra. */
internal fun <A, B> Either<A, B>.feilen(): A = fold({ it }, { fail("Forventet en feil, men fikk: $it") })
