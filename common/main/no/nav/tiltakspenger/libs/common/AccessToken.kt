package no.nav.tiltakspenger.libs.common

@JvmInline
value class AccessToken(val value: String) {
    override fun toString() = "Access token: <REDACTED>"
}
