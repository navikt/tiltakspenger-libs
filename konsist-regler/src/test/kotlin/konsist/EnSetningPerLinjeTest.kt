package no.nav.tiltakspenger.libs.konsist

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

internal class EnSetningPerLinjeTest {
    private val scope = fixtureScope("ensetning")
    private val markdownRot = fixturesti("ensetning-md")

    @Test
    fun `flagger to setninger på én kommentarlinje`() {
        val brudd = EnSetningPerLinje.flereSetningerIKommentarer(scope)

        brudd shouldHaveSize 1
        brudd.single() shouldContain "Denne linjen har to setninger."
    }

    @Test
    fun `flagger setning brukket over to kommentarlinjer`() {
        val brudd = EnSetningPerLinje.brukneSetningerIKommentarer(scope)

        brudd shouldHaveSize 2
        brudd.first() shouldContain "fortsetter på neste linje"
        brudd.last() shouldContain "og fortsetter med prosa her etterpå"
    }

    @Test
    fun `frittstående URL-linjer og language-injection-direktiver flagges ikke som brukne setninger`() {
        val brukne = EnSetningPerLinje.brukneSetningerIKommentarer(scope)

        brukne.filter { it.contains("example.com/docs") || it.contains("language=") }.shouldBeEmpty()
    }

    @Test
    fun `forkortelser, maskert kode og kolon-avslutning flagges ikke`() {
        val renScope = fixtureScope("ensetning")
        val flere = EnSetningPerLinje.flereSetningerIKommentarer(renScope).filter { it.contains("Ren.kt") }
        val brukne = EnSetningPerLinje.brukneSetningerIKommentarer(renScope).filter { it.contains("Ren.kt") }

        flere.shouldBeEmpty()
        brukne.shouldBeEmpty()
    }

    @Test
    fun `flagger to setninger på én markdown-linje`() {
        val brudd = EnSetningPerLinje.flereSetningerIMarkdown(markdownRot)

        brudd shouldHaveSize 1
        brudd.single() shouldContain "Dette er to setninger."
    }

    @Test
    fun `flagger brukket setning i markdown, men ikke kodeblokker og tabeller`() {
        val brudd = EnSetningPerLinje.brukneSetningerIMarkdown(markdownRot)

        brudd shouldHaveSize 1
        brudd.single() shouldContain "to linjer uten avslutning"
    }
}
