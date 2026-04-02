package no.nav.tiltakspenger.libs.persistering.domene.v2

/** Holder en sesjon åpen på tvers av repo-kall. Ikke trådsikker. */
interface SuspendingSessionContext {
    /**
     * @return true dersom 1) den aldri har vært åpnet 2) er lukket 3) en feil skjedde.
     */
    fun isClosed(): Boolean
}

/** Holder en transaksjon åpen på tvers av repo-kall. Ikke trådsikker. */
interface SuspendingTransactionContext : SuspendingSessionContext {
    /**
     * Kjører kun dersom transaksjonen fullfører suksessfult og comittes.
     * Kan kalles flere ganger for å legge til flere callbacks.
     * OBS: Gjør kun handlinger som det er akseptabelt at feiler. Vil logge og spise exceptions. Laget for enklere logging og metrics.
     */
    suspend fun onSuccess(action: suspend () -> Unit)

    /**
     * Kjører kun dersom transaksjonen fullfører suksessfult og comittes.
     * Kan kalles flere ganger for å legge til flere callbacks.
     * OBS: Gjør kun handlinger som det er akseptabelt at feiler. Vil logge og spise exceptions. Laget for enklere logging og metrics.
     */
    suspend fun onError(action: suspend (Throwable) -> Unit)
}

/** Starter og lukker nye sesjoner og transaksjoner */
interface SessionFactory {
    /** Lager en ny context og starter sesjonen - lukkes automatisk  */
    suspend fun <T> withSessionContext(action: (SuspendingSessionContext) -> T): T

    /** Lager en ny context dersom den mangler og starter sesjonen - lukkes automatisk */
    suspend fun <T> withSessionContext(sessionContext: SuspendingSessionContext?, action: (SuspendingSessionContext) -> T): T

    /** Lager en ny context og starter sesjonen - lukkes automatisk  */
    suspend fun <T> withTransactionContext(action: (SuspendingTransactionContext) -> T): T

    /** Lager en ny context dersom den mangler og starter sesjonen - lukkes automatisk */
    suspend fun <T> withTransactionContext(
        transactionContext: SuspendingTransactionContext?,
        action: (SuspendingTransactionContext) -> T,
    ): T

    /** Bruker en eksisterende context og starter sesjonen hvis den ikke er åpen */
    suspend fun <T> use(transactionContext: SuspendingTransactionContext, action: (SuspendingTransactionContext) -> T): T
}
