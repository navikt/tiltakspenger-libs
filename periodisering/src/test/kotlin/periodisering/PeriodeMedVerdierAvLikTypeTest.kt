package no.nav.tiltakspenger.libs.periodisering

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.dato.oktober
import no.nav.tiltakspenger.libs.periode.Periode
import org.junit.jupiter.api.Test

enum class Utfall {
    OPPFYLT,
    IKKE_OPPFYLT,
    KREVER_MANUELL_VURDERING,
}

class PeriodeMedVerdierAvLikTypeTest {
    @Test
    fun `en oppfylt periode kombinert med en delvis oppfylt periode skal gi en delvis oppfylt periode`() {
        val aap = SammenhengendePeriodisering(
            Utfall.IKKE_OPPFYLT,
            Periode(1.oktober(2023), 10.oktober(2023)),
        ).setVerdiForDelperiode(Utfall.OPPFYLT, Periode(6.oktober(2023), 10.oktober(2023)))

        val dagpenger = SammenhengendePeriodisering(
            Utfall.OPPFYLT,
            Periode(1.oktober(2023), 10.oktober(2023)),
        )

        val vedtak: Periodisering<Utfall> = aap.kombiner(dagpenger, ::kombinerToUfall)
        vedtak.size shouldBe 2

        vedtak.count { it.verdi == Utfall.OPPFYLT } shouldBe 1
        vedtak.find { it.verdi == Utfall.OPPFYLT }!!.periode shouldBe Periode(6.oktober(2023), 10.oktober(2023))

        vedtak.count { it.verdi == Utfall.IKKE_OPPFYLT } shouldBe 1
        vedtak.find { it.verdi == Utfall.IKKE_OPPFYLT }!!.periode shouldBe Periode(1.oktober(2023), 5.oktober(2023))
    }

    private fun kombinerToUfall(en: Utfall, to: Utfall): Utfall {
        if (en == Utfall.KREVER_MANUELL_VURDERING || to == Utfall.KREVER_MANUELL_VURDERING) {
            return Utfall.KREVER_MANUELL_VURDERING
        }
        if (en == Utfall.IKKE_OPPFYLT || to == Utfall.IKKE_OPPFYLT) {
            return Utfall.IKKE_OPPFYLT
        }
        return Utfall.OPPFYLT
    }
}
