package no.nav.tiltakspenger.libs.jobber

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Test

internal class RunCheckFactoryTest {
    @Test
    fun `leaderpod - false`() {
        val mock = mockk<LeaderPodLookup> {
            coEvery { amITheLeader(any()) } returns false.right()
        }
        RunCheckFactory(leaderPodLookup = mock) { isReady() }.let {
            it.leaderPod().shouldRun() shouldBe false
            coVerify(exactly = 1) { mock.amITheLeader(any()) }
        }
    }

    @Test
    fun `leaderpod - true`() {
        val mock = mockk<LeaderPodLookup> {
            coEvery { amITheLeader(any()) } returns true.right()
        }
        RunCheckFactory(leaderPodLookup = mock) { isReady() }.let {
            it.leaderPod().shouldRun() shouldBe true
            coVerify(exactly = 1) { mock.amITheLeader(any()) }
        }
    }

    @Test
    fun `leaderpod - left`() {
        val mock = mockk<LeaderPodLookup> {
            coEvery { amITheLeader(any()) } returns LeaderPodLookupFeil.Ikke2xx(500, "").left()
        }
        RunCheckFactory(leaderPodLookup = mock) { isReady() }.let {
            it.leaderPod().shouldRun() shouldBe false
            coVerify(exactly = 1) { mock.amITheLeader(any()) }
        }
    }

    @Test
    fun `isready - false`() {
        val mock = mockk<LeaderPodLookup>()
        RunCheckFactory(leaderPodLookup = mock) { isNotReady() }
            .isReady().shouldRun() shouldBe false
    }

    @Test
    fun `isready - true`() {
        val mock = mockk<LeaderPodLookup>()
        RunCheckFactory(leaderPodLookup = mock) { isReady() }
            .isReady().shouldRun() shouldBe true
    }

    private fun isReady(): Boolean =
        true

    private fun isNotReady(): Boolean =
        false
}
