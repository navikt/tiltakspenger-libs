package no.nav.tiltakspenger.libs.jobber

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class TaskResultatTest {

    @Test
    fun `tilSamletResultat lar MerArbeid vinne over alt annet`() {
        listOf(TaskResultat.IngenArbeid, TaskResultat.Ferdig, TaskResultat.Feilet, TaskResultat.MerArbeid)
            .tilSamletResultat() shouldBe TaskResultat.MerArbeid
    }

    @Test
    fun `tilSamletResultat lar Feilet vinne over Ferdig og IngenArbeid`() {
        listOf(TaskResultat.IngenArbeid, TaskResultat.Ferdig, TaskResultat.Feilet)
            .tilSamletResultat() shouldBe TaskResultat.Feilet
    }

    @Test
    fun `tilSamletResultat lar Ferdig vinne over IngenArbeid`() {
        listOf(TaskResultat.IngenArbeid, TaskResultat.Ferdig)
            .tilSamletResultat() shouldBe TaskResultat.Ferdig
    }

    @Test
    fun `tilSamletResultat gir IngenArbeid når alle del-jobber var uten arbeid`() {
        listOf(TaskResultat.IngenArbeid, TaskResultat.IngenArbeid)
            .tilSamletResultat() shouldBe TaskResultat.IngenArbeid
    }

    @Test
    fun `tilSamletResultat gir IngenArbeid for en tom samling`() {
        emptyList<TaskResultat>().tilSamletResultat() shouldBe TaskResultat.IngenArbeid
    }
}
