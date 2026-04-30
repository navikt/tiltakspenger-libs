package no.nav.tiltakspenger.libs.jobber

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.util.AttributeKey
import io.ktor.util.Attributes
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

internal class RunCheckFactoryTest {
    private val isReadyKey = AttributeKey<Boolean>("isReady")

    @Test
    fun `leaderpod - false`() {
        val mock = mockk<LeaderPodLookup> {
            every { amITheLeader(any()) } returns false.right()
        }
        RunCheckFactory(leaderPodLookup = mock, attributes = Attributes(), isReadyKey = isReadyKey).let {
            it.leaderPod().shouldRun() shouldBe false
            verify(exactly = 1) { mock.amITheLeader(any()) }
        }
    }

    @Test
    fun `leaderpod - true`() {
        val mock = mockk<LeaderPodLookup> {
            every { amITheLeader(any()) } returns true.right()
        }
        RunCheckFactory(leaderPodLookup = mock, attributes = Attributes(), isReadyKey = isReadyKey).let {
            it.leaderPod().shouldRun() shouldBe true
            verify(exactly = 1) { mock.amITheLeader(any()) }
        }
    }

    @Test
    fun `leaderpod - left`() {
        val mock = mockk<LeaderPodLookup> {
            every { amITheLeader(any()) } returns LeaderPodLookupFeil.Ikke2xx(500, "").left()
        }
        RunCheckFactory(leaderPodLookup = mock, attributes = Attributes(), isReadyKey = isReadyKey).let {
            it.leaderPod().shouldRun() shouldBe false
            verify(exactly = 1) { mock.amITheLeader(any()) }
        }
    }

    @Test
    fun `leaderpod - exception when resolving local hostname`() {
        val mock = mockk<LeaderPodLookup>()
        LeaderPod(
            leaderPodLookup = mock,
            localHostNameProvider = { throw IllegalStateException("boom") },
        ).shouldRun() shouldBe false

        verify(exactly = 0) { mock.amITheLeader(any()) }
    }

    @Test
    fun `isready - false`() {
        val mock = mockk<LeaderPodLookup>()
        val attributes = Attributes()
        attributes.put(isReadyKey, false)
        RunCheckFactory(leaderPodLookup = mock, attributes = attributes, isReadyKey = isReadyKey)
            .isReady().shouldRun() shouldBe false
    }

    @Test
    fun `isready - true`() {
        val mock = mockk<LeaderPodLookup>()
        val attributes = Attributes()
        attributes.put(isReadyKey, true)
        RunCheckFactory(leaderPodLookup = mock, attributes = attributes, isReadyKey = isReadyKey)
            .isReady().shouldRun() shouldBe true
    }

    @Test
    fun `isready - missing key`() {
        RunCheckFactory(
            leaderPodLookup = mockk(),
            attributes = Attributes(),
            isReadyKey = isReadyKey,
        ).isReady().shouldRun() shouldBe false
    }
}
