package no.nav.tiltakspenger.libs.texas.client

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.json.deserialize
import no.nav.tiltakspenger.libs.json.serialize
import org.junit.jupiter.api.Test

/**
 * Legger på disse bare for å verifisere at de fungerer som forventet etter jeg endre til param:JsonProperty
 */
class SystemtokenDtosTest {
    @Test
    fun `kan serialisere og deserialisere TexasTokenResponse`() {
        val expectedObject = TexasTokenResponse(
            accessToken = "token",
            expiresInSeconds = 3600L,
        )
        val expectedJson = """{"access_token":"token","expires_in":3600}"""
        serialize(expectedObject) shouldBe expectedJson
        deserialize<TexasTokenResponse>(expectedJson) shouldBe expectedObject
    }

    @Test
    fun `kan serialisere og deserialisere TexasExchangeTokenRequest`() {
        val expectedObject = TexasExchangeTokenRequest(
            identityProvider = "identityProvider",
            target = "target",
            userToken = "userToken",
        )
        val expectedJson = """{"identity_provider":"identityProvider","target":"target","user_token":"userToken"}"""
        serialize(expectedObject) shouldBe expectedJson
        deserialize<TexasExchangeTokenRequest>(expectedJson) shouldBe expectedObject
    }

    @Test
    fun `kan serialisere og deserialisere TexasTokenRequest`() {
        val expectedObject = TexasTokenRequest(
            identityProvider = "identityProvider",
            target = "target",
        )
        val expectedJson = """{"identity_provider":"identityProvider","target":"target"}"""
        serialize(expectedObject) shouldBe expectedJson
        deserialize<TexasTokenRequest>(expectedJson) shouldBe expectedObject
    }
}
