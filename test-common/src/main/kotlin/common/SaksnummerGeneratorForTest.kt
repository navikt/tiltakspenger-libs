package no.nav.tiltakspenger.libs.common

import arrow.atomic.Atomic
import java.time.LocalDate

/**
 * Trådsikker.
 */
class SaksnummerGeneratorForTest(
    første: Saksnummer = Saksnummer.genererSaknummer(løpenr = "1001", dato = LocalDate.of(2021, 1, 1)),
) : SaksnummerGenerator {
    private val neste = Atomic(første)

    fun generer(): Saksnummer = neste.getAndUpdate { it.nesteSaksnummer() }

    /** @param dato blir ignorert */
    override fun generer(dato: LocalDate) = generer()
}
