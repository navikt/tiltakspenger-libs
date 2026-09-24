package no.nav.tiltakspenger.libs.common

import io.kotest.assertions.AssertionErrorBuilder.Companion.fail
import java.util.Optional

/**
 * Henter verdien eller feiler testen med en lesbar melding — i stedet for `get()`, som kaster en kryptisk `NoSuchElementException` når Optional-en er tom.
 * Typisk for header-oppslag i tester: `request.headers().firstValue("X-Correlation-ID").getOrFail() shouldBe "..."`.
 */
fun <T> Optional<T>.getOrFail(): T {
    return orElseGet { fail("Forventet en verdi, men Optional-en var tom.") }
}

/** Som [getOrFail], men med kontekstmelding i testfeilen. */
fun <T> Optional<T>.getOrFail(msg: String): T {
    return orElseGet { fail("Message: $msg, Error: Optional-en var tom.") }
}
