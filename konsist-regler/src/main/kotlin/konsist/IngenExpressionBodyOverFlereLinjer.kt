package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration

/**
 * En funksjon med expression body skal ha hele uttrykket på én linje: `fun navn() = uttrykk`.
 * Går uttrykket over flere linjer, skal funksjonen skrives med blokk og `return`.
 * En expression body over flere linjer gjør det mindre tydelig hvor funksjonen slutter og hva den returnerer, og uttrykket kan bli langt uten at noen merker det.
 *
 * Regelen ser bare på uttrykket etter `=`.
 * En signatur som går over flere linjer, er grei så lenge uttrykket står på én linje.
 * Det er også greit med linjeskift rett etter `=` når uttrykket står på én linje.
 * Sjekken er tekstbasert: uttrykket er teksten etter første `=` som står utenfor parenteser og klammer etter parameterlista.
 * Vinkelparenteser telles ikke, siden typeparametre og returtyper aldri inneholder `=`.
 *
 * [unntatteFilstier] inneholder sti-suffikser for filer som ennå ikke følger regelen.
 * [assertWhitelistenErRyddet] finner unntak som kan fjernes.
 */
object IngenExpressionBodyOverFlereLinjer {

    fun brudd(scope: KoScope, unntatteFilstier: Set<String> = emptySet()): List<String> = scope.kildefiler()
        .filterNot { file -> unntatteFilstier.any { sti -> file.path.endsWith(sti) } }
        .flatMap { file -> file.functions(includeNested = true, includeLocal = true) }
        .filter { funksjon -> funksjon.hasExpressionBody && funksjon.expressionBody()?.trim()?.contains('\n') == true }
        .map { funksjon -> "${funksjon.location}: ${funksjon.name}" }

    fun assert(scope: KoScope, unntatteFilstier: Set<String> = emptySet()) = assertIngenBrudd(
        brudd(scope, unntatteFilstier),
        "En expression body skal stå på én linje. Skriv funksjonen med blokk og return, eller skriv uttrykket på én linje.",
    )

    /** Teksten etter `=` som utgjør uttrykket, eller null hvis den ikke finnes. */
    private fun KoFunctionDeclaration.expressionBody(): String? {
        val utenKDoc = if (text.trimStart().startsWith("/**")) text.indexOf("*/") + 2 else 0
        val start = Regex("""\bfun\b""").find(text, utenKDoc)?.range?.last ?: return null
        var dybde = 0
        var parameterlistaErLest = false
        for (index in start + 1 until text.length) {
            when (text[index]) {
                '(', '[', '{' -> dybde++

                ')', ']', '}' -> {
                    dybde--
                    if (dybde == 0 && text[index] == ')') parameterlistaErLest = true
                }

                '=' -> if (dybde == 0 && parameterlistaErLest) return text.substring(index + 1)
            }
        }
        return null
    }
}
