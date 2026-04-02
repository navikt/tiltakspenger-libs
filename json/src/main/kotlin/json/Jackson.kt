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

val objectMapper: JsonMapper = JsonMapper.builder()
    .addModule(NonEmptyCollectionsModule())
    .addModule(
        KotlinModule.Builder()
            .enable(KotlinFeature.KotlinPropertyNameAsImplicitName)
            .build(),
    )
    .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DateTimeFeature.ONE_BASED_MONTHS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
    .build()

inline fun <reified K, reified V> ObjectMapper.readMap(value: String): Map<K, V> = readValue(
    value,
    typeFactory.constructMapType(
        HashMap::class.java,
        K::class.java,
        V::class.java,
    ),
)

fun serialize(value: Any): String {
    return objectMapper.writeValueAsString(value)
}

fun serializeNullable(value: Any?): String? {
    return value?.let { serialize(it) }
}

inline fun <reified T> List<T>.serialize(): String {
    val listType = objectMapper.typeFactory.constructCollectionLikeType(List::class.java, T::class.java)
    return objectMapper.writerFor(listType).writeValueAsString(this)
}

inline fun <reified T> String.deserializeList(): List<T> {
    val listType = objectMapper.typeFactory.constructCollectionLikeType(List::class.java, T::class.java)
    return objectMapper.readerFor(listType).readValue(this)
}

inline fun <reified T> deserialize(value: String): T {
    return objectMapper.readValue(value)
}

inline fun <reified T> deserializeNullable(value: String?): T? {
    return value?.let { deserialize(it) }
}

inline fun <reified K, reified V> deserializeMap(value: String): Map<K, V> {
    return objectMapper.readMap(value)
}

inline fun <reified K, reified V> deserializeMapNullable(value: String?): Map<K, V>? {
    return value?.let { deserializeMap(it) }
}

@JvmName("deserializeListValue")
inline fun <reified T> deserializeList(value: String): List<T> {
    return value.deserializeList()
}

inline fun <reified T> deserializeListNullable(value: String?): List<T>? {
    return value?.let { deserializeList(it) }
}

fun lesTre(value: String): JsonNode {
    return objectMapper.readTree(value)
}
