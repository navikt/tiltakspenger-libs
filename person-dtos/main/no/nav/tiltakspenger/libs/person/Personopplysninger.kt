package no.nav.tiltakspenger.libs.person

import java.time.LocalDate

interface Personopplysninger {
    val fødselsdato: LocalDate
    val fornavn: String
    val mellomnavn: String?
    val etternavn: String
    val adressebeskyttelseGradering: AdressebeskyttelseGradering
}
