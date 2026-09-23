package no.nav.tiltakspenger.libs.ktor.common.oppstart

import io.micrometer.core.instrument.Clock
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.model.registry.PrometheusRegistry

/**
 * Registeret en app eksponerer på `/metrics`, og som [Jobboppsett] og Kafka-consumerne fører bakgrunnsprosess-målingene i.
 * Kalles én gang, i komposisjonsroten (`start()`), og sendes derfra inn i appens kontekst, Ktor-oppsettet, [Jobboppsett] og hver consumer.
 * Registeret må være det samme alle steder, ellers forsvinner seriene stille, og varselreglene for bakgrunnsprosesser får aldri data.
 *
 * Registeret er bundet til Prometheus sitt globale register, [PrometheusRegistry.defaultRegistry].
 * Da blir tellere en app registrerer rett på Prometheus-klienten også med i skrapingen, slik `MetricRegister` gjør i saksbehandling-api og journalposthendelser.
 * Nye målinger bør heller registreres på registeret som returneres her.
 * [Clock.SYSTEM] er Micrometers egen klokke, som bare brukes til tidsstempling inne i registeret, og har ingenting med appens `java.time.Clock` å gjøre.
 *
 * Testkode og lokale kjøringer skal aldri bruke denne.
 * Det globale registeret er prosessglobal tilstand som ikke kan varieres per test, og et prosessnavn kan bare registreres én gang per register.
 * Test- og lokalkontekster lager i stedet sitt eget `PrometheusMeterRegistry(PrometheusConfig.DEFAULT)`.
 */
fun prometheusMeterRegistry(): PrometheusMeterRegistry = PrometheusMeterRegistry(
    PrometheusConfig.DEFAULT,
    PrometheusRegistry.defaultRegistry,
    Clock.SYSTEM,
)
