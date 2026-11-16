package com.anas.ufc.data

import java.time.Instant
import java.time.format.DateTimeParseException

private val sections = mapOf(
    "main" to CardSection.MAIN,
    "prelims" to CardSection.PRELIMS,
    "early_prelims" to CardSection.EARLY_PRELIMS,
)

fun FighterProfileDto.toCachedProfile(): CachedFighterProfile {
    val image = images.firstOrNull { it.url.isNotBlank() }
    return CachedFighterProfile(
        record = records["ufc"]?.value?.takeIf(String::isNotBlank)
            ?: records["pro_mma"]?.value?.takeIf(String::isNotBlank),
        imageUrl = image?.url,
        credits = image?.let {
            listOf(PhotoCredit(it.artist, it.license, it.licenseUrl, it.sourcePage))
        }.orEmpty(),
    )
}

fun EventDetailDto.toDomain(
    profiles: Map<String, CachedFighterProfile>,
): UfcEvent {
    val mappedCards = CardSection.entries.associateWith { section ->
        card.asSequence()
            .filter { sections[it.cardSection] == section }
            .sortedBy { it.ordering ?: Int.MAX_VALUE }
            .map { fight -> fight.toDomain(startsAt, profiles) }
            .toList()
    }
    return UfcEvent(id, slug, title, startsAt, status, mappedCards)
}

fun EventDto.toSummary(): UfcEvent = UfcEvent(
    id = id,
    slug = slug,
    name = title,
    startsAt = startsAt,
    status = status,
    card = CardSection.entries.associateWith { emptyList() },
)

private fun FightDto.toDomain(
    eventStartsAt: String,
    profiles: Map<String, CachedFighterProfile>,
): Fight {
    val winnerId = result?.winnerFighterId
    val settled = status == "completed" || status == "no_contest"
    val normalizedMethod = result?.methodNormalized
    val draw = normalizedMethod == "draw"

    fun fighter(ref: FighterRefDto?): Fighter? = ref?.let {
        val profile = profiles[it.id.toString()]
        Fighter(
            id = it.id,
            name = it.name,
            record = profile?.record,
            imageUrl = profile?.imageUrl,
            credits = profile?.credits.orEmpty(),
            isWinner = winnerId != null && it.id == winnerId,
        )
    }

    return Fight(
        id = id,
        startsAt = eta ?: eventStartsAt,
        weightClass = weightClass,
        isMainEvent = isMain || ordering == 1,
        isCoMain = ordering == 2,
        left = fighter(fighterA),
        right = fighter(fighterB),
        isFinal = settled,
        result = result?.let {
            FightResult(it.method, it.methodNormalized, it.round, it.time, draw)
        },
    )
}

fun buildResultText(
    result: FightResult,
    drawLabel: String,
    roundTemplate: String,
    methodRoundTemplate: String,
    methodOnlyTemplate: String,
    separator: String,
): String {
    val roundText = when {
        result.round != null -> roundTemplate.format(result.round, result.time.orEmpty()).trim()
        else -> result.time
    }
    val details = when {
        result.method != null && roundText != null ->
            methodRoundTemplate.format(result.method, roundText)
        result.method != null -> methodOnlyTemplate.format(result.method)
        else -> roundText
    }
    return when {
        result.isDraw && details != null -> "$drawLabel$separator$details"
        result.isDraw -> drawLabel
        else -> details.orEmpty()
    }
}

fun selectDefaultEvent(events: List<EventDto>, nowMillis: Long): EventDto? {
    val live = events.firstOrNull { it.status == "live" }
    if (live != null) return live
    val now = Instant.ofEpochMilli(nowMillis)
    fun EventDto.parsedStart(): Instant? = try {
        Instant.parse(startsAt)
    } catch (_: DateTimeParseException) {
        null
    }
    return events
        .filter { it.status == "announced" || it.status == "scheduled" }
        .filter { it.parsedStart()?.isBefore(now) == false }
        .minByOrNull { it.parsedStart() ?: Instant.MAX }
        ?: events
            .filter { it.status == "completed" }
            .maxByOrNull { it.parsedStart() ?: Instant.MIN }
            ?: events.maxByOrNull { it.parsedStart() ?: Instant.MIN }
}
