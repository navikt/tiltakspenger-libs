package fixtures.junitlivssyklus

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class Brudd {

    @BeforeEach
    fun rigg() {
        println("deler tilstand")
    }

    @org.junit.jupiter.api.AfterEach
    fun rydd() {
        println("rydder delt tilstand")
    }

    @Test
    fun `en test`() {
    }
}
