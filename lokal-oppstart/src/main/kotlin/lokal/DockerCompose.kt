package no.nav.tiltakspenger.libs.lokal

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path

private val log = KotlinLogging.logger {}

/**
 * Starter én tjeneste fra en docker compose-fil, og oversetter alt som kan gå galt underveis til [LokalPostgresFeil].
 * Compose-fila letes fram ved å gå oppover fra [LokalPostgresConfig.startkatalog], slik at et sub-repo finner monorepoets fil i rota.
 */
internal class DockerCompose(
    private val config: LokalPostgresConfig,
    private val kommandokjører: Kommandokjører,
) {
    /** Starter tjenesten hvis den ikke allerede kjører, og returnerer compose-fila vi brukte. */
    fun startTjeneste(): Either<LokalPostgresFeil, Path> = either {
        val kommando = finnKommando().bind()
        val composefil = finnComposefil(kommando).bind()
        log.info { "Starter «${config.composeTjeneste}» fra $composefil." }
        val fullKommando = kommando + listOf("-f", composefil.fileName.toString(), "up", "-d", config.composeTjeneste)
        kjør(fullKommando, composefil.parent).bind()
        composefil
    }

    /**
     * Docker compose finnes både som plugin (`docker compose`) og som frittstående binær (`docker-compose`).
     * Vi tar den første som svarer.
     */
    private fun finnKommando(): Either<LokalPostgresFeil, List<String>> {
        var sisteÅrsak: Throwable? = null
        KOMMANDOKANDIDATER.forEach { kandidat ->
            when (val resultat = kommandokjører.kjør(kandidat + "version", null, config.kommandotimeout)) {
                is Either.Right -> if (resultat.value.vellykket) return kandidat.right()

                is Either.Left -> when (val feil = resultat.value) {
                    is Kommandofeil.KunneIkkeStarte -> sisteÅrsak = feil.årsak

                    Kommandofeil.Tidsavbrutt -> return LokalPostgresFeil.KommandoTidsavbrutt(
                        kommando = (kandidat + "version").joinToString(" "),
                        timeout = config.kommandotimeout,
                    ).left()

                    Kommandofeil.Avbrutt -> return LokalPostgresFeil.Avbrutt("oppstart av docker compose").left()
                }
            }
        }
        return LokalPostgresFeil.DockerMangler(
            prøvdeKommandoer = KOMMANDOKANDIDATER.map { it.joinToString(" ") },
            årsak = sisteÅrsak,
        ).left()
    }

    private fun finnComposefil(kommando: List<String>): Either<LokalPostgresFeil, Path> {
        config.composefil?.let { oppgittFil ->
            return when (Files.isRegularFile(oppgittFil)) {
                true -> oppgittFil.right()
                false -> LokalPostgresFeil.ComposefilFinnesIkke(oppgittFil).left()
            }
        }
        val kandidater = composefilkandidater()
        if (kandidater.isEmpty()) {
            return LokalPostgresFeil.FantIngenComposefil(
                startkatalog = config.startkatalog,
                filnavn = config.composefilnavn,
                maksNivåerOpp = config.maksNivåerOpp,
            ).left()
        }
        // Vi slutter å spørre docker så snart en fil har tjenesten, men tar vare på de vi rakk å se på til feilmeldingen.
        val tjenesterPerFil = buildMap<Path, List<String>> {
            kandidater.forEach { kandidat ->
                if (values.none { config.composeTjeneste in it }) {
                    put(kandidat, tjenesterI(kommando, kandidat).getOrElse { return it.left() })
                }
            }
        }
        tjenesterPerFil.entries.firstOrNull { config.composeTjeneste in it.value }?.let { return it.key.right() }
        return LokalPostgresFeil.FantIkkeTjenesten(config.composeTjeneste, tjenesterPerFil).left()
    }

    /** Compose-filer i [LokalPostgresConfig.startkatalog] og oppover, nærmeste først. */
    private fun composefilkandidater(): List<Path> =
        generateSequence(config.startkatalog.toAbsolutePath().normalize()) { it.parent }
            .take(config.maksNivåerOpp + 1)
            .flatMap { katalog -> config.composefilnavn.map { katalog.resolve(it) } }
            .filter { Files.isRegularFile(it) }
            .toList()

    private fun tjenesterI(kommando: List<String>, composefil: Path): Either<LokalPostgresFeil, List<String>> {
        val fullKommando = kommando + listOf("-f", composefil.fileName.toString(), "config", "--services")
        return kjør(fullKommando, composefil.parent)
            .mapLeft { feil ->
                when (feil) {
                    is LokalPostgresFeil.ComposeKommandoFeilet -> LokalPostgresFeil.ComposefilKunneIkkeLeses(
                        composefil = composefil,
                        kommando = feil.kommando,
                        utdata = feil.utdata,
                    )

                    else -> feil
                }
            }
            .map { resultat -> resultat.standardUt.lines().map { it.trim() }.filter { it.isNotBlank() } }
    }

    private fun kjør(kommando: List<String>, arbeidskatalog: Path): Either<LokalPostgresFeil, Kommandoresultat> {
        val vist = kommando.joinToString(" ")
        return when (val resultat = kommandokjører.kjør(kommando, arbeidskatalog, config.kommandotimeout)) {
            is Either.Left -> when (val feil = resultat.value) {
                is Kommandofeil.KunneIkkeStarte -> LokalPostgresFeil.DockerMangler(listOf(vist), feil.årsak).left()
                Kommandofeil.Tidsavbrutt -> LokalPostgresFeil.KommandoTidsavbrutt(vist, config.kommandotimeout).left()
                Kommandofeil.Avbrutt -> LokalPostgresFeil.Avbrutt("kjøring av `$vist`").left()
            }

            is Either.Right -> when {
                resultat.value.vellykket -> resultat.value.right()

                resultat.value.utdata.pekerPåDockerdemonen() ->
                    LokalPostgresFeil.DockerDemonSvarerIkke(vist, resultat.value.utdata).left()

                else -> LokalPostgresFeil.ComposeKommandoFeilet(vist, resultat.value.exitkode, resultat.value.utdata).left()
            }
        }
    }

    private companion object {
        private val KOMMANDOKANDIDATER = listOf(listOf("docker", "compose"), listOf("docker-compose"))

        /** Docker-klienten sier fra på litt ulike måter når demonen ikke er å få tak i. */
        private val DEMONMARKØRER = listOf(
            "cannot connect to the docker daemon",
            "is the docker daemon running",
            "docker daemon is not running",
            "error during connect",
        )

        private fun String.pekerPåDockerdemonen(): Boolean = lowercase().let { tekst -> DEMONMARKØRER.any { it in tekst } }
    }
}
