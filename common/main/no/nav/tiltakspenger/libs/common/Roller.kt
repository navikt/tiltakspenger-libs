package no.nav.tiltakspenger.libs.common

data class Roller(
    val value: List<Rolle>,
) : List<Rolle> by value {
    fun harRolle(rolle: Rolle): Boolean = value.contains(rolle)

    fun harSkjerming(): Boolean = value.contains(Rolle.SKJERMING)

    fun harFortroligAdresse(): Boolean = value.contains(Rolle.FORTROLIG_ADRESSE)

    fun harStrengtFortroligAdresse(): Boolean = value.contains(Rolle.STRENGT_FORTROLIG_ADRESSE)

    fun harSaksbehandlerEllerBehandler(): Boolean =
        value in
            listOf(
                listOf(Rolle.SAKSBEHANDLER, Rolle.BESLUTTER),
            )
}
