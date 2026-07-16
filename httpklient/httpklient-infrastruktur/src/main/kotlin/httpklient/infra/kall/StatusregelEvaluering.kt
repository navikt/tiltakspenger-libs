package no.nav.tiltakspenger.libs.httpklient.infra.kall

/** Sant hvis [statusCode] regnes som suksess etter denne regelen. */
internal fun Statusregel.godtar(statusCode: Int): Boolean = when (this) {
    Statusregel.Alle2xx -> statusCode in 200..299
    is Statusregel.Eksakt -> statusCode in statuser
}
