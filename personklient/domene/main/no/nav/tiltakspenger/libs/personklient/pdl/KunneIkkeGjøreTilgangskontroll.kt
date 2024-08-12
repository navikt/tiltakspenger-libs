package no.nav.tiltakspenger.libs.personklient.pdl

sealed interface KunneIkkeGjøreTilgangskontroll {
    data object UkjentIdent : KunneIkkeGjøreTilgangskontroll
    data class Adressebeskyttelse(val underliggende: FellesAdressebeskyttelseError) : KunneIkkeGjøreTilgangskontroll
    data class Skjerming(val underliggende: FellesSkjermingError) : KunneIkkeGjøreTilgangskontroll
}
