package no.nav.tiltakspenger.libs.httpklient

import kotlin.time.Duration

/**
 * Tidsbudsjettet et kall kjørte under.
 *
 * Bæres i [HttpKlientMetadata] fordi en varighet uten grensen ved siden av ikke er til å tolke: «brukt: 1.003s» kan være en klient som akkurat brøt en grense på 1 s, eller en som brukte en brøkdel av 30 s.
 * Grensene gjelder per forsøk — [HttpKlientMetadata.totalDuration] dekker alle forsøk pluss backoff, og skal ikke sammenlignes direkte med dem.
 *
 * @property svar Tid til å få svaret, fra `HttpKlientConfig.timeout`, satt på `java.net.http.HttpRequest`.
 * @property oppkobling Tid til å få opp forbindelsen, fra transporten (`JavaHttpTransport(connectTimeout = ...)`).
 * `null` når transporten ikke kobler opp noe, altså i tester som kjører på en fake.
 */
data class Tidsgrenser(
    val svar: Duration,
    val oppkobling: Duration?,
) {
    companion object {
        /**
         * Til feil som oppstår helt utenfor et kall, der ikke noe tidsbudsjett var i spill — se [authFeilUtenKall].
         * Bevisst en navngitt konstant og ikke en default: en `Duration.ZERO` sneket inn i en logglinje ville lest som «grense 0s».
         */
        val INGEN = Tidsgrenser(svar = Duration.ZERO, oppkobling = null)
    }
}
