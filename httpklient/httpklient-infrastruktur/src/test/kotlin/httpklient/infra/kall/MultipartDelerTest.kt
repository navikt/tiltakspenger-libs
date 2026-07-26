package no.nav.tiltakspenger.libs.httpklient.infra.kall

import arrow.core.nonEmptyListOf
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

internal class MultipartDelerTest {
    private fun del(feltnavn: String = "file0", innhold: ByteArray = byteArrayOf(1, 2, 3)) =
        MultipartDel(feltnavn = feltnavn, filnavn = "vedlegg.png", contentType = "image/png", innhold = innhold)

    @Test
    fun `to samlinger med like deler er like, selv om delene er ulike instanser`() {
        val en = MultipartDeler(nonEmptyListOf(del("file0"), del("file1")))
        val to = MultipartDeler(nonEmptyListOf(del("file0"), del("file1")))

        // Verdilikheten kommer fra MultipartDel.equals; uten den ville dette vært referansesammenligning av delene.
        en shouldBe to
        en.hashCode() shouldBe to.hashCode()
    }

    @Test
    fun `samlinger med ulike deler er ulike`() {
        MultipartDeler(del("file0")) shouldNotBe MultipartDeler(del("file1"))
        MultipartDeler(del()) shouldNotBe MultipartDeler(nonEmptyListOf(del("file0"), del("file1")))
        MultipartDeler(del(innhold = byteArrayOf(1))) shouldNotBe MultipartDeler(del(innhold = byteArrayOf(2)))
    }

    @Test
    fun `equals er symmetrisk mot en vanlig List, siden typen selv er en List`() {
        val deler = MultipartDeler(del())
        val liste = listOf(del())

        // Den genererte data class-equals ville gitt false her, mens ArrayList.equals gir true andre veien — altså asymmetrisk.
        (deler == liste) shouldBe true
        (liste == deler) shouldBe true
        deler.hashCode() shouldBe liste.hashCode()
    }

    @Test
    fun `en samling er lik seg selv og ulik andre typer`() {
        val deler = MultipartDeler(del())

        deler shouldBe deler
        deler shouldNotBe "file0"
        deler shouldNotBe null
    }

    @Test
    fun `oppfører seg som en List av delene`() {
        val deler = MultipartDeler(nonEmptyListOf(del("file0"), del("file1")))

        deler.size shouldBe 2
        deler.first() shouldBe del("file0")
        deler.map { it.feltnavn } shouldBe listOf("file0", "file1")
    }

    @Test
    fun `avviser duplikate feltnavn, som ville gitt færre svar fra serveren enn filer vi sendte`() {
        shouldThrowWithMessage<IllegalArgumentException>("feltnavn kan ikke ha duplikate verdier, men hadde: [file0, file0]") {
            MultipartDeler(nonEmptyListOf(del("file0"), del("file0")))
        }
    }

    @Test
    fun `ingen konstruksjonsvei omgår guarden, heller ikke copy`() {
        // copy() går via primærkonstruktøren og kjører dermed init — standard Kotlin, låst fast her fordi hele poenget med typen er guarden.
        val deler = MultipartDeler(del("file0"))

        shouldThrowWithMessage<IllegalArgumentException>("feltnavn kan ikke ha duplikate verdier, men hadde: [file0, file0]") {
            deler.copy(value = nonEmptyListOf(del("file0"), del("file0")))
        }
    }

    @Test
    fun `tilMultipartDeler avviser tom liste`() {
        shouldThrowWithMessage<IllegalArgumentException>("multipart-body må ha minst én del.") {
            emptyList<MultipartDel>().tilMultipartDeler()
        }
    }

    @Test
    fun `tilMultipartDeler bevarer rekkefølgen fra lista`() {
        val deler = listOf(del("file0"), del("file1"), del("file2")).tilMultipartDeler()

        deler.map { it.feltnavn } shouldBe listOf("file0", "file1", "file2")
    }
}
