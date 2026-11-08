package vn.qlbx.newver1.data

import android.content.ContentResolver
import android.net.Uri
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.floor
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Android-native XLSX/JSON file support. XLSX is a small OOXML workbook with
 * one Vehicles worksheet; it deliberately avoids desktop-only java.awt APIs.
 */
object VehicleFileManager {
    private val headers = listOf(
        "id", "identityType", "identifier", "model", "color", "department", "team",
        "entryAt", "entryPerson", "exitAt", "exitPerson", "status", "currentSlotId"
    )

    fun exportXlsx(resolver: ContentResolver, uri: Uri, vehicles: List<Vehicle>) {
        val rows = mutableListOf(headers)
        vehicles.forEach { v ->
            rows += listOf(
                v.id, v.identityType.name, v.plate ?: v.vinLast6.orEmpty(), v.model, v.color,
                v.department, v.team, v.entryAt.toString(), v.entryPerson,
                v.exitAt?.toString().orEmpty(), v.exitPerson, v.status.name, v.currentSlotId.orEmpty()
            )
        }
        val bytes = buildWorkbook(rows)
        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: error("Không mở được tệp đích để ghi.")
    }

    fun importXlsx(resolver: ContentResolver, uri: Uri): List<Vehicle> {
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Không mở được tệp đã chọn.")
        require(bytes.size <= 25 * 1024 * 1024) { "Tệp quá lớn (giới hạn 25 MB)." }
        val entries = unzip(bytes)
        val sheet = entries["xl/worksheets/sheet1.xml"] ?: error("Không tìm thấy trang tính Vehicles.")
        val shared = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val rows = parseSheet(sheet, shared)
        require(rows.isNotEmpty()) { "Trang tính không có dữ liệu." }
        val header = rows.first().map { it.trim() }
        val indexes = headers.associateWith { name -> header.indexOf(name) }
        val missing = headers.filter { indexes[it] == -1 }
        require(missing.isEmpty()) { "Thiếu cột bắt buộc: ${missing.joinToString()}" }
        val imported = rows.drop(1).filter { row -> row.any { it.isNotBlank() } }.mapIndexed { i, row ->
            fun cell(name: String): String = row.getOrNull(indexes[name] ?: -1).orEmpty()
            val type = runCatching { IdentityType.valueOf(cell("identityType")) }
                .getOrElse { error("Dòng ${i + 2}: identityType không hợp lệ.") }
            val identifier = cell("identifier")
            val draft = VehicleDraft(type, identifier, cell("model"), cell("color"), cell("department"), cell("team"), cell("entryPerson"))
            val validated = VehicleRules.validate(draft)
            val base = (validated as? VehicleValidation.Valid)?.vehicle
                ?: error("Dòng ${i + 2}: ${(validated as VehicleValidation.Invalid).message}")
            val status = runCatching { VehicleStatus.valueOf(cell("status")) }
                .getOrElse { error("Dòng ${i + 2}: status không hợp lệ.") }
            val entryRaw = cell("entryAt")
            val entryAt = if (entryRaw.isBlank()) base.entryAt else parseTimestamp(entryRaw)
                ?: error("Dòng ${i + 2}: thời điểm nhập không hợp lệ.")
            val exitRaw = cell("exitAt")
            val exitAt = if (exitRaw.isBlank()) null else parseTimestamp(exitRaw)
                ?: error("Dòng ${i + 2}: thời điểm xuất không hợp lệ.")
            base.copy(
                id = cell("id").ifBlank { base.id },
                entryAt = entryAt,
                exitAt = exitAt,
                exitPerson = cell("exitPerson"),
                status = status,
                currentSlotId = cell("currentSlotId").ifBlank { null }
            )
        }
        val duplicate = imported.groupBy {
            if (it.identityType == IdentityType.PLATE) "P:${it.plate}" else "V:${it.vinLast6}"
        }.filterValues { it.size > 1 }
        require(duplicate.isEmpty()) { "Tệp có mã xe trùng nhau. Hãy xử lý trước khi nhập." }
        return imported
    }

    fun exportJson(resolver: ContentResolver, uri: Uri, vehicles: List<Vehicle>) {
        val root = JSONObject().put("schemaVersion", 1).put("exportedAt", System.currentTimeMillis())
        val array = JSONArray()
        vehicles.forEach { v ->
            array.put(JSONObject().apply {
                put("id", v.id); put("identityType", v.identityType.name)
                put("plate", v.plate ?: JSONObject.NULL); put("vinLast6", v.vinLast6 ?: JSONObject.NULL)
                put("model", v.model); put("color", v.color); put("department", v.department); put("team", v.team)
                put("entryAt", v.entryAt); put("entryPerson", v.entryPerson)
                put("exitAt", v.exitAt ?: JSONObject.NULL); put("exitPerson", v.exitPerson)
                put("status", v.status.name); put("currentSlotId", v.currentSlotId ?: JSONObject.NULL)
                put("createdAt", v.createdAt); put("updatedAt", v.updatedAt)
            })
        }
        root.put("vehicles", array)
        resolver.openOutputStream(uri, "wt")?.use { it.write(root.toString(2).toByteArray(Charsets.UTF_8)) }
            ?: error("Không mở được tệp đích để ghi.")
    }

