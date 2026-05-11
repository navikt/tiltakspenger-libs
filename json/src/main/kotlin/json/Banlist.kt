package no.nav.tiltakspenger.libs.json

import arrow.core.Either
import arrow.core.Option
import arrow.core.Tuple4
import arrow.core.Tuple5
import arrow.core.Tuple6
import arrow.core.Tuple7
import arrow.core.Tuple8
import arrow.core.Tuple9
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.BeanDescription
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationConfig
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.ValueSerializerModifier
import tools.jackson.databind.type.CollectionType
import tools.jackson.databind.type.MapType
import java.io.File
import java.time.Clock
import java.util.Optional
import kotlin.reflect.KClass

/**
 * En type som [objectMapper] nekter å serialisere/deserialisere.
 *
 * [grunn] er meldingen som kastes — formuler den som «gjør X i stedet» slik at devs får hjelp på vei.
 */
internal data class BanlistEntry(val type: KClass<*>, val grunn: String)

/**
 * Typer som er bannlyst i [objectMapper] — alle gir [IllegalArgumentException] (eller en Jackson-wrapper rundt den) ved (de)serialisering.
 *
 * Bruk [objectMapperUtenBanlist] eller `enforceBanlist = false`-flagget på public-funksjonene for å overstyre.
 * Det skal være en **bevisst** handling for testing/migrering — produksjonskode bør finne en bedre type.
 */
internal val BANLIST: List<BanlistEntry> = listOf(
    BanlistEntry(
        Either::class,
        "arrow.core.Either er ikke støttet i JSON. Pakk ut verdien (fold/getOrElse) før serialisering, eller deserialiser til den underliggende typen og bygg Either-en i kode.",
    ),
    BanlistEntry(
        Option::class,
        "arrow.core.Option er ikke støttet i JSON. Per prosjektkonvensjon: bruk nullable-typer eller Either i stedet for Option.",
    ),
    BanlistEntry(
        Optional::class,
        "java.util.Optional er ikke støttet i JSON. Per prosjektkonvensjon: bruk nullable-typer eller Either i stedet for Optional.",
    ),
    BanlistEntry(
        Pair::class,
        "kotlin.Pair er ikke støttet i JSON — feltnavnene \"first\"/\"second\" gir ingen domene-mening. Bruk en navngitt data class i stedet.",
    ),
    BanlistEntry(
        Triple::class,
        "kotlin.Triple er ikke støttet i JSON — feltnavnene \"first\"/\"second\"/\"third\" gir ingen domene-mening. Bruk en navngitt data class i stedet.",
    ),
    BanlistEntry(Tuple4::class, "arrow.core.Tuple4 er ikke støttet i JSON — bruk en navngitt data class."),
    BanlistEntry(Tuple5::class, "arrow.core.Tuple5 er ikke støttet i JSON — bruk en navngitt data class."),
    BanlistEntry(Tuple6::class, "arrow.core.Tuple6 er ikke støttet i JSON — bruk en navngitt data class."),
    BanlistEntry(Tuple7::class, "arrow.core.Tuple7 er ikke støttet i JSON — bruk en navngitt data class."),
    BanlistEntry(Tuple8::class, "arrow.core.Tuple8 er ikke støttet i JSON — bruk en navngitt data class."),
    BanlistEntry(Tuple9::class, "arrow.core.Tuple9 er ikke støttet i JSON — bruk en navngitt data class."),
    BanlistEntry(
        Result::class,
        "kotlin.Result er ikke støttet i JSON — Jackson serialiserer success(value) som value (silent unwrap), og failure-tilfellet blir uforutsigbart. Pakk ut verdien eller bruk Either før serialisering.",
    ),
    BanlistEntry(
        Sequence::class,
        "kotlin.sequences.Sequence er ikke støttet i JSON — sequencer er late, og serialisering konsumerer kilden. Materialiser til List før serialisering. NB: kun top-level fanges; nested Sequence-felter går igjennom Jackson sin iterator-protokoll og fanges ikke.",
    ),
    BanlistEntry(
        Throwable::class,
        "java.lang.Throwable er ikke støttet i JSON — Jackson dumper intern tilstand, stack traces og cause-kjeder. Logg exception-en og serialiser en domene-feilmelding i stedet.",
    ),
    BanlistEntry(
        Clock::class,
        "java.time.Clock er ikke støttet i JSON — Clock er runtime-infrastruktur, ikke data. Serialiser Instant.now(clock) eller den konkrete tidsverdien i stedet.",
    ),
    BanlistEntry(
        Thread::class,
        "java.lang.Thread er ikke støttet i JSON — Thread er runtime-infrastruktur med intern tilstand som stack traces og thread groups.",
    ),
    BanlistEntry(
        File::class,
        "java.io.File er ikke støttet i JSON — serialiseres som bare path-strengen, men det er misvisende. Bruk String for filstier.",
    ),
    BanlistEntry(
        Regex::class,
        "kotlin.text.Regex er ikke støttet i JSON — Jackson serialiserer interne felter ({\"options\":[],\"pattern\":\"...\"}), ikke en nyttig representasjon. Bruk String for mønsteret.",
    ),
)

