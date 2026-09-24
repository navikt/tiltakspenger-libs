package no.nav.tiltakspenger.libs.httpklient.infra

import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.httpklient.infra.kall.HttpMethod
import no.nav.tiltakspenger.libs.httpklient.infra.kall.MultipartDel
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
         *
         * [bytes] kopieres bevisst ikke — typen låner kallerens array og eier den ikke.
         * Muterer kalleren arrayet etter at bodyen er bygget, men før requesten er sendt, går det muterte innholdet på wire.
         * Avveiningen er minnebruk: en body kan være flere megabyte, og en kopi her ville lagt ett helt filavtrykk til per samtidige request for å beskytte mot en mutasjon som måtte skje i vinduet mellom konstruksjon og sending — i praksis samme uttrykk.
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
                // Headerne bygges før bytestrømmen slik at den totale størrelsen er kjent eksakt og ByteArrayOutputStream slipper å doble seg opp fra 32 bytes.
                // Uten dette koster et vedlegg på 10 MB rundt 19 reallokeringer med kopiering på nøyaktig den stien som håndterer de største bodyene.
                val hoder = deler.map { it.hode() }
                val avslutning = "--$boundary--$CRLF".toByteArray()
                val ut = ByteArrayOutputStream(størrelse(hoder, avslutning.size))
                deler.forEachIndexed { indeks, del ->
                    ut.writeBytes(hoder[indeks])
                    ut.writeBytes(del.innhold)
                    ut.writeBytes(CRLF.toByteArray())
                }
                ut.writeBytes(avslutning)
                return ut.toByteArray()
            }

            /** Én dels `Content-Disposition`/`Content-Type`-hode, ferdig enkodet — bygget én gang og gjenbrukt både til størrelsesberegningen og til selve skrivingen. */
            private fun MultipartDel.hode(): ByteArray = buildString {
                append("--").append(boundary).append(CRLF)
                append("Content-Disposition: form-data; name=\"").append(feltnavn.escapetIHeader()).append("\"")
                append("; filename=\"").append(filnavn.escapetIHeader()).append("\"").append(CRLF)
                append("Content-Type: ").append(contentType).append(CRLF)
                append(CRLF)
            }.toByteArray()

            /**
             * Nøyaktig bodystørrelse: hvert hode, hver dels innhold, CRLF-en etter hver del, og avslutningsboundaryen.
             * Summeres som `Long` og klippes til [Int.MAX_VALUE] slik at en absurd stor body ikke gir negativ startstørrelse (som [ByteArrayOutputStream] avviser); en body over 2 GB feiler uansett senere i `toByteArray()`.
             */
            private fun størrelse(hoder: List<ByteArray>, avslutning: Int): Int {
                val sum = deler.indices.sumOf { hoder[it].size.toLong() + deler[it].innhold.size + CRLF.length } + avslutning
                return sum.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
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
 *
 * Nettlesere (WHATWG) og OkHttp prosentkoder i stedet (`%22`), og quoted-pair forutsetter at mottakerens parser faktisk implementerer den.
 * Verifisert mot den eneste mottakeren vi har: NAIS-antivirus er [nais/clamav-rest](https://github.com/nais/clamav-rest), som parser med Go sin `mime/multipart` → `mime.ParseMediaType`, og `consumeValue` i Go sin `mime/mediatype.go` unescaper `\X` for tspecials — settet `()<>@,;:\"/[]?=` inneholder både `"` og `\`.
 * Prosentkoding ville vært et regress mot nettopp den mottakeren: Go dekoder ikke `%22`, så et filnavn med anførselstegn kom tilbake verbatim som `cv%22.pdf` i skanneresultatet.
 * Klarer Go derimot ikke å parse headeren, blir `FileName()` tom og parten havner blant skjemaverdiene i stedet for blant filene — altså stille ikke skannet — så det er verdt å holde seg til enkodingen parseren forstår.
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
