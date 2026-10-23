package no.nav.tiltakspenger.libs.kafka.test

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName

object SingletonKafkaProvider {

    private val kafkaContainer: KafkaContainer by lazy {
        KafkaContainer(DockerImageName.parse("apache/kafka"))
            .withEnv("KAFKA_LISTENERS", "PLAINTEXT://:9092,BROKER://:9093,CONTROLLER://:9094") // workaround for https://github.com/testcontainers/testcontainers-java/issues/9506
            .apply {
                start()
                Runtime.getRuntime().addShutdownHook(
                    Thread {
                        adminClient.close()
                        stop()
                    },
                )
            }
    }

    val adminClient: AdminClient by lazy {
        AdminClient.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to getHost()))
    }

    fun getHost(): String = kafkaContainer.bootstrapServers
}
