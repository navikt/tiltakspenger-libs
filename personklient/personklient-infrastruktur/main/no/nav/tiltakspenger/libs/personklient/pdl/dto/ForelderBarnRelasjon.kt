package no.nav.tiltakspenger.libs.personklient.pdl.dto

import no.nav.tiltakspenger.libs.person.BarnUtenFolkeregisteridentifikator
import java.time.LocalDate

enum class ForelderBarnRelasjonRolle {
    BARN,
    MOR,
    FAR,
    MEDMOR,
}

data class Personnavn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
)

data class RelatertBiPerson(
    val navn: Personnavn? = null,
    val foedselsdato: LocalDate? = null,
    val statsborgerskap: String? = null,
)

data class ForelderBarnRelasjon(
    val relatertPersonsIdent: String? = null,
    val relatertPersonsRolle: ForelderBarnRelasjonRolle,
    val minRolleForPerson: ForelderBarnRelasjonRolle? = null,
    val relatertPersonUtenFolkeregisteridentifikator: RelatertBiPerson? = null,
    override val folkeregistermetadata: FolkeregisterMetadata? = null,
    override val metadata: EndringsMetadata,
) : Changeable

fun List<ForelderBarnRelasjon>.toIdenterForBarnIFolkeregisteret(): List<String> {
    return this
        .asSequence()
        .filter { it.relatertPersonsRolle == ForelderBarnRelasjonRolle.BARN }
        .mapNotNull { it.relatertPersonsIdent }
        .distinct()
        .map { it }
        .toList()
}

fun List<ForelderBarnRelasjon>.toBarnUtenforFolkeregisteret(): List<BarnUtenFolkeregisteridentifikator> {
    return this
        .asSequence()
        .filter { it.relatertPersonsRolle == ForelderBarnRelasjonRolle.BARN }
        .filter { it.relatertPersonUtenFolkeregisteridentifikator != null }
        .map {
            val barn = it.relatertPersonUtenFolkeregisteridentifikator!!
            val navn = barn.navn
            BarnUtenFolkeregisteridentifikator(
                fornavn = navn?.fornavn,
                mellomnavn = navn?.mellomnavn,
                etternavn = navn?.etternavn,
                fødselsdato = barn.foedselsdato,
                statsborgerskap = barn.statsborgerskap,
            )
        }
        .toList()
}
