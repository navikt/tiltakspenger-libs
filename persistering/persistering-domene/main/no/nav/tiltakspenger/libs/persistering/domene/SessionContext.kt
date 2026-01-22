package no.nav.tiltakspenger.libs.persistering.domene

/** Holder en sesjon åpen på tvers av repo-kall. Ikke trådsikker. */
interface SessionContext {
    /**
     * @return true dersom 1) den aldri har vært åpnet 2) er lukket 3) en feil skjedde.
     */
    fun isClosed(): Boolean
}

/** Holder en transaksjon åpen på tvers av repo-kall. Ikke trådsikker. */
interface TransactionContext : SessionContext {
    /**
     * Kjører kun dersom transaksjonen fullfører suksessfult og comittes.
     * Kan kalles flere ganger for å legge til flere callbacks.
     * OBS: Gjør kun handlinger som det er akseptabelt at feiler. Vil logge og spise exceptions. Laget for enklere logging og metrics.
     */
    fun onSuccess(action: () -> Unit)

    /**
     * Kjører kun dersom transaksjonen fullfører suksessfult og comittes.
     * Kan kalles flere ganger for å legge til flere callbacks.
     * OBS: Gjør kun handlinger som det er akseptabelt at feiler. Vil logge og spise exceptions. Laget for enklere logging og metrics.
     */
    fun onError(action: (Throwable) -> Unit)
}

/** Starter og lukker nye sesjoner og transaksjoner */
interface SessionFactory {
    /** Lager en ny context og starter sesjonen - lukkes automatisk  */
    fun <T> withSessionContext(action: (SessionContext) -> T): T

    /** Lager en ny context dersom den mangler og starter sesjonen - lukkes automatisk */
    fun <T> withSessionContext(sessionContext: SessionContext?, action: (SessionContext) -> T): T

    /** Lager en ny context og starter sesjonen - lukkes automatisk  */
    fun <T> withTransactionContext(action: (TransactionContext) -> T): T

    /** Lager en ny context dersom den mangler og starter sesjonen - lukkes automatisk */
    fun <T> withTransactionContext(
        transactionContext: TransactionContext?,
        action: (TransactionContext) -> T,
    ): T

    /** Bruker en eksisterende context og starter sesjonen hvis den ikke er åpen */
    fun <T> use(transactionContext: TransactionContext, action: (TransactionContext) -> T): T
}
