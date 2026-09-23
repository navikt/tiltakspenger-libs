package no.nav.tiltakspenger.libs.common

data class Saksbehandlerroller(
    override val value: Set<Saksbehandlerrolle>,
) : Roller<Saksbehandlerrolle>,
    Set<Saksbehandlerrolle> by value {

    constructor(vararg roller: Saksbehandlerrolle) : this(roller.toSet())
    constructor(roller: Collection<Saksbehandlerrolle>) : this(roller.toSet())

    override fun harRolle(rolle: Saksbehandlerrolle): Boolean = value.contains(rolle)

    val erSaksbehandler: Boolean by lazy { value.contains(Saksbehandlerrolle.SAKSBEHANDLER) }

    val erBeslutter: Boolean by lazy { value.contains(Saksbehandlerrolle.BESLUTTER) }

    val erSaksbehandlerEllerBeslutter: Boolean by lazy {
        value.any { it == Saksbehandlerrolle.SAKSBEHANDLER || it == Saksbehandlerrolle.BESLUTTER }
    }

    val erVeileder: Boolean by lazy { value.contains(Saksbehandlerrolle.VEILEDER) }

    val erUtvikler: Boolean by lazy { value.contains(Saksbehandlerrolle.UTVIKLER) }

    val erTilbakekreving: Boolean by lazy { value.contains(Saksbehandlerrolle.TILBAKEKREVING) }
}
