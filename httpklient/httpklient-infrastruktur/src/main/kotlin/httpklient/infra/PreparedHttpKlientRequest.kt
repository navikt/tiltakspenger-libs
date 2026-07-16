package no.nav.tiltakspenger.libs.httpklient.infra

import java.net.http.HttpRequest

internal data class PreparedHttpKlientRequest(
    val request: HttpRequest,
    val rawRequestString: String,
)
