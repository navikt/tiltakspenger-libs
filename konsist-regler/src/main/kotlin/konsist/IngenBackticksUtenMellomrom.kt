package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope

/**
 * Backticks rundt navn er kun for testnavn med mellomrom (`fun \`gjør noe fornuftig\`()`), som per konvensjon kun finnes i testkode.
 * Et gyldig navn uten mellomrom skal aldri wrappes i backticks — det gjelder alle navnetyper: funksjoner, klasser, variabler, imports og kallsteder, siden regelen matcher selve backtick-syntaksen.
 * Kotlin-nøkkelord (f.eks. `\`object\``, `\`is\``) er unntatt, siden backticks der er påkrevd av språket.
 * Kommentarer/KDoc (markdown-kodespenn), strengliteraler og flerlinjestrenger flagges ikke.
 * Deteksjonen er tekstbasert og gjør egen linjebehandling i stedet for [no.nav.tiltakspenger.libs.konsist.kodelinjer], fordi den også må hoppe over innholdet i flerlinjestrenger.
 */
object IngenBackticksUtenMellomrom {

    private val nøkkelord = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if",
        "in", "interface", "is", "null", "object", "package", "return", "super", "this",
        "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while",
    )

    private val backtickIdentifikator = Regex("""`([A-Za-zÆØÅæøå_][A-Za-z0-9ÆØÅæøå_]*)`""")

    fun brudd(scope: KoScope): List<String> = scope.kildefiler().flatMap { file ->
        val brudd = mutableListOf<String>()
        var iFlerlinjestreng = false
        file.text.lines().forEachIndexed { index, linje ->
            val trimmet = linje.trim()
            val erKommentar = trimmet.startsWith("//") || trimmet.startsWith("*") || trimmet.startsWith("/*")
            if (!iFlerlinjestreng && !erKommentar) {
                val kode = linje.utenTrailingKommentar().replace(strengliteralRegex, "\"\"")
                backtickIdentifikator.findAll(kode)
                    .filter { match -> match.groupValues[1] !in nøkkelord }
                    .forEach { match -> brudd += "${file.path}:${index + 1}: ${match.value}" }
            }
            if (!erKommentar && linje.split("\"\"\"").size % 2 == 0) {
                iFlerlinjestreng = !iFlerlinjestreng
            }
        }
        brudd
    }

    fun assert(scope: KoScope) = assertIngenBrudd(
        brudd(scope),
        "Backticks rundt navn er kun for testnavn med mellomrom. Fjern backticks rundt følgende navn.",
    )
}
