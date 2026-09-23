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
import tools.jackson.databind.DeserializationConfig
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JavaType
import tools.jackson.databind.KeyDeserializer
import tools.jackson.databind.SerializationConfig
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.deser.ValueDeserializerModifier
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.ValueSerializerModifier
import tools.jackson.databind.type.CollectionType
import tools.jackson.databind.type.MapType
import java.io.File
import java.time.Clock
import java.util.Optional
import java.util.stream.BaseStream
import kotlin.reflect.KClass

/**
 * En type som [objectMapper] nekter å serialisere eller deserialisere.
 *
 * [grunn] er feilmeldingen som blir kastet — formuler den som «bruk X i stedet» slik at utviklere får hjelp videre.
 */
internal data class BanlistEntry(val type: KClass<*>, val grunn: String)

/**
 * Typer som er bannlyst i [objectMapper] — alle gir [IllegalArgumentException] (eller en Jackson-innpakning rundt den) ved serialisering og deserialisering.
 *
 * Bruk [objectMapperUtenBanlist] eller flagget `enforceBanlist = false` på de offentlige funksjonene for å overstyre.
 * Det skal være en **bevisst** handling, typisk i forbindelse med testing eller migrering — produksjonskode bør bruke en bedre egnet type.
 */
internal val BANLIST: List<BanlistEntry> = listOf(
    BanlistEntry(
        Either::class,
        "arrow.core.Either er ikke støttet i JSON. Pakk ut verdien (fold/getOrElse) før serialisering, eller deserialiser til den underliggende typen og bygg Either-verdien i koden.",
    ),
    BanlistEntry(
        Option::class,
        "arrow.core.Option er ikke støttet i JSON. Bruk nullable typer eller en eksplisitt DTO-representasjon. Either er også bannlyst i JSON; den kan brukes internt i domenekoden, men må pakkes ut før serialisering (og bygges opp igjen etter deserialisering).",
    ),
    BanlistEntry(
        Optional::class,
        "java.util.Optional er ikke støttet i JSON. Bruk nullable typer eller en eksplisitt DTO-representasjon. Either er også bannlyst i JSON; den kan brukes internt i domenekoden, men må pakkes ut før serialisering (og bygges opp igjen etter deserialisering).",
    ),
    BanlistEntry(
        Pair::class,
        "kotlin.Pair er ikke støttet i JSON — feltnavnene \"first\" og \"second\" har ingen betydning i domenet. Bruk en navngitt data class i stedet.",
    ),
    BanlistEntry(
        Triple::class,
        "kotlin.Triple er ikke støttet i JSON — feltnavnene \"first\", \"second\" og \"third\" har ingen betydning i domenet. Bruk en navngitt data class i stedet.",
    ),
    BanlistEntry(Tuple4::class, "arrow.core.Tuple4 er ikke støttet i JSON — bruk en navngitt data class i stedet."),
    BanlistEntry(Tuple5::class, "arrow.core.Tuple5 er ikke støttet i JSON — bruk en navngitt data class i stedet."),
    BanlistEntry(Tuple6::class, "arrow.core.Tuple6 er ikke støttet i JSON — bruk en navngitt data class i stedet."),
    BanlistEntry(Tuple7::class, "arrow.core.Tuple7 er ikke støttet i JSON — bruk en navngitt data class i stedet."),
    BanlistEntry(Tuple8::class, "arrow.core.Tuple8 er ikke støttet i JSON — bruk en navngitt data class i stedet."),
    BanlistEntry(Tuple9::class, "arrow.core.Tuple9 er ikke støttet i JSON — bruk en navngitt data class i stedet."),
    BanlistEntry(
        Result::class,
        "kotlin.Result er ikke støttet i JSON — Jackson serialiserer success(value) som value (stille utpakking), og feiltilfellet blir uforutsigbart. Pakk ut Result til en konkret DTO eller den underliggende typen før serialisering. Either er også bannlyst i JSON; den kan brukes internt i domenekoden, men må også pakkes ut før serialisering.",
    ),
    BanlistEntry(
        Sequence::class,
        "kotlin.sequences.Sequence er ikke støttet i JSON — sekvenser evalueres lat, og serialisering konsumerer kilden. Konverter til List før serialisering.",
    ),
    BanlistEntry(
        Iterator::class,
        "kotlin.collections.Iterator (= java.util.Iterator) er ikke støttet i JSON — iteratorer er konsumerbar tilstand til engangsbruk, ikke data. Konverter til List før serialisering.",
    ),
    BanlistEntry(
        // BaseStream er felles supertype for Stream/IntStream/LongStream/DoubleStream — ved å bannlyse BaseStream fanger vi alle fire i én operasjon.
        BaseStream::class,
        "java.util.stream.BaseStream (inkludert Stream/IntStream/LongStream/DoubleStream) er ikke støttet i JSON — strømmer evalueres lat og kan kun konsumeres én gang. Konverter via .toList() før serialisering.",
    ),
    BanlistEntry(
        Throwable::class,
        "java.lang.Throwable er ikke støttet i JSON — Jackson dumper intern tilstand, stacktrace og cause-kjeder. Logg unntaket og serialiser en domenefeilmelding i stedet.",
    ),
    BanlistEntry(
        Clock::class,
        "java.time.Clock er ikke støttet i JSON — Clock er infrastruktur som hører til runtime, ikke data. Serialiser Instant.now(clock) eller den konkrete tidsverdien i stedet.",
    ),
    BanlistEntry(
        Thread::class,
        "java.lang.Thread er ikke støttet i JSON — Thread er infrastruktur som hører til runtime, med intern tilstand som stacktrace og trådgrupper.",
    ),
    BanlistEntry(
        File::class,
        "java.io.File er ikke støttet i JSON — den serialiseres til kun filstien som streng, noe som er misvisende. Bruk String for filstier.",
    ),
    BanlistEntry(
        Regex::class,
        "kotlin.text.Regex er ikke støttet i JSON — Jackson serialiserer interne felter ({\"options\":[],\"pattern\":\"...\"}), noe som ikke er en nyttig representasjon. Bruk String for selve mønsteret.",
    ),
)

