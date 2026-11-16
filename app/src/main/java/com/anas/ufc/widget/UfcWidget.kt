package com.anas.ufc.widget

import android.content.Context
import android.content.Intent
import androidx.annotation.DimenRes
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.anas.ufc.MainActivity
import com.anas.ufc.R
import com.anas.ufc.data.CardSection
import com.anas.ufc.data.EventDateFormatter
import com.anas.ufc.data.Fight
import com.anas.ufc.data.UfcEvent
import com.anas.ufc.data.UfcRepositoryProvider
import com.anas.ufc.data.RefreshStatus
import com.anas.ufc.data.toDisplayText
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import androidx.glance.ColorFilter

class UfcWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(250.dp, 110.dp), DpSize(320.dp, 180.dp), DpSize(400.dp, 300.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val cache = UfcRepositoryProvider.get(context).cache.first()
        val event = cache.events.firstOrNull { it.slug == cache.selectedEventSlug }
            ?: cache.events.firstOrNull()
        provideContent { WidgetContent(event, cache.lastSuccessfulFetchMillis, cache.refreshStatus) }
    }
}

@androidx.compose.runtime.Composable
private fun WidgetContent(event: UfcEvent?, lastUpdatedMillis: Long?, refreshStatus: RefreshStatus) {
    val context = LocalContext.current
    val size = LocalSize.current
    val red = ColorProvider(context.getColor(R.color.ufc_red))
    val darkRed = ColorProvider(context.getColor(R.color.ufc_dark_red))
    val white = ColorProvider(context.getColor(R.color.white))
    val ink = ColorProvider(context.getColor(R.color.surface_ink))
    val subtle = ColorProvider(context.getColor(R.color.surface_subtle))
    val win = ColorProvider(context.getColor(R.color.win_green))
    val fights = event?.card?.get(CardSection.MAIN).orEmpty()
    val visibleCount = (
        (size.height - context.glanceDimension(R.dimen.widget_reserved_height)).value /
            context.glanceDimension(R.dimen.widget_fight_row_height).value
        ).toInt().coerceIn(1, 8)
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(context.getColor(R.color.surface_background)))
            .cornerRadius(context.glanceDimension(R.dimen.widget_corner_radius))
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
    ) {
        Row(
            GlanceModifier.fillMaxWidth().background(red).padding(
                horizontal = context.glanceDimension(R.dimen.widget_padding),
                vertical = context.glanceDimension(R.dimen.space_small),
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    event?.name ?: context.getString(R.string.app_name),
                    style = TextStyle(
                        color = white,
                        fontWeight = FontWeight.Bold,
                        fontSize = context.glanceTextSize(R.dimen.widget_header_text),
                    ),
                    maxLines = 1,
                )
            }
            Box(
                GlanceModifier
                    .background(darkRed)
                    .cornerRadius(context.glanceDimension(R.dimen.widget_corner_radius))
                    .padding(context.glanceDimension(R.dimen.space_small))
                    .clickable(actionRunCallback<WidgetRefreshAction>()),
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_refresh),
                    contentDescription = context.getString(R.string.widget_refresh),
                    modifier = GlanceModifier
                        .width(context.glanceDimension(R.dimen.widget_icon_size))
                        .height(context.glanceDimension(R.dimen.widget_icon_size)),
                    colorFilter = ColorFilter.tint(white),
                )
            }
        }
        if (fights.isEmpty()) {
            Box(GlanceModifier.fillMaxSize().defaultWeight(), contentAlignment = Alignment.Center) {
                Text(context.getString(R.string.widget_no_fights), style = TextStyle(color = subtle))
            }
        } else {
            Column(
                GlanceModifier.defaultWeight().fillMaxWidth().padding(
                    horizontal = context.glanceDimension(R.dimen.widget_padding),
                    vertical = context.glanceDimension(R.dimen.space_small),
                ),
            ) {
                fights.take(visibleCount).forEachIndexed { index, fight ->
                    FightLine(fight, ink, subtle, win)
                    if (index != minOf(fights.lastIndex, visibleCount - 1)) {
                        Spacer(GlanceModifier.height(context.glanceDimension(R.dimen.space_small)))
                    }
                }
            }
        }
        val updatedTime = lastUpdatedMillis
            ?.let { Date(it).let(android.text.format.DateFormat.getTimeFormat(context)::format) }
        val footerText = when (refreshStatus) {
            RefreshStatus.REFRESHING -> context.getString(R.string.refreshing)
            RefreshStatus.ALREADY_UP_TO_DATE -> context.getString(R.string.already_up_to_date)
            RefreshStatus.MONTHLY_LIMIT_REACHED -> updatedTime?.let {
                context.getString(R.string.widget_updated_with_status, it, context.getString(R.string.monthly_limit_reached))
            } ?: context.getString(R.string.monthly_limit_reached)
            RefreshStatus.OFFLINE -> updatedTime?.let {
                context.getString(R.string.widget_updated_with_status, it, context.getString(R.string.showing_saved_data))
            } ?: context.getString(R.string.showing_saved_data)
            else -> updatedTime?.let { context.getString(R.string.widget_updated, it) }
        }
        if (footerText != null) {
            Text(
                footerText,
                modifier = GlanceModifier.fillMaxWidth().padding(
                    horizontal = context.glanceDimension(R.dimen.widget_padding),
                    vertical = context.glanceDimension(R.dimen.space_xsmall),
                ),
                style = TextStyle(color = subtle, fontSize = context.glanceTextSize(R.dimen.widget_small_text)),
                maxLines = 1,
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun FightLine(
    fight: Fight,
    ink: ColorProvider,
    subtle: ColorProvider,
    win: ColorProvider,
) {
    val context = LocalContext.current
    val left = fight.left
    val right = fight.right
    Column(GlanceModifier.fillMaxWidth()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                left?.name ?: context.getString(R.string.unknown_fighter),
                style = TextStyle(color = if (fight.isFinal && right?.isWinner == true) subtle else ink,
                    fontSize = context.glanceTextSize(R.dimen.widget_body_text),
                    fontWeight = if (left?.isWinner == true) FontWeight.Bold else FontWeight.Normal),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            if (left?.isWinner == true) {
                Image(
                    provider = ImageProvider(R.drawable.ic_winner_check),
                    contentDescription = context.getString(R.string.winner_content_description),
                    modifier = GlanceModifier
                        .width(context.glanceDimension(R.dimen.widget_icon_size))
                        .height(context.glanceDimension(R.dimen.widget_icon_size)),
                    colorFilter = ColorFilter.tint(win),
                )
            }
            Text(
                context.getString(R.string.widget_vs),
                modifier = GlanceModifier.padding(horizontal = context.glanceDimension(R.dimen.space_xsmall)),
                style = TextStyle(color = subtle, fontSize = context.glanceTextSize(R.dimen.widget_small_text)),
            )
            if (right?.isWinner == true) {
                Image(
                    provider = ImageProvider(R.drawable.ic_winner_check),
                    contentDescription = context.getString(R.string.winner_content_description),
                    modifier = GlanceModifier
                        .width(context.glanceDimension(R.dimen.widget_icon_size))
                        .height(context.glanceDimension(R.dimen.widget_icon_size)),
                    colorFilter = ColorFilter.tint(win),
                )
            }
            Text(
                right?.name ?: context.getString(R.string.unknown_fighter),
                style = TextStyle(color = if (fight.isFinal && left?.isWinner == true) subtle else ink,
                    fontSize = context.glanceTextSize(R.dimen.widget_body_text),
                    fontWeight = if (right?.isWinner == true) FontWeight.Bold else FontWeight.Normal),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
        val result = fight.result?.takeIf { fight.isFinal }?.let {
            it.toDisplayText(context)
        }
        val fallback = listOfNotNull(
            fight.weightClass,
            fight.startsAt?.let {
                EventDateFormatter.format(
                    it, Instant.now(), ZoneId.systemDefault(), Locale.getDefault(),
                    context.getString(R.string.date_today), context.getString(R.string.date_tomorrow),
                )
            },
        ).joinToString(context.getString(R.string.text_separator))
        Text(
            result?.takeIf(String::isNotBlank) ?: fallback,
            style = TextStyle(
                color = if (fight.result?.isDraw == true) subtle else if (result != null) win else subtle,
                fontSize = context.glanceTextSize(R.dimen.widget_small_text),
            ),
            maxLines = 1,
        )
    }
}

class WidgetRefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        UfcRepositoryProvider.get(context).setRefreshStatus(RefreshStatus.REFRESHING)
        UfcWidget().updateAll(context)
        UfcRefreshWorker.enqueueManual(context)
    }
}

private fun Context.glanceDimension(@DimenRes resource: Int): Dp =
    Dp(resources.getDimension(resource) / resources.displayMetrics.density)

private fun Context.glanceTextSize(@DimenRes resource: Int) =
    (resources.getDimension(resource) / resources.displayMetrics.scaledDensity).sp
