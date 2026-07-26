package no.nav.tiltakspenger.libs.httpklient.infra.kall

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

internal class MultipartDelTest {
    private val innhold = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    private fun del(
        feltnavn: String = "file0",
        filnavn: String = "vedlegg.png",
        contentType: String = "image/png",
        innhold: ByteArray = this.innhold,
    ) = MultipartDel(feltnavn = feltnavn, filnavn = filnavn, contentType = contentType, innhold = innhold)

    @Test
    fun `to deler med likt innhold er like, selv om bytene er ulike instanser`() {
        val en = del(innhold = byteArrayOf(1, 2, 3))
        val to = del(innhold = byteArrayOf(1, 2, 3))

        // Poenget med den håndskrevne equals-en: en generert data class-equals ville sammenlignet ByteArray på referanse.
        (en.innhold === to.innhold) shouldBe false
        en shouldBe to
        en.hashCode() shouldBe to.hashCode()
    }

    @Test
    fun `deler som er ulike i ett felt er ulike`() {
        del() shouldNotBe del(feltnavn = "file1")
        del() shouldNotBe del(filnavn = "annet.png")
        del() shouldNotBe del(contentType = "image/jpeg")
        del() shouldNotBe del(innhold = byteArrayOf(9, 9, 9))
    }

    @Test
    fun `en del er lik seg selv og ulik andre typer`() {
        val en = del()

        en shouldBe en
        en shouldNotBe "file0"
        en shouldNotBe null
    }

    @Test
    fun `hashCode bygges kun av metadataen, slik at vedlegg på flere megabyte ikke hashes`() {
        // Lovlig: hashen bruker en delmengde av feltene equals bruker, så like objekter gir fortsatt lik hash.
        del(innhold = byteArrayOf(1)).hashCode() shouldBe del(innhold = ByteArray(5_000_000)).hashCode()
        del(feltnavn = "file0").hashCode() shouldNotBe del(feltnavn = "file1").hashCode()
    }

    @Test
    fun `toString oppgir størrelsen på innholdet, aldri selve bytene`() {
        val tekstligInnhold = "hemmelig vedleggsinnhold".toByteArray()

        val tekst = del(innhold = tekstligInnhold).toString()

        tekst shouldBe "MultipartDel(feltnavn=file0, filnavn=vedlegg.png, contentType=image/png, innhold=<${tekstligInnhold.size} bytes>)"
        tekst shouldNotContain "hemmelig"
    }

    @Test
    fun `avviser blanke navn`() {
        shouldThrowWithMessage<IllegalArgumentException>("feltnavn kan ikke være blankt") { del(feltnavn = " ") }
        shouldThrowWithMessage<IllegalArgumentException>("filnavn kan ikke være blankt") { del(filnavn = "") }
        shouldThrowWithMessage<IllegalArgumentException>("contentType kan ikke være blank") { del(contentType = "") }
    }

    @Test
    fun `avviser linjeskift som kunne injisert egne headere eller deler`() {
        shouldThrowWithMessage<IllegalArgumentException>("feltnavn kan ikke inneholde linjeskift, var 'file0\nX-Injisert: ja'") {
            del(feltnavn = "file0\nX-Injisert: ja")
        }
        shouldThrowWithMessage<IllegalArgumentException>("filnavn kan ikke inneholde linjeskift, var 'en.png\r\nX-Injisert: ja'") {
            del(filnavn = "en.png\r\nX-Injisert: ja")
        }
        shouldThrowWithMessage<IllegalArgumentException>("contentType kan ikke inneholde linjeskift, var 'image/png\nX-Injisert: ja'") {
            del(contentType = "image/png\nX-Injisert: ja")
        }
    }

    @Test
    fun `anførselstegn og backslash i filnavn godtas, siden de escapes ved enkoding`() {
        // Filnavn kommer fra brukeropplastede vedlegg og skal ikke kunne velte kallet.
        del(filnavn = """cv"; name="annet\.png""").filnavn shouldContain "\""
    }
}
