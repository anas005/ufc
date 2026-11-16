package com.anas.ufc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import coil.compose.AsyncImage
import com.anas.ufc.R
import com.anas.ufc.data.CacheSnapshot
import com.anas.ufc.data.CardSection
import com.anas.ufc.data.EventDateFormatter
import com.anas.ufc.data.Fight
import com.anas.ufc.data.Fighter
import com.anas.ufc.data.PhotoCredit
import com.anas.ufc.data.RefreshStatus
import com.anas.ufc.data.toDisplayText
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen(state: UfcUiState, viewModel: UfcViewModel) {
    val colors = LocalUfcColors.current
    val events = state.snapshot.events
    val selectedIndex = events.indexOfFirst { it.slug == state.selectedEventSlug }.coerceAtLeast(0)
    val selectedEvent = events.getOrNull(selectedIndex)
    val fights = selectedEvent?.card?.get(state.selectedSection).orEmpty()

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        Header(state, viewModel)
        if (state.showCredits) {
            AboutCredits(state.snapshot)
        } else {
            if (events.isNotEmpty()) EventTabs(events.map { it.slug to it.name }, selectedIndex, viewModel::selectEvent)
            SectionTabs(state.selectedSection, viewModel::selectSection)
            PullToRefreshBox(
                isRefreshing = state.snapshot.refreshStatus == RefreshStatus.REFRESHING,
                onRefresh = viewModel::refresh,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.snapshot.refreshStatus == RefreshStatus.REFRESHING && events.isEmpty() ->
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.refreshing))
                        }
                    events.isEmpty() -> EmptyEvents(state.snapshot.refreshStatus, viewModel::refresh)
                    fights.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.empty_card), color = colors.subtle)
                    }
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(fights, key = { it.id }) { fight ->
                            FightRow(fight)
                            HorizontalDivider(color = colors.divider)
                        }
                    }
                }
            }
            Footer(state.snapshot)
        }
    }
}

