package no.nav.tiltakspenger.libs.ktor.common.oppstart

import io.kotest.matchers.shouldBe
import io.prometheus.metrics.model.registry.PrometheusRegistry
import org.junit.jupiter.api.Test

class MetrikkregisterTest {
    /**
     * Testen leser bare hvilket Prometheus-register Micrometer-registeret er bundet til.
     * Ingenting registreres på det globale registeret, så testen deler ingen muterbar tilstand med andre tester.
     */
    @Test
    fun `prod-registeret er bundet til Prometheus sitt globale register`() {
        prometheusMeterRegistry().prometheusRegistry shouldBe PrometheusRegistry.defaultRegistry
    }
}
