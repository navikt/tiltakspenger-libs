package no.nav.tiltakspenger.libs.logging

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test

internal class SeSikkerloggTest {
    @Test
    fun `med nais-miljøvariabler blir det en lenke til appens logger i riktig prosjekt`() {
        seSikkerloggTekst("tiltakspenger-datadeling", "tpts-prod-b5ff") shouldBe
            "Se sikkerlogg for mer kontekst: " +
            "https://console.cloud.google.com/logs/query;query=resource.labels.container_name%3D%22tiltakspenger-datadeling%22?project=tpts-prod-b5ff"
    }

    @Test
    fun `uten miljøvariabler blir det ren tekst uten lenke`() {
        seSikkerloggTekst(null, null) shouldBe "Se sikkerlogg for mer kontekst."
        seSikkerloggTekst("tiltakspenger-datadeling", null) shouldBe "Se sikkerlogg for mer kontekst."
        seSikkerloggTekst(null, "tpts-dev-6211") shouldBe "Se sikkerlogg for mer kontekst."
    }

    @Test
    fun `SE_SIKKERLOGG kan leses utenfor nais uten å feile`() {
        SE_SIKKERLOGG shouldStartWith "Se sikkerlogg for mer kontekst"
    }
}
