package vn.qlbx.newver1.data

import android.content.ContentResolver
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class AppBackup(
    val vehicles: List<Vehicle>,
    val movements: List<VehicleMovement>,
    val slots: List<YardSlot>,
    val events: List<SpecialEvent>
)

object AppBackupFileManager {
    fun export(resolver: ContentResolver, uri: Uri, backup: AppBackup) {
        val root = JSONObject().put("schemaVersion", 2).put("exportedAt", System.currentTimeMillis())
        val vehicles = JSONArray()
        backup.vehicles.forEach { v ->
            vehicles.put(JSONObject().apply {
                put("id", v.id); put("identityType", v.identityType.name)
                put("plate", v.plate ?: JSONObject.NULL); put("vinLast6", v.vinLast6 ?: JSONObject.NULL)
                put("model", v.model); put("color", v.color); put("department", v.department); put("team", v.team)
                put("entryAt", v.entryAt); put("entryPerson", v.entryPerson)
                put("exitAt", v.exitAt ?: JSONObject.NULL); put("exitPerson", v.exitPerson)
                put("status", v.status.name); put("currentSlotId", v.currentSlotId ?: JSONObject.NULL)
                put("createdAt", v.createdAt); put("updatedAt", v.updatedAt)
            })
        }
        val movements = JSONArray()
        backup.movements.forEach { m ->
            movements.put(JSONObject().put("id", m.id).put("vehicleId", m.vehicleId).put("action", m.action)
                .put("occurredAt", m.occurredAt).put("identifier", m.identifier).put("model", m.model)
                .put("person", m.person).put("note", m.note))
        }
        val slots = JSONArray()
        backup.slots.forEach { s ->
            slots.put(JSONObject().put("id", s.id).put("row", s.row).put("lane", s.lane)
                .put("position", s.position).put("label", s.label).put("active", s.active)
                .put("vehicleId", s.vehicleId ?: JSONObject.NULL))
        }
        val events = JSONArray()
        backup.events.forEach { e -> events.put(JSONObject().put("id", e.id).put("date", e.date).put("description", e.description)) }
        root.put("vehicles", vehicles).put("movements", movements).put("slots", slots).put("events", events)
        resolver.openOutputStream(uri, "wt")?.use { it.write(root.toString(2).toByteArray(Charsets.UTF_8)) }
            ?: error("Không mở được tệp đích để ghi.")
    }

    fun import(resolver: ContentResolver, uri: Uri): AppBackup {
        val raw = resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Không mở được tệp đã chọn.")
        require(raw.toByteArray().size <= 50 * 1024 * 1024) { "Bản sao lưu vượt quá 50 MB." }
        val root = JSONObject(raw)
        require(root.optInt("schemaVersion") == 2) { "Đây không phải bản sao lưu QLBX đầy đủ phiên bản 2." }
        val vehiclesArray = root.getJSONArray("vehicles")
        val vehicles = (0 until vehiclesArray.length()).map { i ->
            val o = vehiclesArray.getJSONObject(i)
            val type = IdentityType.valueOf(o.getString("identityType"))
            val identifier = if (type == IdentityType.PLATE) o.optString("plate") else o.optString("vinLast6")
            val validation = VehicleRules.validate(VehicleDraft(type, identifier, o.optString("model"), o.optString("color"), o.optString("department"), o.optString("team"), o.optString("entryPerson")))
            val base = (validation as? VehicleValidation.Valid)?.vehicle ?: error("Xe ở dòng ${i + 1} không hợp lệ.")
            base.copy(
                id = o.optString("id").ifBlank { base.id }, entryAt = o.optLong("entryAt", base.entryAt),
                exitAt = if (o.isNull("exitAt")) null else o.optLong("exitAt"), exitPerson = o.optString("exitPerson"),
                status = VehicleStatus.valueOf(o.optString("status", "INSIDE")),
                currentSlotId = if (o.isNull("currentSlotId")) null else o.optString("currentSlotId"),
                createdAt = o.optLong("createdAt", base.createdAt), updatedAt = o.optLong("updatedAt", base.updatedAt)
            )
        }
        require(vehicles.map { it.id }.distinct().size == vehicles.size) { "Bản sao lưu có ID xe trùng nhau." }
        val movementArray = root.optJSONArray("movements") ?: JSONArray()
        val movements = (0 until movementArray.length()).map { i ->
            val o = movementArray.getJSONObject(i)
            VehicleMovement(o.optString("id"), o.getString("vehicleId"), o.getString("action"), o.optLong("occurredAt"),
                o.optString("identifier"), o.optString("model"), o.optString("person"), o.optString("note"))
        }
        require(movements.all { m -> vehicles.any { it.id == m.vehicleId } }) { "Nhật ký chứa xe không tồn tại trong bản sao lưu." }
        val slotArray = root.optJSONArray("slots") ?: JSONArray()
        val slots = (0 until slotArray.length()).map { i ->
            val o = slotArray.getJSONObject(i)
            YardSlot(o.getString("id"), o.getInt("row"), o.getInt("lane"), o.getInt("position"), o.getString("label"),
                o.optBoolean("active", true), if (o.isNull("vehicleId")) null else o.optString("vehicleId"))
        }
        require(slots.map { it.id }.distinct().size == slots.size) { "Sơ đồ bãi có ID ô trùng nhau." }
        val occupiedIds = slots.mapNotNull { it.vehicleId }
        require(occupiedIds.distinct().size == occupiedIds.size) { "Một xe đang được gán vào nhiều ô." }
        require(occupiedIds.all { id -> vehicles.any { it.id == id && it.status != VehicleStatus.EXITED } }) { "Ô bãi đang tham chiếu đến xe không hợp lệ." }
        val eventArray = root.optJSONArray("events") ?: JSONArray()
        val events = (0 until eventArray.length()).map { i ->
            val o = eventArray.getJSONObject(i)
            SpecialEvent(o.getString("id"), o.getLong("date"), o.getString("description"))
        }
        require(events.map { it.id }.distinct().size == events.size) { "Sự kiện có ID trùng nhau." }
        return AppBackup(vehicles, movements, slots, events)
    }
}
