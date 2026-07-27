package no.nav.tiltakspenger.libs.httpklient.infra.kall

import arrow.core.Nel
import arrow.core.nonEmptyListOf
import arrow.core.toNonEmptyListOrNull

/**
 * Delene i én `multipart/form-data`-body.
 *
 * Egen type i stedet for en naken `List<MultipartDel>` fordi invariantene hører hjemme i typen, ikke på hvert kallsted.
 * En body uten deler er meningsløs.
 *
 * Både [MultipartDel.feltnavn] og [MultipartDel.filnavn] må være unike.
 * Duplikater av begge slag er lovlige etter RFC 7578, men i praksis alltid en feil hos oss: mottakeren nøkler da flere deler på samme verdi og svarer med færre resultater enn vi sendte filer, uten å si fra.
 * For filnavn er dette ikke teoretisk — NAIS-antivirus gjør `files[header.Filename] = buf`, så to vedlegg som heter det samme kollapser til ett, og den ene blir aldri skannet (se navikt/tiltakspenger-soknad-api#861).
 * Har kallstedet filnavn det ikke kontrollerer, som brukeropplastede vedlegg, må det gjøre dem unike selv før delene bygges.
 *
 * Delegerer til [value], så enkoderen kan bruke typen som en vanlig `List<MultipartDel>`.
 */
data class MultipartDeler(
    val value: Nel<MultipartDel>,
) : List<MultipartDel> by value {
    constructor(del: MultipartDel) : this(nonEmptyListOf(del))

    init {
        value.map { it.feltnavn }.also {
            require(it.size == it.distinct().size) {
                "feltnavn kan ikke ha duplikate verdier, men hadde: $it"
            }
        }
        value.map { it.filnavn }.also {
            require(it.size == it.distinct().size) {
                "filnavn kan ikke ha duplikate verdier, men hadde: $it"
            }
        }
    }

    /**
     * Samme likhetskontrakt som Arrow sin [Nel]: to `MultipartDeler` med like deler er like, og typen er dessuten symmetrisk mot en vanlig `List`.
     * Den genererte data class-`equals` ville kun godtatt en annen `MultipartDeler`, mens `ArrayList.equals` på sin side godtar enhver `List` — og siden vi *er* en `List` via delegering, ville `listOf(del) == deler` og `deler == listOf(del)` gitt ulikt svar.
     * En asymmetrisk `equals` er en reell felle i `Set`/`Map` og i assertions, så den skrives for hånd her.
     */
    override fun equals(other: Any?): Boolean = when (other) {
        is MultipartDeler -> value == other.value
        else -> value == other
    }

    override fun hashCode(): Int = value.hashCode()
}

/**
 * For kallsteder som bygger delene med `mapIndexed` o.l. og dermed sitter igjen med en `List` selv om kilden var ikke-tom.
 * En egen extension i stedet for en sekundærkonstruktør fordi [Nel] blir til `List` på JVM (typeutvisking), og de to konstruktørene ville fått samme signatur.
 * Kravet er et sikkerhetsnett: har kallstedet allerede en [Nel] oppstrøms, kan det ikke feile.
 */
fun List<MultipartDel>.tilMultipartDeler(): MultipartDeler =
    MultipartDeler(requireNotNull(toNonEmptyListOrNull()) { "multipart-body må ha minst én del." })
