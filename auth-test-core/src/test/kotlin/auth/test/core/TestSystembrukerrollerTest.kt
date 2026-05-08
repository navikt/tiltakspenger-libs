package no.nav.tiltakspenger.libs.auth.test.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class TestSystembrukerrollerTest {
    @Test
    fun `oppretter testroller fra vararg`() {
        val roller = TestSystembrukerroller(
            TestSystembrukerrolle.TEST_ROLLE_1,
            TestSystembrukerrolle.TEST_ROLLE_2,
        )

        roller.value shouldBe setOf(
            TestSystembrukerrolle.TEST_ROLLE_1,
            TestSystembrukerrolle.TEST_ROLLE_2,
        )
        roller.toSet() shouldBe roller.value
        roller.harTestRolle1() shouldBe true
        roller.harTestRolle2() shouldBe true
    }

    @Test
    fun `oppretter testroller fra collection`() {
        val roller = TestSystembrukerroller(
            listOf(
                TestSystembrukerrolle.TEST_ROLLE_1,
                TestSystembrukerrolle.TEST_ROLLE_1,
            ),
        )

        roller.value shouldBe setOf(TestSystembrukerrolle.TEST_ROLLE_1)
        roller.toSet() shouldBe roller.value
        roller.harTestRolle1() shouldBe true
        roller.harTestRolle2() shouldBe false
    }
}
