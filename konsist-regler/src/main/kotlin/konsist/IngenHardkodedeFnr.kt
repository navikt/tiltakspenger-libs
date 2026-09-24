package no.nav.tiltakspenger.libs.konsist

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readLines

/**
 * Finner sekvenser på nøyaktig 11 sifre som er hardkodet i tekstfiler i repoet.
 *
 * Verdier med 8 eller 9 som tredje siffer er syntetiske etter Folkeregisterets 2032-standard og er alltid tillatt.
 * Treff maskeres i feilmeldingen, slik at regelen ikke selv lekker fødselsnummeret til bygglogger.
 *
 * Kalleren sender repo-rota, typisk `Path.of(System.getProperty("user.dir"))`.
 */
object IngenHardkodedeFnr {
    val standardFilendelser = setOf(
        "avsc",
        "conf",
        "config",
        "csv",
        "env",
        "feature",
        "gql",
        "graphql",
        "groovy",
        "http",
        "java",
        "json",
        "json5",
        "jsonl",
        "kt",
        "kts",
        "md",
        "properties",
        "scala",
        "sh",
        "sql",
        "toml",
        "txt",
        "xml",
        "yaml",
        "yml",
    )

    fun brudd(
        rot: Path,
        ekstraEkskluderteKataloger: Set<String> = emptySet(),
        ekstraFilendelser: Set<String> = emptySet(),
    ): List<String> {
        val filendelser = standardFilendelser + ekstraFilendelser.map(String::lowercase)
        return rot
            .filerUnder(standardEkskluderteKataloger + ekstraEkskluderteKataloger) { path ->
                Files.isRegularFile(path) && path.extension.lowercase() in filendelser
            }.flatMap { fil ->
                fil.readLines().asSequence().flatMapIndexed { index, linje ->
                    fnrKandidatRegex
                        .findAll(linje)
                        .filterNot { match -> match.value[2] == '8' || match.value[2] == '9' }
                        .map { "${rot.relativize(fil)}:${index + 1}: ***********" }
                }
            }.toList().sorted()
    }

    fun assert(
        rot: Path,
        ekstraEkskluderteKataloger: Set<String> = emptySet(),
        ekstraFilendelser: Set<String> = emptySet(),
    ) = assertIngenBrudd(
        brudd(rot, ekstraEkskluderteKataloger, ekstraFilendelser),
        "Ikke hardkod 11-sifrede tall. Bruk FnrGenerator fra test-common for fødselsnumre i tester.",
    )

    private val fnrKandidatRegex = Regex("""(?<![\p{L}\d])\d{11}(?![\p{L}\d])""")
}
