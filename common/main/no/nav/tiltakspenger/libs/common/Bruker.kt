package no.nav.tiltakspenger.libs.common

interface Bruker<R : Rolle, RR : Roller<R>> {
    val brukernavn: String
    val roller: RR
}
