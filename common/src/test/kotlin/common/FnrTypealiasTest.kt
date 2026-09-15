package no.nav.tiltakspenger.libs.common

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import no.nav.tiltakspenger.libs.common.personopplysning.Fnr as NyFnr

@Suppress("DEPRECATION")
internal class FnrTypealiasTest {
    @Test
    fun `gammelt alias beholder konstruksjon companion og runtime-type`() {
        val fnr: Fnr = Fnr.fromString("12845678901")
        val verdi: Any = fnr

        Fnr.tryFromString("12845678901") shouldBe fnr
        Fnr.tryFromString("ugyldig") shouldBe null
        (verdi is Fnr) shouldBe true
        Fnr::class shouldBe NyFnr::class
        shouldThrow<UgyldigFnrException> { Fnr.fromString("ugyldig") }
    }
}
