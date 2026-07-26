package no.nav.tiltakspenger.libs.personklient.pdl.dto

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.json.deserialize
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * DTO-ene speiler PDL sitt GraphQL-svar, så testene deserialiserer den formen konsumentene faktisk får.
 */
internal class PdlPersonTest {
    @Test
    fun `deserialiserer en person med alle feltene`() {
        val person = deserialize<PdlPerson>(
            """
            {
              "navn": [
                {
                  "fornavn": "Ola",
                  "etternavn": "Nordmann",
                  "mellomnavn": null,
                  "metadata": { "master": "FREG", "endringer": [] },
                  "folkeregistermetadata": { "ajourholdstidspunkt": "2026-01-01T12:00:00" }
                }
              ],
              "foedselsdato": [
                {
                  "foedselsdato": "1990-01-01",
                  "metadata": { "master": "FREG", "endringer": [] }
                }
              ],
              "adressebeskyttelse": [
                {
                  "gradering": "FORTROLIG",
                  "metadata": { "master": "FREG", "endringer": [] }
                }
              ],
              "forelderBarnRelasjon": [
                {
                  "relatertPersonsIdent": "12345678910",
                  "relatertPersonsRolle": "BARN",
                  "minRolleForPerson": "MOR",
                  "metadata": { "master": "FREG", "endringer": [] }
                }
              ],
              "doedsfall": [{ "doedsdato": "2026-02-03" }]
            }
            """.trimIndent(),
        )

        person.navn.single().fornavn shouldBe "Ola"
        person.foedselsdato.single().foedselsdato shouldBe LocalDate.of(1990, 1, 1)
        person.adressebeskyttelse.single().gradering shouldBe AdressebeskyttelseGradering.FORTROLIG
        person.forelderBarnRelasjon.single().relatertPersonsRolle shouldBe ForelderBarnRelasjonRolle.BARN
        person.doedsfall.single().doedsdato shouldBe LocalDate.of(2026, 2, 3)
    }

    @Test
    fun `person uten felter gir tomme lister`() {
        deserialize<PdlPerson>("{}") shouldBe PdlPerson()
    }

    @Test
    fun `deserialiserer bolk-svar med kodene PDL bruker`() {
        val ok = deserialize<PdlPersonBolk>(
            """{ "ident": "12345678910", "person": {}, "code": "ok" }""",
        )
        ok shouldBe PdlPersonBolk(ident = "12345678910", person = PdlPerson(), code = PdlPersonBolkCode.OK)

        deserialize<PdlPersonBolk>(
            """{ "ident": "12345678910", "person": null, "code": "not_found" }""",
        ).code shouldBe PdlPersonBolkCode.NOT_FOUND

        deserialize<PdlPersonBolk>(
            """{ "ident": "12345678910", "person": null, "code": "bad_request" }""",
        ).code shouldBe PdlPersonBolkCode.BAD_REQUEST
    }

    @Test
    fun `deserialiserer geografisk tilknytning`() {
        val tilknytning = deserialize<GeografiskTilknytning>(
            """
            {
              "gtType": "BYDEL",
              "gtKommune": null,
              "gtBydel": "030102",
              "gtLand": null,
              "regel": "2"
            }
            """.trimIndent(),
        )

        tilknytning shouldBe GeografiskTilknytning(
            gtType = GtType.BYDEL,
            gtKommune = null,
            gtBydel = "030102",
            gtLand = null,
            regel = "2",
        )
    }

    @Test
    fun `deserialiserer alle gt-typene PDL kan svare med`() {
        GtType.entries.forEach { type ->
            deserialize<GeografiskTilknytning>(
                """{ "gtType": "$type", "gtKommune": null, "gtBydel": null, "gtLand": null, "regel": "2" }""",
            ).gtType shouldBe type
        }
    }
}
