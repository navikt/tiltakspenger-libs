package no.nav.tiltakspenger.libs.httpklient

/**
 * Ferdige predikater for hvilke HTTP-statuser som skal regnes som suksess.
 * Brukes som `successStatus` på [HttpKlient.HttpKlientConfig] og per request via [RequestBuilder.successStatus].
 */
object HttpStatusSuccess {
    /** Standard: alle `2xx`-statuser (200–299) regnes som suksess. */
    val is2xx: (Int) -> Boolean = { statusCode -> statusCode in 200..299 }

    /**
     * Kun de eksplisitt oppgitte statuskodene regnes som suksess (f.eks. `exactly(200, 201, 204)`).
     * Krever minst én kode.
     */
    fun exactly(vararg codes: Int): (Int) -> Boolean {
        require(codes.isNotEmpty()) { "Må oppgi minst én statuskode" }
        val allowed = codes.toHashSet()
        return { statusCode -> statusCode in allowed }
    }

    /** Alle statuskoder i [range] (inklusiv) regnes som suksess (f.eks. `inRange(200..204)`). */
    fun inRange(range: IntRange): (Int) -> Boolean = { statusCode -> statusCode in range }
}
