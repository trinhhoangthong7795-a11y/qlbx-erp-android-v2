package vn.qlbx.newver1.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale

object ReportExporter {
    fun exportPng(
        resolver: ContentResolver,
        uri: Uri,
        vehicles: List<Vehicle>,
        movements: List<VehicleMovement>,
        events: List<SpecialEvent>,
        selectedEventIds: Set<String>,
        period: String = "Ngày"
    ) {
        val width = 1080
        val height = 1500
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(244, 247, 251))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun text(value: String, x: Float, y: Float, size: Float, color: Int = android.graphics.Color.rgb(23, 43, 77), bold: Boolean = false) {
            paint.color = color; paint.textSize = size
            paint.typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
            canvas.drawText(value.take(90), x, y, paint)
        }
        val brand = android.graphics.Color.rgb(23, 105, 170)
        paint.color = brand
        canvas.drawRoundRect(40f, 35f, 1040f, 230f, 34f, 34f, paint)
        text("QLBX ERP  •  BÁO CÁO BÃI XE", 82f, 105f, 30f, android.graphics.Color.WHITE, true)
        text("Tổng hợp hoạt động và tình trạng xe", 82f, 158f, 24f, android.graphics.Color.WHITE)
        text("Ngày xuất: " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN")).format(Date()), 82f, 202f, 18f, android.graphics.Color.WHITE)

        val inside = vehicles.count { it.status != VehicleStatus.EXITED }
        val exited = vehicles.count { it.status == VehicleStatus.EXITED }
        val waiting = vehicles.count { it.status == VehicleStatus.WAITING }
        val now = System.currentTimeMillis()
        val ages = listOf(
            "0–10 ngày" to vehicles.count { it.status != VehicleStatus.EXITED && (now - it.entryAt) / 86400000L <= 10 },
            "11–20 ngày" to vehicles.count { it.status != VehicleStatus.EXITED && (now - it.entryAt) / 86400000L in 11..20 },
            "21–30 ngày" to vehicles.count { it.status != VehicleStatus.EXITED && (now - it.entryAt) / 86400000L in 21..30 },
            "Trên 30 ngày" to vehicles.count { it.status != VehicleStatus.EXITED && (now - it.entryAt) / 86400000L > 30 }
        )
        text("CHỈ SỐ HIỆN TẠI", 60f, 300f, 24f, brand, true)
        val metrics = listOf("Trong bãi" to inside, "Chờ xếp ô" to waiting, "Đã xuất" to exited)
        metrics.forEachIndexed { i, pair ->
            val x = 55f + i * 340f
            paint.color = android.graphics.Color.WHITE
            canvas.drawRoundRect(x, 330f, x + 310f, 470f, 22f, 22f, paint)
            text(pair.second.toString(), x + 24f, 405f, 48f, brand, true)
            text(pair.first, x + 24f, 445f, 20f)
        }
        text("THỜI GIAN LƯU BÃI", 60f, 545f, 24f, brand, true)
        ages.forEachIndexed { i, pair ->
            val y = 580f + i * 72f
            text(pair.first, 70f, y + 28f, 20f)
            paint.color = android.graphics.Color.rgb(222, 231, 241)
            canvas.drawRoundRect(300f, y, 900f, y + 38f, 12f, 12f, paint)
            paint.color = brand
            val bar = (pair.second.toFloat() / maxOf(inside, 1) * 600f).coerceIn(0f, 600f)
            canvas.drawRoundRect(300f, y, 300f + bar, y + 38f, 12f, 12f, paint)
            text(pair.second.toString(), 930f, y + 28f, 20f, brand, true)
        }
        text("DIỄN BIẾN NHẬP / XUẤT • $period", 60f, 900f, 24f, brand, true)
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
        val chartRanges: List<Triple<String, Long, Long>> = when (period) {
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
            "Tháng" -> (5 downTo 0).map { offset ->
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
                calendar.add(Calendar.MONTH, -offset)
                val start = calendar.timeInMillis
                calendar.add(Calendar.MONTH, 1)
                Triple(SimpleDateFormat("MMM yy", Locale("vi", "VN")).format(Date(start)), start, calendar.timeInMillis)
            }
            else -> (6 downTo 0).map { offset ->
                val (start, end) = dayBounds(offset)
                Triple(SimpleDateFormat("dd/MM", Locale.US).format(Date(start)), start, end)
            }
        }
        val days = chartRanges.map { bucket ->
            Triple(bucket.first, countEntries(bucket.second, bucket.third), countExits(bucket.second, bucket.third))
        }
        days.forEachIndexed { i, d ->
            val x = 85f + i * 140f
            val maxVal = maxOf(days.maxOf { maxOf(it.second, it.third) }, 1)
            val entryHeight = d.second.toFloat() / maxVal * 180f
            val exitHeight = d.third.toFloat() / maxVal * 180f
            paint.color = android.graphics.Color.rgb(31, 139, 89)
            canvas.drawRect(x, 1150f - entryHeight, x + 35f, 1150f, paint)
            paint.color = android.graphics.Color.rgb(225, 139, 45)
            canvas.drawRect(x + 42f, 1150f - exitHeight, x + 77f, 1150f, paint)
            text(d.first, x - 5f, 1185f, 16f)
        }
        text("Xanh: nhập bãi     Cam: xuất bãi", 70f, 1225f, 17f)
        text("DIỄN BIẾN ĐẶC BIỆT", 60f, 1290f, 24f, brand, true)
        val selected = events.filter { it.id in selectedEventIds }.take(5)
        if (selected.isEmpty()) text("Không có sự kiện được chọn.", 70f, 1335f, 18f)
        else selected.forEachIndexed { i, e ->
            val date = SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).format(Date(e.date))
            text("${date}: ${e.description}", 70f, 1335f + i * 30f, 17f)
        }
        val stream = resolver.openOutputStream(uri, "wt") ?: error("Không mở được tệp ảnh đích.")
        stream.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it).also { ok -> check(ok) { "Không thể mã hóa ảnh PNG." } } }
        bitmap.recycle()
    }
}
