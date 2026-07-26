package fixtures.httpklienttest

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Testkode som driver `testApplication`-serveren med ktor-klienten og asserter på kontraktstypene til vår egen transport.
 * Ingen av delene er et nettverkskall, og ingen av dem skal gi brudd.
 */
class Ren
