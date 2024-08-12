package no.nav.tiltakspenger.libs.common

@JvmInline
value class AccessToken(val value: String) {

    init {
        require(value.startsWith("Bearer: ")) { "Access token må starte med Bearer:" }
    }
    override fun toString() = "Access token: <REDACTED>"
}
