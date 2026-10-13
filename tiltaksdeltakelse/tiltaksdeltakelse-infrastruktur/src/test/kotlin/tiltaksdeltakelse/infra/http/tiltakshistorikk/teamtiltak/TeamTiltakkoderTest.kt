package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.teamtiltak

import arrow.core.left
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.TeamTiltakstatus
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.TiltakstypeSomGirRett
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.felles.UgyldigKontraktsverdi
import org.junit.jupiter.api.Test

class TeamTiltakkoderTest {

    @Test
    fun `team tiltak-tabellen er pinnet`() {
        teamTiltakkoderSomGirRett shouldBe mapOf(
            "ARBEIDSTRENING" to TiltakstypeSomGirRett.ARBEIDSTRENING,
        )
        kjenteTeamTiltakkoderUtenRett shouldBe setOf(
            "INKLUDERINGSTILSKUDD",
            "MENTOR",
            "SOMMERJOBB",
            "VTAO",
        )
    }

    @Test
    fun `tabellene er disjunkte og har pinnet størrelse`() {
        teamTiltakkoderSomGirRett.size shouldBe 1
        teamTiltakkoderSomFratarRett.size shouldBe 3
        kjenteTeamTiltakkoderUtenRett.size shouldBe 4
        (teamTiltakkoderSomGirRett.keys intersect kjenteTeamTiltakkoderUtenRett).shouldBeEmpty()
        (teamTiltakkoderSomGirRett.keys intersect teamTiltakkoderSomFratarRett).shouldBeEmpty()
        (teamTiltakkoderSomFratarRett intersect kjenteTeamTiltakkoderUtenRett).shouldBeEmpty()
    }

    @Test
    fun `kodene som fratar rett er pinnet — lønnstilskuddene, alle typer`() {
        teamTiltakkoderSomFratarRett shouldBe setOf(
            "FIREARIG_LONNSTILSKUDD",
            "MIDLERTIDIG_LONNSTILSKUDD",
            "VARIG_LONNSTILSKUDD",
        )
    }

    @Test
    fun `alle kjente team tiltak-statuser blir Kjent`() {
        TeamTiltakstatus.Type.entries.forEach { type ->
            teamTiltakstatus(type.name).getOrFail() shouldBe TeamTiltakstatus.Kjent(type)
        }
    }

    @Test
    fun `ukjent team tiltak-status bæres ordrett`() {
        teamTiltakstatus("HELT_NY_STATUS").getOrFail() shouldBe TeamTiltakstatus.Ukjent("HELT_NY_STATUS")
    }

    @Test
    fun `blank team tiltak-status feiler`() {
        teamTiltakstatus(" ") shouldBe UgyldigKontraktsverdi("Blank avtalestatus fra Team Tiltak kan ikke bæres som ukjent kildeverdi").left()
    }
}
