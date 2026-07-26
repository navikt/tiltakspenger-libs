package no.nav.tiltakspenger.libs.texas

import no.nav.tiltakspenger.libs.common.GenerellSystembruker
import no.nav.tiltakspenger.libs.common.GenerellSystembrukerrolle
import no.nav.tiltakspenger.libs.common.GenerellSystembrukerroller

/**
 * Systembruker-typene testene mapper til.
 * Deles av [TexasAuthenticationProviderTest] og [ApplicationCallHelpersTest] slik at begge kjører samme mapper som en konsument ville skrevet.
 */
internal data class TestSystembruker(
    override val roller: TestSystembrukerroller,
    override val klientId: String,
    override val klientnavn: String,
) : GenerellSystembruker<TestSystembrukerrolle, TestSystembrukerroller>

internal enum class TestSystembrukerrolle : GenerellSystembrukerrolle {
    LAGE_HENDELSER,
    HENTE_DATA,
}

internal data class TestSystembrukerroller(
    override val value: Set<TestSystembrukerrolle>,
) : GenerellSystembrukerroller<TestSystembrukerrolle>,
    Set<TestSystembrukerrolle> by value {

    constructor(vararg roller: TestSystembrukerrolle) : this(roller.toSet())
    constructor(roller: Collection<TestSystembrukerrolle>) : this(roller.toSet())

    fun harLageHendelser(): Boolean = value.contains(TestSystembrukerrolle.LAGE_HENDELSER)
    fun harHenteData(): Boolean = value.contains(TestSystembrukerrolle.HENTE_DATA)
}

internal fun mapper(
    klientId: String = "klientId",
    klientnavn: String = "klientnavn",
    roller: Set<String>,
): TestSystembruker {
    return TestSystembruker(
        roller = TestSystembrukerroller(
            roller.map {
                TestSystembrukerrolle.valueOf(it.uppercase())
            }.toSet(),
        ),
        klientId = klientId,
        klientnavn = klientnavn,
    )
}

/**
 * [mapper] castet til signaturen [ApplicationCall.systembruker][no.nav.tiltakspenger.libs.texas.systembruker] krever.
 */
@Suppress("UNCHECKED_CAST")
internal val testSystembrukerMapper =
    ::mapper as (String, String, Set<String>) -> GenerellSystembruker<GenerellSystembrukerrolle, GenerellSystembrukerroller<GenerellSystembrukerrolle>>
