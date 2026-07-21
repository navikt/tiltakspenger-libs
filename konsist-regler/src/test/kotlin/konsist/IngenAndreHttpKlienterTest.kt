package no.nav.tiltakspenger.libs.konsist

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

internal class IngenAndreHttpKlienterTest {
    private val scope = fixtureScope("httpklienter")

    @Test
    fun `flagger klient-importer, men ikke http-vokabular eller httpklient selv`() {
        val brudd = IngenAndreHttpKlienter.brudd(scope)

        brudd shouldHaveSize 5
        val samlet = brudd.joinToString("\n")
        samlet shouldContain "io.ktor.client.HttpClient"
        samlet shouldContain "io.ktor.client.request.get"
        samlet shouldContain "java.net.http.HttpRequest"
        samlet shouldContain "okhttp3.OkHttpClient"
        samlet shouldContain "org.apache.hc.client5.http.classic.HttpClient"
    }

    @Test
    fun `unntatte filstier flagges ikke`() {
        IngenAndreHttpKlienter.brudd(scope, unntatteFilstier = setOf("httpklienter/Brudd.kt")).shouldBeEmpty()
    }

    @Test
    fun `assert kaster med lesbar melding ved brudd`() {
        val feil = shouldThrow<AssertionError> { IngenAndreHttpKlienter.assert(scope) }
        feil.message shouldContain "HTTP-kall går via libs sin httpklient"
        feil.message shouldContain "Fant 5 brudd"
    }
}
