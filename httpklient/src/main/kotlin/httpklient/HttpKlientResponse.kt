package no.nav.tiltakspenger.libs.httpklient

import kotlin.time.Duration

data class HttpKlientResponse<out Body>(
    val statusCode: Int,
    val body: Body,
    val metadata: HttpKlientMetadata,
) {
    init {
        require(statusCode in 100..999) { "statusCode must be a three-digit HTTP status code" }
    }

    /**
     * Convenience-aksessorer som peker rett inn i [metadata]. Lar konsumenter slippe å skrive
     * `response.metadata.requestHeaders` osv., samtidig som vi beholder [HttpKlientMetadata] som
     * eneste datatype og sannhetskilde for disse feltene.
     */
    val rawRequestString: String get() = metadata.rawRequestString
    val rawResponseString: String? get() = metadata.rawResponseString
    val requestHeaders: Map<String, List<String>> get() = metadata.requestHeaders
    val responseHeaders: Map<String, List<String>> get() = metadata.responseHeaders
    val attempts: Int get() = metadata.attempts
    val attemptDurations: List<Duration> get() = metadata.attemptDurations
    val totalDuration: Duration get() = metadata.totalDuration
}
