package no.nav.tiltakspenger.libs.personklient.pdl

internal val pdlIkkeAutentisertResponse = """
{
  "errors": [
    {
      "message": "Ikke autentisert",
      "locations": [
        {
          "line": 2,
          "column": 5
        }
      ],
      "path": [
        "hentGeografiskTilknytning"
      ],
      "extensions": {
        "code": "unauthenticated",
        "classification": "ExecutionAborted"
      }
    },
    {
      "message": "Ikke autentisert",
      "locations": [
        {
          "line": 9,
          "column": 5
        }
      ],
      "path": [
        "hentPerson"
      ],
      "extensions": {
        "code": "unauthenticated",
        "classification": "ExecutionAborted"
      }
    }
  ],
  "data": {
    "hentGeografiskTilknytning": null,
    "hentPerson": null
  }
}

""".trimIndent()
