package fixtures.httpklienter

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.net.HttpURLConnection
import java.net.http.HttpRequest
import org.apache.hc.client5.http.classic.HttpClient as ApacheHttpClient

class Brudd {
    /* Fullkvalifisert bruk uten import: usynlig for en regel som kun ser på importer. */
    fun fullkvalifisert() = java.net.http.HttpClient.newHttpClient()

    /* Nettverkskall uten at noen klient-import avslører det. */
    fun utenKlient() = java.net.URI("https://example.com").toURL().openConnection()
}
