package vn.qlbx.newver1

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.qlbx.newver1.data.*

@Composable
fun YardScreen(yard: YardStore, vehicleStore: VehicleStore, vehicles: List<Vehicle>) {
    val slots by yard.slots.collectAsState()
    var selectedSlot by remember { mutableStateOf<YardSlot?>(null) }
    var showWaiting by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sơ đồ bãi • ${slots.count { it.vehicleId != null }} xe có vị trí", modifier = Modifier.weight(1f), fontSize = 12.sp)
            TextButton(onClick = { yard.addRow() }) { Text("+ Thêm dãy") }
        }
        if (feedback != null) Text(feedback!!, color = Color(0xFF16834A), fontSize = 12.sp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            slots.groupBy { it.row }.toSortedMap().forEach { (rowNo, rowSlots) ->
                item(key = "row-$rowNo") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("DÃY $rowNo", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                val result = yard.removeEmptyRow(rowNo)
                                feedback = result.fold({ "Đã xóa dãy trống." }, { it.message ?: "Không thể xóa dãy." })
                            }) { Text("Xóa dãy") }
                        }
                        rowSlots.groupBy { it.lane }.toSortedMap().forEach { (lane, laneSlots) ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Làn $lane", modifier = Modifier.weight(1f), fontSize = 11.sp, color = Color(0xFF61738B))
                                TextButton(onClick = { yard.addSlot(rowNo, lane) }) { Text("+ Thêm ô") }
                            }
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                laneSlots.sortedBy { it.position }.forEach { slot ->
                                    val vehicle = vehicles.firstOrNull { it.id == slot.vehicleId }
                                    OutlinedButton(
                                        onClick = { selectedSlot = slot; showWaiting = slot.vehicleId == null },
                                        modifier = Modifier.width(82.dp).height(62.dp),
                                        contentPadding = PaddingValues(4.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (vehicle == null) Color.White else if (vehicle.identityType == IdentityType.VIN6) Color(0xFFE6F3FF) else Color(0xFFE9F7EF)
                                        )
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                            Text(vehicle?.let { it.plate ?: it.vinLast6.orEmpty() } ?: slot.label, fontSize = 10.sp, maxLines = 1)
                                            Text(if (vehicle == null) "Trống" else if (vehicle.identityType == IdentityType.VIN6) "VIN" else "Biển số", fontSize = 9.sp, color = Color(0xFF61738B))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showWaiting && selectedSlot != null) {
        val waiting = vehicles.filter { it.status != VehicleStatus.EXITED }
        AlertDialog(
            onDismissRequest = { showWaiting = false; selectedSlot = null },
            title = { Text("Chọn xe vào ô ${selectedSlot!!.label}") },
            text = {
                if (waiting.isEmpty()) Text("Không có xe chờ xếp vị trí.")
                else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(waiting, key = { it.id }) { v ->
                        TextButton(onClick = {
                            val result = yard.assign(selectedSlot!!.id, v.id, vehicleStore)
                            feedback = result.fold({ "Đã xếp ${v.plate ?: v.vinLast6} vào ${selectedSlot!!.label}." }, { it.message ?: "Không thể xếp xe." })
                            showWaiting = false; selectedSlot = null
                        }, modifier = Modifier.fillMaxWidth()) { Text("${v.plate ?: v.vinLast6}  •  ${v.model}") }
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        val result = yard.removeEmptySlot(selectedSlot!!.id)
                        feedback = result.fold({ "Đã xóa ô trống." }, { it.message ?: "Không thể xóa ô." })
                        showWaiting = false; selectedSlot = null
                    }) { Text("Xóa ô trống") }
                    TextButton(onClick = { showWaiting = false; selectedSlot = null }) { Text("Đóng") }
                }
            }
        )
    } else if (selectedSlot != null) {
        val slot = selectedSlot!!
        val vehicle = vehicles.firstOrNull { it.id == slot.vehicleId }
        AlertDialog(
            onDismissRequest = { selectedSlot = null },
            title = { Text(slot.label) },
            text = { Text(vehicle?.let { "${it.plate ?: it.vinLast6} • ${it.model.ifBlank { "Chưa có dòng xe" }}" } ?: "Ô trống") },
            confirmButton = {
                TextButton(onClick = {
                    val result = yard.removeToWaiting(slot.id, vehicleStore)
                    feedback = result.fold({ "Đã chuyển xe về danh sách chờ." }, { it.message ?: "Không thể chuyển xe." })
                    selectedSlot = null
                }, enabled = vehicle != null) { Text("Chuyển về chờ") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val result = vehicle?.let { vehicleStore.exit(it.id) }
                        if (result?.isSuccess == true) yard.clearVehicle(vehicle!!.id)
                        feedback = result?.fold({ "Đã xuất bãi: " + (it.plate ?: it.vinLast6) }, { it.message ?: "Không thể xuất xe." })
                        selectedSlot = null
                    }, enabled = vehicle != null) { Text("Xuất bãi") }
                    TextButton(onClick = { selectedSlot = null }) { Text("Đóng") }
                }
            }
        )
    }
}
