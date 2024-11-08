package no.nav.tiltakspenger.libs.common

/**
 * @param brukernavn Brukernavn til systembruker (azp_name for Entra ID). Kan ikke brukes til autentisering.
 */
data class Systembruker(
    override val brukernavn: String,
    override val roller: Roller,
) : Bruker
