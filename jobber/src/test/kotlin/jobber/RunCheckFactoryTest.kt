package no.nav.tiltakspenger.libs.jobber

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

internal class RunCheckFactoryTest {
    @Test
    fun `leaderpod - ikke leder`() {
        val mock = mockk<LeaderPodLookup> {
            every { amITheLeader(any()) } returns false.right()
        }
        RunCheckFactory(leaderPodLookup = mock, isReady = { true }).let {
            it.leaderPod().shouldRun() shouldBe JobbSkalIkkeKjøre.IkkeLederPod.left()
            verify(exactly = 1) { mock.amITheLeader(any()) }
        }
    }

    @Test
    fun `leaderpod - er leder`() {
        val mock = mockk<LeaderPodLookup> {
            every { amITheLeader(any()) } returns true.right()
        }
        RunCheckFactory(leaderPodLookup = mock, isReady = { true }).let {
            it.leaderPod().shouldRun() shouldBe Unit.right()
            verify(exactly = 1) { mock.amITheLeader(any()) }
        }
    }

    @Test
    fun `leaderpod - oppslag feilet`() {
        val mock = mockk<LeaderPodLookup> {
            every { amITheLeader(any()) } returns LeaderPodLookupFeil.Ikke2xx(500, "").left()
        }
        RunCheckFactory(leaderPodLookup = mock, isReady = { true }).let {
            it.leaderPod().shouldRun() shouldBe
                JobbSkalIkkeKjøre.LederpodOppslagFeilet(LeaderPodLookupFeil.Ikke2xx(500, "")).left()
            verify(exactly = 1) { mock.amITheLeader(any()) }
        }
    }

    @Test
    fun `leaderpod - oppslag kaster exception`() {
        val mock = mockk<LeaderPodLookup> {
            every { amITheLeader(any()) } throws IllegalStateException("boom")
        }

        LeaderPod(
            leaderPodLookup = mock,
            localHostNameProvider = { "pod-1" },
        ).shouldRun().leftOrNull().shouldBeInstanceOf<JobbSkalIkkeKjøre.LederpodOppslagKastet>()

        verify(exactly = 1) { mock.amITheLeader("pod-1") }
    }

    @Test
    fun `leaderpod - vertsnavnoppslag kaster exception`() {
        val mock = mockk<LeaderPodLookup>()
        LeaderPod(
            leaderPodLookup = mock,
            localHostNameProvider = { throw IllegalStateException("boom") },
        ).shouldRun().leftOrNull().shouldBeInstanceOf<JobbSkalIkkeKjøre.VertsnavnOppslagKastet>()

        verify(exactly = 0) { mock.amITheLeader(any()) }
    }

    @Test
    fun `isready - ikke klar`() {
        RunCheckFactory(leaderPodLookup = mockk(), isReady = { false })
            .isReady().shouldRun() shouldBe JobbSkalIkkeKjøre.IkkeKlar.left()
    }

    @Test
    fun `isready - klar`() {
        RunCheckFactory(leaderPodLookup = mockk(), isReady = { true })
            .isReady().shouldRun() shouldBe Unit.right()
    }
}
