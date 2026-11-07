package vn.qlbx.newver1.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Small-device persistent store for the first native acceptance build.
 * Cloud sync and transactional multi-device consistency are not implemented yet.
 */
class VehicleStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("qlbx_local_v1", Context.MODE_PRIVATE)
    private val mutableVehicles = MutableStateFlow(load())
    val vehicles: StateFlow<List<Vehicle>> = mutableVehicles.asStateFlow()

    @Synchronized
    fun add(draft: VehicleDraft): Result<Vehicle> {
        return when (val validation = VehicleRules.validate(draft)) {
            is VehicleValidation.Invalid -> Result.failure(IllegalArgumentException(validation.message))
            is VehicleValidation.Valid -> {
                if (VehicleRules.isDuplicateActive(validation.vehicle, mutableVehicles.value)) {
                    Result.failure(IllegalStateException("Xe này đang có trong bãi."))
                } else {
                    mutableVehicles.value = listOf(validation.vehicle) + mutableVehicles.value
                    persist()
                    Result.success(validation.vehicle)
                }
            }
        }
    }

    @Synchronized
    fun exit(vehicleId: String, person: String = ""): Result<Vehicle> {
        val current = mutableVehicles.value.firstOrNull { it.id == vehicleId }
            ?: return Result.failure(NoSuchElementException("Không tìm thấy xe."))
        if (current.status == VehicleStatus.EXITED) {
            return Result.failure(IllegalStateException("Xe đã xuất bãi trước đó."))
        }
        val now = System.currentTimeMillis()
        val updated = current.copy(
            status = VehicleStatus.EXITED,
            exitAt = now,
            exitPerson = person.trim(),
            currentSlotId = null,
            updatedAt = now
        )
        mutableVehicles.value = mutableVehicles.value.map { if (it.id == vehicleId) updated else it }
        persist()
        return Result.success(updated)
    }

    fun search(query: String): List<Vehicle> {
        val q = query.trim().uppercase()
        if (q.isBlank()) return mutableVehicles.value
        return mutableVehicles.value.filter {
            it.plate.orEmpty().contains(q) || it.vinLast6.orEmpty().contains(q) ||
                it.model.uppercase().contains(q)
        }
    }

    private fun persist() {
        val array = JSONArray()
        mutableVehicles.value.forEach { v ->
            array.put(JSONObject().apply {
                put("id", v.id)
                put("identityType", v.identityType.name)
                put("plate", v.plate ?: JSONObject.NULL)
                put("vinLast6", v.vinLast6 ?: JSONObject.NULL)
                put("model", v.model)
                put("color", v.color)
                put("department", v.department)
                put("team", v.team)
                put("entryAt", v.entryAt)
                put("entryPerson", v.entryPerson)
                put("exitAt", v.exitAt ?: JSONObject.NULL)
                put("exitPerson", v.exitPerson)
                put("status", v.status.name)
                put("currentSlotId", v.currentSlotId ?: JSONObject.NULL)
                put("createdAt", v.createdAt)
                put("updatedAt", v.updatedAt)
            })
        }
        prefs.edit().putString("vehicles", array.toString()).apply()
    }

    private fun load(): List<Vehicle> = runCatching {
        val raw = prefs.getString("vehicles", null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Vehicle(
                id = o.getString("id"),
                identityType = IdentityType.valueOf(o.getString("identityType")),
                plate = o.optString("plate").takeUnless { it == "null" || it.isBlank() },
                vinLast6 = o.optString("vinLast6").takeUnless { it == "null" || it.isBlank() },
                model = o.optString("model"),
                color = o.optString("color"),
                department = o.optString("department"),
                team = o.optString("team"),
                entryAt = o.optLong("entryAt"),
                entryPerson = o.optString("entryPerson"),
                exitAt = if (o.isNull("exitAt")) null else o.optLong("exitAt"),
                exitPerson = o.optString("exitPerson"),
                status = VehicleStatus.valueOf(o.getString("status")),
                currentSlotId = if (o.isNull("currentSlotId")) null else o.optString("currentSlotId"),
                createdAt = o.optLong("createdAt"),
                updatedAt = o.optLong("updatedAt")
            )
        }
    }.getOrDefault(emptyList())
}
