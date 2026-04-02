package no.nav.tiltakspenger.libs.personklient.pdl

internal val pdlErrorResponse = """
{
  "errors": [
    {
      "message": "Validation error of type FieldUndefined: Field 'rettIdentitetErUkjentadsa' in type 'FalskIdentitet' is undefined @ 'hentPerson/falskIdentitet/rettIdentitetErUkjentadsa'",
      "locations": [
        {
          "line": 5,
          "column": 7
        }
      ],
      "extensions": {
        "classification": "ValidationError"
      }
    }
  ]
}
""".trimIndent()
