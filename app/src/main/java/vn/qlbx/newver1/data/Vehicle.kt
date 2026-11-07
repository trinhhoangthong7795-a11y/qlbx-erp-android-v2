package vn.qlbx.newver1.data

import java.util.UUID

enum class IdentityType { PLATE, VIN6 }
enum class VehicleStatus { INSIDE, WAITING, EXITED }

data class Vehicle(
    val id: String = UUID.randomUUID().toString(),
    val identityType: IdentityType,
    val plate: String? = null,
    val vinLast6: String? = null,
    val model: String = "",
    val color: String = "",
    val department: String = "",
    val team: String = "",
    val entryAt: Long = System.currentTimeMillis(),
    val entryPerson: String = "",
    val exitAt: Long? = null,
    val exitPerson: String = "",
    val status: VehicleStatus = VehicleStatus.INSIDE,
    val currentSlotId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class VehicleDraft(
    val identityType: IdentityType,
    val identifier: String,
    val model: String = "",
    val color: String = "",
    val department: String = "",
    val team: String = "",
    val entryPerson: String = ""
)

sealed interface VehicleValidation {
    data class Valid(val vehicle: Vehicle) : VehicleValidation
    data class Invalid(val message: String) : VehicleValidation
}

object VehicleRules {
    fun normalizeIdentifier(type: IdentityType, raw: String): String =
        if (type == IdentityType.PLATE) raw.trim().uppercase().replace(" ", "").replace("-", "")
        else raw.filter(Char::isDigit)

    fun validate(draft: VehicleDraft): VehicleValidation {
        val id = normalizeIdentifier(draft.identityType, draft.identifier)
        if (id.isBlank()) return VehicleValidation.Invalid(
            if (draft.identityType == IdentityType.PLATE) "Vui lòng nhập biển số xe." else "Vui lòng nhập 6 số cuối VIN."
        )
        if (draft.identityType == IdentityType.VIN6 && (id.length != 6 || !id.all(Char::isDigit))) {
            return VehicleValidation.Invalid("VIN cuối phải gồm đúng 6 chữ số.")
        }
        if (draft.identityType == IdentityType.PLATE && id.length < 5) {
            return VehicleValidation.Invalid("Biển số xe chưa hợp lệ.")
        }
        val vehicle = Vehicle(
            identityType = draft.identityType,
            plate = if (draft.identityType == IdentityType.PLATE) id else null,
            vinLast6 = if (draft.identityType == IdentityType.VIN6) id else null,
            model = draft.model.trim(),
            color = draft.color.trim(),
            department = draft.department.trim(),
            team = draft.team.trim(),
            entryPerson = draft.entryPerson.trim()
        )
        return VehicleValidation.Valid(vehicle)
    }

    fun isDuplicateActive(candidate: Vehicle, existing: Iterable<Vehicle>): Boolean {
        return existing.any { current ->
            current.status != VehicleStatus.EXITED &&
                current.id != candidate.id &&
                when (candidate.identityType) {
                    IdentityType.PLATE -> current.identityType == IdentityType.PLATE &&
                        current.plate.equals(candidate.plate, ignoreCase = true)
                    IdentityType.VIN6 -> current.identityType == IdentityType.VIN6 &&
                        current.vinLast6 == candidate.vinLast6
                }
        }
    }
}
