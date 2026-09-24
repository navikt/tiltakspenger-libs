package fixtures.routebuilderkontrakt.infra.route

import io.kotest.matchers.shouldBe

interface BruddBuilder {

    suspend fun taBehandlingReturnerRespons(sakId: String): String

    suspend fun taBehandling(
        sakId: String,
        forventetStatus: Int? = null,
        forventetBody: String? = null,
    ): String? {
        forventetStatus shouldBe 200
        return forventetBody
    }
}
