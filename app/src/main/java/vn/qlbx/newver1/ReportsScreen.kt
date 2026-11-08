package vn.qlbx.newver1

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.qlbx.newver1.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale

@Composable
fun ReportsScreen(vehicles: List<Vehicle>, movements: List<VehicleMovement>, eventStore: SpecialEventStore) {
    val context = LocalContext.current
    val events by eventStore.events.collectAsState()
    var dateText by remember { mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date())) }
    var description by remember { mutableStateOf("") }
    var selectedEvents by remember { mutableStateOf(setOf<String>()) }
    var chartPeriod by remember { mutableStateOf("Ngày") }
    var status by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) status = runCatching {
            ReportExporter.exportPng(context.contentResolver, uri, vehicles, movements, events, selectedEvents, chartPeriod)
            "Đã xuất ảnh báo cáo PNG 1080px."
        }.getOrElse { "Xuất báo cáo thất bại: ${it.message}" }
    }
    val now = System.currentTimeMillis()
    fun dayBounds(offset: Int): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.DAY_OF_MONTH, -offset)
        val start = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        return start to calendar.timeInMillis
    }
    fun countEntries(start: Long, end: Long): Int {
        val logged = movements.count { it.action == "ENTRY" && it.occurredAt >= start && it.occurredAt < end }
        val imported = vehicles.count { v -> v.entryAt >= start && v.entryAt < end &&
            movements.none { it.vehicleId == v.id && it.action == "ENTRY" && it.occurredAt >= start && it.occurredAt < end } }
        return logged + imported
    }
    fun countExits(start: Long, end: Long): Int {
        val logged = movements.count { it.action == "EXIT" && it.occurredAt >= start && it.occurredAt < end }
        val imported = vehicles.count { v -> v.status == VehicleStatus.EXITED &&
            v.exitAt?.let { it >= start && it < end } == true &&
            movements.none { it.vehicleId == v.id && it.action == "EXIT" && it.occurredAt >= start && it.occurredAt < end } }
        return logged + imported
    }
    val chartBuckets: List<Triple<String, Long, Long>> = when (chartPeriod) {
        "Ngày" -> (6 downTo 0).map { offset ->
            val (start, end) = dayBounds(offset)
            Triple(SimpleDateFormat("dd/MM", Locale.US).format(Date(start)), start, end)
        }
        "Tuần" -> (3 downTo 0).map { offset ->
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
            val daysSinceMonday = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
            calendar.add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
            calendar.add(Calendar.WEEK_OF_YEAR, -offset)
            val start = calendar.timeInMillis
            calendar.add(Calendar.WEEK_OF_YEAR, 1)
            Triple("Tuần " + SimpleDateFormat("dd/MM", Locale.US).format(Date(start)), start, calendar.timeInMillis)
        }
        else -> (5 downTo 0).map { offset ->
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
            calendar.add(Calendar.MONTH, -offset)
            val start = calendar.timeInMillis
            calendar.add(Calendar.MONTH, 1)
            Triple(SimpleDateFormat("MMM yy", Locale("vi", "VN")).format(Date(start)), start, calendar.timeInMillis)
        }
    }
    val inside = vehicles.filter { it.status != VehicleStatus.EXITED }
    val ages = listOf(
        "0–10 ngày" to inside.count { (now - it.entryAt) / 86400000L <= 10 },
        "11–20 ngày" to inside.count { (now - it.entryAt) / 86400000L in 11..20 },
        "21–30 ngày" to inside.count { (now - it.entryAt) / 86400000L in 21..30 },
        "Trên 30 ngày" to inside.count { (now - it.entryAt) / 86400000L > 30 }
    )
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportMetric("Trong bãi", inside.size.toString(), Modifier.weight(1f))
                ReportMetric("Đã xuất", vehicles.count { it.status == VehicleStatus.EXITED }.toString(), Modifier.weight(1f))
                ReportMetric("Chờ xếp ô", vehicles.count { it.status == VehicleStatus.WAITING }.toString(), Modifier.weight(1f))
            }
        }
        item { Text("Thời gian lưu bãi", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        items(ages) { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.first, modifier = Modifier.width(120.dp), fontSize = 13.sp)
                Box(Modifier.weight(1f).height(12.dp).background(Color(0xFFE1E9F2), RoundedCornerShape(8.dp))) {
                    Box(Modifier.fillMaxWidth(if (inside.isEmpty()) 0f else item.second.toFloat() / inside.size).fillMaxHeight().background(Color(0xFF1769AA), RoundedCornerShape(8.dp)))
                }
                Text("  ${item.second}", fontWeight = FontWeight.Bold)
            }
        }
        item {
            Text("Biểu đồ nhập / xuất", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Ngày", "Tuần", "Tháng").forEachIndexed { index, label ->
                    SegmentedButton(chartPeriod == label, { chartPeriod = label }, shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(label) }
                }
            }
        }
        items(chartBuckets) { bucket ->
            val inCount = countEntries(bucket.second, bucket.third)
            val outCount = countExits(bucket.second, bucket.third)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(bucket.first, modifier = Modifier.width(82.dp), fontSize = 12.sp)
                Text("Nhập $inCount", color = Color(0xFF16834A), modifier = Modifier.weight(1f), fontSize = 12.sp)
                Text("Xuất $outCount", color = Color(0xFFE08A2E), modifier = Modifier.weight(1f), fontSize = 12.sp)
            }
        }
        item { Text("Nhật ký hoạt động gần đây", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        items(movements.take(100), key = { it.id }) { movement ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(movement.identifier + " • " + when (movement.action) { "ENTRY" -> "Nhập bãi"; "EXIT" -> "Xuất bãi"; "EDIT" -> "Chỉnh sửa"; "DELETE" -> "Xóa bản ghi"; else -> movement.action }, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(movement.note.ifBlank { movement.model }, color = Color(0xFF61738B), fontSize = 11.sp)
                }
                Text(SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date(movement.occurredAt)), fontSize = 10.sp, color = Color(0xFF61738B))
            }
        }
        item { Divider(); Text("Diễn biến đặc biệt", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        item {
            OutlinedTextField(dateText, { dateText = it }, Modifier.fillMaxWidth(), label = { Text("Ngày (dd/MM/yyyy)") }, singleLine = true)
            OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Nội dung sự kiện") }, minLines = 2)
            Button(onClick = {
                val date = runCatching { SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { isLenient = false }.parse(dateText)?.time }.getOrNull()
                status = if (date == null) "Ngày không hợp lệ." else eventStore.add(date, description).fold({ description = ""; "Đã lưu sự kiện." }, { it.message })
            }, modifier = Modifier.fillMaxWidth()) { Text("Lưu sự kiện") }
        }
        items(events, key = { it.id }) { event ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = event.id in selectedEvents, onCheckedChange = { checked ->
                    selectedEvents = when {
                        checked && selectedEvents.size < 5 -> selectedEvents + event.id
                        checked -> selectedEvents
                        else -> selectedEvents - event.id
                    }
                })
                Column(Modifier.weight(1f)) {
                    Text(SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(event.date)), fontSize = 12.sp, color = Color(0xFF61738B))
                    Text(event.description, fontSize = 14.sp)
                }
                TextButton(onClick = { eventStore.delete(event.id); selectedEvents = selectedEvents - event.id }) { Text("Xóa") }
            }
        }
        item {
            Text("Đã chọn ${selectedEvents.size}/5 sự kiện cho ảnh báo cáo.", fontSize = 12.sp, color = Color(0xFF61738B))
            Button(onClick = { export.launch("QLBX_BaoCao_1080.png") }, modifier = Modifier.fillMaxWidth()) { Text("XUẤT ẢNH BÁO CÁO 1080px") }
            if (status != null) Text(status!!, color = if (status!!.startsWith("Đã")) Color(0xFF16834A) else MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ReportMetric(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(10.dp)) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color(0xFF1769AA))
            Text(label, fontSize = 10.sp, color = Color(0xFF61738B))
        }
    }
}
