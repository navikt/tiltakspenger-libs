package no.nav.tiltakspenger.libs.common

interface Roller<R : Rolle> : Set<R> {
    fun harRolle(rolle: R): Boolean = contains(rolle)
    val value: Set<R>
}
