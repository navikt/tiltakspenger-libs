package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.felles

import arrow.core.Either
import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakstype
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.TiltakstypeSomGirRett
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.arena.arenaTiltakstype
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.arena.arenakoderSomGirRett
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.arena.kjenteArenakoderUtenRett
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.komet.kjenteKometkoderUtenRett
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.komet.kometTiltakstype
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.komet.kometkoderSomGirRett
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.teamtiltak.kjenteTeamTiltakkoderUtenRett
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.teamtiltak.teamTiltakTiltakstype
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.teamtiltak.teamTiltakkoderSomGirRett
import org.junit.jupiter.api.Test

class TiltakstypeklassifiseringTest {

    @Test
    fun `kjente koder klassifiseres etter rett, ukjente bæres ordrett og blank feiler`() {
        data class Kilde(
            val navn: String,
            val klassifiser: (String) -> Either<UgyldigKontraktsverdi, Tiltakstype>,
            val somGirRett: Map<String, TiltakstypeSomGirRett>,
            val utenRett: Set<String>,
        )

        listOf(
            Kilde("Arena", ::arenaTiltakstype, arenakoderSomGirRett, kjenteArenakoderUtenRett),
            Kilde("Komet", ::kometTiltakstype, kometkoderSomGirRett, kjenteKometkoderUtenRett),
            Kilde("Team Tiltak", ::teamTiltakTiltakstype, teamTiltakkoderSomGirRett, kjenteTeamTiltakkoderUtenRett),
        ).forEach { kilde ->
            kilde.somGirRett.forEach { (kode, forventet) ->
                kilde.klassifiser(kode).getOrFail() shouldBe Tiltakstype.SomGirRett(kode, forventet)
            }
            kilde.utenRett.forEach { kode ->
                kilde.klassifiser(kode).getOrFail() shouldBe Tiltakstype.SomIkkeGirRett(kode)
            }
            kilde.klassifiser("HELT_NY_KODE").getOrFail() shouldBe Tiltakstype.Ukjent("HELT_NY_KODE")
            kilde.klassifiser(" ") shouldBe UgyldigKontraktsverdi("Blank tiltakskode fra ${kilde.navn} kan ikke bæres som ukjent kildeverdi").left()
        }
    }
}
