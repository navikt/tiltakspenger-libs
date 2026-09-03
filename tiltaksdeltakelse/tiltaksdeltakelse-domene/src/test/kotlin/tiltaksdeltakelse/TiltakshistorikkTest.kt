package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class TiltakshistorikkTest {

    private val hentetTidspunkt = LocalDateTime.of(2026, 3, 1, 8, 30)

    @Test
    fun `aggregatet samler ukjente verdier fra deltakelsene og deltakelsesformene`() {
        val ukjentStatus = Arenastatus.Ukjent("HELT_NY_STATUS")
        val ukjentForm = UkjentDeltakelsesform("NyDeltakelsesform")

        val historikk = Tiltakshistorikk(
            deltakelser = Tiltaksdeltakelser(listOf(testdeltakelse(kildestatus = ukjentStatus))),
            ukjenteDeltakelsesformer = UkjenteDeltakelsesformer(listOf(ukjentForm)),
            hentetTidspunkt = hentetTidspunkt,
        )

        historikk.ukjenteKildeverdier shouldBe listOf(ukjentStatus, ukjentForm)
        historikk.hentetTidspunkt shouldBe hentetTidspunkt
    }

    @Test
    fun `en henting der alt lot seg tolke har ingen ukjente kildeverdier`() {
        val historikk = Tiltakshistorikk(
            deltakelser = Tiltaksdeltakelser(listOf(testdeltakelse())),
            ukjenteDeltakelsesformer = UkjenteDeltakelsesformer(emptyList()),
            hentetTidspunkt = hentetTidspunkt,
        )

        historikk.ukjenteKildeverdier.shouldBeEmpty()
    }

    @Test
    fun `en ukjent deltakelsesform bærer kontraktens kode og sier hva den er`() {
        val form = UkjentDeltakelsesform("NyDeltakelsesform")

        form.kodeIKontrakten shouldBe "NyDeltakelsesform"
        form.hva shouldBe "deltakelsesform fra tiltakshistorikk"
    }

    @Test
    fun `en ukjent deltakelsesform kan ikke være blank`() {
        shouldThrowWithMessage<IllegalArgumentException>("En ukjent kildeverdi må bære kontraktens kode") {
            UkjentDeltakelsesform(" ")
        }
    }

    @Test
    fun `samme ukjente deltakelsesform kan stå flere ganger, og antallet bevares`() {
        val former = UkjenteDeltakelsesformer(
            listOf(UkjentDeltakelsesform("NyDeltakelsesform"), UkjentDeltakelsesform("NyDeltakelsesform")),
        )

        former.ukjenteKildeverdier shouldBe listOf(UkjentDeltakelsesform("NyDeltakelsesform"), UkjentDeltakelsesform("NyDeltakelsesform"))
    }
}
