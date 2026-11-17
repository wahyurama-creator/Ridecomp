package com.ridecomp.data.json

import com.ridecomp.core.model.CustomPlan
import com.ridecomp.core.model.Intensity
import com.ridecomp.core.model.PlanSession
import com.ridecomp.core.model.PlanWeek
import com.ridecomp.core.model.TrainingPlanLimits
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The rider's plans as JSON in `settings` (T7.4, D-94):
 * `[{"id":1,"name":"…","weeks":4,"days":[null,{"i":"endurance","min":60},…]}]`.
 * Decoding never throws: a damaged entry is skipped (STB-09).
 */
internal object PlansJson {
    private const val ID = "id"
    private const val NAME = "name"
    private const val WEEKS = "weeks"
    private const val DAYS = "days"
    private const val INTENSITY = "i"
    private const val MINUTES = "min"

    fun encode(plans: List<CustomPlan>): String {
        val array = JSONArray()
        plans.forEach { plan ->
            val days = JSONArray()
            plan.week.days.forEach { day ->
                days.put(
                    day?.let { JSONObject().put(INTENSITY, it.intensity.name.lowercase()).put(MINUTES, it.durationMin) }
                        ?: JSONObject.NULL,
                )
            }
            array.put(JSONObject().put(ID, plan.id).put(NAME, plan.name).put(WEEKS, plan.weekCount).put(DAYS, days))
        }
        return array.toString()
    }

    fun decode(json: String?): List<CustomPlan> {
        val array = json?.takeIf { it.isNotBlank() }?.let(::parseArray) ?: return emptyList()
        return (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.toPlan() }
    }

    private fun parseArray(json: String): JSONArray? = try {
        JSONArray(json)
    } catch (_: JSONException) {
        null
    }

    private fun JSONObject.toPlan(): CustomPlan? {
        val days = optJSONArray(DAYS)?.takeIf { it.length() == TrainingPlanLimits.DAYS_PER_WEEK } ?: return null
        val sessions = (0 until days.length()).map { index -> days.optJSONObject(index)?.toSession() }
        return CustomPlan(
            id = optLong(ID),
            name = optString(NAME),
            weekCount = optInt(WEEKS, 1).coerceIn(1, TrainingPlanLimits.MAX_WEEKS),
            week = PlanWeek(sessions),
        )
    }

    private fun JSONObject.toSession(): PlanSession? {
        val intensity = Intensity.entries.firstOrNull { it.name.equals(optString(INTENSITY), ignoreCase = true) }
        val minutes = optInt(MINUTES).takeIf {
            it in
                TrainingPlanLimits.MIN_SESSION_MIN..TrainingPlanLimits.MAX_SESSION_MIN
        }
        return if (intensity != null && minutes != null) PlanSession(intensity, minutes) else null
    }
}
