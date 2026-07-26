package fixtures.httpklienter

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import no.nav.tiltakspenger.libs.httpklient.HttpKlientResponse
import java.io.InputStream
import java.net.URI

class Ren {
    /* En classpath-ressurs leses med openStream, og det er ikke et nettverkskall — heller ikke når getResource står på en annen linje. */
    fun lesRessurs(): InputStream {
        val ressurs = javaClass.getResource("/fixtures/data.json")!!
        return ressurs.openStream()
    }
}
