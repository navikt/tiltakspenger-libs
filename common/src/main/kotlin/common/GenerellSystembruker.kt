package no.nav.tiltakspenger.libs.common

interface GenerellSystembruker<R : GenerellSystembrukerrolle, RR : GenerellSystembrukerroller<R>> : Bruker<R, RR> {
    override val navIdent get() = null
}
