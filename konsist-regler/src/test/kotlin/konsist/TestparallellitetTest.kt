package no.nav.tiltakspenger.libs.konsist

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

internal class TestparallellitetTest {

    @Test
    fun `synkron og komplett konfigurasjon gir ingen brudd`() {
        Testparallellitet.brudd(fixturesti("testparallellitet/ren")).shouldBeEmpty()
    }

    @Test
    fun `innstillingene er konfigurerbare`() {
        val utvidet = Testparallellitet.standardInnstillinger + ("junit.jupiter.testinstance.lifecycle.default" to "per_class")

        Testparallellitet.brudd(fixturesti("testparallellitet/ren"), forventedeInnstillinger = utvidet).shouldBeEmpty()
    }

    @Test
    fun `flagger feil verdi og manglende nøkkel i begge kilder, men ikke kommentarer`() {
        val brudd = Testparallellitet.brudd(fixturesti("testparallellitet/brutt"))

        brudd shouldHaveSize 4
        val samlet = brudd.joinToString("\n")
        samlet shouldContain "junit-platform.properties: junit.jupiter.execution.parallel.mode.default=same_thread, forventet concurrent"
        samlet shouldContain "junit-platform.properties: mangler junit.jupiter.execution.parallel.mode.classes.default=concurrent"
        samlet shouldContain "build.gradle.kts: systemProperty junit.jupiter.execution.parallel.mode.default=same_thread, forventet concurrent"
        samlet shouldContain "build.gradle.kts: mangler systemProperty(\"junit.jupiter.execution.parallel.mode.classes.default\", \"concurrent\")"
    }

    @Test
    fun `flagger manglende properties-fil og udeklarerte system-properties`() {
        val brudd = Testparallellitet.brudd(fixturesti("testparallellitet/mangler"))

        // Fila som mangler pluss de tre systemProperty-linjene som ikke finnes i byggfila.
        brudd shouldHaveSize 4
        brudd.joinToString("\n") shouldContain "fila mangler"
    }

    @Test
    fun `assert kaster med lesbar melding ved brudd`() {
        val feil = shouldThrow<AssertionError> { Testparallellitet.assert(fixturesti("testparallellitet/brutt")) }
        feil.message shouldContain "håndhevelsen av at tester ikke deler tilstand"
    }
}
