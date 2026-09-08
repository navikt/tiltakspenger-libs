package no.nav.tiltakspenger.libs.jobber

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration

/**
 * Registrerer målinger for skedulerte jobber.
 * `kafka`-modulen har en tvillingklasse med samme metrikknavn fordi `kafka` ikke kan avhenge av `jobber`, som trekker inn HTTP-klienten, og `kafka` skal ha få avhengigheter.
 */
class Bakgrunnsprosessmålinger(
    private val meterRegistry: MeterRegistry,
    private val clock: Clock,
) {
    // Micrometer holder bare svake referanser til objektene en gauge måler, så begge mapper må beholdes; intervallerMillis brukes ellers ikke og er den sterke referansen som hindrer NaN etter GC.
    private val sisteVellykkedeTidspunkter = ConcurrentHashMap<String, AtomicLong>()
    private val intervallerMillis = ConcurrentHashMap<String, AtomicLong>()

    /**
     * Registrerer prosessen med intervallet målt i sekunder og tidspunktet målt som epoch-sekunder.
     * Ved registrering settes tidspunktet til 0.
     * Da rapporterer ikke-lederne 0, og lederens ekte verdi vinner i varselets `max`-aggregering.
     * En prosess som aldri lykkes blir synlig så snart varselets `for`-vindu er passert, i stedet for å se frisk ut helt til intervallterskelen er nådd.
     * En ikke-leder som starter på nytt kan ikke utsette et varsel om en jobb som har stoppet ved å skrive et ferskt tidspunkt inn i aggregeringen.
     * Prosessnavnet må være unikt per register på tvers av instanser, og en ny instans som registrerer samme navn kaster [IllegalStateException].
     */
    fun registrer(prosess: String, intervall: Duration) {
        sisteVellykkedeTidspunkter.computeIfAbsent(prosess) {
            krevUniktProsessnavn(SIST_VELLYKKET_TIDSPUNKT, prosess)
            AtomicLong(0).also { holder ->
                Gauge.builder(SIST_VELLYKKET_TIDSPUNKT, holder) { tidspunkt -> tidspunkt.get().toDouble() }
                    .tag(PROSESS, prosess)
                    .tag(TYPE, JOBB)
                    .register(meterRegistry)
            }
        }
        intervallerMillis.computeIfAbsent(prosess) {
            krevUniktProsessnavn(INTERVALL, prosess)
            AtomicLong(intervall.inWholeMilliseconds).also { holder ->
                Gauge.builder(INTERVALL, holder) { varighet -> varighet.get() / 1_000.0 }
                    .tag(PROSESS, prosess)
                    .tag(TYPE, JOBB)
                    .register(meterRegistry)
            }
        }
    }

    /**
     * Setter tidspunktet for siste vellykkede kjøring til nå som epoch-sekunder.
     */
    fun registrerVellykketKjøring(prosess: String) {
        requireNotNull(sisteVellykkedeTidspunkter[prosess]) { "Prosessen '$prosess' er ikke registrert" }
            .set(Instant.now(clock).epochSecond)
    }

    private fun krevUniktProsessnavn(metrikknavn: String, prosess: String) =
        check(meterRegistry.find(metrikknavn).tags(PROSESS, prosess, TYPE, JOBB).gauge() == null) {
            "Prosessnavnet '$prosess' må være unikt per register"
        }

    private companion object {
        const val SIST_VELLYKKET_TIDSPUNKT = "tpts.bakgrunnsprosess.sist_vellykket_tidspunkt_sekunder"
        const val INTERVALL = "tpts.bakgrunnsprosess.intervall_sekunder"
        const val PROSESS = "prosess"
        const val TYPE = "type"
        const val JOBB = "jobb"
    }
}
