package no.nav.tiltakspenger.libs.lokal

import arrow.core.Either
import arrow.core.flatten
import arrow.core.left
import arrow.core.right
import java.io.BufferedReader
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/** Resultatet av en ferdigkjørt kommando. */
internal data class Kommandoresultat(
    val exitkode: Int,
    val standardUt: String,
    val standardFeil: String,
) {
    val vellykket: Boolean = exitkode == 0

    /** Begge strømmene slått sammen, til bruk i feilmeldinger. */
    val utdata: String = listOf(standardUt, standardFeil).filter { it.isNotBlank() }.joinToString("\n").trim()
}

/** Kommandoen kom aldri så langt som til en exitkode. */
internal sealed interface Kommandofeil {
    data class KunneIkkeStarte(val årsak: Throwable) : Kommandofeil
    data object Tidsavbrutt : Kommandofeil
    data object Avbrutt : Kommandofeil
}

/** Finnes for at oppstartslogikken skal kunne testes uten docker. */
internal fun interface Kommandokjører {
    fun kjør(kommando: List<String>, arbeidskatalog: Path?, timeout: Duration): Either<Kommandofeil, Kommandoresultat>
}

internal class ProsessKommandokjører : Kommandokjører {
    override fun kjør(
        kommando: List<String>,
        arbeidskatalog: Path?,
        timeout: Duration,
    ): Either<Kommandofeil, Kommandoresultat> = Either.catch { startOgVent(kommando, arbeidskatalog, timeout) }
        .mapLeft { throwable ->
            when (throwable) {
                is InterruptedException -> Kommandofeil.Avbrutt
                else -> Kommandofeil.KunneIkkeStarte(throwable)
            }
        }
        .flatten()

    private fun startOgVent(
        kommando: List<String>,
        arbeidskatalog: Path?,
        timeout: Duration,
    ): Either<Kommandofeil, Kommandoresultat> {
        val prosess = ProcessBuilder(kommando)
            .also { bygger -> arbeidskatalog?.let { bygger.directory(it.toFile()) } }
            .start()
        // Begge strømmene leses av egne tråder mens prosessen kjører.
        // Leser vi den ene først, kan prosessen blokkere på en full pipe på den andre.
        val standardUt = StringBuilder()
        val standardFeil = StringBuilder()
        val utLeser = les(prosess.inputStream.bufferedReader(), standardUt)
        val feilLeser = les(prosess.errorStream.bufferedReader(), standardFeil)
        val ferdig = prosess.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        if (!ferdig) {
            prosess.destroyForcibly()
        }
        utLeser.join()
        feilLeser.join()
        return when (ferdig) {
            true -> Kommandoresultat(prosess.exitValue(), standardUt.toString().trim(), standardFeil.toString().trim()).right()
            false -> Kommandofeil.Tidsavbrutt.left()
        }
    }

    private fun les(leser: BufferedReader, mål: StringBuilder): Thread = Thread.ofVirtual().start {
        leser.use { åpenLeser -> åpenLeser.forEachLine { linje -> mål.appendLine(linje) } }
    }
}
