package no.nav.tiltakspenger.libs.texas

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.tiltakspenger.libs.common.Saksbehandlerrolle
import org.junit.jupiter.api.Test

internal class TexasPrincipalInternalTest {
    private val saksbehandlerrolleObjectId = "1b3a2c4d-d620-4fcf-a29b-a6cdadf29680"
    private val alleAdRoller = listOf(
        AdRolle(Saksbehandlerrolle.SAKSBEHANDLER, saksbehandlerrolleObjectId),
        AdRolle(Saksbehandlerrolle.BESLUTTER, "79985315-b2de-40b8-a740-9510796993c6"),
    )
    private val saksbehandlerclaims = mapOf(
        "azp_name" to "saksbehandling",
        "azp" to "saksbehandling-id",
        "NAVident" to "Z12345",
        "preferred_username" to "Sak.Behandler@nav.no",
    )
    private val systembrukerclaims = mapOf(
        "azp_name" to "saksbehandling",
        "azp" to "saksbehandling-id",
        "idtyp" to "app",
    )

    private fun principal(claims: Map<String, Any?>, tilganger: List<String>) = TexasPrincipalInternal(
        claims = claims,
        token = "token",
        tilganger = tilganger,
    )

    @Test
    fun `toSaksbehandler - mapper claims og roller`() {
        val saksbehandler = principal(saksbehandlerclaims, listOf(saksbehandlerrolleObjectId))
            .toSaksbehandler(alleAdRoller)
            .getOrNull()!!

        saksbehandler.navIdent shouldBe "Z12345"
        saksbehandler.brukernavn shouldBe "Sak Behandler"
        saksbehandler.epost shouldBe "Sak.Behandler@nav.no"
        saksbehandler.roller.toList() shouldBe listOf(Saksbehandlerrolle.SAKSBEHANDLER)
        saksbehandler.klientId shouldBe "saksbehandling-id"
        saksbehandler.klientnavn shouldBe "saksbehandling"
        // Saksbehandlere har ingen systembrukerroller; scopes er tomme uansett hvilken rolle det spørres om.
        saksbehandler.scopes.harRolle(TestSystembrukerrolle.HENTE_DATA) shouldBe false
        saksbehandler.scopes.toList() shouldBe emptyList()
    }

    @Test
    fun `toSaksbehandler - tilgang uten treff i autoriserte roller gir tom rolleliste`() {
        val saksbehandler = principal(saksbehandlerclaims, listOf("en-helt-annen-gruppe"))
            .toSaksbehandler(alleAdRoller)
            .getOrNull()!!

        saksbehandler.roller.toList() shouldBe emptyList()
    }

    @Test
    fun `toSaksbehandler - manglende azp_name gir ManglerClaim`() {
        principal(saksbehandlerclaims - "azp_name", listOf(saksbehandlerrolleObjectId))
            .toSaksbehandler(alleAdRoller)
            .leftOrNull() shouldBe InternalPrincipalMappingfeil.ManglerClaim("azp_name")
    }

    @Test
    fun `toSaksbehandler - manglende azp gir ManglerClaim`() {
        principal(saksbehandlerclaims - "azp", listOf(saksbehandlerrolleObjectId))
            .toSaksbehandler(alleAdRoller)
            .leftOrNull() shouldBe InternalPrincipalMappingfeil.ManglerClaim("azp")
    }

    @Test
    fun `toSaksbehandler - manglende NAVident gir ManglerClaim`() {
        principal(saksbehandlerclaims - "NAVident", listOf(saksbehandlerrolleObjectId))
            .toSaksbehandler(alleAdRoller)
            .leftOrNull() shouldBe InternalPrincipalMappingfeil.ManglerClaim("NAVident")
    }

    @Test
    fun `toSaksbehandler - manglende preferred_username gir ManglerClaim`() {
        principal(saksbehandlerclaims - "preferred_username", listOf(saksbehandlerrolleObjectId))
            .toSaksbehandler(alleAdRoller)
            .leftOrNull() shouldBe InternalPrincipalMappingfeil.ManglerClaim("preferred_username")
    }

    @Test
    fun `toSaksbehandler - systembruker gir IkkeSaksbehandler`() {
        principal(systembrukerclaims, listOf(saksbehandlerrolleObjectId))
            .toSaksbehandler(alleAdRoller)
            .leftOrNull() shouldBe InternalPrincipalMappingfeil.IkkeSaksbehandler
    }

    @Test
    fun `toSaksbehandler - ingen tilganger gir IngenRoller`() {
        principal(saksbehandlerclaims, emptyList())
            .toSaksbehandler(alleAdRoller)
            .leftOrNull() shouldBe InternalPrincipalMappingfeil.IngenRoller
    }

    @Test
    fun `toSystembruker - mapper klient og trimmer roller til små bokstaver`() {
        val systembruker = principal(systembrukerclaims, listOf("  HENTE_DATA  "))
            .toSystembruker(testSystembrukerMapper)
            .getOrNull()!!

        systembruker.klientId shouldBe "saksbehandling-id"
        systembruker.klientnavn shouldBe "saksbehandling"
        systembruker.roller.toList() shouldBe listOf(TestSystembrukerrolle.HENTE_DATA)
    }

    @Test
    fun `toSystembruker - manglende azp_name gir ManglerClaim`() {
        principal(systembrukerclaims - "azp_name", listOf("HENTE_DATA"))
            .toSystembruker(testSystembrukerMapper)
            .leftOrNull() shouldBe InternalPrincipalMappingfeil.ManglerClaim("azp_name")
    }

    @Test
    fun `toSystembruker - manglende azp gir ManglerClaim`() {
        principal(systembrukerclaims - "azp", listOf("HENTE_DATA"))
            .toSystembruker(testSystembrukerMapper)
            .leftOrNull() shouldBe InternalPrincipalMappingfeil.ManglerClaim("azp")
    }

    @Test
    fun `toSystembruker - saksbehandler gir IkkeSystembruker`() {
        principal(saksbehandlerclaims, listOf("HENTE_DATA"))
            .toSystembruker(testSystembrukerMapper)
            .leftOrNull() shouldBe InternalPrincipalMappingfeil.IkkeSystembruker
    }

    @Test
    fun `toSystembruker - ingen tilganger gir IngenRoller`() {
        principal(systembrukerclaims, emptyList())
            .toSystembruker(testSystembrukerMapper)
            .leftOrNull() shouldBe InternalPrincipalMappingfeil.IngenRoller
    }

    /**
     * Feiltypene sammenlignes med `shouldBe` i testene over, så verdilikheten må faktisk skille på hvilket claim som mangler.
     * Uten dette ville en test som forventer `ManglerClaim("azp")` bestått på `ManglerClaim("NAVident")`.
     */
    @Test
    fun `ManglerClaim skiller på hvilket claim som mangler`() {
        InternalPrincipalMappingfeil.ManglerClaim("azp") shouldBe InternalPrincipalMappingfeil.ManglerClaim("azp")
        InternalPrincipalMappingfeil.ManglerClaim("azp") shouldNotBe InternalPrincipalMappingfeil.ManglerClaim("NAVident")
        InternalPrincipalMappingfeil.ManglerClaim("azp") shouldNotBe InternalPrincipalMappingfeil.IkkeSaksbehandler
    }
}
