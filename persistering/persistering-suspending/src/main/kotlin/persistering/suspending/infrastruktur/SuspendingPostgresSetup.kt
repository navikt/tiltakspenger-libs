package no.nav.tiltakspenger.libs.persistering.suspending.infrastruktur

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory

class SuspendingPostgresSetup(
    private val jdbcUrl: String,
) {
    val connectionFactory: ConnectionFactory? = ConnectionFactories.get(jdbcUrl)
}