@Composable
private fun Header(state: UfcUiState, viewModel: UfcViewModel) {
    val colors = LocalUfcColors.current
    Column(Modifier.background(colors.red).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(
                horizontal = dimensionResource(R.dimen.space_large),
                vertical = dimensionResource(R.dimen.space_medium),
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.showCredits) {
                IconButton(onClick = viewModel::closeCredits) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.navigate_back), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Text(
                stringResource(R.string.header_brand),
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                fontSize = MaterialTheme.typography.titleLarge.fontSize,
                modifier = Modifier
                    .background(colors.darkRed, RoundedCornerShape(dimensionResource(R.dimen.space_xsmall)))
                    .padding(horizontal = dimensionResource(R.dimen.space_small), vertical = dimensionResource(R.dimen.space_xsmall)),
            )
            Spacer(Modifier.width(dimensionResource(R.dimen.space_large)))
            Text(
                if (state.showCredits) stringResource(R.string.about_title) else stringResource(R.string.header_subtitle),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (!state.showCredits) {
                IconButton(onClick = viewModel::openCredits) {
                    Icon(Icons.Default.Info, contentDescription = stringResource(R.string.about), tint = MaterialTheme.colorScheme.onPrimary)
                }
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        val status = state.snapshot.refreshStatus
        if (status == RefreshStatus.OFFLINE && state.snapshot.events.isNotEmpty()) {
            Text(
                stringResource(R.string.showing_saved_data),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(
                    start = dimensionResource(R.dimen.space_large),
                    bottom = dimensionResource(R.dimen.space_small),
                ),
            )
        }
    }
}

@Composable
private fun EventTabs(
    events: List<Pair<String, String>>,
    selectedIndex: Int,
    onSelect: (String) -> Unit,
) {
    val colors = LocalUfcColors.current
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = colors.darkRed,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        edgePadding = 0.dp,
    ) {
        events.forEachIndexed { index, event ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(event.first) },
                text = { Text(event.second.uppercase(), maxLines = 1, style = MaterialTheme.typography.labelLarge) },
                selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                unselectedContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun SectionTabs(selected: CardSection, onSelect: (CardSection) -> Unit) {
    val colors = LocalUfcColors.current
    TabRow(
        selectedTabIndex = selected.ordinal,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        CardSection.entries.forEach { section ->
            Tab(
                selected = section == selected,
                onClick = { onSelect(section) },
                text = { Text(stringResource(section.labelRes), maxLines = 1) },
                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                unselectedContentColor = colors.subtle,
            )
        }
    }
}

@Composable
private fun EmptyEvents(status: RefreshStatus, onRetry: () -> Unit) {
    val message = when (status) {
        RefreshStatus.API_KEY_REQUIRED -> stringResource(R.string.error_api_key)
        RefreshStatus.ERROR, RefreshStatus.OFFLINE -> stringResource(R.string.error_generic)
        RefreshStatus.MONTHLY_LIMIT_REACHED -> stringResource(R.string.monthly_limit_reached)
        else -> stringResource(R.string.empty_events)
    }
    Column(
        Modifier.fillMaxSize().padding(dimensionResource(R.dimen.space_large)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(dimensionResource(R.dimen.space_medium)))
        androidx.compose.material3.Button(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun FightRow(fight: Fight) {
    val colors = LocalUfcColors.current
    val context = LocalContext.current
    val timeLabel = EventDateFormatter.format(
        fight.startsAt,
        Instant.now(),
        ZoneId.systemDefault(),
        Locale.getDefault(),
        stringResource(R.string.date_today),
        stringResource(R.string.date_tomorrow),
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = dimensionResource(R.dimen.space_large), vertical = dimensionResource(R.dimen.space_medium))) {
        if (timeLabel != null) {
            Text(timeLabel, color = colors.subtle, style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(fight.weightClass.orEmpty(), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
            val slot = when {
                fight.isMainEvent -> stringResource(R.string.fight_main_event)
                fight.isCoMain -> stringResource(R.string.fight_co_main)
                else -> null
            }
            if (slot != null) {
                Text(stringResource(R.string.text_separator), color = colors.subtle)
                Text(slot, color = colors.subtle, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.weight(1f))
            if (fight.isFinal) {
                Text(stringResource(R.string.fight_final), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(dimensionResource(R.dimen.space_medium)))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FighterSide(fight.left, dimmed = fight.isFinal && fight.right?.isWinner == true, alignEnd = false, Modifier.weight(1f))
            FighterSide(fight.right, dimmed = fight.isFinal && fight.left?.isWinner == true, alignEnd = true, Modifier.weight(1f))
        }
        val result = fight.result?.takeIf { fight.isFinal }?.let {
            it.toDisplayText(context)
        }
        if (!result.isNullOrBlank()) {
            Spacer(Modifier.height(dimensionResource(R.dimen.space_small)))
            Text(
                result,
                color = if (fight.result?.isDraw == true) colors.subtle else colors.win,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun FighterSide(fighter: Fighter?, dimmed: Boolean, alignEnd: Boolean, modifier: Modifier) {
    val colors = LocalUfcColors.current
    val textColor = if (dimmed) colors.subtle else MaterialTheme.colorScheme.onSurface
    Row(
        modifier,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!alignEnd) {
            FighterAvatar(fighter)
            Spacer(Modifier.width(dimensionResource(R.dimen.space_small)))
        }
        Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
            Text(
                fighter?.name ?: stringResource(R.string.unknown_fighter),
                color = textColor,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
            )
            fighter?.record?.let {
                Text(it, color = colors.subtle, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
        if (alignEnd) {
            Spacer(Modifier.width(dimensionResource(R.dimen.space_small)))
            FighterAvatar(fighter)
        }
    }
}

@Composable
private fun FighterAvatar(fighter: Fighter?) {
    val colors = LocalUfcColors.current
    Box(Modifier.size(dimensionResource(R.dimen.avatar_size))) {
        AsyncImage(
            model = fighter?.imageUrl,
            contentDescription = fighter?.name ?: stringResource(R.string.unknown_fighter),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(colors.avatar)
                .then(
                    if (fighter?.isWinner == true) Modifier.border(
                        dimensionResource(R.dimen.avatar_border_width),
                        colors.win,
                        CircleShape,
                    ) else Modifier,
                ),
        )
        if (fighter?.isWinner == true) {
            Icon(
                Icons.Default.Check,
                contentDescription = stringResource(R.string.winner_content_description),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(dimensionResource(R.dimen.avatar_badge_size))
                    .background(colors.win, CircleShape)
                    .padding(dimensionResource(R.dimen.space_xsmall)),
            )
        }
    }
}

@Composable
private fun Footer(snapshot: CacheSnapshot) {
    val context = LocalContext.current
    val colors = LocalUfcColors.current
    val requestsLeft = (com.anas.ufc.data.RefreshConfig.monthlyRequestLimit -
        com.anas.ufc.data.requestsForCurrentMonth(
            snapshot.requestMonth,
            snapshot.requestCount,
            Instant.now(),
        )).coerceAtLeast(0)
    val updated = snapshot.lastSuccessfulFetchMillis?.let { Date(it).let(android.text.format.DateFormat.getTimeFormat(context)::format) }
    val text = if (updated != null) {
        stringResource(R.string.footer_updated, updated, requestsLeft)
    } else {
        stringResource(R.string.footer_not_updated)
    }
    Text(
        text,
        color = colors.subtle,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = dimensionResource(R.dimen.space_large),
            vertical = dimensionResource(R.dimen.space_small),
        ),
    )
    val messageRes = when (snapshot.refreshStatus) {
        RefreshStatus.ALREADY_UP_TO_DATE -> R.string.already_up_to_date
        RefreshStatus.MONTHLY_LIMIT_REACHED -> R.string.monthly_limit_reached
        RefreshStatus.API_KEY_REQUIRED -> R.string.error_api_key
        RefreshStatus.ERROR -> R.string.error_generic
        else -> null
    }
    if (messageRes != null) {
        Text(
            stringResource(messageRes),
            color = colors.subtle,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(
                start = dimensionResource(R.dimen.space_large),
                bottom = dimensionResource(R.dimen.space_small),
            ),
        )
    }
}

@Composable
private fun AboutCredits(snapshot: CacheSnapshot) {
    val credits = snapshot.events
        .flatMap { event -> event.card.values.flatten() }
        .flatMap { fight -> listOfNotNull(fight.left, fight.right).flatMap(Fighter::credits) }
        .distinct()
    if (credits.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_photo_credits))
        }
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(dimensionResource(R.dimen.space_large))) {
            items(credits) { credit -> PhotoCreditRow(credit) }
        }
    }
}

@Composable
private fun PhotoCreditRow(credit: PhotoCredit) {
    val artist = credit.artist?.takeIf(String::isNotBlank) ?: stringResource(R.string.credit_artist_unknown)
    val license = credit.license?.takeIf(String::isNotBlank) ?: stringResource(R.string.credit_license_unknown)
    Column(Modifier.fillMaxWidth().padding(vertical = dimensionResource(R.dimen.space_medium))) {
        Text(stringResource(R.string.photo_credit, artist, license), style = MaterialTheme.typography.bodyLarge)
        credit.sourcePage?.let { Text(it, color = LocalUfcColors.current.subtle, style = MaterialTheme.typography.bodySmall) }
        credit.licenseUrl?.let { Text(it, color = LocalUfcColors.current.subtle, style = MaterialTheme.typography.bodySmall) }
        HorizontalDivider(color = LocalUfcColors.current.divider)
    }
}
