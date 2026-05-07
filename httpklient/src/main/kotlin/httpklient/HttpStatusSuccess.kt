package no.nav.tiltakspenger.libs.httpklient

object HttpStatusSuccess {
    val is2xx: (Int) -> Boolean = { statusCode -> statusCode in 200..299 }
}
