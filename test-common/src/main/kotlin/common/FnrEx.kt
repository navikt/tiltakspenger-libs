package no.nav.tiltakspenger.libs.common

import no.nav.tiltakspenger.libs.common.personopplysning.Fnr

/**
 * 11 tilfeldige sifre mellom 0 og 9.
 * Gir sjeldent gyldige fødselsnumre :)
 */
fun Fnr.Companion.random(): Fnr {
    val numbers: CharRange = '0'..'9'
    return fromString(
        (1..11)
            .map { numbers.random() }
            .joinToString(""),
    )
}
