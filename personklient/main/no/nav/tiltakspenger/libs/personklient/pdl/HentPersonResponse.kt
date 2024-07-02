package no.nav.tiltakspenger.libs.personklient.pdl

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import no.nav.tiltakspenger.libs.person.Person
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError.FantIkkePerson
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError.ResponsManglerPerson
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError.UkjentFeil
import no.nav.tiltakspenger.libs.personklient.pdl.dto.AdressebeskyttelseGradering
import no.nav.tiltakspenger.libs.personklient.pdl.dto.GeografiskTilknytning
import no.nav.tiltakspenger.libs.personklient.pdl.dto.PdlPerson
import no.nav.tiltakspenger.libs.personklient.pdl.dto.avklarFødsel
import no.nav.tiltakspenger.libs.personklient.pdl.dto.avklarGradering
import no.nav.tiltakspenger.libs.personklient.pdl.dto.avklarNavn
import no.nav.tiltakspenger.libs.personklient.pdl.dto.toBarnUtenforFolkeregisteret
import no.nav.tiltakspenger.libs.personklient.pdl.dto.toIdenterForBarnIFolkeregisteret
import no.nav.tiltakspenger.libs.person.AdressebeskyttelseGradering as AdressebeskyttelseGradLib

internal data class PdlResponseData(
    val hentPerson: PdlPerson?,
    val hentGeografiskTilknytning: GeografiskTilknytning?,
)

internal const val FANT_IKKE_PERSON = "Fant ikke person"

internal data class HentPersonResponse(
    val data: PdlResponseData? = null,
    val errors: List<PdlError> = emptyList(),
) {
    private fun extractPerson(): Either<FellesPersonklientError, PdlPerson> {
        return if (this.errors.isNotEmpty()) {
            if (errors.any { it.message == FANT_IKKE_PERSON }) {
                FantIkkePerson.left()
            } else {
                UkjentFeil(this.errors).left()
            }
        } else {
            this.data?.hentPerson?.right()
                ?: ResponsManglerPerson.left()
        }
    }

    private fun geografiskTilknytning(): GeografiskTilknytning? {
        return data?.hentGeografiskTilknytning
    }

    fun toPerson(): Either<FellesPersonklientError, Pair<Person, List<String>>> {
        return either {
            val person = extractPerson().bind()
            val navn = avklarNavn(person.navn).bind()
            val fødsel = avklarFødsel(person.foedsel).bind()
            val adressebeskyttelse = avklarGradering(person.adressebeskyttelse).bind()
            val geografiskTilknytning = geografiskTilknytning()

            Person(
                fornavn = navn.fornavn,
                mellomnavn = navn.mellomnavn,
                etternavn = navn.etternavn,
                fødselsdato = fødsel.foedselsdato,
                adressebeskyttelseGradering = toAdressebeskyttelseGradering(adressebeskyttelse),
                barn = emptyList(),
                barnUtenFolkeregisteridentifikator = person.forelderBarnRelasjon.toBarnUtenforFolkeregisteret(),
                gtBydel = geografiskTilknytning?.gtBydel,
                gtKommune = geografiskTilknytning?.gtKommune,
                gtLand = geografiskTilknytning?.gtLand,
            ) to person.forelderBarnRelasjon.toIdenterForBarnIFolkeregisteret()
        }
    }

    private fun toAdressebeskyttelseGradering(adressebeskyttelse: AdressebeskyttelseGradering) =
        when (adressebeskyttelse) {
            AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND -> AdressebeskyttelseGradLib.STRENGT_FORTROLIG_UTLAND
            AdressebeskyttelseGradering.STRENGT_FORTROLIG -> AdressebeskyttelseGradLib.STRENGT_FORTROLIG
            AdressebeskyttelseGradering.FORTROLIG -> AdressebeskyttelseGradLib.FORTROLIG
            AdressebeskyttelseGradering.UGRADERT -> AdressebeskyttelseGradLib.UGRADERT
        }
}
