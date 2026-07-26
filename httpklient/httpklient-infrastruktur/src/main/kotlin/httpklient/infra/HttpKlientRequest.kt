package no.nav.tiltakspenger.libs.httpklient.infra

import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.httpklient.infra.kall.HttpMethod
import no.nav.tiltakspenger.libs.httpklient.infra.kall.MultipartDeler
import no.nav.tiltakspenger.libs.httpklient.infra.kall.Statusregel
import no.nav.tiltakspenger.libs.httpklient.infra.kall.godtar
import java.io.ByteArrayOutputStream
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
        /**
         * `Content-Type`-headeren denne bodyen skal sendes med, eller `null` når det ikke er noen body.
         * Ligger på varianten selv slik at request-byggingen ikke trenger å kjenne formatet til hver enkelt body.
         */
        val contentType: String?

        data object Ingen : Body {
            override val contentType: String? = null
        }

        /** DTO som serialiseres med `tiltakspenger-libs/json` før sending. */
        data class Json(val value: Any) : Body {
            override val contentType = "application/json"
        }

        /** Ferdigserialisert JSON som sendes verbatim (se [no.nav.tiltakspenger.libs.httpklient.infra.kall.SerialisertJson]). */
        data class FerdigJson(val json: String) : Body {
            override val contentType = "application/json"
        }

        /** Rå tekst (`text/plain`). [sensitiv] maskerer bodyen i `rawRequestString` (f.eks. fnr mot tilgangsmaskinen). */
        data class Tekst(val tekst: String, val sensitiv: Boolean) : Body {
            override val contentType = "text/plain; charset=utf-8"
        }

        /** Ferdig URL-enkodet `application/x-www-form-urlencoded`-body. */
        data class Form(val enkodet: String) : Body {
            override val contentType = "application/x-www-form-urlencoded"
        }

        /**
         * Rå bytes med konsument-oppgitt [contentType] (bilde mot pdfgens bilde-endepunkt).
         * Bevisst ikke en data class: `ByteArray` har referanselikhet i `equals`, og typen trenger ikke verdilikhet.
         */
        class Bytes(val bytes: ByteArray, override val contentType: String) : Body {
            /** Rå bytes skal aldri havne i `rawRequestString` (og dermed i sikkerlogg) — samme regel som for binære responser. */
            val visningstekst: String get() = "<binær body, ${bytes.size} bytes, $contentType>"
        }

        /**
         * `multipart/form-data` med binære fildeler (ClamAV-virusskanning).
         * [boundary] genereres sammen med bodyen og inngår i [contentType], slik at headeren og selve bodyen aldri kan komme i utakt.
         */
        class Multipart(val deler: MultipartDeler, val boundary: String) : Body {
            override val contentType = "multipart/form-data; boundary=$boundary"

            /**
             * Enkoder delene til én `multipart/form-data`-body (RFC 7578).
             * Hver del får `Content-Disposition: form-data` med feltnavn og filnavn i anførselstegn, og sin egen `Content-Type`.
             * Delen avsluttes med CRLF før neste boundary.
             * Ligger her sammen med [contentType] fordi de to må bygges av samme [boundary] for at requesten skal være gyldig.
             */
            fun enkodet(): ByteArray {
                val ut = ByteArrayOutputStream()
                deler.forEach { del ->
                    ut.writeBytes(
                        buildString {
                            append("--").append(boundary).append(CRLF)
                            append("Content-Disposition: form-data; name=\"").append(del.feltnavn.escapetIHeader()).append("\"")
                            append("; filename=\"").append(del.filnavn.escapetIHeader()).append("\"").append(CRLF)
                            append("Content-Type: ").append(del.contentType).append(CRLF)
                            append(CRLF)
                        }.toByteArray(),
                    )
                    ut.writeBytes(del.innhold)
                    ut.writeBytes(CRLF.toByteArray())
                }
                ut.writeBytes("--$boundary--$CRLF".toByteArray())
                return ut.toByteArray()
            }

            /** Sikkerlogg-trygg gjengivelse: struktur og størrelser, aldri filinnholdet. */
            val visningstekst: String
                get() = deler.joinToString(separator = "\n", prefix = "<multipart/form-data, ${deler.size} deler>\n") {
                    "<binær del '${it.feltnavn}' (${it.filnavn}), ${it.innhold.size} bytes, ${it.contentType}>"
                }
        }
    }
}

/**
 * Escaper backslash og anførselstegn i navn som gjengis i anførselstegn i `Content-Disposition` (RFC 7578 §4.2, med quoted-string fra RFC 2045).
 * CR/LF er allerede avvist i [no.nav.tiltakspenger.libs.httpklient.infra.kall.MultipartDel], så dette er alt som gjenstår for at et brukeropplastet filnavn ikke skal kunne bryte ut av headeren.
 */
private fun String.escapetIHeader(): String = replace("\\", "\\\\").replace("\"", "\\\"")

private const val CRLF = "\r\n"

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
