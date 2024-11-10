package no.nav.tiltakspenger.libs.common

data class Saksbehandler(
    val navIdent: String,
    // TODO post-mvp: Dersom brukernavn og epost ikke brukes, fjerne disse fra Saksbehandler. Brukernavn utledes fra epost og det føles ikke idéelt.
    override val brukernavn: String,
    val epost: String,
    override val roller: Saksbehandlerroller,
) : Bruker<Saksbehandlerrolle, Saksbehandlerroller> {
    fun erSaksbehandler() = roller.erSaksbehandler()

    fun erBeslutter() = roller.erBeslutter()

    fun erSaksbehandlerEllerBeslutter() = roller.erSaksbehandlerEllerBeslutter()

    override fun toString(): String =
        "Saksbehandler(navIdent='$navIdent', brukernavn='*****', epost='*****', roller=$roller)"
}
