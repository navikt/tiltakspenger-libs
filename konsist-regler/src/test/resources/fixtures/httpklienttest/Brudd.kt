package fixtures.httpklienttest

import io.ktor.client.engine.mock.MockEngine
import okhttp3.OkHttpClient
import java.net.http.HttpClient

/**
 * Testkode som lager en ekte nettverksklient, eller stubber en klient med motor-mock i stedet for `FakeHttpTransport`.
 */
class Brudd