    fun importJson(resolver: ContentResolver, uri: Uri): List<Vehicle> {
        val raw = resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Không mở được tệp đã chọn.")
        require(raw.toByteArray().size <= 25 * 1024 * 1024) { "Tệp quá lớn (giới hạn 25 MB)." }
        val root = JSONObject(raw)
        require(root.optInt("schemaVersion") == 1) { "Phiên bản sao lưu không được hỗ trợ." }
        val array = root.getJSONArray("vehicles")
        val result = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val type = IdentityType.valueOf(o.getString("identityType"))
            val id = if (type == IdentityType.PLATE) o.optString("plate") else o.optString("vinLast6")
            val valid = VehicleRules.validate(VehicleDraft(type, id, o.optString("model"), o.optString("color"), o.optString("department"), o.optString("team"), o.optString("entryPerson")))
            val v = (valid as? VehicleValidation.Valid)?.vehicle ?: error("Dữ liệu xe thứ ${i + 1} không hợp lệ.")
            v.copy(
                id = o.optString("id").ifBlank { v.id }, entryAt = o.optLong("entryAt", v.entryAt),
                exitAt = if (o.isNull("exitAt")) null else o.optLong("exitAt"),
                exitPerson = o.optString("exitPerson"), status = VehicleStatus.valueOf(o.optString("status", "INSIDE")),
                currentSlotId = if (o.isNull("currentSlotId")) null else o.optString("currentSlotId"),
                createdAt = o.optLong("createdAt", v.createdAt), updatedAt = o.optLong("updatedAt", v.updatedAt)
            )
        }
        require(result.map { it.id }.distinct().size == result.size) { "Bản sao lưu có ID xe bị trùng." }
        return result
    }

    private fun parseTimestamp(raw: String): Long? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        val numeric = value.toDoubleOrNull()
        if (numeric != null) {
            if (numeric >= 100_000_000_000.0) return numeric.toLong()
            if (numeric >= 1_000_000_000.0) return (numeric * 1000.0).toLong()
            if (numeric in 20_000.0..100_000.0) {
                val wholeDays = floor(numeric).toLong()
                val fractionalDay = numeric - wholeDays
                val localDate = LocalDate.of(1899, 12, 30).plusDays(wholeDays)
                return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() +
                    (fractionalDay * 86_400_000.0).toLong()
            }
        }
        val patterns = listOf(
            "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm", "dd/MM/yyyy",
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd",
            "yyyy-MM-dd'T'HH:mm:ss", "MM/dd/yyyy HH:mm:ss", "MM/dd/yyyy"
        )
        for (pattern in patterns) {
            val formatter = SimpleDateFormat(pattern, Locale("vi", "VN")).apply { isLenient = false }
            val parsed = runCatching { formatter.parse(value)?.time }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val out = ByteArrayOutputStream()
                    zip.copyTo(out)
                    result[entry.name] = out.toByteArray()
                }
                zip.closeEntry()
            }
        }
        return result
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xml), "UTF-8")
        val values = mutableListOf<String>()
        var current = StringBuilder()
        var inSi = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> if (parser.name == "si") { current = StringBuilder(); inSi = true }
                XmlPullParser.TEXT -> if (inSi) current.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.name == "si") { values += current.toString(); inSi = false }
            }
            event = parser.next()
        }
        return values
    }

    private fun parseSheet(xml: ByteArray, shared: List<String>): List<List<String>> {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xml), "UTF-8")
        val rows = mutableListOf<List<String>>()
        var row: MutableMap<Int, String>? = null
        var col = 0
        var cellType = ""
        var value = StringBuilder()
        var inValue = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> row = mutableMapOf()
                    "c" -> {
                        val ref = parser.getAttributeValue(null, "r").orEmpty()
                        col = columnIndex(ref)
                        cellType = parser.getAttributeValue(null, "t").orEmpty()
                        value = StringBuilder()
                    }
                    "v", "t" -> inValue = true
                }
                XmlPullParser.TEXT -> if (inValue) value.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val raw = value.toString()
                        row?.set(col, if (cellType == "s") shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty() else raw)
                    }
                    "row" -> {
                        val current = row ?: mutableMapOf()
                        rows += (0..(current.keys.maxOrNull() ?: -1)).map { current[it].orEmpty() }
                        row = null
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun columnIndex(ref: String): Int {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        var n = 0
        letters.forEach { n = n * 26 + (it - 'A' + 1) }
        return n - 1
    }

    private fun buildWorkbook(rows: List<List<String>>): ByteArray {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
        fun colName(index: Int): String {
            var n = index + 1
            var out = ""
            while (n > 0) { val r = (n - 1) % 26; out = ('A' + r) + out; n = (n - 1) / 26 }
            return out
        }
        val sheet = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            rows.forEachIndexed { ri, cells ->
                append("<row r=\"${ri + 1}\">")
                cells.forEachIndexed { ci, cell ->
                    append("<c r=\"${colName(ci)}${ri + 1}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(cell)}</t></is></c>")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }
        val parts = linkedMapOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Vehicles" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to sheet
        )
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            parts.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
