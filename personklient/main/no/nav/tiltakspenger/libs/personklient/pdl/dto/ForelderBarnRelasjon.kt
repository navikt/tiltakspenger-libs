package no.nav.tiltakspenger.libs.personklient.pdl.dto

import no.nav.tiltakspenger.libs.person.BarnUtenFolkeregisteridentifikator
import java.time.LocalDate

internal enum class ForelderBarnRelasjonRolle {
    BARN,
    MOR,
    FAR,
    MEDMOR,
}

internal data class Personnavn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
)

internal enum class KjoennType {
    MANN,
    KVINNE,
    UKJENT,
}

internal data class RelatertBiPerson(
    val navn: Personnavn?,
    val foedselsdato: LocalDate?,
    val statsborgerskap: String?,
    val kjoenn: KjoennType?,
)

internal data class ForelderBarnRelasjon(
    val relatertPersonsIdent: String?,
    val relatertPersonsRolle: ForelderBarnRelasjonRolle,
    val minRolleForPerson: ForelderBarnRelasjonRolle?,
    val relatertPersonUtenFolkeregisteridentifikator: RelatertBiPerson?,
    override val folkeregistermetadata: FolkeregisterMetadata?,
    override val metadata: EndringsMetadata,
) : Changeable

internal fun List<ForelderBarnRelasjon>.toIdenterForBarnIFolkeregisteret(): List<String> {
    return this
        .asSequence()
        .filter { it.relatertPersonsRolle == ForelderBarnRelasjonRolle.BARN }
        .mapNotNull { it.relatertPersonsIdent }
        .distinct()
        .map { it }
        .toList()
}

internal fun List<ForelderBarnRelasjon>.toBarnUtenforFolkeregisteret(): List<BarnUtenFolkeregisteridentifikator> {
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
