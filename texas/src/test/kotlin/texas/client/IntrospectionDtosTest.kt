package no.nav.tiltakspenger.libs.texas.client

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.json.deserialize
import no.nav.tiltakspenger.libs.json.serialize
import org.junit.jupiter.api.Test

/**
 * Legger på disse bare for å verifisere at de fungerer som forventet etter jeg endre til param:JsonProperty
 */
class IntrospectionDtosTest {
    @Test
    fun `kan serialisere og deserialisere TexasIntrospectionRequest`() {
        val expectedObject = TexasIntrospectionRequest(
            identityProvider = "identityProvider",
            token = "token",
        )
        val expectedJson = """{"identity_provider":"identityProvider","token":"token"}"""
        serialize(expectedObject) shouldBe expectedJson
        deserialize<TexasIntrospectionRequest>(expectedJson) shouldBe expectedObject
    }

    @Test
    fun `kan serialisere og deserialisere TexasIntrospectionResponse`() {
        val expectedObject = TexasIntrospectionResponse(
            active = true,
            error = "some error",
            groups = listOf("group1", "group2"),
            roles = listOf("role1", "role2"),
            other = mapOf("custom_claim" to "custom_value"),
        )
        val expectedJson = """{"active":true,"error":"some error","groups":["group1","group2"],"roles":["role1","role2"],"custom_claim":"custom_value"}"""
        serialize(expectedObject) shouldBe expectedJson
        deserialize<TexasIntrospectionResponse>(expectedJson) shouldBe expectedObject
    }
}
