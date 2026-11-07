package vn.qlbx.newver1.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SpecialEvent(val id: String = UUID.randomUUID().toString(), val date: Long, val description: String)

class SpecialEventStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("qlbx_events_v1", Context.MODE_PRIVATE)
    private val mutableEvents = MutableStateFlow(load())
    val events: StateFlow<List<SpecialEvent>> = mutableEvents.asStateFlow()

    @Synchronized fun add(date: Long, description: String): Result<SpecialEvent> {
        if (description.isBlank()) return Result.failure(IllegalArgumentException("Vui lòng nhập nội dung sự kiện."))
        val event = SpecialEvent(date = date, description = description.trim())
        mutableEvents.value = (listOf(event) + mutableEvents.value).sortedByDescending { it.date }
        persist()
        return Result.success(event)
    }

    @Synchronized fun delete(id: String) {
        mutableEvents.value = mutableEvents.value.filterNot { it.id == id }
        persist()
    }

    @Synchronized
    fun replaceAll(replacement: List<SpecialEvent>): Result<Unit> {
        if (replacement.map { it.id }.distinct().size != replacement.size) return Result.failure(IllegalArgumentException("Sự kiện có ID trùng nhau."))
        if (replacement.any { it.description.isBlank() }) return Result.failure(IllegalArgumentException("Sự kiện có nội dung trống."))
        mutableEvents.value = replacement.sortedByDescending { it.date }
        persist()
        return Result.success(Unit)
    }

    private fun persist() {
        val array = JSONArray()
        mutableEvents.value.forEach { array.put(JSONObject().put("id", it.id).put("date", it.date).put("description", it.description)) }
        prefs.edit().putString("events", array.toString()).apply()
    }

    private fun load(): List<SpecialEvent> = runCatching {
        val raw = prefs.getString("events", null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            SpecialEvent(o.getString("id"), o.getLong("date"), o.getString("description"))
        }.sortedByDescending { it.date }
    }.getOrDefault(emptyList())
}
