package no.nav.tiltakspenger.libs.konsist

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

internal class IngenHardkodedeFnrTest {
    @Test
    fun `finner ellevesifret tall og maskerer treffet`(@TempDir rot: Path) {
        val fnr = listOf("100206", "62730").joinToString("")
        rot.resolve("src/test/kotlin/Test.kt").skriv("""val fnr = "$fnr"""")

        IngenHardkodedeFnr.brudd(rot) shouldContainExactly listOf("src/test/kotlin/Test.kt:1: ***********")
        shouldThrow<AssertionError> {
            IngenHardkodedeFnr.assert(rot)
        }.message.let { melding ->
            melding shouldContain "***********"
            melding shouldNotContain fnr
        }
    }

    @Test
    fun `tillater syntetiske fnr med 8 eller 9 som tredje siffer`(@TempDir rot: Path) {
        rot.resolve("src/test/resources/personer.json").skriv(
            listOf(
                listOf("008999", "99999").joinToString(""),
                listOf("009000", "00000").joinToString(""),
            ).joinToString("\n"),
        )

        IngenHardkodedeFnr.brudd(rot).shouldBeEmpty()
    }

    @Test
    fun `finner alle ellevesifrede tall men ignorerer sifre i lengre tall og alfanumeriske verdier`(@TempDir rot: Path) {
        val elleveSifre = listOf("100206", "62731").joinToString("")
        rot.resolve("src/main/kotlin/Test.kt").skriv(
            listOf(
                listOf("321399", "12345").joinToString(""),
                elleveSifre,
                listOf("1100206", "627300").joinToString(""),
                "a${elleveSifre}b",
            ).joinToString("\n"),
        )

        IngenHardkodedeFnr.brudd(rot) shouldContainExactly listOf(
            "src/main/kotlin/Test.kt:1: ***********",
            "src/main/kotlin/Test.kt:2: ***********",
        )
    }

    @Test
    fun `skanner ressurser og ekstra filendelser men ikke ekskluderte kataloger`(@TempDir rot: Path) {
        val fnr = listOf("070706", "76243").joinToString("")
        rot.resolve("src/main/resources/person.json").skriv(fnr)
        rot.resolve("src/test/data/person.custom").skriv(fnr)
        rot.resolve("build/generated/person.json").skriv(fnr)
        rot.resolve("utdata/person.json").skriv(fnr)

        IngenHardkodedeFnr.brudd(
            rot,
            ekstraEkskluderteKataloger = setOf("utdata"),
            ekstraFilendelser = setOf("CUSTOM"),
        ) shouldContainExactly listOf(
            "src/main/resources/person.json:1: ***********",
            "src/test/data/person.custom:1: ***********",
        )
    }

    private fun Path.skriv(innhold: String) {
        parent.createDirectories()
        writeText(innhold)
    }
}
