package no.nav.tiltakspenger.libs.jobber

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.util.AttributeKey
import io.ktor.util.Attributes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Test

internal class RunCheckFactoryTest {
    private val isReadyKey = AttributeKey<Boolean>("isReady")
    private val attributes = Attributes()

    @Test
    fun `leaderpod - false`() {
        val mock = mockk<LeaderPodLookup> {
            coEvery { amITheLeader(any()) } returns false.right()
        }
        RunCheckFactory(leaderPodLookup = mock, attributes = attributes, isReadyKey = isReadyKey).let {
            it.leaderPod().shouldRun() shouldBe false
            coVerify(exactly = 1) { mock.amITheLeader(any()) }
        }
    }

    @Test
    fun `leaderpod - true`() {
        val mock = mockk<LeaderPodLookup> {
            coEvery { amITheLeader(any()) } returns true.right()
        }
        RunCheckFactory(leaderPodLookup = mock, attributes = attributes, isReadyKey = isReadyKey).let {
            it.leaderPod().shouldRun() shouldBe true
            coVerify(exactly = 1) { mock.amITheLeader(any()) }
        }
    }

    @Test
    fun `leaderpod - left`() {
        val mock = mockk<LeaderPodLookup> {
            coEvery { amITheLeader(any()) } returns LeaderPodLookupFeil.Ikke2xx(500, "").left()
        }
        RunCheckFactory(leaderPodLookup = mock, attributes = attributes, isReadyKey = isReadyKey).let {
            it.leaderPod().shouldRun() shouldBe false
            coVerify(exactly = 1) { mock.amITheLeader(any()) }
        }
    }

    @Test
    fun `isready - false`() {
        val mock = mockk<LeaderPodLookup>()
        attributes.put(isReadyKey, false)
        RunCheckFactory(leaderPodLookup = mock, attributes = attributes, isReadyKey = isReadyKey)
            .isReady().shouldRun() shouldBe false
    }

    @Test
    fun `isready - true`() {
        val mock = mockk<LeaderPodLookup>()
        attributes.put(isReadyKey, true)
        RunCheckFactory(leaderPodLookup = mock, attributes = attributes, isReadyKey = isReadyKey)
            .isReady().shouldRun() shouldBe true
    }
}
