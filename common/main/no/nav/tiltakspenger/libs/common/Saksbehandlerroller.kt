package no.nav.tiltakspenger.libs.common

data class Saksbehandlerroller(
    override val value: Set<Saksbehandlerrolle>,
) : Roller<Saksbehandlerrolle>, Set<Saksbehandlerrolle> by value {

    constructor(vararg roller: Saksbehandlerrolle) : this(roller.toSet())
    constructor(roller: Collection<Saksbehandlerrolle>) : this(roller.toSet())

    override fun harRolle(rolle: Saksbehandlerrolle): Boolean = contains(rolle)

    fun harSkjerming(): Boolean = value.contains(Saksbehandlerrolle.SKJERMING)

    fun harFortroligAdresse(): Boolean = value.contains(Saksbehandlerrolle.FORTROLIG_ADRESSE)

    fun harStrengtFortroligAdresse(): Boolean = value.contains(Saksbehandlerrolle.STRENGT_FORTROLIG_ADRESSE)

    fun erSaksbehandler(): Boolean = value.contains(Saksbehandlerrolle.SAKSBEHANDLER)

    fun erBeslutter(): Boolean = value.contains(Saksbehandlerrolle.BESLUTTER)

    fun erSaksbehandlerEllerBeslutter(): Boolean =
        any { it == Saksbehandlerrolle.SAKSBEHANDLER || it == Saksbehandlerrolle.BESLUTTER }

    fun erDrift(): Boolean = value.contains(Saksbehandlerrolle.DRIFT)
}
