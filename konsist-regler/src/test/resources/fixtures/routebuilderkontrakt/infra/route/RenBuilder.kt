package fixtures.routebuilderkontrakt.infra.route

interface RenBuilder {

    // Forventninger uttrykkes gjennom typen, og assertions bor i libs-hjelperen defaultRequestWithAssertions.
    suspend fun taBehandling(
        sakId: String,
        forventet: ForventetRespons? = null,
    ): String?
}
