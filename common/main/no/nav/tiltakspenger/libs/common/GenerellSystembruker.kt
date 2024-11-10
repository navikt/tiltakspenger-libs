package no.nav.tiltakspenger.libs.common

/**
 * @property brukernavn Brukernavn til systembruker (azp_name for Entra ID). Kan ikke brukes til autentisering.
 */
interface GenerellSystembruker<R : GenerellSystembrukerrolle, RR : GenerellSystembrukerroller<R>> : Bruker<R, RR> {
    override val brukernavn: String
}
