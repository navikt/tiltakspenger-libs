package no.nav.tiltakspenger.libs.lokal

import arrow.core.Either
import arrow.core.left
import arrow.core.right

/**
 * Hvordan den lokale postgres-databasen skaffes til veie når en app startes fra sin `LokalMain`.
 * Standard er [DockerCompose].
 */
sealed interface LokalDatabaseModus {

    /**
     * Starter tjenesten fra monorepoets `docker-compose.yml` hvis den ikke allerede kjører.
     * Porten, brukeren og det navngitte volumet fra compose-fila beholdes, så data overlever både omstart av appen og av containeren.
     */
    data object DockerCompose : LokalDatabaseModus

    /**
     * Starter en egen postgres-container via Testcontainers, uten å røre compose-oppsettet.
     * Rømningsluke for de som kjører appen utenfor monorepoet, eller som ikke vil ha compose-containerne liggende.
     * Porten er tilfeldig (appen får den via jdbc-url-en), og databasen er tom ved hver oppstart med mindre containeren gjenbrukes.
     */
    data object Testcontainers : LokalDatabaseModus

    companion object {
        /** Miljøvariabelen (eller system-propertyen) som velger modus. */
        const val MILJØVARIABEL: String = "LOKAL_DB_MODUS"

        private val gyldigeVerdier: Map<String, LokalDatabaseModus> = mapOf(
            "compose" to DockerCompose,
            "docker-compose" to DockerCompose,
            "docker" to DockerCompose,
            "testcontainers" to Testcontainers,
            "tc" to Testcontainers,
        )

        /**
         * Leser [MILJØVARIABEL] med [lesVerdi].
         * Er den ikke satt, brukes [DockerCompose].
         * En verdi vi ikke kjenner igjen er en feil og ikke noe vi faller stille tilbake fra — da hadde en skrivefeil gitt deg en annen database enn du trodde.
         */
        fun fraMiljø(lesVerdi: (String) -> String?): Either<LokalPostgresFeil.UgyldigModus, LokalDatabaseModus> {
            val verdi = lesVerdi(MILJØVARIABEL)?.trim()
            return when {
                verdi.isNullOrBlank() -> DockerCompose.right()

                else -> gyldigeVerdier[verdi.lowercase()]?.right()
                    ?: LokalPostgresFeil.UgyldigModus(verdi, gyldigeVerdier.keys.toList()).left()
            }
        }
    }
}
