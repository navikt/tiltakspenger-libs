package no.nav.tiltakspenger.libs.person

enum class AdressebeskyttelseGradering {
    STRENGT_FORTROLIG_UTLAND,
    STRENGT_FORTROLIG,
    FORTROLIG,
    UGRADERT,
}

fun List<AdressebeskyttelseGradering>.harStrengtFortroligAdresse(): Boolean {
    return this.contains(AdressebeskyttelseGradering.STRENGT_FORTROLIG) || this.contains(AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND)
}

fun List<AdressebeskyttelseGradering>.harFortroligAdresse(): Boolean {
    return this.contains(AdressebeskyttelseGradering.FORTROLIG)
}
