package vn.qlbx.newver1.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory development store. Replace with Room-backed storage before acceptance;
 * this class intentionally makes no cloud-sync or persistence claims.
 */
class VehicleStore {
    private val mutableVehicles = MutableStateFlow<List<Vehicle>>(emptyList())
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
        val updated = current.copy(
            status = VehicleStatus.EXITED,
            exitAt = System.currentTimeMillis(),
            exitPerson = person.trim(),
            currentSlotId = null,
            updatedAt = System.currentTimeMillis()
        )
        mutableVehicles.value = mutableVehicles.value.map { if (it.id == vehicleId) updated else it }
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
}
