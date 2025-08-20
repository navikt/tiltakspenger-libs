package no.nav.tiltakspenger.libs.texas.client

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

data class TexasIntrospectionRequest(
    @JsonProperty("identity_provider") val identityProvider: String,
    val token: String,
)

data class TexasIntrospectionResponse(
    val active: Boolean,
    @JsonInclude(JsonInclude.Include.NON_NULL) val error: String?,
    val groups: List<String>?,
    val roles: List<String>?,
    @JsonAnySetter @get:JsonAnyGetter val other: Map<String, Any?> = mutableMapOf(),
)
