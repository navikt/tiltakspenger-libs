package no.nav.tiltakspenger.libs.httpklient.infra

import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.httpklient.infra.kall.HttpMethod
import no.nav.tiltakspenger.libs.httpklient.infra.kall.Statusregel
import no.nav.tiltakspenger.libs.httpklient.infra.kall.godtar
import java.net.URI
import kotlin.reflect.KType

/**
 * Den interne, ferdig materialiserte beskrivelsen av ett kall — bygget av de offentlige metodene på [HttpKlient].
 *
 * Erstatter den gamle `RequestBuilder`-en: konsumenten tar ingen valg utover metodens parametre, og `Accept`/`Content-Type` er allerede lagt på [headers] som en konsekvens av metode + bodytype (se `byggHttpKlientRequest`).
 * Headere bevarer innsettingsrekkefølge, med klientens default-headere til slutt.
 */
internal data class HttpKlientRequest(
    val method: HttpMethod,
    val uri: URI,
    val headers: Map<String, List<String>>,
    /** Lowercase-navn på konsument-headere markert [no.nav.tiltakspenger.libs.httpklient.infra.kall.Header.sensitiv] — maskeres i `rawRequestString` i tillegg til standard auth-/cookie-headere. */
    val sensitiveHeaderNavn: Set<String>,
    val body: Body,
    /** Per-kall bearer-token (typisk OBO); overstyrer [no.nav.tiltakspenger.libs.httpklient.infra.kall.KlientAuth.System] på klienten. */
    val authToken: AccessToken?,
    val godta: Statusregel,
    val responsFormat: ResponsFormat,
) {
    sealed interface Body {
        data object Ingen : Body

        /** DTO som serialiseres med `tiltakspenger-libs/json` før sending. */
        data class Json(val value: Any) : Body

        /** Ferdigserialisert JSON som sendes verbatim (se [no.nav.tiltakspenger.libs.httpklient.infra.kall.SerialisertJson]). */
        data class FerdigJson(val json: String) : Body

        /** Rå tekst (`text/plain`). [sensitiv] maskerer bodyen i `rawRequestString` (f.eks. fnr mot tilgangsmaskinen). */
        data class Tekst(val tekst: String, val sensitiv: Boolean) : Body

        /** Ferdig URL-enkodet `application/x-www-form-urlencoded`-body. */
        data class Form(val enkodet: String) : Body
    }
}

/**
 * Hvordan respons-bytene skal tolkes — bestemt av hvilken metode konsumenten kalte, aldri av respons-typeargumentet alene.
 * Dette erstatter den gamle runtime-dispatchen på `String`/`Unit`/`ByteArray`-typeargumenter.
 */
internal sealed interface ResponsFormat {
    /** Deserialiseres fra JSON med Jackson til [type]. */
    data class Json(val type: KType) : ResponsFormat

    /** Som [Json], men statuser i [nullVedStatus] regnes som suksess med `null`-body og hopper over deserialisering. */
    data class JsonEllerNull(val type: KType, val nullVedStatus: Set<Int>) : ResponsFormat

    /** Rå bytes, aldri dekodet som tekst (PDF). */
    data object PdfBytes : ResponsFormat

    /** Bodyen ignoreres typemessig (`Unit`), men fanges fortsatt lesbart i metadata. */
    data object IngenBody : ResponsFormat
}

/**
 * Det effektive suksess-predikatet for kallet: [Statusregel]-en, utvidet med [ResponsFormat.JsonEllerNull.nullVedStatus] når det er relevant.
 * En `getJsonEllerNull(nullVedStatus = setOf(204, 404))` trenger altså ikke (og skal ikke) gjenta statusene i `godta`.
 */
internal fun HttpKlientRequest.erSuksessStatus(statusCode: Int): Boolean {
    if (godta.godtar(statusCode)) return true
    val format = responsFormat
    return format is ResponsFormat.JsonEllerNull && statusCode in format.nullVedStatus
}
