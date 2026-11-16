package com.anas.ufc.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ApiMetaDto(
    val pagination: JsonObject? = null,
)

@Serializable
data class EventsResponseDto(
    val data: List<EventDto> = emptyList(),
    val meta: ApiMetaDto = ApiMetaDto(),
)

@Serializable
data class EventResponseDto(
    val data: EventDetailDto,
    val meta: ApiMetaDto = ApiMetaDto(),
)

@Serializable
data class FighterResponseDto(
    val data: FighterProfileDto,
    val meta: ApiMetaDto = ApiMetaDto(),
)

@Serializable
data class EventDto(
    val id: Int = 0,
    val slug: String = "",
    val title: String = "",
    @SerialName("starts_at") val startsAt: String = "",
    val status: String = "",
)

@Serializable
data class EventDetailDto(
    val id: Int = 0,
    val slug: String = "",
    val title: String = "",
    @SerialName("starts_at") val startsAt: String = "",
    val status: String = "",
    val card: List<FightDto> = emptyList(),
)

@Serializable
data class FightDto(
    val id: Int = 0,
    val ordering: Int? = null,
    @SerialName("card_section") val cardSection: String? = null,
    @SerialName("weight_class") val weightClass: String? = null,
    @SerialName("is_main") val isMain: Boolean = false,
    val status: String? = null,
    @SerialName("fighter_a") val fighterA: FighterRefDto? = null,
    @SerialName("fighter_b") val fighterB: FighterRefDto? = null,
    val result: FightResultDto? = null,
    val eta: String? = null,
)

@Serializable
data class FighterRefDto(
    val id: Int = 0,
    val slug: String = "",
    val name: String = "",
)

@Serializable
data class FightResultDto(
    @SerialName("winner_fighter_id") val winnerFighterId: Int? = null,
    val method: String? = null,
    @SerialName("method_normalized") val methodNormalized: String? = null,
    val round: Int? = null,
    val time: String? = null,
)

@Serializable
data class FighterProfileDto(
    val id: Int = 0,
    val slug: String = "",
    val name: String = "",
    val records: Map<String, FighterRecordDto> = emptyMap(),
    val images: List<FighterImageDto> = emptyList(),
)

@Serializable
data class FighterRecordDto(
    val value: String = "",
)

@Serializable
data class FighterImageDto(
    val url: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val license: String? = null,
    @SerialName("license_url") val licenseUrl: String? = null,
    val artist: String? = null,
    @SerialName("source_page") val sourcePage: String? = null,
)
