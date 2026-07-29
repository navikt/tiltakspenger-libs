package no.nav.tiltakspenger.libs.kafka.avro.infra

/**
 * Basic auth mot schema registry, slik Aiven på Nais leverer den via miljøvariabler.
 * Propertynavnene kommer fra `io.confluent:kafka-avro-serializer`, men er hardkodet for å slippe avhengigheten.
 */
data class SchemaRegistryBasicAuth(
    val brukernavn: String,
    val passord: String,
) {
    fun tilConfig(): Map<String, Any> = mapOf(
        "basic.auth.credentials.source" to "USER_INFO",
        "basic.auth.user.info" to "$brukernavn:$passord",
    )

    /** Passordet skal aldri ut i logg, så toString maskerer det. */
    override fun toString(): String = "SchemaRegistryBasicAuth(brukernavn=$brukernavn, passord=*****)"
}
