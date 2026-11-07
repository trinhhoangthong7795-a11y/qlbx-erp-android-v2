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
                    val previous = mutableVehicles.value.firstOrNull { old ->
                        old.status == VehicleStatus.EXITED &&
                            old.identityType == validation.vehicle.identityType &&
                            if (old.identityType == IdentityType.PLATE) old.plate.equals(validation.vehicle.plate, true) else old.vinLast6 == validation.vehicle.vinLast6
                    }
                    val now = System.currentTimeMillis()
                    val saved = if (previous != null) previous.copy(
                        model = validation.vehicle.model.ifBlank { previous.model },
                        color = validation.vehicle.color.ifBlank { previous.color },
                        department = validation.vehicle.department.ifBlank { previous.department },
                        team = validation.vehicle.team.ifBlank { previous.team },
                        entryAt = now, entryPerson = validation.vehicle.entryPerson,
                        exitAt = null, exitPerson = "", status = VehicleStatus.INSIDE,
                        currentSlotId = null, updatedAt = now
                    ) else validation.vehicle
                    mutableVehicles.value = if (previous != null) {
                        listOf(saved) + mutableVehicles.value.filterNot { it.id == previous.id }
                    } else listOf(saved) + mutableVehicles.value
                    persist()
                    Result.success(saved)
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

    @Synchronized
    fun replaceAll(replacement: List<Vehicle>): Result<Unit> {
        if (replacement.map { it.id }.distinct().size != replacement.size) {
            return Result.failure(IllegalArgumentException("Tệp có ID xe trùng nhau."))
        }
        val activeKeys = replacement.filter { it.status != VehicleStatus.EXITED }.map {
            if (it.identityType == IdentityType.PLATE) "P:${it.plate?.uppercase()}" else "V:${it.vinLast6}"
        }
        if (activeKeys.distinct().size != activeKeys.size) {
            return Result.failure(IllegalArgumentException("Có xe đang trong bãi bị trùng biển số/VIN."))
        }
        mutableVehicles.value = replacement.sortedByDescending { it.entryAt }
        persist()
        return Result.success(Unit)
    }

    @Synchronized
    fun update(updated: Vehicle): Result<Vehicle> {
        val old = mutableVehicles.value.firstOrNull { it.id == updated.id }
            ?: return Result.failure(NoSuchElementException("Không tìm thấy xe."))
        if (VehicleRules.isDuplicateActive(updated, mutableVehicles.value)) {
            return Result.failure(IllegalStateException("Biển số/VIN đang được xe khác sử dụng trong bãi."))
        }
        val saved = updated.copy(updatedAt = System.currentTimeMillis())
        mutableVehicles.value = mutableVehicles.value.map { if (it.id == old.id) saved else it }
        persist()
        return Result.success(saved)
    }

    @Synchronized
    fun delete(vehicleId: String): Result<Unit> {
        if (mutableVehicles.value.none { it.id == vehicleId }) {
            return Result.failure(NoSuchElementException("Không tìm thấy xe."))
        }
        mutableVehicles.value = mutableVehicles.value.filterNot { it.id == vehicleId }
        persist()
        return Result.success(Unit)
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
