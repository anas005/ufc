package com.anas.ufc.data

import androidx.annotation.StringRes
import com.anas.ufc.R
import kotlinx.serialization.Serializable

@Serializable
enum class CardSection(@StringRes val labelRes: Int) {
    MAIN(R.string.section_main),
    PRELIMS(R.string.section_prelims),
    EARLY_PRELIMS(R.string.section_early_prelims),
}

@Serializable
data class PhotoCredit(
    val artist: String?,
    val license: String?,
    val licenseUrl: String?,
    val sourcePage: String?,
)

@Serializable
data class Fighter(
    val id: Int,
    val name: String,
    val record: String?,
    val imageUrl: String?,
    val credits: List<PhotoCredit> = emptyList(),
    val isWinner: Boolean = false,
)

@Serializable
data class FightResult(
    val method: String?,
    val methodNormalized: String?,
    val round: Int?,
    val time: String?,
    val isDraw: Boolean,
)

@Serializable
data class Fight(
    val id: Int,
    val startsAt: String?,
    val weightClass: String?,
    val isMainEvent: Boolean,
    val isCoMain: Boolean,
    val left: Fighter?,
    val right: Fighter?,
    val isFinal: Boolean,
    val result: FightResult?,
)

@Serializable
data class UfcEvent(
    val id: Int,
    val slug: String,
    val name: String,
    val startsAt: String,
    val status: String,
    val card: Map<CardSection, List<Fight>>,
)

@Serializable
data class CachedFighterProfile(
    val record: String?,
    val imageUrl: String?,
    val credits: List<PhotoCredit>,
)

@Serializable
data class CacheSnapshot(
    val events: List<UfcEvent> = emptyList(),
    val fighterProfiles: Map<String, CachedFighterProfile> = emptyMap(),
    val selectedEventSlug: String? = null,
    val lastSuccessfulFetchMillis: Long? = null,
    val nextApiRequestMillis: Long = 0,
    val requestMonth: String = "",
    val requestCount: Int = 0,
    val refreshStatus: RefreshStatus = RefreshStatus.IDLE,
)

@Serializable
enum class RefreshStatus {
    IDLE,
    REFRESHING,
    OFFLINE,
    ERROR,
    API_KEY_REQUIRED,
    ALREADY_UP_TO_DATE,
    MONTHLY_LIMIT_REACHED,
}

data class RefreshOutcome(
    val skippedForCooldown: Boolean = false,
    val monthlyLimitReached: Boolean = false,
)
