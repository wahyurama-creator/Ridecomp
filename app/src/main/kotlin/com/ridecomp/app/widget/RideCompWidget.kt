package com.ridecomp.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ridecomp.app.MainActivity
import com.ridecomp.app.R
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.model.BuiltInPlan
import com.ridecomp.core.model.PlanRef
import com.ridecomp.core.model.RideStatus
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.Logger
import com.ridecomp.core.usecase.LoadWidgetSummaryUseCase
import com.ridecomp.core.usecase.WidgetSummary
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.core.util.runCatchingCancellable
import com.ridecomp.feature.ride.R as RideR
import com.ridecomp.feature.ride.model.titleRes
import com.ridecomp.platform.locale.AppLocale
import com.ridecomp.platform.service.RideStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Home screen widget (T9.2, D-96): today's planned session, this week's rides and a start button; during
 * a ride its distance and time. Updated by the system every 30 min, by the recording service at most once a
 * minute while riding (BAT-03), and when the app goes to the background. No data leaves the phone.
 */
class RideCompWidget :
    AppWidgetProvider(),
    KoinComponent {
    private val loadSummary: LoadWidgetSummaryUseCase by inject()
    private val rideState: RideStateHolder by inject()
    private val dispatchers: DispatcherProvider by inject()
    private val logger: Logger by inject()

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(dispatchers.io).launch {
            try {
                val live = rideState.liveRide.value
                // Without the summary (database busy, timeout) the widget still shows the start button.
                val summary = withTimeoutOrNull(WidgetConfig.LOAD_TIMEOUT_MS) {
                    runCatchingCancellable { loadSummary() }
                        .onFailure { logger.warn(TAG, it) { "Widget summary unavailable" } }
                        .getOrNull()
                }
                val views = render(AppLocale.wrap(context), live, summary)
                appWidgetIds.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }

    private fun render(context: Context, live: LiveRide, summary: WidgetSummary?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_ride)
        val isRiding = live.status != RideStatus.IDLE
        if (isRiding) {
            val formatter = UnitFormatter(summary?.unitSystem ?: UnitSystem.METRIC)
            views.setTextViewText(
                R.id.widget_line1,
                context.getString(
                    R.string.widget_riding,
                    formatter.distance(live.distanceM),
                    formatter.distanceUnit,
                    formatter.duration(live.movingMs),
                ),
            )
        } else {
            views.setTextViewText(R.id.widget_line1, todayText(context, summary))
        }
        views.setTextViewText(R.id.widget_line2, weekText(context, summary))
        views.setTextViewText(
            R.id.widget_start,
            context.getString(if (isRiding) R.string.widget_open_ride else R.string.widget_start_ride),
        )
        views.setOnClickPendingIntent(R.id.widget_start, buttonIntent(context, isRiding))
        views.setOnClickPendingIntent(R.id.widget_root, openIntent(context))
        return views
    }

    private fun todayText(context: Context, summary: WidgetSummary?): String {
        val plan = summary?.plan
        val today = plan?.today
        val session = today?.session
        return when {
            plan == null -> context.getString(R.string.widget_no_plan)
            today == null -> context.getString(
                R.string.widget_plan_done,
                planName(context, plan.plan.ref, plan.plan.name),
            )
            session == null -> context.getString(R.string.widget_rest_day)
            else -> context.getString(
                R.string.widget_today,
                context.getString(session.intensity.titleRes()),
                session.durationMin,
            )
        }
    }

    private fun weekText(context: Context, summary: WidgetSummary?): String {
        val week = summary?.week ?: return ""
        val formatter = UnitFormatter(summary.unitSystem)
        return context.resources.getQuantityString(
            R.plurals.widget_week,
            week.rides,
            week.rides,
            formatter.distance(week.distanceM),
            formatter.distanceUnit,
        )
    }

    private fun planName(context: Context, ref: PlanRef, name: String): String = when (ref) {
        is PlanRef.BuiltIn -> context.getString(
            when (ref.plan) {
                BuiltInPlan.ZONE2_BASE -> RideR.string.session_plan_zone2_base
                BuiltInPlan.RACE_PREP -> RideR.string.session_plan_race_prep
                BuiltInPlan.GRAN_FONDO -> RideR.string.session_plan_gran_fondo
            },
        )
        is PlanRef.Custom -> name.ifBlank { context.getString(RideR.string.session_plan_custom) }
    }

    /** "Mulai ride" opens the pre-ride check, "Buka ride" the running ride (explicit, immutable). */
    private fun buttonIntent(context: Context, isRiding: Boolean): PendingIntent = PendingIntent.getActivity(
        context,
        WidgetConfig.REQUEST_START,
        Intent(context, MainActivity::class.java)
            .setAction(if (isRiding) WidgetConfig.ACTION_OPEN_RIDE else WidgetConfig.ACTION_START_RIDE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        WidgetConfig.REQUEST_OPEN,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val TAG = "RideCompWidget"

        /** Asks every placed widget to update (no-op without widgets). */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RideCompWidget::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, RideCompWidget::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }
    }
}

/** Widget constants (T9.2). */
object WidgetConfig {
    /** The widget's "Mulai ride" button; MainActivity opens the pre-ride check or the running ride. */
    const val ACTION_START_RIDE = "com.ridecomp.app.action.START_RIDE"
    const val ACTION_OPEN_RIDE = "com.ridecomp.app.action.OPEN_RIDE"
    const val REQUEST_START = 1
    const val REQUEST_OPEN = 2

    /** A widget update must finish well within the broadcast limit. */
    const val LOAD_TIMEOUT_MS = 5_000L
}
