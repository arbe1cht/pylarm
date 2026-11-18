package ru.pylarm

import org.json.JSONArray
import org.json.JSONObject

data class Alarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val second: Int,
    val millisecond: Int,
    val date: String? = null,
    val weekdays: Set<Int> = emptySet(),
    val enabled: Boolean = true,
    val ringtoneUri: String? = null,
    val ringtoneTitle: String = "Мелодия по умолчанию"
) {
    fun toJson() = JSONObject().apply {
        put("id", id)
        put("hour", hour)
        put("minute", minute)
        put("second", second)
        put("millisecond", millisecond)
        put("date", date ?: JSONObject.NULL)
        put("weekdays", JSONArray(weekdays.sorted()))
        put("enabled", enabled)
        put("ringtoneUri", ringtoneUri ?: JSONObject.NULL)
        put("ringtoneTitle", ringtoneTitle)
    }

    companion object {
        fun fromJson(json: JSONObject) = Alarm(
            id = json.getLong("id"),
            hour = json.getInt("hour"),
            minute = json.getInt("minute"),
            second = json.optInt("second"),
            millisecond = json.optInt("millisecond"),
            date = json.optString("date").takeUnless { it == "null" || it.isBlank() },
            weekdays = json.optJSONArray("weekdays")?.let { values ->
                (0 until values.length()).map { values.getInt(it) }.toSet()
            } ?: emptySet(),
            enabled = json.optBoolean("enabled", true),
            ringtoneUri = json.optString("ringtoneUri").takeUnless { it == "null" || it.isBlank() },
            ringtoneTitle = json.optString("ringtoneTitle", "Мелодия по умолчанию")
        )
    }
}

object AlarmStore {
    private const val PREFS = "alarms"
    private const val KEY_LIST = "items"

    fun get(context: android.content.Context): List<Alarm> {
        val raw = context.getSharedPreferences(PREFS, 0).getString(KEY_LIST, "[]") ?: "[]"
        val array = JSONArray(raw)
        return (0 until array.length()).map { Alarm.fromJson(array.getJSONObject(it)) }
    }

    fun save(context: android.content.Context, alarms: List<Alarm>) {
        val array = JSONArray()
        alarms.forEach { array.put(it.toJson()) }
        context.getSharedPreferences(PREFS, 0).edit().putString(KEY_LIST, array.toString()).apply()
    }
}
