package no.nav.tiltakspenger.libs.personklient.pdl

sealed interface KunneIkkeGjøreTilgangskontroll {
    val exception: Throwable?
    data object UkjentIdent : KunneIkkeGjøreTilgangskontroll {
        override val exception = null
    }
    data class Adressebeskyttelse(val underliggende: FellesAdressebeskyttelseError) : KunneIkkeGjøreTilgangskontroll {
        override val exception = underliggende.exception
    }
    data class Skjerming(val underliggende: FellesSkjermingError) : KunneIkkeGjøreTilgangskontroll {
        override val exception = underliggende.exception
    }
}
