package vn.qlbx.newver1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Menu
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

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Brand,
            background = Canvas,
            surface = Color.White,
            onBackground = Ink,
            onSurface = Ink
        )
    ) {
        if (!signedIn) {
            LoginScreen(
                onOffline = { signedIn = true },
                onLogin = { email, password, done ->
                    val configured = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
                    if (!configured) {
                        done("Firebase chưa được cấu hình. Hãy thêm app/google-services.json.")
                    } else {
                        runCatching { FirebaseAuth.getInstance() }
                            .onSuccess { auth ->
                                auth.signInWithEmailAndPassword(email.trim(), password)
                                    .addOnSuccessListener {
                                        userEmail = it.user?.email.orEmpty()
                                        signedIn = true
                                        done(null)
                                    }
                                    .addOnFailureListener { done(it.localizedMessage ?: "Đăng nhập thất bại.") }
                            }
                            .onFailure { done(it.localizedMessage ?: "Không khởi tạo được Firebase.") }
                    }
                }
            )
        } else {
            Scaffold(
                containerColor = Canvas,
                bottomBar = {
                    NavigationBar(containerColor = Color.White) {
                        listOf(
                            Triple("Tổng quan", Icons.Default.Home, "overview"),
                            Triple("Nhập/Xuất", Icons.Default.DirectionsCar, "movement"),
                            Triple("Sơ đồ bãi", Icons.Default.LocalParking, "yard"),
                            Triple("Tìm xe", Icons.Default.Search, "search"),
                            Triple("Hệ thống", Icons.Default.Settings, "settings")
                        ).forEach { item ->
                            NavigationBarItem(
                                selected = selected == item.first,
                                onClick = { selected = item.first },
                                icon = { Icon(item.second, contentDescription = item.first) },
                                label = { Text(item.first, fontSize = 10.sp) }
                            )
                        }
                    }
                }
            ) { padding ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("QLBX ERP", color = Brand, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(selected, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
                            if (userEmail.isNotBlank()) Text(userEmail, color = Color(0xFF61738B), fontSize = 12.sp)
                        }
                        IconButton(onClick = { selected = "Hệ thống" }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Brand),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("BÃI XE CỦA BẠN", color = Color.White.copy(alpha = .78f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Quản lý vận hành trên một màn hình", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text("Theo dõi xe vào, xe ra và vị trí đỗ.", color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                        }
                    }
                    Text("Truy cập nhanh", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = { selected = "Nhập/Xuất" }, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(16.dp)) { Text("Nhập bãi") }
                        FilledTonalButton(onClick = { selected = "Nhập/Xuất" }, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(16.dp)) { Text("Xuất bãi") }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Đang khởi tạo hệ thống", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(6.dp))
                            Text("Các phân hệ quản lý xe, sơ đồ bãi, nhật ký, báo cáo và nhập/xuất Excel sẽ được hoàn thiện theo từng đợt kiểm thử.", color = Color(0xFF61738B), lineHeight = 21.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        runCatching { FirebaseAuth.getInstance().signOut() }
                        userEmail = ""
                        signedIn = false
                    }, modifier = Modifier.align(Alignment.End)) { Text("Đăng xuất") }
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(
    onOffline: () -> Unit,
    onLogin: (String, String, (String?) -> Unit) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    Surface(color = Canvas, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text("QLBX ERP", color = Brand, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Text("Đăng nhập hệ thống", color = Ink, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            Text("Quản lý bãi xe ngay trên điện thoại.", color = Color(0xFF61738B))
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(email, { email = it; message = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(password, { password = it; message = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Mật khẩu") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            if (message != null) {
                Spacer(Modifier.height(10.dp))
                Text(message!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        message = "Vui lòng nhập email và mật khẩu."
                    } else {
                        busy = true
                        onLogin(email, password) { error ->
                            busy = false
                            message = error
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Đăng nhập")
            }
            TextButton(onClick = onOffline, modifier = Modifier.fillMaxWidth()) { Text("Tiếp tục ngoại tuyến") }
        }
    }
}
