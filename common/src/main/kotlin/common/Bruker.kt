package no.nav.tiltakspenger.libs.common

/**
 * @property klientId Tilsvarer azp for systembruker. Dette er klienten som kaller oss. Unik identifikator for klienten.
 * @property klientnavn Tilsvarer azp_name for systembruker. Dette er klienten som kaller oss. Skal kun brukes til visning og ikke auth. Er ikke nødvendigvis unik.
 * @property navIdent Custom Nav-claim. Vil kun gjelde der en sluttbruker er involvert (login eller on-behalf-of).
 */
interface Bruker<R : Rolle, RR : Roller<R>> {
    val roller: RR
    val klientId: String
    val klientnavn: String

    val navIdent: String?
}
