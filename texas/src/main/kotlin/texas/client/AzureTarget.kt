package no.nav.tiltakspenger.libs.texas.client

import no.nav.tiltakspenger.libs.texas.IdentityProvider

/**
 * Normaliserer target-verdien Azure AD skal få for client credentials og on-behalf-of.
 *
 * Entra ID krever at target er en application ID URI med `/.default` som suffiks, og svarer `invalid_scope` (AADSTS1002012) på alt annet.
 * Appene våre konfigurerer scope i to stiler: nais-kortformen `cluster:namespace:app` og den ferdig utskrevne `api://cluster.namespace.app/.default`.
 * Noen få peker på eksterne API-er utenfor nais, som `https://graph.microsoft.com/.default`.
 *
 * Formen utledes derfor her, av verdien selv, i stedet for å være et flagg på hvert kallsted.
 * Et slikt flagg må stemme overens med en verdi som står i en helt annen fil (nais-manifestet eller miljøkonfigurasjonen), ingenting kobler de to, og feil kombinasjon har tatt ned produksjon to ganger.
 *
 * Verdier som allerede inneholder et skjema (`api://`, `https://`) er per definisjon ferdig utskrevet og sendes uendret.
 * Alt annet tolkes som nais-kortformen: kolon byttes til punktum, og resultatet pakkes inn som application ID URI.
 */
internal fun normaliserAzureTarget(audienceTarget: String): String =
    if (audienceTarget.contains("://")) audienceTarget else "api://${audienceTarget.replace(':', '.')}/.default"

/**
 * Target normaliseres kun for Azure AD.
 * TokenX bruker `cluster:namespace:app` direkte som target, og en omskriving der ville gjort et fungerende kall ugyldig.
 * Maskinporten og ID-porten har egne target-formater (scopes) som heller ikke skal røres.
 */
internal fun IdentityProvider.normaliserTarget(audienceTarget: String): String =
    if (this == IdentityProvider.AZUREAD) normaliserAzureTarget(audienceTarget) else audienceTarget
