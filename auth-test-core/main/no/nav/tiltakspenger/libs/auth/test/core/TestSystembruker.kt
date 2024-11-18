package no.nav.tiltakspenger.libs.auth.test.core

import no.nav.tiltakspenger.libs.common.GenerellSystembruker
import no.nav.tiltakspenger.libs.common.GenerellSystembrukerrolle
import no.nav.tiltakspenger.libs.common.GenerellSystembrukerroller

internal fun systembrukerMapperForTest(
    klientId: String = "klientId",
    klientnavn: String = "klientnavn",
    roller: Set<String>,
): TestSystembruker {
    return TestSystembruker(
        roller = TestSystembrukerroller(
            roller.map {
                when (it) {
                    "test_rolle_1" -> TestSystembrukerrolle.TEST_ROLLE_1
                    "test_rolle_2" -> TestSystembrukerrolle.TEST_ROLLE_2
                    else -> throw IllegalArgumentException("Ukjent testsystemrolle: $it")
                }
            }.toSet(),
        ),
        klientId = klientId,
        klientnavn = klientnavn,
    )
}

data class TestSystembruker(
    override val roller: TestSystembrukerroller,
    override val klientId: String,
    override val klientnavn: String,
) : GenerellSystembruker<TestSystembrukerrolle, TestSystembrukerroller>

enum class TestSystembrukerrolle : GenerellSystembrukerrolle {
    TEST_ROLLE_1,
    TEST_ROLLE_2,
}

data class TestSystembrukerroller(
    override val value: Set<TestSystembrukerrolle>,
) : GenerellSystembrukerroller<TestSystembrukerrolle>, Set<TestSystembrukerrolle> by value {

    constructor(vararg roller: TestSystembrukerrolle) : this(roller.toSet())
    constructor(roller: Collection<TestSystembrukerrolle>) : this(roller.toSet())

    fun harTestRolle1(): Boolean = value.contains(TestSystembrukerrolle.TEST_ROLLE_1)
    fun harTestRolle2(): Boolean = value.contains(TestSystembrukerrolle.TEST_ROLLE_2)
}
