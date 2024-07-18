package no.nav.tiltakspenger.libs.jobber

import arrow.core.Either

interface LeaderPodLookup {
    fun amITheLeader(localHostName: String): Either<LeaderPodLookupFeil, Boolean>
}

sealed interface LeaderPodLookupFeil {
    data object KunneIkkeKontakteLeaderElectorContainer : LeaderPodLookupFeil
    data object UkjentSvarFraLeaderElectorContainer : LeaderPodLookupFeil
    data class Ikke2xx(
        val status: Int,
        val body: String?,
    ) : LeaderPodLookupFeil
}
