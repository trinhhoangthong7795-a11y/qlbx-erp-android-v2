package vn.qlbx.newver1.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class YardSlot(
    val id: String,
    val row: Int,
    val lane: Int,
    val position: Int,
    val label: String,
    val active: Boolean = true,
    val vehicleId: String? = null
)

class YardStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("qlbx_yard_v1", Context.MODE_PRIVATE)
    private val laneNames = listOf("T", "TLD", "PLD", "P")
    private val mutableSlots = MutableStateFlow(load())
    val slots: StateFlow<List<YardSlot>> = mutableSlots.asStateFlow()

    @Synchronized
    fun assign(slotId: String, vehicleId: String, vehicleStore: VehicleStore): Result<Unit> {
        val slot = mutableSlots.value.firstOrNull { it.id == slotId && it.active }
            ?: return Result.failure(NoSuchElementException("Ô đỗ không tồn tại."))
        if (slot.vehicleId != null && slot.vehicleId != vehicleId) return Result.failure(IllegalStateException("Ô này đã có xe."))
        val vehicle = vehicleStore.vehicles.value.firstOrNull { it.id == vehicleId }
            ?: return Result.failure(NoSuchElementException("Không tìm thấy xe."))
        if (vehicle.status == VehicleStatus.EXITED) return Result.failure(IllegalStateException("Xe đã xuất bãi."))
        val updated = vehicle.copy(currentSlotId = slotId, status = VehicleStatus.INSIDE)
        vehicleStore.update(updated).getOrElse { return Result.failure(it) }
        mutableSlots.value = mutableSlots.value.map { s ->
            when {
                s.id == slotId -> s.copy(vehicleId = vehicleId)
                s.vehicleId == vehicleId -> s.copy(vehicleId = null)
                else -> s
            }
        }
        persist()
        return Result.success(Unit)
    }

    @Synchronized
    fun removeToWaiting(slotId: String, vehicleStore: VehicleStore): Result<Unit> {
        val slot = mutableSlots.value.firstOrNull { it.id == slotId }
            ?: return Result.failure(NoSuchElementException("Ô đỗ không tồn tại."))
        val vehicleId = slot.vehicleId ?: return Result.failure(IllegalStateException("Ô đang trống."))
        val vehicle = vehicleStore.vehicles.value.firstOrNull { it.id == vehicleId }
            ?: return Result.failure(NoSuchElementException("Không tìm thấy xe."))
        vehicleStore.update(vehicle.copy(currentSlotId = null, status = VehicleStatus.WAITING))
            .getOrElse { return Result.failure(it) }
        mutableSlots.value = mutableSlots.value.map { if (it.id == slotId) it.copy(vehicleId = null) else it }
        persist()
        return Result.success(Unit)
    }

    @Synchronized
    fun clearVehicle(vehicleId: String) {
        mutableSlots.value = mutableSlots.value.map { if (it.vehicleId == vehicleId) it.copy(vehicleId = null) else it }
        persist()
    }

    @Synchronized
    fun addRow(): Int {
        val next = (mutableSlots.value.maxOfOrNull { it.row } ?: 0) + 1
        mutableSlots.value = mutableSlots.value + makeRow(next)
        persist()
        return next
    }

    @Synchronized
    fun removeEmptyRow(row: Int): Result<Unit> {
        val items = mutableSlots.value.filter { it.row == row }
        if (items.isEmpty()) return Result.failure(NoSuchElementException("Không tìm thấy dãy."))
        if (items.any { it.vehicleId != null }) return Result.failure(IllegalStateException("Dãy còn xe, hãy di chuyển xe trước."))
        mutableSlots.value = mutableSlots.value.filterNot { it.row == row }
        persist()
        return Result.success(Unit)
    }

    @Synchronized
    fun addSlot(row: Int, lane: Int): YardSlot {
        val next = (mutableSlots.value.filter { it.row == row && it.lane == lane }.maxOfOrNull { it.position } ?: 0) + 1
        val slot = makeSlot(row, lane, next)
        mutableSlots.value = mutableSlots.value + slot
        persist()
        return slot
    }

    @Synchronized
    fun removeEmptySlot(slotId: String): Result<Unit> {
        val slot = mutableSlots.value.firstOrNull { it.id == slotId }
            ?: return Result.failure(NoSuchElementException("Không tìm thấy ô."))
        if (slot.vehicleId != null) return Result.failure(IllegalStateException("Ô đang có xe."))
        mutableSlots.value = mutableSlots.value.filterNot { it.id == slotId }
        persist()
        return Result.success(Unit)
    }

    @Synchronized
    fun replaceAll(replacement: List<YardSlot>): Result<Unit> {
        if (replacement.map { it.id }.distinct().size != replacement.size) return Result.failure(IllegalArgumentException("Sơ đồ bãi có ID ô trùng nhau."))
        val occupied = replacement.mapNotNull { it.vehicleId }
        if (occupied.distinct().size != occupied.size) return Result.failure(IllegalArgumentException("Một xe không thể chiếm nhiều ô."))
        mutableSlots.value = replacement
        persist()
        return Result.success(Unit)
    }

    private fun makeRow(row: Int): List<YardSlot> = (1..4).flatMap { lane -> (1..20).map { pos -> makeSlot(row, lane, pos) } }
    private fun makeSlot(row: Int, lane: Int, pos: Int) = YardSlot("$row-$lane-$pos", row, lane, pos, "$row${laneNames[lane - 1]}-$pos")

    private fun persist() {
        val arr = JSONArray()
        mutableSlots.value.forEach { s -> arr.put(JSONObject().put("id", s.id).put("row", s.row).put("lane", s.lane).put("position", s.position).put("label", s.label).put("active", s.active).put("vehicleId", s.vehicleId ?: JSONObject.NULL)) }
        prefs.edit().putString("slots", arr.toString()).apply()
    }

    private fun load(): List<YardSlot> = runCatching {
        val raw = prefs.getString("slots", null) ?: return (1..5).flatMap(::makeRow)
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            YardSlot(o.getString("id"), o.getInt("row"), o.getInt("lane"), o.getInt("position"), o.getString("label"), o.optBoolean("active", true), if (o.isNull("vehicleId")) null else o.optString("vehicleId"))
        }
    }.getOrElse { (1..5).flatMap(::makeRow) }
}
