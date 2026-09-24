package fixtures.rewriteaudiencetarget

/**
 * KDoc som omtaler rewriteAudienceTarget = false skal ikke flagges.
 */
class Ren(private val texasClient: Any) {
    // Utkommentert kode flagges heller ikke: rewriteAudienceTarget = false

    val provider = TexasSystemTokenProvider(
        texasClient = texasClient,
        audienceTarget = "prod-fss:pdl:pdl-api",
    ) // rewriteAudienceTarget i trailing kommentar er greit

    fun melding(): String = "tekst om rewriteAudienceTarget = false i en strengliteral er heller ikke et kall"
}
