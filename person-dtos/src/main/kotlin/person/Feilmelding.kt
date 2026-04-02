package no.nav.tiltakspenger.libs.person

enum class Feilmelding(val message: String) {
    PersonIkkeFunnet("Fant ikke person i PDL"),
    NavnIkkeFunnet("Fant ikke navn i PDL"),
    NavnKunneIkkeAvklares("Navn kunne ikke avklares fra PDL respons"),
    FødselKunneIkkeAvklares("Fødsel kunne ikke avklares fra PDL respons"),
    AdressebeskyttelseKunneIkkeAvklares("Adressebeskyttelse kunne ikke avklares fra PDL respons"),
    NetworkError("PDL er nede!!"),
    UkjentFeil("Uhåndtert feil oppsto ved å hente person fra PDL"),
    ResponsManglerPerson("Person finnes ikke i PDL"),
    SerializationException("Feil ved serializering av PDL data"),
    GraderingKunneIkkeAvklares("Kunne ikke avklare gradering"),
    AzureAuthFailureException("Kunne ikke autentisere mot Azure"),
}
