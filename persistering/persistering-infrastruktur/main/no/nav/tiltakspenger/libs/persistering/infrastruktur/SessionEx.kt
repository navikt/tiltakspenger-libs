package no.nav.tiltakspenger.libs.persistering.infrastruktur

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotliquery.Session
import kotliquery.action.ExecuteQueryAction
import kotliquery.action.ListResultQueryAction
import kotliquery.action.NullableResultQueryAction
import kotliquery.action.UpdateAndReturnGeneratedKeyQueryAction
import kotliquery.action.UpdateQueryAction

suspend fun Session.runSuspend(action: ExecuteQueryAction): Boolean {
    return withContext(Dispatchers.IO) {
        action.runWithSession(this@runSuspend)
    }
}

suspend fun Session.runSuspend(action: UpdateQueryAction): Int {
    return withContext(Dispatchers.IO) {
        action.runWithSession(this@runSuspend)
    }
}

suspend fun Session.runSuspend(action: UpdateAndReturnGeneratedKeyQueryAction): Long? {
    return withContext(Dispatchers.IO) {
        action.runWithSession(this@runSuspend)
    }
}

suspend fun <A> Session.runSuspend(action: ListResultQueryAction<A>): List<A> {
    return withContext(Dispatchers.IO) {
        action.runWithSession(this@runSuspend)
    }
}

suspend fun <A> Session.runSuspend(action: NullableResultQueryAction<A>): A? {
    return withContext(Dispatchers.IO) {
        action.runWithSession(this@runSuspend)
    }
}
