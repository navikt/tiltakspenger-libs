package no.nav.tiltakspenger.libs.json

import arrow.integrations.jackson.module.NonEmptyCollectionsModule
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.cfg.EnumFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.KotlinModule
import tools.jackson.module.kotlin.readValue
import kotlin.reflect.jvm.javaType
import kotlin.reflect.typeOf

/**
 * Utfører felles konfig på en ny [JsonMapper.Builder]. Tar ikke imot en eksisterende builder —
 * det ville innbudt til mutasjon på tvers av kallsstedene (en delt builder kunne fått
 * `banlistModule()` lagt på seg fra ett kall, og lekket inn i alle andre mappere).
 */
private fun nyKonfigurertBuilder(): JsonMapper.Builder = JsonMapper.builder()
    .addModule(NonEmptyCollectionsModule())
    .addModule(
        KotlinModule.Builder()
            .enable(KotlinFeature.KotlinPropertyNameAsImplicitName)
            // Konverterer kotlin.time.Duration <-> java.time.Duration så Jackson kan deserialisere ISO-8601-strings ("PT1M30S") direkte til kotlin.time.Duration både som top-level og som felt i DTO-er.
            .enable(KotlinFeature.UseJavaDurationConversion)
            .build(),
    )
    .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DateTimeFeature.ONE_BASED_MONTHS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)

/**
 * Standard `ObjectMapper` for prosjektet — håndhever [BANLIST] for både serialisering og
 * deserialisering, på *alle* nivåer (top-level, felt i DTO, element i List/Set, verdi i Map,
 * nøkkel i Map, og vilkårlig dypt nestet).
 *
 * Bruk denne overalt med mindre du har en eksplisitt grunn til å nå [objectMapperUtenBanlist].
 */
val objectMapper: JsonMapper = nyKonfigurertBuilder().addModule(banlistModule()).build()

/**
 * Mapper uten [BANLIST] — en bevisst rømningsluke.
 *
 * Bruk kun når du *bevisst* trenger å serialisere/deserialisere en bannlyst type — typisk i
 * tester eller migreringer. Produksjonskode bør finne en bedre type i stedet.
 */
val objectMapperUtenBanlist: JsonMapper = nyKonfigurertBuilder().build()

@PublishedApi
internal fun mapperFor(enforceBanlist: Boolean): JsonMapper =
    if (enforceBanlist) objectMapper else objectMapperUtenBanlist

inline fun <reified K, reified V> ObjectMapper.readMap(value: String): Map<K, V> = readValue(
    value,
    typeFactory.constructType(typeOf<Map<K, V>>().javaType),
)

/**
 * Serialiserer [value] til kompakt JSON.
 *
 * Typer i [BANLIST] kaster på rot- og under-nivåer. Sett [enforceBanlist] = false kun
 * bevisst (testing/migrering) — det skrur av sjekken og åpner [objectMapperUtenBanlist].
 */
fun serialize(value: Any, enforceBanlist: Boolean = true): String {
    krevIkkeBannlistet(value, enforceBanlist)
    return mapperFor(enforceBanlist).writeValueAsString(value)
}

/** Som [serialize], men returnerer null for null-input. Banlist-regler: se [BANLIST]. */
fun serializeNullable(value: Any?, enforceBanlist: Boolean = true): String? =
    value?.let { serialize(it, enforceBanlist) }

/** Serialiserer en `List<T>` med bevart element-type. Banlist-regler: se [BANLIST]. */
inline fun <reified T> List<T>.serialize(enforceBanlist: Boolean = true): String {
    val mapper = mapperFor(enforceBanlist)
    val listType = mapper.typeFactory.constructType(typeOf<List<T>>().javaType)
    return mapper.writerFor(listType).writeValueAsString(this)
}

/**
 * Deserialiserer denne JSON-strengen til `List<T>` med bevart element-type.
 * Banlist-regler: se [BANLIST].
 */
inline fun <reified T> String.deserializeList(enforceBanlist: Boolean = true): List<T> {
    val mapper = mapperFor(enforceBanlist)
    val listType = mapper.typeFactory.constructType(typeOf<List<T>>().javaType)
    return mapper.readerFor(listType).readValue(this)
}

/** Deserialiserer JSON til [T]. Banlist-regler: se [BANLIST]. */
inline fun <reified T> deserialize(value: String, enforceBanlist: Boolean = true): T =
    mapperFor(enforceBanlist).readValue(value)

/** Som [deserialize], men returnerer null for null-input. Banlist-regler: se [BANLIST]. */
inline fun <reified T> deserializeNullable(value: String?, enforceBanlist: Boolean = true): T? =
    value?.let { deserialize<T>(it, enforceBanlist) }

/**
 * Deserialiserer JSON til `Map<K, V>` med bevart nøkkel- og verdi-type.
 * Banlist-regler: se [BANLIST].
 */
inline fun <reified K, reified V> deserializeMap(value: String, enforceBanlist: Boolean = true): Map<K, V> =
    mapperFor(enforceBanlist).readMap(value)

/** Som [deserializeMap], men returnerer null for null-input. Banlist-regler: se [BANLIST]. */
inline fun <reified K, reified V> deserializeMapNullable(value: String?, enforceBanlist: Boolean = true): Map<K, V>? =
    value?.let { deserializeMap<K, V>(it, enforceBanlist) }

/** Deserialiserer JSON til `List<T>`. Banlist-regler: se [BANLIST]. */
@JvmName("deserializeListValue")
inline fun <reified T> deserializeList(value: String, enforceBanlist: Boolean = true): List<T> =
    value.deserializeList(enforceBanlist)

/** Som [deserializeList], men returnerer null for null-input. Banlist-regler: se [BANLIST]. */
inline fun <reified T> deserializeListNullable(value: String?, enforceBanlist: Boolean = true): List<T>? =
    value?.let { deserializeList<T>(it, enforceBanlist) }

/**
 * Parser JSON til [JsonNode] uten å materialisere typer.
 *
 * Trigger ikke banlisten siden vi ikke binder til konkrete typer her — Jackson vet ikke at
 * f.eks. `{"right":42}` "betyr" en bannlyst type.
 */
fun lesTre(value: String): JsonNode = objectMapper.readTree(value)
