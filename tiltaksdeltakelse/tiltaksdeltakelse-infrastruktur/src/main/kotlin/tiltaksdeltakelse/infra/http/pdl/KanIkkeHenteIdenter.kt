package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.pdl

import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.HttpKlientMetadata

/**
 * Identoppslaget mot PDL ga ikke identer å bruke.
 *
 * Skillet mellom variantene er det hente-tjenesten trenger for fallback-reglene: et kall som feilet feller hele oppslaget, mens et svar uten brukbare identer faller tilbake til innsendt fnr.
 * Alle variantene bærer nok metadata til at hente-tjenesten kan logge én gang per feilsituasjon, med rå respons i sikkerlogg.
 */
sealed interface KanIkkeHenteIdenter {
    /** Selve kallet feilet — nettverk, timeout eller uventet status. */
    data class KallFeilet(
        val httpKlientError: HttpKlientError,
    ) : KanIkkeHenteIdenter

    /** PDL svarte 200, men med funksjonelle feil i errors-lista. */
    data class GraphQLFeil(
        val feilmeldinger: List<String>,
        val metadata: HttpKlientMetadata,
    ) : KanIkkeHenteIdenter

    /** PDL svarte uten feil, men identlisten var tom eller manglet. */
    data class FantIngenIdenter(
        val metadata: HttpKlientMetadata,
    ) : KanIkkeHenteIdenter

    /** PDL svarte med en ident som ikke er et gyldig fødselsnummer — svaret stoles ikke på. */
    data class UgyldigIdent(
        val metadata: HttpKlientMetadata,
    ) : KanIkkeHenteIdenter
}
