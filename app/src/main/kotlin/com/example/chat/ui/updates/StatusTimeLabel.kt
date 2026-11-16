package com.example.chat.ui.updates

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.chat.R
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay

private val LocalStatusNow = compositionLocalOf<Instant?> { null }
private val LocalStatusCalendar = compositionLocalOf<StatusCalendar?> { null }

/** UI-thread-confined mutable formatters, recreated on display calendar changes. */
private class StatusCalendar(
    val zone: ZoneId,
    val date: java.text.DateFormat,
    val time: java.text.DateFormat,
    val number: java.text.NumberFormat,
)

@Composable
private fun rememberStatusCalendar(revision: Int): StatusCalendar {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val zone = ZoneId.systemDefault()
    return remember(context, configuration, zone, revision) {
        StatusCalendar(zone, android.text.format.DateFormat.getMediumDateFormat(context),
            android.text.format.DateFormat.getTimeFormat(context),
            java.text.NumberFormat.getIntegerInstance(configuration.locales[0]))
    }
}

/** One coarse clock for all status timestamps; no per-card timers and no ticking while stopped. */
@Composable
internal fun ProvideStatusTime(content: @Composable () -> Unit) {
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val context = LocalContext.current
    var calendarRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(context, owner) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) { calendarRevision++ }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_DATE_CHANGED)
            addAction(android.content.Intent.ACTION_TIME_CHANGED)
            addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
            addAction(android.content.Intent.ACTION_LOCALE_CHANGED)
        }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) calendarRevision++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); context.unregisterReceiver(receiver) }
    }
    val calendar = rememberStatusCalendar(calendarRevision)
    val now by produceState(Instant.now(), owner, calendarRevision) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { value = Instant.now(); delay(60_000L) }
        }
    }
    CompositionLocalProvider(LocalStatusNow provides now, LocalStatusCalendar provides calendar, content = content)
}

@Composable
internal fun StatusTimeLabel(timestamp: Instant, color: Color) {
    val now = LocalStatusNow.current ?: Instant.now()
    val bucket = StatusRelativeTimeRules.bucket(timestamp, now)
    val calendar = LocalStatusCalendar.current ?: rememberStatusCalendar(0)
    val unavailable = stringResource(R.string.social_status_time_unavailable)
    val absolute = remember(timestamp, calendar, unavailable) {
        try {
            val value = java.util.Date.from(timestamp)
            "${calendar.date.format(value)} ${calendar.time.format(value)}"
        } catch (_: Exception) { unavailable }
    }
    val number = calendar.number.format(bucket.amount)
    val compact: String
    val spoken: String
    if (bucket.amount > Int.MAX_VALUE.toLong()) {
        compact = absolute
        spoken = absolute
    } else if (bucket.unit == StatusRelativeTimeRules.Unit.JUST_NOW) {
        compact = stringResource(R.string.social_status_just_now)
        spoken = compact
    } else {
        compact = stringResource(when (bucket.unit) {
            StatusRelativeTimeRules.Unit.MINUTES -> R.string.social_status_minutes_short
            StatusRelativeTimeRules.Unit.HOURS -> R.string.social_status_hours_short
            else -> R.string.social_status_days_short
        }, number)
        val resource = when (bucket.unit) {
            StatusRelativeTimeRules.Unit.MINUTES -> R.plurals.social_status_minutes_ago
            StatusRelativeTimeRules.Unit.HOURS -> R.plurals.social_status_hours_ago
            else -> R.plurals.social_status_days_ago
        }
        spoken = pluralStringResource(resource, bucket.amount.toInt(), number)
    }
    val description = if (spoken == absolute) absolute else stringResource(R.string.social_status_time_description, spoken, absolute)
    Text(compact, color = color, fontSize = 12.sp,
        modifier = Modifier.clearAndSetSemantics { contentDescription = description })
}
