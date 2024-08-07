package no.nav.tiltakspenger.libs.personklient.pdl

internal val pdlResponseManglerFolkeregisterdata = """
{
  "data": {
    "hentGeografiskTilknytning": {
      "gtType": "KOMMUNE",
      "gtKommune": "3807",
      "gtBydel": null,
      "gtLand": null,
      "regel": "2"
    },
    "hentPerson": {
      "adressebeskyttelse": [],
      "forelderBarnRelasjon": [
        {
          "relatertPersonsIdent": "10051981176",
          "relatertPersonsRolle": "BARN",
          "minRolleForPerson": "FAR",
          "relatertPersonUtenFolkeregisteridentifikator": null,
          "folkeregistermetadata": {
            "aarsak": null,
            "ajourholdstidspunkt": "2022-08-08T11:54:52",
            "gyldighetstidspunkt": "2022-08-08T11:54:52",
            "kilde": "FREG",
            "opphoerstidspunkt": null,
            "sekvens": null
          },
          "metadata": {
            "endringer": [
              {
                "kilde": "FREG",
                "registrert": "2022-08-08T11:55:03",
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
          "fornavn": "ABDI",
          "mellomnavn": "",
          "etternavn": "MAHAMMED ARTAN",
          "folkeregistermetadata": null,
          "metadata": {
            "endringer": [
              {
                "kilde": "TPS",
                "registrert": "2019-12-04T00:00",
                "registrertAv": "P149480",
                "systemkilde": "BI00",
                "type": "OPPRETT"
              }
            ],
            "master": "PDL"
          }
        },
        {
          "fornavn": "ABDI MOHAMED",
          "mellomnavn": null,
          "etternavn": "ARTAN",
          "folkeregistermetadata": {
            "aarsak": "Innflytting",
            "ajourholdstidspunkt": "2022-05-23T14:39:09",
            "gyldighetstidspunkt": "2022-05-23T00:00",
            "kilde": "utlendingsdirektoratet",
            "opphoerstidspunkt": null,
            "sekvens": null
          },
          "metadata": {
            "endringer": [
              {
                "kilde": "utlendingsdirektoratet",
                "registrert": "2022-05-23T14:39:20",
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
          "foedselsdato": "1997-06-15",
          "folkeregistermetadata": {
            "aarsak": "Innflytting",
            "ajourholdstidspunkt": "2022-05-23T14:39:09",
            "gyldighetstidspunkt": "2022-05-23T00:00",
            "kilde": "utlendingsdirektoratet",
            "opphoerstidspunkt": null,
            "sekvens": null
          },
          "metadata": {
            "endringer": [
              {
                "kilde": "utlendingsdirektoratet",
                "registrert": "2022-05-23T14:39:20",
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
