package no.nav.tiltakspenger.libs.kafka.test

import mu.KotlinLogging
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName

object SingletonKafkaProvider {
    private val log = KotlinLogging.logger {}
    private var kafkaContainer: KafkaContainer? = null

    val adminClient: AdminClient by lazy {
        AdminClient.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to getHost()))
    }

    fun start() {
        if (kafkaContainer != null) return

        log.info { "Starting new Kafka Instance..." }

        kafkaContainer = KafkaContainer(DockerImageName.parse("apache/kafka"))
            .withEnv("KAFKA_LISTENERS", "PLAINTEXT://:9092,BROKER://:9093,CONTROLLER://:9094") // workaround for https://github.com/testcontainers/testcontainers-java/issues/9506
        kafkaContainer!!.start()

        setupShutdownHook()
        log.info { "Kafka setup finished listening on ${kafkaContainer!!.bootstrapServers}." }
    }

    fun getHost(): String {
        if (kafkaContainer == null) {
            start()
        }
        return kafkaContainer!!.bootstrapServers
    }

    private fun setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                log.info { "Shutting down Kafka server..." }
                kafkaContainer?.stop()
            },
        )
    }

    fun cleanup() {
        val topics = adminClient.listTopics().names().get()

        topics.forEach {
            try {
                adminClient.deleteTopics(listOf(it))
                log.info { "Deleted topic $it" }
            } catch (e: Exception) {
                log.warn(e) { "Could not delete topic $it" }
            }
        }
    }
}
