package no.nav.tiltakspenger.libs.logging

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.logging.infra.KotlinLoggingSikkerlogg
import org.junit.jupiter.api.Test

internal class SeSikkerloggTest {
    @Test
    fun `med appnavn og prosjekt blir henvisningen en lenke til appens logger i riktig prosjekt`() {
        KotlinLoggingSikkerlogg(appNavn = "tiltakspenger-datadeling", gcpProsjektId = "tpts-prod-b5ff").seSikkerlogg shouldBe
            "Se sikkerlogg for mer kontekst: " +
            "https://console.cloud.google.com/logs/query;query=resource.labels.container_name%3D%22tiltakspenger-datadeling%22?project=tpts-prod-b5ff"
    }

    @Test
    fun `uten verdier blir henvisningen ren tekst uten lenke`() {
        KotlinLoggingSikkerlogg().seSikkerlogg shouldBe "Se sikkerlogg for mer kontekst."
        KotlinLoggingSikkerlogg(appNavn = "tiltakspenger-datadeling").seSikkerlogg shouldBe "Se sikkerlogg for mer kontekst."
        KotlinLoggingSikkerlogg(gcpProsjektId = "tpts-dev-6211").seSikkerlogg shouldBe "Se sikkerlogg for mer kontekst."
    }

    @Test
    fun `companion-objektet har ren tekst-henvisning`() {
        Sikkerlogg.seSikkerlogg shouldBe "Se sikkerlogg for mer kontekst."
    }
}
