package no.nav.tiltakspenger.libs.common

data class Roller(val value: List<Rolle>) : List<Rolle> by value {
    fun harRolle(rolle: Rolle): Boolean {
        return value.contains(rolle)
    }

    fun harSkjerming(): Boolean {
        return value.contains(Rolle.SKJERMING)
    }

    fun harFortroligAdresse(): Boolean {
        return value.contains(Rolle.FORTROLIG_ADRESSE)
    }

    fun harStrengtFortroligAdresse(): Boolean {
        return value.contains(Rolle.STRENGT_FORTROLIG_ADRESSE)
    }
}
