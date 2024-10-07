package no.nav.tiltakspenger.libs.personklient.pdl

sealed interface KunneIkkeGjøreTilgangskontroll {
    val exception: Throwable?
    val body: String?
    val status: Int?
    data object UkjentIdent : KunneIkkeGjøreTilgangskontroll {
        override val exception = null
        override val body = null
        override val status = null
    }
    data class Adressebeskyttelse(val underliggende: FellesAdressebeskyttelseError) : KunneIkkeGjøreTilgangskontroll {
        override val exception = underliggende.exception
        override val body = underliggende.body
        override val status = underliggende.status
    }
    data class Skjerming(val underliggende: FellesSkjermingError) : KunneIkkeGjøreTilgangskontroll {
        override val exception = underliggende.exception
        override val body = underliggende.body
        override val status = underliggende.status
    }
}
