package no.nav.tiltakspenger.libs.personklient.pdl

import no.nav.tiltakspenger.libs.common.Fnr

internal fun hentPersonQuery(fnr: Fnr): GraphqlQuery {
    return GraphqlQuery(
        query = query,
        variables = mapOf(
            "ident" to fnr.verdi,
        ),
    )
}

private val query = """
query(${'$'}ident: ID!){
    hentGeografiskTilknytning(ident: ${'$'}ident) {
        gtType,
        gtKommune,
        gtBydel,
        gtLand,
        regel
    }
    hentPerson(ident: ${'$'}ident) {
        adressebeskyttelse(historikk: false) {
            gradering
            folkeregistermetadata {
                ...folkeregistermetadataDetails
            }
            metadata {
                ...metadataDetails
            }
        }
        forelderBarnRelasjon {
            relatertPersonsIdent
            relatertPersonsRolle
            minRolleForPerson
            relatertPersonUtenFolkeregisteridentifikator {
                navn {
                    fornavn
                    mellomnavn
                    etternavn
                }
                foedselsdato
                statsborgerskap
                kjoenn
            }
            folkeregistermetadata {
                ...folkeregistermetadataDetails
            }
            metadata {
                ...metadataDetails
            }
        }
        navn(historikk: false) {
            fornavn
            mellomnavn
            etternavn
            folkeregistermetadata {
                ...folkeregistermetadataDetails
            }
            metadata {
                ...metadataDetails
            }
        }
        foedsel {
            foedselsdato
            folkeregistermetadata {
                ...folkeregistermetadataDetails
            }
            metadata {
                ...metadataDetails
            }
        }
    }
}

fragment folkeregistermetadataDetails on Folkeregistermetadata {
    aarsak
    ajourholdstidspunkt
    gyldighetstidspunkt
    kilde
    opphoerstidspunkt
    sekvens
}

fragment metadataDetails on Metadata {
    endringer {
        kilde
        registrert
        registrertAv
        systemkilde
        type
    }
    master
}
""".trimIndent()
