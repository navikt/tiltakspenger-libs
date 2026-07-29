package no.nav.tiltakspenger.libs.kafka.infra

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.config.SslConfigs

/**
 * Sikkerhetsoppsettet mot Kafka-brokerne, som ren data.
 * Nais-miljøet bruker [Ssl] med filene Aiven legger i poden; lokalt og i tester finnes ingen sikkerhet, altså [Ingen].
 */
sealed interface KafkaSikkerhet {
    fun tilConfig(): Map<String, Any>

    data object Ingen : KafkaSikkerhet {
        override fun tilConfig(): Map<String, Any> = emptyMap()
    }

    /** SSL med truststore og keystore slik Aiven på Nais leverer dem, via filstier og et felles passord. */
    data class Ssl(
        val truststorePath: String,
        val keystorePath: String,
        val credstorePassword: String,
    ) : KafkaSikkerhet {
        override fun tilConfig(): Map<String, Any> = mapOf(
            CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "SSL",
            // Verifisering av servernavn er slått av, slik Aiven-oppsettet forutsetter.
            SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "",
            SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to "JKS",
            SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to "PKCS12",
            SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to truststorePath,
            SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to credstorePassword,
            SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to keystorePath,
            SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to credstorePassword,
            SslConfigs.SSL_KEY_PASSWORD_CONFIG to credstorePassword,
        )

        /** Passordet skal aldri ut i logg, så toString maskerer det. */
        override fun toString(): String = "Ssl(truststorePath=$truststorePath, keystorePath=$keystorePath, credstorePassword=*****)"
    }
}
