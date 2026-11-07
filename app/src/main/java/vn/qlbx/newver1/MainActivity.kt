package vn.qlbx.newver1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    var selected by remember { mutableStateOf("Tổng quan") }
    var signedIn by remember { mutableStateOf(false) }
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
            LoginScreen(onOffline = { signedIn = true }, onLogin = { /* Firebase Auth wiring follows config */ })
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(top = 22.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("QLBX ERP", color = Brand, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(selected, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
                            }
                            IconButton(onClick = { selected = "Hệ thống" }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        }
                    }
                    item { DashboardHero() }
                    item {
                        Text("Truy cập nhanh", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickAction("Nhập bãi", Modifier.weight(1f)) { selected = "Nhập/Xuất" }
                            QuickAction("Xuất bãi", Modifier.weight(1f)) { selected = "Nhập/Xuất" }
                        }
                    }
                    item {
                        Text("Chức năng", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Màn hình nền tảng đã được tạo. Các nghiệp vụ xe, sơ đồ, nhật ký, báo cáo và đồng bộ sẽ được triển khai theo đặc tả QLBX.", color = Color(0xFF61738B), lineHeight = 22.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardHero() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Brand),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("BÃI XE CỦA BẠN", color = Color.White.copy(alpha = .78f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("Quản lý vận hành
trên một màn hình", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp)
            Text("Theo dõi xe vào, xe ra và vị trí đỗ.", color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun QuickAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(16.dp)
    ) { Text(label, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun LoginScreen(onOffline: () -> Unit, onLogin: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Surface(color = Canvas, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text("QLBX ERP", color = Brand, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Text("Đăng nhập hệ thống", color = Ink, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            Text("Quản lý bãi xe mọi lúc, ngay trên điện thoại.", color = Color(0xFF61738B))
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(email, { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Mật khẩu") }, singleLine = true)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Đăng nhập") }
            TextButton(onClick = onOffline, modifier = Modifier.fillMaxWidth()) { Text("Tiếp tục ngoại tuyến") }
        }
    }
}
