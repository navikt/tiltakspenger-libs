package no.nav.tiltakspenger.libs.kafka.infra

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Registrerer målinger for meldingslesere.
 * `jobber`-modulen har en tvillingklasse med samme metrikknavn fordi `kafka` ikke kan avhenge av `jobber`, som trekker inn HTTP-klienten, og `kafka` skal ha få avhengigheter.
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
     * Ved registrering settes tidspunktet til 0, slik at bare ekte vellykkede kjøringer bidrar med et tidspunkt.
     * En prosess som aldri lykkes blir synlig så snart varselets `for`-vindu er passert, i stedet for å se frisk ut helt til intervallterskelen er nådd.
     * En pod som starter på nytt kan ikke utsette et varsel om en meldingsleser som har stoppet ved å skrive et ferskt tidspunkt inn i målingen.
     * Prosessnavnet må være unikt per register på tvers av instanser, og en ny instans som registrerer samme navn kaster [IllegalStateException].
     * Alle podene leser fra topicet, og varselet aggregerer derfor per pod for meldingslesere, slik at en enkelt pod som har sluttet å polle ikke skjules av at de andre er friske.
     */
    fun registrer(prosess: String, intervall: Duration) {
        sisteVellykkedeTidspunkter.computeIfAbsent(prosess) {
            krevUniktProsessnavn(SIST_VELLYKKET_TIDSPUNKT, prosess)
            AtomicLong(0).also { holder ->
                Gauge.builder(SIST_VELLYKKET_TIDSPUNKT, holder) { tidspunkt -> tidspunkt.get().toDouble() }
                    .tag(PROSESS, prosess)
                    .tag(TYPE, MELDINGSLESER)
                    .register(meterRegistry)
            }
        }
        intervallerMillis.computeIfAbsent(prosess) {
            krevUniktProsessnavn(INTERVALL, prosess)
            AtomicLong(intervall.toMillis()).also { holder ->
                Gauge.builder(INTERVALL, holder) { varighet -> varighet.get() / 1_000.0 }
                    .tag(PROSESS, prosess)
                    .tag(TYPE, MELDINGSLESER)
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
        check(meterRegistry.find(metrikknavn).tags(PROSESS, prosess, TYPE, MELDINGSLESER).gauge() == null) {
            "Prosessnavnet '$prosess' må være unikt per register"
        }

    private companion object {
        const val SIST_VELLYKKET_TIDSPUNKT = "tpts.bakgrunnsprosess.sist_vellykket_tidspunkt_sekunder"
        const val INTERVALL = "tpts.bakgrunnsprosess.intervall_sekunder"
        const val PROSESS = "prosess"
        const val TYPE = "type"
        const val MELDINGSLESER = "meldingsleser"
    }
}