/**
 * Bakoverkompatibelt alias for Either-meldingen.
 *
 * Tester refererer til denne for å bekrefte at Either-guarden trigger.
 */
internal val EITHER_FEILMELDING: String = BANLIST.first { it.type == Either::class }.grunn
private class BannedTypeSerializer(private val grunn: String) : ValueSerializer<Any>() {
    override fun serialize(value: Any, gen: JsonGenerator, ctxt: SerializationContext): Unit =
        throw IllegalArgumentException(grunn)
}
private class BannedTypeDeserializer(private val grunn: String) : ValueDeserializer<Any>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Any =
        throw IllegalArgumentException(grunn)
}

/**
 * Fanger banlist-typer ved serializer-oppslag — også for interface-typer som [Either] der Jackson har egne defaults.
 *
 * Per-type `addSerializer(Class, serializer)` slår ikke alltid til for interface-typer; en modifier sjekker alle typer Jackson velger serializer for.
 */
private class BanlistSerializerModifier : ValueSerializerModifier() {
    private fun erstatning(rawClass: Class<*>): ValueSerializer<*>? {
        val entry = BANLIST.firstOrNull { it.type.java.isAssignableFrom(rawClass) }
        return entry?.let { BannedTypeSerializer(it.grunn) }
    }
    override fun modifySerializer(
        config: SerializationConfig,
        beanDesc: BeanDescription.Supplier,
        serializer: ValueSerializer<*>,
    ): ValueSerializer<*> = erstatning(beanDesc.beanClass) ?: serializer
    override fun modifyCollectionSerializer(
        config: SerializationConfig,
        valueType: CollectionType,
        beanDesc: BeanDescription.Supplier,
        serializer: ValueSerializer<*>,
    ): ValueSerializer<*> = erstatning(beanDesc.beanClass) ?: serializer
    override fun modifyMapSerializer(
        config: SerializationConfig,
        valueType: MapType,
        beanDesc: BeanDescription.Supplier,
        serializer: ValueSerializer<*>,
    ): ValueSerializer<*> = erstatning(beanDesc.beanClass) ?: serializer
}
internal fun banlistModule(): SimpleModule {
    val module = SimpleModule("BanlistModule")
    BANLIST.forEach { entry ->
        @Suppress("UNCHECKED_CAST")
        val klass = entry.type.java as Class<Any>
        module.addSerializer(klass, BannedTypeSerializer(entry.grunn))
        module.addDeserializer(klass, BannedTypeDeserializer(entry.grunn))
    }
    module.setSerializerModifier(BanlistSerializerModifier())
    return module
}

/**
 * Top-level fast-fail: kaster med en gang vi ser en bannlyst type, før Jackson pakker exception i en wrapper.
 *
 * Brukt av [serialize] for å gi en ren [IllegalArgumentException] med klar stack — Jackson-stien fanger fortsatt nestede tilfeller.
 */
@PublishedApi
internal fun krevIkkeBannlistet(value: Any?, enforceBanlist: Boolean) {
    if (!enforceBanlist || value == null) return
    val entry = BANLIST.firstOrNull { it.type.isInstance(value) } ?: return
    throw IllegalArgumentException(entry.grunn)
}
