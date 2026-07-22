package no.nav.tiltakspenger.libs.logging

/**
 * Ferdig setning som legges på slutten av vanlige logglinjer som har en tilhørende sikkerlogg-linje.
 * Lenken går til appens logger i Google Cloud Console for riktig miljø, der sikkerloggen (team-logs) kan leses.
 * Utledes fra nais-miljøvariablene `NAIS_APP_NAME` og `GCP_TEAM_PROJECT_ID`; uten dem (lokalt/test) blir det ren tekst uten lenke.
 * Samme mønster som de app-lokale `SE_SIKKERLOGG`-konstantene i saksbehandling-api, datadeling og arena — appene kan legges over på denne.
 */
val SE_SIKKERLOGG: String by lazy {
    seSikkerloggTekst(System.getenv("NAIS_APP_NAME"), System.getenv("GCP_TEAM_PROJECT_ID"))
}

internal fun seSikkerloggTekst(appNavn: String?, gcpProsjektId: String?): String {
    if (appNavn == null || gcpProsjektId == null) {
        return "Se sikkerlogg for mer kontekst."
    }
    return "Se sikkerlogg for mer kontekst: " +
        "https://console.cloud.google.com/logs/query;query=resource.labels.container_name%3D%22$appNavn%22?project=$gcpProsjektId"
}
