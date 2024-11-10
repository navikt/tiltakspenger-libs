package no.nav.tiltakspenger.libs.common

interface GenerellSystembrukerroller<R : GenerellSystembrukerrolle> : Roller<R> {
    override val value: Set<R>
    override fun harRolle(rolle: R): Boolean = contains(rolle)
}
