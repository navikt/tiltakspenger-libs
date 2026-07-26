package fixtures.rewriteaudiencetarget

class Brudd(private val texasClient: Any) {
    val provider = TexasSystemTokenProvider(
        texasClient = texasClient,
        audienceTarget = "prod-fss:pdl:pdl-api",
        rewriteAudienceTarget = false,
    )

    suspend fun token() = texasClient.getSystemToken(
        audienceTarget = "prod-gcp:tpts:tiltakspenger-datadeling",
        rewriteAudienceTarget = true,
    )
}

/** Egen fasade som viderefører flagget fanges også, siden deklarasjonen treffes. */
class EgenFasade(
    private val rewriteAudienceTarget: Boolean = true,
)
