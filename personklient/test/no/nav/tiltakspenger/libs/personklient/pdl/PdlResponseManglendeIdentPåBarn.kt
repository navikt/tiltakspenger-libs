package no.nav.tiltakspenger.libs.personklient.pdl

internal val pdlResponseManglendeIdentPåBarn = """
{
  "data": {
    "hentGeografiskTilknytning": {
      "gtType": "KOMMUNE",
      "gtKommune": "5444",
      "gtBydel": null,
      "gtLand": null,
      "regel": "2"
    },
    "hentPerson": {
      "adressebeskyttelse": [],
      "forelderBarnRelasjon": [
        {
          "relatertPersonsIdent": null,
          "relatertPersonsRolle": "BARN",
          "minRolleForPerson": "MOR",
          "relatertPersonUtenFolkeregisteridentifikator": {
            "navn": {
              "fornavn": "Geometrisk",
              "mellomnavn": "Sprudlende",
              "etternavn": "Jakt"
            },
            "foedselsdato": "2016-05-23",
            "statsborgerskap": "BHS",
            "kjoenn": "MANN"
          },
          "folkeregistermetadata": {
            "aarsak": null,
            "ajourholdstidspunkt": "2022-06-17T09:32:16",
            "gyldighetstidspunkt": "2022-06-17T09:32:16",
            "kilde": "Dolly",
            "opphoerstidspunkt": null,
            "sekvens": null
          },
          "metadata": {
            "endringer": [
              {
                "kilde": "Dolly",
                "registrert": "2022-06-17T09:32:16",
                "registrertAv": "Folkeregisteret",
                "systemkilde": "FREG",
                "type": "OPPRETT"
              }
            ],
            "master": "FREG"
          }
        }
      ],
      "navn": [
        {
          "fornavn": "Lykkelig",
          "mellomnavn": null,
          "etternavn": "Eksamen",
          "folkeregistermetadata": {
            "aarsak": null,
            "ajourholdstidspunkt": "2022-06-17T09:32:15",
            "gyldighetstidspunkt": "2022-06-17T09:32:15",
            "kilde": "Dolly",
            "opphoerstidspunkt": null,
            "sekvens": null
          },
          "metadata": {
            "endringer": [
              {
                "kilde": "Dolly",
                "registrert": "2022-06-17T09:32:15",
                "registrertAv": "Folkeregisteret",
                "systemkilde": "FREG",
                "type": "OPPRETT"
              }
            ],
            "master": "FREG"
          }
        }
      ],
      "foedsel": [
        {
          "foedselsdato": "1984-07-04",
          "folkeregistermetadata": {
            "aarsak": null,
            "ajourholdstidspunkt": "2022-06-17T09:32:15",
            "gyldighetstidspunkt": "2022-06-17T09:32:15",
            "kilde": "Dolly",
            "opphoerstidspunkt": null,
            "sekvens": null
          },
          "metadata": {
            "endringer": [
              {
                "kilde": "Dolly",
                "registrert": "2022-06-17T09:32:15",
                "registrertAv": "Folkeregisteret",
                "systemkilde": "FREG",
                "type": "OPPRETT"
              }
            ],
            "master": "FREG"
          }
        }
      ]
    }
  }
}

""".trimIndent()
