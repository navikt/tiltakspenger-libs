package no.nav.tiltakspenger.libs.arena.tiltak

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class ArenaDeltakerStatusTypeTest {
    @Test
    fun `mapper arena deltakerstatus til deltakerstatus DTO`() {
        val iDag = LocalDate.now(fixedClock)

        listOf(
            TestCase(
                status = ArenaDeltakerStatusType.DELAVB,
                fom = iDag,
                expected = TiltakResponsDTO.DeltakerStatusDTO.AVBRUTT,
            ),
            TestCase(
                status = ArenaDeltakerStatusType.FULLF,
                fom = iDag,
                expected = TiltakResponsDTO.DeltakerStatusDTO.FULLFORT,
            ),
            TestCase(
                status = ArenaDeltakerStatusType.GJENN,
                fom = iDag.minusDays(1),
                expected = TiltakResponsDTO.DeltakerStatusDTO.DELTAR,
            ),
            TestCase(
                status = ArenaDeltakerStatusType.GJENN,
                fom = iDag,
                expected = TiltakResponsDTO.DeltakerStatusDTO.DELTAR,
            ),
            TestCase(
                status = ArenaDeltakerStatusType.GJENN,
                fom = iDag.plusDays(1),
                expected = TiltakResponsDTO.DeltakerStatusDTO.VENTER_PA_OPPSTART,
            ),
            TestCase(
                status = ArenaDeltakerStatusType.GJENN,
                fom = null,
                expected = TiltakResponsDTO.DeltakerStatusDTO.VENTER_PA_OPPSTART,
            ),
            TestCase(
                status = ArenaDeltakerStatusType.GJENN_AVB,
                fom = iDag,
                expected = TiltakResponsDTO.DeltakerStatusDTO.AVBRUTT,
            ),
            TestCase(
                status = ArenaDeltakerStatusType.IKKEM,
                fom = iDag,
                expected = TiltakResponsDTO.DeltakerStatusDTO.AVBRUTT,
            ),
            TestCase(
                status = ArenaDeltakerStatusType.JATAKK,
                fom = iDag,
                expected = TiltakResponsDTO.DeltakerStatusDTO.DELTAR,
            ),
            TestCase(
                status = ArenaDeltakerStatusType.TILBUD,
                fom = iDag,
                expected = TiltakResponsDTO.DeltakerStatusDTO.VENTER_PA_OPPSTART,
            ),
            TestCase(
                status = ArenaDeltakerStatusType.AKTUELL,
                fom = iDag,
                expected = TiltakResponsDTO.DeltakerStatusDTO.SOKT_INN,
            ),
            TestCase(
                status = ArenaDeltakerStatusType.AVSLAG,
                fom = iDag,
                expected = TiltakResponsDTO.DeltakerStatusDTO.IKKE_AKTUELL,
            ),
            TestCase(
                status = ArenaDeltakerStatusType.GJENN_AVL,
                fom = iDag,
                expected = TiltakResponsDTO.DeltakerStatusDTO.IKKE_AKTUELL,
            ),
            TestCase(
                status = ArenaDeltakerStatusType.IKKAKTUELL,
                fom = iDag,
                expected = TiltakResponsDTO.DeltakerStatusDTO.IKKE_AKTUELL,
            ),
            TestCase(
                status = ArenaDeltakerStatusType.INFOMOETE,
                fom = iDag,
                expected = TiltakResponsDTO.DeltakerStatusDTO.VENTELISTE,
            ),
            TestCase(
                status = ArenaDeltakerStatusType.NEITAKK,
                fom = iDag,
                expected = TiltakResponsDTO.DeltakerStatusDTO.IKKE_AKTUELL,
            ),
            TestCase(
                status = ArenaDeltakerStatusType.VENTELISTE,
                fom = iDag,
                expected = TiltakResponsDTO.DeltakerStatusDTO.VENTELISTE,
            ),
            TestCase(
                status = ArenaDeltakerStatusType.FEILREG,
                fom = iDag,
                expected = TiltakResponsDTO.DeltakerStatusDTO.FEILREGISTRERT,
            ),
        ).forEach { testCase ->
            testCase.status.toDTO(
                fom = testCase.fom,
                clock = fixedClock,
            ) shouldBe testCase.expected
        }
    }

    private data class TestCase(
        val status: ArenaDeltakerStatusType,
        val fom: LocalDate?,
        val expected: TiltakResponsDTO.DeltakerStatusDTO,
    )
}
