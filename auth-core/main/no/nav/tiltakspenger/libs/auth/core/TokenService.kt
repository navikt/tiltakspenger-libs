package no.nav.tiltakspenger.libs.auth.core

import arrow.core.Either
import no.nav.tiltakspenger.libs.common.Bruker

@Deprecated("Erstattet av Texas")
interface TokenService {
    suspend fun validerOgHentBruker(token: String): Either<Valideringsfeil, Bruker<*, *>>
}
