package no.nav.tiltakspenger.libs.common

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import java.util.concurrent.Executors

internal class CorrelationIdExTest {
    private val mdcKey = "test-call-id"

    @Test
    fun `correlation-id følger coroutinen over trådbytte, lekker ikke og gir tilbake MDC-en som fantes fra før`() {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { trådA ->
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { trådB ->
                runBlocking(trådA) {
                    val navnA = Thread.currentThread().name
                    MDC.put(mdcKey, "fra-før")
                    MDC.put("annen-nøkkel", "annen-verdi")

                    withCorrelationIdSuspend(mdcKey) { id ->
                        MDC.get(mdcKey) shouldBe id.toString()
                        MDC.get("annen-nøkkel") shouldBe "annen-verdi"
                        withContext(trådB) {
                            Thread.currentThread().name shouldNotBe navnA
                            MDC.get(mdcKey) shouldBe id.toString()
                            MDC.get("annen-nøkkel") shouldBe "annen-verdi"
                        }
                        Thread.currentThread().name shouldBe navnA
                        MDC.get(mdcKey) shouldBe id.toString()
                    }

                    MDC.get(mdcKey) shouldBe "fra-før"
                    MDC.get("annen-nøkkel") shouldBe "annen-verdi"
                    withContext(trådB) {
                        MDC.get(mdcKey) shouldBe null
                        MDC.get("annen-nøkkel") shouldBe null
                    }
                    MDC.clear()
                }
            }
        }
    }
}
