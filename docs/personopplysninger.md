# Personopplysninger

`Personopplysning` markerer utvalgte verdityper som personopplysninger.
Implementasjonene maskerer `toString()`, og maskeringen følger med i den genererte `toString()` til en `data class` som har typen som felt.
Råverdien, json-serialisering, API-svar og lagring er ikke beskyttet av markeringen.
Det lukkede hierarkiet er et hjelpemiddel ved personvernkonsekvensvurdering (PVK), ikke en komplett liste over personopplysninger tjenestene behandler.
Typene ligger i `common/src/main/kotlin/common/personopplysning/`, med én fil per type.

## Legg til en type

Legg den nye typen i en egen fil i pakken `no.nav.tiltakspenger.libs.common.personopplysning`.
Skriv KDoc med høyst fire linjer om verdien, mulig personvernkonsekvens, eier eller kilde og valideringskontrakt.
Skriv `begrunnelse` med høyst to setninger.
Masker råverdien i en eksplisitt `toString()`.
Legg til en test for maskering, validering og `begrunnelse`.
Konsistregelen `PersonopplysningMaskererToString` kontrollerer at en `data class` i hierarkiet deklarerer egen `toString()`.
Formål, behandlingsgrunnlag og lagringstid hører til behandlingen i konsumenten, ikke til verditypen.
