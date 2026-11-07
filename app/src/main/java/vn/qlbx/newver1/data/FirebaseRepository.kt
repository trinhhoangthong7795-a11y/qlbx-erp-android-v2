package vn.qlbx.newver1.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

data class UserAccess(val uid: String, val displayName: String, val role: String, val active: Boolean)

class FirebaseRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun userRef(uid: String) = db.collection("users").document(uid)
    private fun vehicles() = db.collection("yards").document("main").collection("vehicles")
    private fun identifiers() = db.collection("yards").document("main").collection("activeIdentifiers")
    private fun movements() = db.collection("yards").document("main").collection("movements")
    private fun activeIdentifiers() = db.collection("yards").document("main").collection("activeIdentifiers")
    private fun slots() = db.collection("yards").document("main").collection("slots")
    private fun events() = db.collection("yards").document("main").collection("events")
    private fun logs() = db.collection("yards").document("main").collection("logs")

    suspend fun currentAccess(): UserAccess {
        val user = auth.currentUser ?: error("Bạn chưa đăng nhập.")
        val snap = userRef(user.uid).get().await()
        require(snap.exists()) { "Tài khoản chưa được cấp hồ sơ quyền trong hệ thống." }
        val active = snap.getBoolean("active") ?: false
        require(active) { "Tài khoản đã bị khóa. Liên hệ quản trị viên." }
        val role = snap.getString("role") ?: error("Hồ sơ chưa được gán vai trò.")
        require(role in setOf("super_admin", "admin", "operator", "viewer")) { "Vai trò không hợp lệ." }
        return UserAccess(user.uid, snap.getString("displayName") ?: user.email.orEmpty(), role, active)
    }

    suspend fun syncLocalVehicles(local: List<Vehicle>): Int {
        val access = currentAccess()
        require(access.role in setOf("super_admin", "admin", "operator")) { "Tài khoản chỉ có quyền xem, không thể đồng bộ thay đổi." }
        var synced = 0
        local.forEach { vehicle ->
            val identifierKey = identifierKey(vehicle)
            val vehicleRef = vehicles().document(vehicle.id)
            val indexRef = identifiers().document(identifierKey)
            db.runTransaction { tx ->
                val existingIndex = tx.get(indexRef)
                if (vehicle.status != VehicleStatus.EXITED) {
                    val ownerId = existingIndex.getString("vehicleId")
                    require(ownerId == null || ownerId == vehicle.id) { "Trùng biển số/VIN đang hoạt động: ${vehicle.plate ?: vehicle.vinLast6}" }
                    tx.set(indexRef, mapOf("vehicleId" to vehicle.id, "identityType" to vehicle.identityType.name, "updatedAt" to FieldValue.serverTimestamp()))
                } else if (existingIndex.exists() && existingIndex.getString("vehicleId") == vehicle.id) {
                    tx.delete(indexRef)
                }
                tx.set(vehicleRef, vehicle.toMap(access.uid).plus("updatedAt" to FieldValue.serverTimestamp()))
                tx.set(logs().document(), mapOf(
                    "entityType" to "vehicle", "entityId" to vehicle.id,
                    "action" to "SYNC_UPSERT", "actorUid" to access.uid,
                    "createdAt" to FieldValue.serverTimestamp()
                ))
                null
            }.await()
            synced++
        }
        return synced
    }

    suspend fun downloadVehicles(): List<Vehicle> {
        currentAccess()
        val snapshot = vehicles().get().await()
        return snapshot.documents.mapNotNull { doc ->
            runCatching {
                val m = doc.data ?: return@runCatching null
                val type = IdentityType.valueOf(m["identityType"] as? String ?: return@runCatching null)
                Vehicle(
                    id = doc.id, identityType = type,
                    plate = m["plate"] as? String, vinLast6 = m["vinLast6"] as? String,
                    model = m["model"] as? String ?: "", color = m["color"] as? String ?: "",
                    department = m["department"] as? String ?: "", team = m["team"] as? String ?: "",
                    entryAt = (m["entryAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    entryPerson = m["entryPerson"] as? String ?: "",
                    exitAt = (m["exitAt"] as? Number)?.toLong(),
                    exitPerson = m["exitPerson"] as? String ?: "",
                    status = VehicleStatus.valueOf(m["status"] as? String ?: "INSIDE"),
                    currentSlotId = m["currentSlotId"] as? String,
                    createdAt = (m["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                )
            }.getOrNull()
        }.sortedByDescending { it.entryAt }
    }

    suspend fun syncAll(backup: AppBackup): Int {
        val access = currentAccess()
        require(access.role in setOf("super_admin", "admin")) { "Chỉ quản trị viên mới được đồng bộ sơ đồ và sự kiện." }
        var count = syncLocalVehicles(backup.vehicles)
        backup.movements.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { m ->
                batch.set(movements().document(m.id), mapOf(
                    "vehicleId" to m.vehicleId, "action" to m.action, "occurredAt" to m.occurredAt,
                    "identifier" to m.identifier, "model" to m.model, "person" to m.person,
                    "note" to m.note, "actorUid" to access.uid
                ))
            }
            batch.commit().await()
            count += chunk.size
        }
        backup.slots.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { s ->
                batch.set(slots().document(s.id), mapOf(
                    "row" to s.row, "lane" to s.lane, "position" to s.position, "label" to s.label,
                    "active" to s.active, "occupiedVehicleId" to s.vehicleId
                ))
            }
            batch.commit().await()
            count += chunk.size
        }
        backup.events.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { e ->
                batch.set(events().document(e.id), mapOf(
                    "eventDate" to e.date, "description" to e.description,
                    "createdBy" to access.uid, "createdAt" to FieldValue.serverTimestamp()
                ))
            }
            batch.commit().await()
            count += chunk.size
        }
        return count
    }

    suspend fun downloadAll(): AppBackup {
        currentAccess()
        val vehicleList = downloadVehicles()
        val movementDocs = movements().get().await().documents
        val movementList = movementDocs.mapNotNull { doc ->
            runCatching {
                val m = doc.data ?: return@runCatching null
                val time = when (val raw = m["occurredAt"]) {
                    is com.google.firebase.Timestamp -> raw.toDate().time
                    is Number -> raw.toLong()
                    else -> 0L
                }
                VehicleMovement(
                    id = doc.id, vehicleId = m["vehicleId"] as? String ?: return@runCatching null,
                    action = m["action"] as? String ?: "UNKNOWN", occurredAt = time,
                    identifier = m["identifier"] as? String ?: "", model = m["model"] as? String ?: "",
                    person = m["person"] as? String ?: "", note = m["note"] as? String ?: ""
                )
            }.getOrNull()
        }.sortedByDescending { it.occurredAt }
        val slotDocs = slots().get().await().documents
        val slotList = slotDocs.mapNotNull { doc ->
            runCatching {
                val m = doc.data ?: return@runCatching null
                YardSlot(doc.id, (m["row"] as Number).toInt(), (m["lane"] as Number).toInt(),
                    (m["position"] as Number).toInt(), m["label"] as? String ?: doc.id,
                    m["active"] as? Boolean ?: true, m["occupiedVehicleId"] as? String)
            }.getOrNull()
        }
        val eventDocs = events().get().await().documents
        val eventList = eventDocs.mapNotNull { doc ->
            runCatching {
                val m = doc.data ?: return@runCatching null
                SpecialEvent(doc.id, (m["eventDate"] as Number).toLong(), m["description"] as? String ?: "")
            }.getOrNull()
        }.sortedByDescending { it.date }
        return AppBackup(vehicleList, movementList, slotList, eventList)
    }

    suspend fun recordMovement(vehicle: Vehicle, action: String, note: String = "") {
        val access = currentAccess()
        require(access.role in setOf("super_admin", "admin", "operator")) { "Bạn không có quyền ghi nhật ký." }
        movements().add(mapOf(
            "vehicleId" to vehicle.id, "action" to action, "occurredAt" to FieldValue.serverTimestamp(),
            "actorUid" to access.uid, "snapshot" to vehicle.toMap(access.uid), "note" to note
        )).await()
    }

    private fun identifierKey(vehicle: Vehicle): String {
        val raw = if (vehicle.identityType == IdentityType.PLATE) "PLATE:${vehicle.plate.orEmpty().uppercase()}" else "VIN6:${vehicle.vinLast6.orEmpty()}"
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun Vehicle.toMap(uid: String): Map<String, Any?> = mapOf(
        "identityType" to identityType.name, "plate" to plate, "vinLast6" to vinLast6,
        "model" to model, "color" to color, "department" to department, "team" to team,
        "entryAt" to entryAt, "entryPerson" to entryPerson, "exitAt" to exitAt,
        "exitPerson" to exitPerson, "status" to status.name, "currentSlotId" to currentSlotId,
        "createdAt" to createdAt, "updatedAt" to updatedAt, "lastActorUid" to uid
    )
}
