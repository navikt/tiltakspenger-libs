package no.nav.tiltakspenger.libs.ktor.test.common

/**
 * Forventninger til responsen i [defaultRequestWithAssertions].
 * [status] assertes alltid.
 * [body] assertes etter valgt [ForventetBody]-variant; `null` betyr at bodyen ikke assertes.
 * [contentType] asserter responsens Content-Type når den er satt; `null` betyr at Content-Type ikke assertes.
 * [ForventetBody.Tom] kan ikke kombineres med [contentType], siden Tom allerede krever at responsen ikke har Content-Type.
 *
 * Statuskode som `Int` og Content-Type som `String` er samme vokabular som `httpklient` bruker (`HttpKlientResponse.statusCode`, `harStatus(409)`), slik at rutetester og klienttester snakker likt om HTTP.
 */
data class ForventetRespons(
    val status: Int,
    val body: ForventetBody? = null,
    /** Skrives som ktor gjengir headeren, f.eks. `application/json` eller `text/plain; charset=UTF-8`. */
    val contentType: String? = null,
) {
    init {
        require(status in 100..999) { "status må være en tresifret HTTP-statuskode, var $status" }
        require(body !is ForventetBody.Tom || contentType == null) {
            "ForventetBody.Tom krever at responsen ikke har Content-Type, så contentType kan ikke settes samtidig"
        }
    }

    /**
     * Snarveier til de fire [ForventetBody]-variantene.
     * De sparer kallstedet for å pakke verdien i en variant, og for å importere [ForventetBody] i det hele tatt.
     * Bruk hovedkonstruktøren når bodyen ikke skal assertes: `ForventetRespons(status = 200)`.
     *
     * Statusen står alltid som første argument, også der den er `200`.
     * Den er det viktigste en rutetest sier noe om, og skal kunne leses uten å slå opp hva funksjonsnavnet impliserer.
     */
    companion object {
        /** Asserter JSON-likhet mot bodyen, jf. [ForventetBody.Json]. */
        fun json(status: Int, json: String, contentType: String? = null): ForventetRespons =
            ForventetRespons(status = status, body = ForventetBody.Json(json), contentType = contentType)

        /** Asserter eksakt strenglikhet mot bodyen, jf. [ForventetBody.Eksakt]. */
        fun eksakt(status: Int, tekst: String, contentType: String? = null): ForventetRespons =
            ForventetRespons(status = status, body = ForventetBody.Eksakt(tekst), contentType = contentType)

        /** Asserter eksakt bytelikhet mot den rå responsbodyen, jf. [ForventetBody.Bytes]. */
        fun bytes(status: Int, bytes: ByteArray, contentType: String? = null): ForventetRespons =
            ForventetRespons(status = status, body = ForventetBody.Bytes(bytes), contentType = contentType)

        /**
         * Asserter at bodyen er tom og at responsen ikke har Content-Type, jf. [ForventetBody.Tom].
         * Tar bevisst ingen `contentType`: [ForventetBody.Tom] utelukker den allerede, så her er invarianten uttrykt i signaturen i stedet for som en `require` som smeller først når testen kjører.
         */
        fun tom(status: Int): ForventetRespons =
            ForventetRespons(status = status, body = ForventetBody.Tom)
    }
}

/**
 * Måten responsbodyen assertes på i [ForventetRespons].
 */
sealed interface ForventetBody {
    /** Asserter at bodyen er tom og at responsen ikke har Content-Type. */
    data object Tom : ForventetBody

    /** Asserter eksakt strenglikhet mot bodyen. */
    data class Eksakt(val verdi: String) : ForventetBody

    /** Asserter JSON-likhet mot bodyen. */
    data class Json(val verdi: String) : ForventetBody

    /**
     * Asserter eksakt bytelikhet mot den rå responsbodyen, f.eks. for PDF-er.
     * Bevisst ikke en data class: generert `equals`/`hashCode` ville sammenlignet arrayen på referanse, ikke innhold.
     */
    class Bytes(val verdi: ByteArray) : ForventetBody
}
