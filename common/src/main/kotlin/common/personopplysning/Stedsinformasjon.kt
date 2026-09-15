package no.nav.tiltakspenger.libs.common.personopplysning

/**
 * Personopplysning som kan røpe hvor en person befinner seg.
 * Kategorien skiller stedsinformasjon fra identifiserende opplysninger fordi de kan treffe ulike grupper.
 * For personer med adressebeskyttelse kan sted være særlig sensitivt, mens fødselsnummeret alene ikke røper stedet.
 */
sealed interface Stedsinformasjon : Personopplysning
