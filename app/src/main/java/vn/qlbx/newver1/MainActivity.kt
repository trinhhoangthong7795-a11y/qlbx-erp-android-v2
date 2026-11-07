package vn.qlbx.newver1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import vn.qlbx.newver1.data.*

private val Brand = Color(0xFF1769AA)
private val Canvas = Color(0xFFF4F7FB)
private val Ink = Color(0xFF172B4D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { QlbxApp() }
    }
}

@Composable
private fun QlbxApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { VehicleStore(context) }
    val vehicles by store.vehicles.collectAsState()
    var selected by remember { mutableStateOf("Tổng quan") }
    var signedIn by remember { mutableStateOf(false) }
    var userEmail by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        signedIn = runCatching {
            FirebaseApp.getApps(context).isNotEmpty() &&
                FirebaseAuth.getInstance().currentUser != null
        }.getOrDefault(false)
        if (signedIn) userEmail = runCatching { FirebaseAuth.getInstance().currentUser?.email.orEmpty() }.getOrDefault("")
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Brand, background = Canvas, surface = Color.White, onBackground = Ink, onSurface = Ink)) {
        if (!signedIn) {
            LoginScreen(
                onOffline = { signedIn = true },
                onLogin = { email, password, done ->
                    val configured = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
                    if (!configured) done("Chưa có google-services.json trong app. Hãy cấu hình Firebase Android trước khi đăng nhập.")
                    else runCatching { FirebaseAuth.getInstance() }
                        .onSuccess { auth ->
                            auth.signInWithEmailAndPassword(email.trim(), password)
                                .addOnSuccessListener { userEmail = it.user?.email.orEmpty(); signedIn = true; done(null) }
                                .addOnFailureListener { done(it.localizedMessage ?: "Đăng nhập thất bại.") }
                        }.onFailure { done(it.localizedMessage ?: "Không khởi tạo được Firebase.") }
                }
            )
        } else {
            val nav = listOf(
                Triple("Tổng quan", Icons.Default.Home, "overview"),
                Triple("Nhập/Xuất", Icons.Default.DirectionsCar, "movement"),
                Triple("Sơ đồ bãi", Icons.Default.LocalParking, "yard"),
                Triple("Tìm xe", Icons.Default.Search, "search"),
                Triple("Hệ thống", Icons.Default.Settings, "settings")
            )
            Scaffold(
                containerColor = Canvas,
                bottomBar = {
                    NavigationBar(containerColor = Color.White) {
                        nav.forEach { item ->
                            NavigationBarItem(selected == item.first, { selected = item.first }, icon = { Icon(item.second, item.first) }, label = { Text(item.first, fontSize = 10.sp) })
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Text("QLBX ERP", color = Brand, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(selected, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
                    if (userEmail.isNotBlank()) Text(userEmail, color = Color(0xFF61738B), fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    when (selected) {
                        "Nhập/Xuất" -> MovementScreen(store, vehicles)
                        "Tìm xe" -> VehicleSearchScreen(store, vehicles)
                        "Sơ đồ bãi" -> YardPlaceholder(vehicles)
                        "Hệ thống" -> SettingsScreen(userEmail) {
                            runCatching { FirebaseAuth.getInstance().signOut() }
                            userEmail = ""; signedIn = false
                        }
                        else -> OverviewScreen(vehicles, onEntry = { selected = "Nhập/Xuất" }, onSearch = { selected = "Tìm xe" })
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewScreen(vehicles: List<Vehicle>, onEntry: () -> Unit, onSearch: () -> Unit) {
    val inside = vehicles.count { it.status != VehicleStatus.EXITED }
    val exited = vehicles.count { it.status == VehicleStatus.EXITED }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Brand), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("BÃI XE CỦA BẠN", color = Color.White.copy(alpha = .8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("Vận hành bãi xe", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("Dữ liệu lưu trên thiết bị này.", color = Color.White.copy(alpha = .85f))
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard("Đang trong bãi", inside.toString(), Modifier.weight(1f))
        MetricCard("Đã xuất", exited.toString(), Modifier.weight(1f))
    }
    Spacer(Modifier.height(18.dp))
    Text("Thao tác nhanh", fontWeight = FontWeight.Bold, fontSize = 18.sp)
    Spacer(Modifier.height(8.dp))
    Button(onClick = onEntry, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("＋  Nhập bãi / Xuất bãi") }
    OutlinedButton(onClick = onSearch, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Tìm kiếm và quản lý xe") }
    Spacer(Modifier.height(16.dp))
    Text("Xe mới cập nhật", fontWeight = FontWeight.Bold, fontSize = 17.sp)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(vehicles.take(5), key = { it.id }) { VehicleRow(it) }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(value, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Brand)
            Text(label, fontSize = 12.sp, color = Color(0xFF61738B))
        }
    }
}

@Composable
private fun MovementScreen(store: VehicleStore, vehicles: List<Vehicle>) {
    var mode by remember { mutableStateOf(IdentityType.PLATE) }
    var identifier by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var person by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var showExited by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        Text("NHẬP BÃI", fontWeight = FontWeight.Bold, color = Brand)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(mode == IdentityType.PLATE, { mode = IdentityType.PLATE; identifier = ""; message = null }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Có biển số") }
            SegmentedButton(mode == IdentityType.VIN6, { mode = IdentityType.VIN6; identifier = ""; message = null }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("VIN 6 số") }
        }
        OutlinedTextField(identifier, { identifier = it; message = null }, modifier = Modifier.fillMaxWidth(), label = { Text(if (mode == IdentityType.PLATE) "Biển số xe *" else "6 số cuối VIN *") }, singleLine = true)
        OutlinedTextField(model, { model = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Dòng xe (không bắt buộc)") }, singleLine = true)
        OutlinedTextField(person, { person = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Người nhập bãi (không bắt buộc)") }, singleLine = true)
        Button(onClick = {
            val result = store.add(VehicleDraft(mode, identifier, model = model, entryPerson = person))
            message = result.fold({ "Đã nhập bãi: " + (it.plate ?: it.vinLast6) }, { it.message ?: "Không thể lưu xe." })
            if (result.isSuccess) { identifier = ""; model = ""; person = "" }
        }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("XÁC NHẬN NHẬP BÃI") }
        if (message != null) Text(message!!, color = if (message!!.startsWith("Đã")) Color(0xFF16834A) else MaterialTheme.colorScheme.error)
        Divider()
        Text("XE ĐANG TRONG BÃI", fontWeight = FontWeight.Bold)
        OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Lọc theo biển số / VIN / dòng xe") }, singleLine = true)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(store.search(search).filter { it.status != VehicleStatus.EXITED }, key = { it.id }) { v ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(v.plate ?: v.vinLast6.orEmpty(), fontWeight = FontWeight.Bold)
                            Text(v.model.ifBlank { if (v.identityType == IdentityType.PLATE) "Xe có biển số" else "Xe VIN 6 số" }, color = Color(0xFF61738B), fontSize = 12.sp)
                        }
                        TextButton(onClick = {
                            val r = store.exit(v.id)
                            message = r.fold({ "Đã xuất bãi: " + (it.plate ?: it.vinLast6) }, { it.message })
                        }) { Text("Xuất bãi") }
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleSearchScreen(store: VehicleStore, vehicles: List<Vehicle>) {
    var query by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Nhập biển số, VIN 6 số hoặc dòng xe") }, singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lịch sử xe", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Chỉ xe trong bãi" else "Tất cả") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(store.search(query).filter { showAll || it.status != VehicleStatus.EXITED }, key = { it.id }) { VehicleRow(it) }
        }
    }
}

@Composable
private fun VehicleRow(v: Vehicle) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(v.plate ?: v.vinLast6.orEmpty(), fontWeight = FontWeight.Bold)
                Text(v.model.ifBlank { "Chưa cập nhật dòng xe" }, color = Color(0xFF61738B), fontSize = 12.sp)
            }
            Text(if (v.status == VehicleStatus.EXITED) "Đã xuất" else "Trong bãi", color = if (v.status == VehicleStatus.EXITED) Color(0xFF61738B) else Color(0xFF16834A), fontSize = 12.sp)
        }
    }
}

@Composable
private fun YardPlaceholder(vehicles: List<Vehicle>) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sơ đồ bãi đang được xây dựng", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("Dữ liệu xe hiện có: ${vehicles.size}. Bản này chưa triển khai gán ô, di chuyển xe hoặc sơ đồ 5 dãy × 4 × 20 ô.")
        }
    }
}

@Composable
private fun SettingsScreen(email: String, onLogout: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tài khoản", fontWeight = FontWeight.Bold)
        Text(email.ifBlank { "Chế độ ngoại tuyến" })
        Text("Lưu ý: đồng bộ Firebase, phân quyền, sao lưu và Excel chưa được bật trong bản thử nghiệm này.", color = Color(0xFF61738B))
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Đăng xuất / Thoát phiên") }
    }
}

@Composable
private fun LoginScreen(onOffline: () -> Unit, onLogin: (String, String, (String?) -> Unit) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    Surface(color = Canvas, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("QLBX ERP", color = Brand, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Text("Đăng nhập hệ thống", color = Ink, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            Text("Quản lý bãi xe trên điện thoại.", color = Color(0xFF61738B))
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(email, { email = it; message = null }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(password, { password = it; message = null }, Modifier.fillMaxWidth(), label = { Text("Mật khẩu") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            if (message != null) Text(message!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            Button(onClick = {
                if (email.isBlank() || password.isBlank()) message = "Vui lòng nhập email và mật khẩu."
                else { busy = true; onLogin(email, password) { error -> busy = false; message = error } }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Đăng nhập")
            }
            TextButton(onClick = onOffline, modifier = Modifier.fillMaxWidth()) { Text("Tiếp tục ngoại tuyến (bản thử nghiệm)") }
        }
    }
}