/**
 * Bakoverkompatibelt alias for feilmeldingen knyttet til Either.
 *
 * Tester refererer til denne konstanten for å bekrefte at Either-guarden utløses.
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

private class BannedKeyDeserializer(private val grunn: String) : KeyDeserializer() {
    override fun deserializeKey(key: String, ctxt: DeserializationContext): Any =
        throw IllegalArgumentException(grunn)
}

/**
 * Fanger opp typer på banlisten ved oppslag av serializer — også for interface-typer som [Either], der Jackson har egne standardvalg.
 *
 * `addSerializer(Class, serializer)` per type slår ikke alltid til for interface-typer; en modifier sjekker alle typer Jackson velger serializer for.
 *
 * Map-nøkler går via en separat key-serializer-sti i Jackson — uten [modifyKeySerializer] kunne en bannlyst type lekket gjennom som `toString()` når den brukes som Map-nøkkel.
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

    override fun modifyKeySerializer(
        config: SerializationConfig,
        valueType: JavaType,
        beanDesc: BeanDescription.Supplier,
        serializer: ValueSerializer<*>,
    ): ValueSerializer<*> = erstatning(valueType.rawClass) ?: serializer
}

/**
 * Speilbilde av [BanlistSerializerModifier] på deserialiseringsstien.
 *
 * `addDeserializer(Class, ...)` registrerer på *eksakt* type, så subklasser av f.eks. [Throwable] ville sluppet gjennom uten denne — Jackson velger en bean-deserializer for subklassen og banlist-treffet uteblir.
 * Modifieren sjekker alle typer Jackson bygger deserializere for og bytter ut dem som er assignable til en bannlyst type.
 */
private class BanlistDeserializerModifier : ValueDeserializerModifier() {
    private fun erstatning(rawClass: Class<*>): ValueDeserializer<*>? {
        val entry = BANLIST.firstOrNull { it.type.java.isAssignableFrom(rawClass) }
        return entry?.let { BannedTypeDeserializer(it.grunn) }
    }

    override fun modifyDeserializer(
        config: DeserializationConfig,
        beanDesc: BeanDescription.Supplier,
        deserializer: ValueDeserializer<*>,
    ): ValueDeserializer<*> = erstatning(beanDesc.beanClass) ?: deserializer

    override fun modifyCollectionDeserializer(
        config: DeserializationConfig,
        type: CollectionType,
        beanDesc: BeanDescription.Supplier,
        deserializer: ValueDeserializer<*>,
    ): ValueDeserializer<*> = erstatning(beanDesc.beanClass) ?: deserializer

    override fun modifyMapDeserializer(
        config: DeserializationConfig,
        type: MapType,
        beanDesc: BeanDescription.Supplier,
        deserializer: ValueDeserializer<*>,
    ): ValueDeserializer<*> = erstatning(beanDesc.beanClass) ?: deserializer

    override fun modifyKeyDeserializer(
        config: DeserializationConfig,
        type: JavaType,
        deserializer: KeyDeserializer,
    ): KeyDeserializer {
        val entry = BANLIST.firstOrNull { it.type.java.isAssignableFrom(type.rawClass) }
        return entry?.let { BannedKeyDeserializer(it.grunn) } ?: deserializer
    }
}

internal fun banlistModule(): SimpleModule {
    val module = SimpleModule("BanlistModule")
    BANLIST.forEach { entry ->
        @Suppress("UNCHECKED_CAST")
        val klass = entry.type.java as Class<Any>
        module.addSerializer(klass, BannedTypeSerializer(entry.grunn))
        module.addDeserializer(klass, BannedTypeDeserializer(entry.grunn))
        // Map-nøkler: separat (de)serializer-sti i Jackson.
        // Uten disse ville bannlyste typer kunne slippe gjennom som nøkler via toString()/KeyDeserializer-fallback.
        module.addKeySerializer(klass, BannedTypeSerializer(entry.grunn))
        module.addKeyDeserializer(klass, BannedKeyDeserializer(entry.grunn))
    }
    module.setSerializerModifier(BanlistSerializerModifier())
    module.setDeserializerModifier(BanlistDeserializerModifier())
    return module
}

/**
 * Top-level fast-fail: kaster så snart vi ser en bannlyst type, før Jackson rekker å pakke unntaket inn i en wrapper.
 *
 * Brukes av [serialize] for å gi en ren [IllegalArgumentException] med tydelig stacktrace — Jackson-stien fanger fortsatt opp nestede tilfeller.
 */
@PublishedApi
internal fun krevIkkeBannlistet(value: Any?, enforceBanlist: Boolean) {
    if (!enforceBanlist || value == null) return
    val entry = BANLIST.firstOrNull { it.type.isInstance(value) } ?: return
    throw IllegalArgumentException(entry.grunn)
}
