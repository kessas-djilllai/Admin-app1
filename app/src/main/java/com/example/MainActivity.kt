package com.example

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.ui.window.Dialog
import com.example.admin.*
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import java.text.SimpleDateFormat
import java.util.*
import android.util.Base64
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.common.MediaItem as Media3MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize

class MainActivity : ComponentActivity() {
    private val viewModel: AdminViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = innerPadding.calculateTopPadding(),
                                bottom = innerPadding.calculateBottomPadding()
                            )
                    ) {
                        AppNavigation(viewModel)
                    }
                }
            }
        }
    }
}

// Helper to save downloaded screenshot/camera photo to Photos Gallery
fun saveBitmapToGallery(context: Context, bitmap: Bitmap, title: String): Boolean {
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$title.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SupervisorControl")
        }
    }
    return try {
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri).use { out ->
                if (out != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
            }
            true
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }
}

fun saveVideoToDownloads(context: Context, base64: String, fileName: String): Boolean {
    return try {
        val cleanBase64 = if (base64.contains(",")) base64.substringAfter(",") else base64
        val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$fileName.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/SupervisorControl")
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri).use { out ->
                out?.write(bytes)
            }
            true
        } else false
    } catch (t: Throwable) {
        false
    }
}

@Composable
fun AppNavigation(viewModel: AdminViewModel) {
    AdminDashboard(viewModel)
}

// 1. GORGEOUS PASSCODE LOCK SCREEN
@Composable
fun PinLockScreen(onUnlockSuccess: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var errorAlert by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFF3F4F6), Color(0xFFE5E7EB))))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_admin_logo),
            contentDescription = "قفل المشرف",
            modifier = Modifier
                .size(72.dp)
                .padding(bottom = 16.dp)
        )

        Text(
            text = "تأكيد هوية المشرف",
            color = Color(0xFF9155FF),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "الرجاء إدخال الرمز السري للدخول للوحة المراقبة",
            color = Color(0xFF6B7280),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
        )

        // PIN indicator dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 40.dp)
        ) {
            for (i in 1..4) {
                val filled = pin.length >= i
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(
                            if (errorAlert) Color(0xFFEF4444)
                            else if (filled) Color(0xFF9155FF)
                            else Color(0xFFE5E7EB)
                        )
                        .border(
                            width = 2.dp,
                            color = if (errorAlert) Color(0xFFEF4444) else Color(0xFFE5E7EB),
                            shape = CircleShape
                        )
                )
            }
        }

        // Numeric Keypad
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "⌫")
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.width(280.dp)
        ) {
            for (row in keys) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (key in row) {
                        Button(
                            onClick = {
                                errorAlert = false
                                when (key) {
                                    "C" -> pin = ""
                                    "⌫" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                    else -> {
                                        if (pin.length < 4) {
                                            pin += key
                                            if (pin.length == 4) {
                                                val ok = onUnlockSuccess(pin)
                                                if (!ok) {
                                                    errorAlert = true
                                                    pin = ""
                                                    Toast.makeText(context, "رمز PIN خاطئ! حاول مجدداً", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (key == "C" || key == "⌫") Color(0xFFE5E7EB) else Color(0xFFFFFFFF),
                                contentColor = if (key == "C" || key == "⌫") Color(0xFFFF4081) else Color(0xFF1F2937)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                        ) {
                            Text(
                                text = key,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "الرمز الافتراضي: 1111",
            color = Color(0xFF6B7280).copy(alpha = 0.5f),
            fontSize = 12.sp
        )
    }
}

// 2. ROOT ADMIN DASHBOARD WITH ADAPTIVE TABS AND PANELS
@Composable
fun getRelativeTimeString(lastActive: Long): String {
    if (lastActive == 0L) return "لا يوجد نشاط مسجل"
    val deltaMs = System.currentTimeMillis() - lastActive
    if (deltaMs < 0L) return "نشط الآن"
    val diffSec = deltaMs / 1000
    if (diffSec < 60) return "كان نشط قبل ثوانٍ"
    val diffMin = diffSec / 60
    if (diffMin < 60) return "كان نشط قبل $diffMin دقائق"
    val diffHours = diffMin / 60
    if (diffHours < 24) return "كان نشط قبل $diffHours ساعات"
    val diffDays = diffHours / 24
    return "كان نشط قبل $diffDays أيام"
}

@Composable
fun SkeletonDeviceItem() {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE5E7EB).copy(alpha = alpha))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE5E7EB).copy(alpha = alpha))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.35f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE5E7EB).copy(alpha = alpha))
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF3F4F6).copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

@Composable
fun AdminDashboard(viewModel: AdminViewModel) {
    val devices by viewModel.devices.collectAsState()
    val selectedToken by viewModel.selectedDeviceToken.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val commandResponse by viewModel.commandResponse.collectAsState()
    val currentDbUrl by viewModel.currentDatabaseUrl.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()

    val activeCommandProgress by viewModel.activeCommandProgress.collectAsState()
    val directScreenshotToShow by viewModel.directScreenshotToShow.collectAsState()
    val directPhotoToShow by viewModel.directPhotoToShow.collectAsState()

    var showAddDeviceDialog by remember { mutableStateOf(false) }
    var showEditDbDialog by remember { mutableStateOf(false) }
    var deviceListTabSelected by remember { mutableIntStateOf(0) } // 0: Active, 1: Inactive
    var bottomNavSelectedTab by remember { mutableIntStateOf(0) } // 0: Home, 1: Commands
    var openCommandDetails by remember { mutableStateOf<String?>(null) } // null = commands index list

    val context = LocalContext.current

    LaunchedEffect(openCommandDetails) {
        if (openCommandDetails != null) {
            while (true) {
                delay(10000)
                viewModel.refreshCurrentDevice()
            }
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearStatusMessage()
        }
    }

    val activeDevice = devices.find { it.id == selectedToken }

    if (activeDevice == null) {
        // --- 1. DEVICE LIST SCREEN (الشاشة الأولى لعرض الهواتف بتبويبات) ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFFF3F4F6), Color(0xFFE5E7EB))))
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 4.dp,
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF9155FF).copy(alpha = 0.15f), Color(0xFF9155FF).copy(alpha = 0.05f)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_admin_logo),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "التحكم والمراقبة الأبوية",
                                color = Color(0xFF111827),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "اختر جهاز طفل للملخصات والتحكم",
                                color = Color(0xFF6B7280),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = { showAddDeviceDialog = true },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF9155FF), Color(0xFF7C3AED))))
                            .size(42.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "إضافة جهاز", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }

            // Realtime WebSocket Status & Data Log Card
            val websocketConnected by viewModel.devicesWebSocketConnected.collectAsState()
            val websocketEvents by viewModel.websocketReceivedEvents.collectAsState()
            var showEventsLogs by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (websocketConnected) Color(0xFFEDFBF0) else Color(0xFFFEF2F2)),
                border = BorderStroke(1.dp, if (websocketConnected) Color(0xFFB1EED1) else Color(0xFFFECACA))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (websocketConnected) Color(0xFF10B981) else Color(0xFFEF4444))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (websocketConnected) "توصيل البث الحي والـ WebSocket نشط" else "انقطع الاتصال بالبث الحي والـ WebSocket",
                                color = if (websocketConnected) Color(0xFF065F46) else Color(0xFF991B1B),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = { showEventsLogs = !showEventsLogs },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (showEventsLogs) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "عرض سجل البيانات والمزامنة",
                                tint = if (websocketConnected) Color(0xFF065F46) else Color(0xFF991B1B)
                            )
                        }
                    }

                    if (showEventsLogs) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = if (websocketConnected) Color(0xFFD1FAE5) else Color(0xFFFEE2E2))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "سجل التحديثات المستلمة من الأجهزة بالخادم:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.DarkGray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        if (websocketEvents.isEmpty()) {
                            Text(
                                text = "بانتظار تلقي أي تحديثات تلقائية من جهاز الطفل الآن...",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 120.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                websocketEvents.forEach { logMessage ->
                                    Text(
                                        text = logMessage,
                                        fontSize = 11.sp,
                                        color = Color(0xFF374151),
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bento Stats Light Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Bento Box 1: Total Children Devices
                Card(
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFEFF6FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "إجمالي الهواتف والـ Presence", color = Color(0xFF6B7280), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "${devices.size} أجهزة مسجلة", color = Color(0xFF111827), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                // Bento Box 2: Online Status Counter
                val onlineCountValue = devices.count { it.isOnline }
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFEFFFEC)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF39D353), modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "متصل بالبث", color = Color(0xFF6B7280), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "$onlineCountValue أونلاين", color = Color(0xFF39D353), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabs = listOf(
                    "جميع الأجهزة" to Icons.Default.PhoneAndroid,
                    "الأجهزة النشطة" to null,
                    "الأجهزة غير النشطة" to null
                )
                
                tabs.forEachIndexed { index, (title, icon) ->
                    val isSelected = deviceListTabSelected == index
                    val backgroundColor = if (isSelected) Color(0xFF9155FF) else Color.White
                    val contentColor = if (isSelected) Color.White else Color(0xFF6B7280)
                    val borderColor = if (isSelected) Color.Transparent else Color(0xFFE5E7EB)
                    
                    Surface(
                        onClick = { deviceListTabSelected = index },
                        shape = RoundedCornerShape(24.dp),
                        color = backgroundColor,
                        contentColor = contentColor,
                        border = BorderStroke(1.dp, borderColor),
                        shadowElevation = if (isSelected) 4.dp else 0.dp,
                        modifier = Modifier.height(42.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            if (index == 0 && icon != null) {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            } else if (index == 1) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if (isSelected) Color.White else Color(0xFF39D353)))
                            } else if (index == 2) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if (isSelected) Color.White else Color(0xFF9CA3AF)))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            val filteredDevices = when (deviceListTabSelected) {
                0 -> devices
                1 -> devices.filter { it.isOnline }
                else -> devices.filter { !it.isOnline }
            }.sortedByDescending { it.isOnline }

            if (devices.isEmpty()) {
                // Skeleton Waiting State (مؤشر تحميل الهيكل عند البدء وبانتظار بيانات البث حضور Presence)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "بانتظار تلقي اتصال الأجهزة والبث الحي (Presence Sync)...",
                        color = Color(0xFF6B7280),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    repeat(3) {
                        SkeletonDeviceItem()
                    }
                }
            } else if (filteredDevices.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassEmpty,
                            contentDescription = null,
                            tint = Color(0xFF6B7280).copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when (deviceListTabSelected) {
                                0 -> "لا توجد أجهزة متصلة بقاعدة البيانات حالياً"
                                1 -> "لا توجد أجهزة نشطة حالياً (نشط خلال 15 دقيقة)"
                                else -> "لا توجد أجهزة غير نشطة حالياً"
                            },
                            color = Color(0xFF6B7280),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val isRefreshing by viewModel.isRefreshing.collectAsState()
                val heartbeats by viewModel.deviceHeartbeats.collectAsState()

                SwipeToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refreshAllDevices() },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredDevices) { dev ->
                            val lastHbTime = heartbeats[dev.id] ?: 0L
                            val isPulsing = System.currentTimeMillis() - lastHbTime < 1500

                            val cardScale by animateFloatAsState(
                                targetValue = if (isPulsing) 1.03f else 1.0f,
                                animationSpec = keyframes {
                                    durationMillis = 500
                                    1.03f at 150 with FastOutLinearInEasing
                                    1.0f at 500 with LinearOutSlowInEasing
                                },
                                label = "cardScale"
                            )
                            val cardBorderColor by animateColorAsState(
                                targetValue = if (isPulsing) Color(0xFFFF4D94) else Color(0xFFE5E7EB),
                                animationSpec = tween(durationMillis = 350),
                                label = "cardBorderColor"
                            )
                            val cardBgColor by animateColorAsState(
                                targetValue = if (isPulsing) Color(0xFFFFF5F8) else Color.White,
                                animationSpec = tween(durationMillis = 350),
                                label = "cardBgColor"
                            )

                            Card(
                                onClick = { viewModel.selectDevice(dev.id) },
                                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                border = BorderStroke(if (isPulsing) 2.dp else 1.5.dp, cardBorderColor),
                                shape = RoundedCornerShape(20.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (isPulsing) 4.dp else 0.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        scaleX = cardScale
                                        scaleY = cardScale
                                    }
                                    .shadow(
                                        elevation = if (isPulsing) 12.dp else 4.dp, 
                                        shape = RoundedCornerShape(20.dp), 
                                        clip = false, 
                                        spotColor = if (isPulsing) Color(0xFFFF4D94).copy(alpha = 0.25f) else Color(0xFF9155FF).copy(alpha = 0.05f), 
                                        ambientColor = if (isPulsing) Color(0xFFFF4D94).copy(alpha = 0.25f) else Color(0xFF9155FF).copy(alpha = 0.05f)
                                    )
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Box(
                                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(
                                                    Brush.linearGradient(
                                                        colors = if (isPulsing) {
                                                            listOf(Color(0xFFFF4D94).copy(alpha = 0.25f), Color(0xFFFF4D94).copy(alpha = 0.1f))
                                                        } else {
                                                            listOf(Color(0xFF9155FF).copy(alpha = 0.15f), Color(0xFF9155FF).copy(alpha = 0.05f))
                                                        }
                                                    )
                                                ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (isPulsing) Icons.Default.Favorite else Icons.Default.PhoneAndroid, 
                                                    contentDescription = null, 
                                                    tint = if (isPulsing) Color(0xFFFF4D94) else Color(0xFF9155FF), 
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = dev.name,
                                                        color = Color(0xFF111827),
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    if (dev.isRealtimeUpdated) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(Color(0xFFE0F2FE))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(6.dp)
                                                                        .clip(CircleShape)
                                                                        .background(Color(0xFF0284C7))
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text(
                                                                    text = "بث فوري",
                                                                    color = Color(0xFF0369A1),
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier.size(8.dp).clip(CircleShape).background(if (dev.isOnline) Color(0xFF10B981) else Color(0xFFF59E0B))
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        if (dev.isOnline) "متصل الآن" else getRelativeTimeString(dev.lastActive), 
                                                        color = if (dev.isOnline) Color(0xFF10B981) else Color(0xFFF59E0B), 
                                                        fontSize = 12.sp, 
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                        
                                        Box(
                                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFF9FAFB)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowLeft, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(14.dp))
                                    
                                    // Bento Details Grid Row 
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Bento Compartment 1: Battery block
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp))
                                                .border(1.dp, Color(0xFFF0FDF4), RoundedCornerShape(12.dp))
                                                .padding(10.dp)
                                        ) {
                                            Column {
                                                Text("البطارية", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(getBatteryIcon(dev.battery), null, tint = getBatteryColor(dev.battery), modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("${dev.battery}%", color = Color(0xFF111827), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        // Bento Compartment 2: Network block
                                        Box(
                                            modifier = Modifier
                                                .weight(1.3f)
                                                .background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp))
                                                .border(1.dp, Color(0xFFEFF6FF), RoundedCornerShape(12.dp))
                                                .padding(10.dp)
                                        ) {
                                            Column {
                                                Text("الشبكة والاتصال", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    val isWifi = dev.networkType?.contains("WIFI", ignoreCase = true) == true
                                                    Icon(if (isWifi) Icons.Default.Wifi else Icons.Default.CellTower, null, tint = Color(0xFF9155FF), modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(dev.networkType ?: "غير معروف", color = Color(0xFF111827), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                        }
                                        // Bento Compartment 3: Signal Alert Type
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp))
                                                .border(1.dp, Color(0xFFFFF7ED), RoundedCornerShape(12.dp))
                                                .padding(10.dp)
                                        ) {
                                            Column {
                                                Text("الحالة", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (dev.isOnline) Color(0xFF10B981) else Color(0xFFF59E0B)))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(if (dev.isOnline) "نشط" else "مغلق", color = Color(0xFF111827), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

            if (showAddDeviceDialog) {
                DeviceSelectionDialog(
                    devicesList = devices,
                    currentToken = null,
                    onClose = { showAddDeviceDialog = false },
                    onSelect = { tok ->
                        viewModel.selectDevice(tok)
                        showAddDeviceDialog = false
                    },
                    onAddDevice = { tok, name ->
                        viewModel.registerDeviceManually(tok, name)
                        showAddDeviceDialog = false
                    }
                )
            }

            if (showEditDbDialog) {
                EditDatabaseUrlDialog(
                    currentUrl = currentDbUrl,
                    onClose = { showEditDbDialog = false },
                    onSave = { newUrl ->
                        viewModel.updateDatabaseUrl(newUrl)
                        showEditDbDialog = false
                    }
                )
            }
        }
    } else {
        // --- 2. DEVICE DETAIL SCREEN WITH BOTTOM NAVIGATION ---
        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier.background(Brush.verticalGradient(listOf(Color(0xFFF3F4F6), Color(0xFFE5E7EB)))),
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 24.dp)
                ) {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(32.dp),
                        shadowElevation = 16.dp,
                        modifier = Modifier.fillMaxWidth().shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(32.dp),
                            spotColor = Color(0xFF9155FF).copy(alpha = 0.15f),
                            ambientColor = Color(0xFF9155FF).copy(alpha = 0.15f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                Triple(0, Icons.Default.Home, "الرئيسية"),
                                Triple(1, Icons.Default.GridView, "الأوامر")
                            ).forEach { (index, icon, label) ->
                                val isSelected = bottomNavSelectedTab == index
                                val contentColor = if (isSelected) Color(0xFF9155FF) else Color(0xFF9CA3AF)
                                val backgroundColor = if (isSelected) Color(0xFF9155FF).copy(alpha = 0.1f) else Color.Transparent

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp, vertical = 8.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(backgroundColor)
                                        .clickable {
                                            bottomNavSelectedTab = index
                                            if (index == 0) openCommandDetails = null
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = contentColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = label,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = contentColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White.copy(alpha = 0.85f),
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    IconButton(onClick = { 
                        if (openCommandDetails != null) {
                            openCommandDetails = null
                        } else {
                            viewModel.selectDevice("")
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color(0xFF1F2937))
                    }
                    
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(activeDevice.name, color = Color(0xFF1F2937), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            if (activeDevice.isRealtimeUpdated) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0369A1))
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isRealtime = activeDevice.isRealtimeUpdated
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(if (isRealtime) Color(0xFF0284C7) else if (activeDevice.isOnline) Color(0xFF39D353) else Color(0xFF6B7280))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isRealtime) "بث حي فوري WebSocket نشط" else if (activeDevice.isOnline) "متصل الآن" else "غير متصل", 
                                color = if (isRealtime) Color(0xFF0369A1) else if (activeDevice.isOnline) Color(0xFF39D353) else Color(0xFF6B7280), 
                                fontSize = 10.sp,
                                fontWeight = if (isRealtime) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(48.dp)) // To center title
                }
                }

                commandResponse?.let { resp ->
                    // Only show status messages if they are NOT "success" to remove top success notifications
                    if (resp.first != "success") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                            shape = RoundedCornerShape(0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null, tint = Color(0xFF1F2937), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("تنبيه: ${resp.second}", color = Color(0xFF1F2937), fontSize = 11.sp)
                            }
                        }
                    }
                }

                val isRefreshing by viewModel.isRefreshing.collectAsState()

                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    if (bottomNavSelectedTab == 0) {
                        DeviceHomeTab(activeDevice, viewModel)
                    } else {
                        DeviceCommandsTab(activeDevice, viewModel, openCommandDetails, onOpenCommand = { openCommandDetails = it })
                    }
                }
            }
        }
    }

    // Modal Progress Dialog for Two-Stage Command Execution (إرسال وتنفيذ الأوامر بمرحلتين)
    activeCommandProgress?.let { progress ->
        Dialog(onDismissRequest = { viewModel.clearActiveCommandProgress() }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                border = BorderStroke(1.dp, Color(0xFFE5E7EB))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.SettingsSuggest,
                        contentDescription = null,
                        tint = Color(0xFF9155FF),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "جاري تنفيذ الأمر",
                        color = Color(0xFF1F2937),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = progress.commandLabel,
                        color = Color(0xFF9155FF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    // Stage 1: Sending Command (المرحلة الأولى: إرسال الأمر)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    when (progress.sendStatus) {
                                        CommandStepStatus.RUNNING -> Color(0xFF9155FF).copy(alpha = 0.2f)
                                        CommandStepStatus.SUCCESS -> Color(0xFF238636).copy(alpha = 0.2f)
                                        CommandStepStatus.FAILED -> Color(0xFFEF4444).copy(alpha = 0.2f)
                                        else -> Color(0xFFE5E7EB)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            when (progress.sendStatus) {
                                CommandStepStatus.RUNNING -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color(0xFF9155FF),
                                        strokeWidth = 2.dp
                                    )
                                }
                                CommandStepStatus.SUCCESS -> {
                                    Icon(Icons.Default.Check, null, tint = Color(0xFF39D353), modifier = Modifier.size(18.dp))
                                }
                                CommandStepStatus.FAILED -> {
                                    Icon(Icons.Default.Close, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                }
                                else -> {
                                    Text("١", color = Color(0xFF6B7280), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "المرحلة الأولى: إرسال الأمر للجهاز",
                                color = Color(0xFF1F2937),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when (progress.sendStatus) {
                                    CommandStepStatus.IDLE -> "في الانتظار..."
                                    CommandStepStatus.RUNNING -> "جاري برمجة وإرسال الأمر إلى شبكة التحكم..."
                                    CommandStepStatus.SUCCESS -> "تم إرسال الأمر وتأكيده بنجاح في قاعدة البيانات!"
                                    CommandStepStatus.FAILED -> "فشل إرسال الأمر بنجاح"
                                },
                                color = Color(0xFF6B7280),
                                fontSize = 11.sp
                            )
                        }
                    }

                    progress.sendError?.let { err ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.1f)),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 44.dp, bottom = 10.dp)
                        ) {
                            Text(
                                text = "خطأ بالتفصيل: $err",
                                color = Color(0xFFEF4444),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .width(2.dp)
                            .height(20.dp)
                            .background(Color(0xFFE5E7EB))
                            .align(Alignment.Start)
                    )

                    // Stage 2: Accepting & Executing command (المرحلة الثانية: قبول وتنفيذ هاتف الطفل للأمر)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    when (progress.executionStatus) {
                                        CommandStepStatus.RUNNING -> Color(0xFF9155FF).copy(alpha = 0.2f)
                                        CommandStepStatus.SUCCESS -> Color(0xFF238636).copy(alpha = 0.2f)
                                        CommandStepStatus.FAILED -> Color(0xFFEF4444).copy(alpha = 0.2f)
                                        else -> Color(0xFFE5E7EB)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            when (progress.executionStatus) {
                                CommandStepStatus.RUNNING -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color(0xFF9155FF),
                                        strokeWidth = 2.dp
                                    )
                                }
                                CommandStepStatus.SUCCESS -> {
                                    Icon(Icons.Default.Check, null, tint = Color(0xFF39D353), modifier = Modifier.size(18.dp))
                                }
                                CommandStepStatus.FAILED -> {
                                    Icon(Icons.Default.Close, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                }
                                else -> {
                                    Text("٢", color = Color(0xFF6B7280), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "المرحلة الثانية: قبول وتنفيذ هاتف الطفل للأمر",
                                color = Color(0xFF1F2937),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when (progress.executionStatus) {
                                    CommandStepStatus.IDLE -> "بانتظار اكتمال المرحلة الأولى..."
                                    CommandStepStatus.RUNNING -> "بانتظار هاتف الطفل لتلقي وتأكيد معالجة الطلب..."
                                    CommandStepStatus.SUCCESS -> "تمت معالجة وتنفيذ الأمر بنجاح من طرف هاتف الطفل!"
                                    CommandStepStatus.FAILED -> "فشل هاتف الطفل في معالجة أو تنفيذ الأمر"
                                },
                                color = Color(0xFF6B7280),
                                fontSize = 11.sp
                            )
                        }
                    }

                    progress.executionError?.let { err ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.1f)),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 44.dp, bottom = 10.dp)
                        ) {
                            Text(
                                text = "تفاصيل الخطأ: $err",
                                color = Color(0xFFEF4444),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    progress.resultMessage?.let { msg ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6)),
                            border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "الرد المستلم:",
                                    color = Color(0xFF9155FF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = msg,
                                    color = Color(0xFF1F2937),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Button(
                        onClick = { viewModel.clearActiveCommandProgress() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (progress.executionStatus == CommandStepStatus.RUNNING || progress.sendStatus == CommandStepStatus.RUNNING) Color(0xFFE5E7EB) else Color(0xFF9155FF)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (progress.executionStatus == CommandStepStatus.RUNNING || progress.sendStatus == CommandStepStatus.RUNNING) "إلغاء المتابعة" else "إغلاق",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Direct Screenshot Viewer Overlay (عرض لقطة الشاشة المستلمة مباشرة على الشاشة)
    directScreenshotToShow?.let { item ->
        val bitmap by produceState<Bitmap?>(initialValue = null, item.base64) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { item.toBitmap() }
        }

        Dialog(onDismissRequest = { viewModel.clearDirectScreenshot() }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                border = BorderStroke(1.dp, Color(0xFFE5E7EB))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تم استلام لقطة الشاشة المطلوبة مباشرة!",
                            color = Color(0xFF1F2937),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewModel.clearDirectScreenshot() }) {
                            Icon(Icons.Default.Close, null, tint = Color(0xFF1F2937))
                        }
                    }

                    Text(
                        text = "تم التقاطها: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp)),
                        color = Color(0xFF6B7280),
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val bmp = bitmap
                    if (bmp != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(380.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "بث مباشر شاشة",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val ok = saveBitmapToGallery(context, bmp, "child_screenshot_${item.timestamp}")
                                    if (ok) {
                                        Toast.makeText(context, "تم حفظ لقطة الشاشة في الاستوديو!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "فشل حفظ الملف لعدم توفر الصلاحيات", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9155FF)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("حفظ بالاستوديو", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { viewModel.clearDirectScreenshot() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5E7EB)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("إغلاق", fontSize = 12.sp)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF9155FF))
                        }
                    }
                }
            }
        }
    }

    // Direct Camera Photo Viewer Overlay (عرض صورة الكاميرا المستلمة مباشرة على الشاشة)
    directPhotoToShow?.let { item ->
        val bitmap by produceState<Bitmap?>(initialValue = null, item.base64) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { item.toBitmap() }
        }

        Dialog(onDismissRequest = { viewModel.clearDirectPhoto() }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                border = BorderStroke(1.dp, Color(0xFFE5E7EB))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تم التقاط واستلام صورة الكاميرا بنجاح!",
                            color = Color(0xFF1F2937),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewModel.clearDirectPhoto() }) {
                            Icon(Icons.Default.Close, null, tint = Color(0xFF1F2937))
                        }
                    }

                    Text(
                        text = "الكاميرا المستعملة: " + (if (item.cameraType == "front" || item.type == "camera_front") "الأمامية" else "الخلفية") + " | " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp)),
                        color = Color(0xFF6B7280),
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val bmp = bitmap
                    if (bmp != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(380.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "صورة الكاميرا المستلمة",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val ok = saveBitmapToGallery(context, bmp, "child_camera_${item.timestamp}")
                                    if (ok) {
                                        Toast.makeText(context, "تم حفظ الصورة بنجاح بالأستوديو!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "فشل حفظ الصورة لعدم توفر الصلاحيات", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9155FF)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("حفظ بالاستوديو", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { viewModel.clearDirectPhoto() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5E7EB)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("إغلاق", fontSize = 12.sp)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF9155FF))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceHomeTab(device: Device, viewModel: AdminViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(0.5.dp, Color(0xFFF3F4F6)),
            modifier = Modifier.fillMaxWidth().shadow(
                elevation = 8.dp, 
                shape = RoundedCornerShape(20.dp), 
                spotColor = Color(0xFF9155FF).copy(alpha = 0.08f), 
                ambientColor = Color(0xFF9155FF).copy(alpha = 0.08f)
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (device.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = if (device.isLocked) Color(0xFFFF4081) else Color(0xFF238636),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(if (device.isLocked) "الجهاز مقفل حالياً" else "الجهاز حر ومفتوح", color = Color(0xFF111827), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("قفل أو فك قفل شاشة الطفل لمنع استخدامه", color = Color(0xFF6B7280), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { viewModel.changeLockStatus(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.linearGradient(listOf(Color(0xFFFF4B8B), Color(0xFFFF7A55))))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("قفل الهاتف", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    Button(
                        onClick = { viewModel.changeLockStatus(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669))))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("فك القفل", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.runCommand("ACTION_LOCK_SCREEN") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(listOf(Color(0xFF9155FF), Color(0xFF7C3AED))))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PowerSettingsNew, null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("إطفاء شاشة الهاتف", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { viewModel.runCommand("hide_app", mapOf("packageName" to "com.aistudio.commander.bxyz", "hidden" to true)) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.linearGradient(listOf(Color(0xFFFF4B8B), Color(0xFFFF7A55))))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("إخفاء أيقونة الطفل", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    Button(
                        onClick = { viewModel.runCommand("hide_app", mapOf("packageName" to "com.aistudio.commander.bxyz", "hidden" to false)) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669))))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("إظهار أيقونة الطفل", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(0.5.dp, Color(0xFFF3F4F6)),
                modifier = Modifier.weight(1f).shadow(
                    elevation = 8.dp, 
                    shape = RoundedCornerShape(16.dp), 
                    spotColor = Color(0xFF9155FF).copy(alpha = 0.08f), 
                    ambientColor = Color(0xFF9155FF).copy(alpha = 0.08f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(getBatteryIcon(device.battery), null, tint = getBatteryColor(device.battery), modifier = Modifier.size(32.dp))
                        if (device.isCharging) {
                            Icon(Icons.Default.Bolt, null, tint = Color(0xFFFFEB3B), modifier = Modifier.size(18.dp).offset(y = 2.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("البطارية", color = Color(0xFF6B7280), fontSize = 11.sp)
                    Text("${device.battery}%", color = Color(0xFF1F2937), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(0.5.dp, Color(0xFFF3F4F6)),
                modifier = Modifier.weight(1f).shadow(
                    elevation = 8.dp, 
                    shape = RoundedCornerShape(16.dp), 
                    spotColor = Color(0xFF9155FF).copy(alpha = 0.08f), 
                    ambientColor = Color(0xFF9155FF).copy(alpha = 0.08f)
                )
            ) {
                val networkIcon = when (device.networkType?.uppercase()) {
                    "WIFI" -> Icons.Default.Wifi
                    "5G" -> Icons.Default.SignalCellularAlt
                    "4G", "LTE" -> Icons.Default.SignalCellular4Bar
                    else -> Icons.Default.NetworkCell
                }
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(networkIcon, null, tint = Color(0xFF2196F3), modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    val displayNetType = if (device.networkType?.contains("cellular", ignoreCase = true) == true) "Phone data" else device.networkType
                    val networkText = if (displayNetType != null && device.carrierName != null) {
                        "${displayNetType} (${device.carrierName})"
                    } else {
                        displayNetType ?: device.carrierName ?: "---"
                    }
                    Text("الشبكة", color = Color(0xFF6B7280), fontSize = 11.sp)
                    Text(networkText, color = Color(0xFF1F2937), fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(0.5.dp, Color(0xFFF3F4F6)),
                modifier = Modifier.weight(1f).shadow(
                    elevation = 8.dp, 
                    shape = RoundedCornerShape(16.dp), 
                    spotColor = Color(0xFF9155FF).copy(alpha = 0.08f), 
                    ambientColor = Color(0xFF9155FF).copy(alpha = 0.08f)
                )
            ) {
                val usagePercentage = if (device.storageTotal > 0) ((device.storageUsed.toDouble() / device.storageTotal.toDouble()) * 100).toInt() else 0
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Storage, null, tint = Color(0xFF9155FF), modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("التخزين", color = Color(0xFF6B7280), fontSize = 11.sp)
                    Text("$usagePercentage%", color = Color(0xFF1F2937), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(0.5.dp, Color(0xFFF3F4F6)),
            modifier = Modifier.fillMaxWidth().shadow(
                elevation = 8.dp, 
                shape = RoundedCornerShape(20.dp), 
                spotColor = Color(0xFF9155FF).copy(alpha = 0.08f), 
                ambientColor = Color(0xFF9155FF).copy(alpha = 0.08f)
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("تفاصيل مساحة التخزين", color = Color(0xFF1F2937), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                val usedGb = if (device.storageUsed > 1_000_000) 
                    String.format(Locale.ENGLISH, "%.2f", device.storageUsed.toDouble() / (1024.0 * 1024.0 * 1024.0))
                else 
                    String.format(Locale.ENGLISH, "%.2f", device.storageUsed.toDouble())
                
                val totalGb = if (device.storageTotal > 1_000_000) 
                    String.format(Locale.ENGLISH, "%.2f", device.storageTotal.toDouble() / (1024.0 * 1024.0 * 1024.0))
                else 
                    String.format(Locale.ENGLISH, "%.2f", device.storageTotal.toDouble())
                val progress = if (device.storageTotal > 0) (device.storageUsed.toFloat() / device.storageTotal.toFloat()) else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    color = Color(0xFF9155FF),
                    trackColor = Color(0xFFE5E7EB),
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("المستخدم: $usedGb جيجا", color = Color(0xFF6B7280), fontSize = 11.sp)
                    Text("الإجمالي: $totalGb جيجا", color = Color(0xFF6B7280), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun DeviceCommandsTab(
    device: Device,
    viewModel: AdminViewModel,
    openCommandDetails: String?,
    onOpenCommand: (String?) -> Unit
) {
    if (openCommandDetails == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("قائمة الأوامر الفورية", color = Color(0xFF1F2937), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            
            val cmdItems = listOf(
                CommandItemInfo("screenshot", "لقطة الشاشة والكاميرات", "التقاط لقطة شاشة هاتف الطفل أو صور حية بالكاميرا", Icons.Default.Screenshot, Color(0xFF9155FF)),
                CommandItemInfo("audio_record", "تسجيل الصوت المحيطي", "تسجيل مقطع صوتي محيطي بالوقت الحقيقي والاستماع إليه", Icons.Default.Mic, Color(0xFF00E5FF)),
                CommandItemInfo("file_explorer", "مستكشف ملفات الهاتف", "استكشاف وتنزيل ملفات جهاز الطفل بالكامل", Icons.Default.FolderOpen, Color(0xFFFFD54F)),
                CommandItemInfo("apps", "قائمة التطبيقات وحزمها", "الاطلاع وفلترة التطبيقات المنصبة على الهاتف للأمان", Icons.Default.Apps, Color(0xFFFF4081)),
                CommandItemInfo("sms", "الرسائل وتنبيهات الأمان", "مزامنة الرسائل النصية والتنبيهات المكتشفة بالهاتف", Icons.Default.Sms, Color(0xFFFF9100)),
                CommandItemInfo("contacts", "سجل جهات الاتصال", "عرض الأسماء والأرقام المسجلة في هاتف الطفل", Icons.Default.ContactPhone, Color(0xFF9155FF)),
                CommandItemInfo("remote_control", "التحكم عن بعد (لمس)", "بث مباشر للشاشة مع إمكانية التحكم الكامل باللمس", Icons.Default.SettingsRemote, Color(0xFF2196F3)),
                CommandItemInfo("audio_control", "التحكم في الصوت والتنبيه", "تشغيل أصوات تنبيهية والتحكم في مستوى صوت هاتف الطفل", Icons.Default.VolumeUp, Color(0xFFFFA726)),
                CommandItemInfo("send_message", "إرسال رسالة فورية", "إرسال رسالة تظهر كإشعار على هاتف الطفل", Icons.Default.Chat, Color(0xFF00C853)),
                CommandItemInfo("change_icon", "استبدال أيقونة التطبيق", "تغيير شكل واسم أيقونة تطبيق الطفل من ضمن القائمة", Icons.Default.Apps, Color(0xFFE91E63))
            )

            cmdItems.forEach { cmd ->
                Card(
                    onClick = { onOpenCommand(cmd.id) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(0.5.dp, Color(0xFFF3F4F6)),
                    modifier = Modifier.fillMaxWidth().shadow(
                        elevation = 6.dp, 
                        shape = RoundedCornerShape(16.dp), 
                        spotColor = Color(0xFF9155FF).copy(alpha = 0.08f), 
                        ambientColor = Color(0xFF9155FF).copy(alpha = 0.08f)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(6.dp)).background(cmd.color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(cmd.icon, null, tint = cmd.color, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cmd.title, color = Color(0xFF1F2937), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(cmd.description, color = Color(0xFF6B7280), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Default.ArrowForwardIos, null, tint = Color(0xFF6B7280), modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (openCommandDetails) {
                    "screenshot" -> ScreenshotRequirementsPage(viewModel)
                    "audio_record" -> AudioRecordRequirementsPage(viewModel)
                    "live_stream" -> LiveStreamRequirementsPage(viewModel)
                    "file_explorer" -> RemoteFileExplorerTab(viewModel)
                    "apps" -> InstalledAppsRequirementsPage(viewModel)
                    "sms" -> SmsAndSecurityAlertsTab(viewModel)
                    "contacts" -> ContactsTab(viewModel)
                    "remote_control" -> RemoteControlTab(viewModel)
                    "audio_control" -> AudioControlTab(viewModel)
                    "send_message" -> NotificationTab(viewModel)
                    "camera_live" -> CameraLiveTab(viewModel)
                    "change_icon" -> ChangeIconRequirementsPage(viewModel)
                }
            }
        }
    }
}

data class CommandItemInfo(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun EmptyHistoryPlaceholder(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(Color(0xFFFFFFFF), RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, Color(0xFFE5E7EB)), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.History, null, tint = Color(0xFF6B7280), modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(text, color = Color(0xFF6B7280), fontSize = 12.sp)
        }
    }
}

@Composable
fun ScreenshotRequirementsPage(viewModel: AdminViewModel) {
    val screenshots by viewModel.screenshots.collectAsState()
    val cameraPhotos by viewModel.cameraPhotos.collectAsState()
    val cameraVideos by viewModel.cameraVideos.collectAsState()
    val context = LocalContext.current
    var fullscreenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var playingVideo by remember { mutableStateOf<MediaItem?>(null) }
    var selectedSubTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFFFFFFF), RoundedCornerShape(12.dp)).padding(4.dp)) {
            listOf("الشاشة" to 0, "الصور" to 1, "الفيديو" to 2).forEach { (label, index) ->
                val isSelected = selectedSubTab == index
                Box(modifier = Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(8.dp)).background(if (isSelected) Color(0xFF9155FF) else Color.Transparent).clickable { selectedSubTab = index }, contentAlignment = Alignment.Center) {
                    Text(label, color = if (isSelected) Color(0xFF1F2937) else Color(0xFF6B7280), fontSize = 11.sp)
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))

        when (selectedSubTab) {
            0 -> {
                Button(onClick = { viewModel.requestScreenshot() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9155FF))) {
                    Icon(Icons.Default.Screenshot, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("التقاط شاشة الطفل")
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.weight(1f)) {
                    BentoMediaGrid(items = screenshots, category = "screenshots", onDelete = { viewModel.deleteMediaItem("screenshots", it.id) }, onExpand = { fullscreenBitmap = it }, onSave = { bmp -> saveBitmapToGallery(context, bmp, "screenshot_${System.currentTimeMillis()}") })
                }
            }
            1 -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.requestPhoto(false) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))) {
                        Text("خلفية")
                    }
                    Button(onClick = { viewModel.requestPhoto(true) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))) {
                        Text("أمامية", color = Color.Black)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.weight(1f)) {
                    BentoMediaGrid(items = cameraPhotos, category = "camera_photos", onDelete = { viewModel.deleteMediaItem("camera_photos", it.id) }, onExpand = { fullscreenBitmap = it }, onSave = { bmp -> saveBitmapToGallery(context, bmp, "photo_${System.currentTimeMillis()}") })
                }
            }
            2 -> {
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.requestVideo(false) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))) {
                            Text("فيديو خلفي")
                        }
                        Button(onClick = { viewModel.requestVideo(true) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9100))) {
                            Text("فيديو أمامي", color = Color.Black)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(cameraVideos) { video ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { playingVideo = video },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                                border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayCircle, null, tint = Color(0xFFEF4444))
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(if (video.cameraType == "front") "فيديو من الكاميرا الأمامية" else "فيديو من الكاميرا الخلفية", color = Color(0xFF1F2937), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault()).format(Date(video.timestamp)), color = Color(0xFF6B7280), fontSize = 10.sp)
                                    }
                                    IconButton(onClick = { viewModel.deleteMediaItem("video_records", video.id) }) {
                                        Icon(Icons.Default.Delete, "حذف", tint = Color(0xFF6B7280), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        if (cameraVideos.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                    Text("لم يتم استلام أي مقاطع فيديو بعد", color = Color(0xFF6B7280), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fullscreenBitmap?.let { bmp ->
        Dialog(onDismissRequest = { fullscreenBitmap = null }) {
            Box(Modifier.fillMaxSize().clickable { fullscreenBitmap = null }.background(Color.Black.copy(0.9f)), contentAlignment = Alignment.Center) {
                Image(bmp.asImageBitmap(), null, modifier = Modifier.fillMaxWidth(0.95f), contentScale = ContentScale.Fit)
            }
        }
    }

    playingVideo?.let { video ->
        VideoPlayerDialog(
            videoBase64 = video.base64,
            onDismiss = { playingVideo = null },
            onDownload = {
                val success = saveVideoToDownloads(context, video.base64, "video_${video.timestamp}")
                if (success) {
                    Toast.makeText(context, "تم حفظ الفيديو بجهازك (في الأفلام)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "فشل حفظ الفيديو", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun BentoMediaGrid(
    items: List<MediaItem>,
    category: String,
    onDelete: (MediaItem) -> Unit,
    onExpand: (Bitmap) -> Unit,
    onSave: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("لا توجد وسائط متوفرة حالياً", color = Color(0xFF6B7280), fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text("لم يرسل تطبيق الطفل صوراً أو لم يتم العثور على الأعمدة (response_data, image_code, result) في الجدول", color = Color(0xFF9CA3AF), fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items) { item ->
            var isDecodingError by remember { mutableStateOf(false) }
            val bitmap by produceState<Bitmap?>(initialValue = null, item.base64) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val bmp = item.toBitmap()
                    if (bmp == null) {
                        isDecodingError = true
                    }
                    bmp
                }
            }
            
            var showOptions by remember { mutableStateOf(false) }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (bitmap != null) {
                        Image(
                            bitmap!!.asImageBitmap(),
                            null,
                            modifier = Modifier.fillMaxSize().clickable { onExpand(bitmap!!) },
                            contentScale = ContentScale.Crop
                        )
                    } else if (isDecodingError) {
                        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFFEBEE)).padding(4.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Error, null, tint = Color(0xFFEF4444), modifier = Modifier.size(24.dp))
                                Spacer(Modifier.height(4.dp))
                                Text("صورة تالفة", color = Color(0xFFEF4444), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(2.dp))
                                Text(item.lastDecodeError ?: "Unknown error", color = Color(0xFFEF4444), fontSize = 8.sp, maxLines = 4, textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF9155FF), strokeWidth = 2.dp)
                        }
                    }

                    // Overlay info
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                            .padding(6.dp)
                    ) {
                        Column {
                            if (item.cameraType != null) {
                                Text(
                                    if(item.cameraType == "front") "أمامية" else "خلفية",
                                    color = Color.Cyan,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                SimpleDateFormat("h:mm a, d/M", Locale.getDefault()).format(Date(item.timestamp)),
                                color = Color(0xFF1F2937),
                                fontSize = 8.sp
                            )
                        }
                    }

                    // Action buttons overlay
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { showOptions = true },
                            modifier = Modifier.size(28.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(Icons.Default.MoreVert, null, tint = Color(0xFF1F2937), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            
            if (showOptions) {
                Dialog(onDismissRequest = { showOptions = false }) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("خيارات الوسائط", color = Color(0xFF1F2937), fontWeight = FontWeight.Bold)
                            
                            HorizontalDivider(color = Color(0xFFE5E7EB))
                            
                            Button(
                                onClick = { 
                                    bitmap?.let { onSave(it) }
                                    showOptions = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
                            ) {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("حفظ في الاستوديو")
                            }

                            Button(
                                onClick = { 
                                    onDelete(item)
                                    showOptions = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                            ) {
                                Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("حذف نهائي")
                            }
                            
                            OutlinedButton(onClick = { showOptions = false }, modifier = Modifier.fillMaxWidth()) {
                                Text("إلغاء", color = Color(0xFF1F2937))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioRecordRequirementsPage(viewModel: AdminViewModel) {
    val audioRecords by viewModel.audioRecords.collectAsState()
    val isAudioPlaying by viewModel.isAudioPlaying.collectAsState()
    val audioDuration by viewModel.audioDuration.collectAsState()
    val audioPosition by viewModel.audioPosition.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
            border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Mic, null, tint = Color(0xFF00E5FF), modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text("الأصوات المحيطة", color = Color(0xFF1F2937), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("سجل أصوات البيئة المحيطة بهاتف الطفل", color = Color(0xFF6B7280), fontSize = 11.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.requestAudioRecord() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("طلب تسجيل صوتي فوري (10ث)", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        HorizontalDivider(color = Color(0xFFE5E7EB))

        Text("سجل التسجيلات المستلمة (${audioRecords.size})", color = Color(0xFF1F2937), fontSize = 14.sp, fontWeight = FontWeight.Bold)

        if (audioRecords.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Text("لا توجد تسجيلات حتى الآن", color = Color(0xFF6B7280), fontSize = 12.sp)
            }
        } else {
            audioRecords.forEach { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Audiotrack, null, tint = Color(0xFF1F2937), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("تسجيل صوتي", color = Color(0xFF1F2937), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(SimpleDateFormat("h:mm:ss a, d MMM yyyy", Locale.getDefault()).format(Date(item.timestamp)), color = Color(0xFF6B7280), fontSize = 10.sp)
                            }
                            IconButton(onClick = { viewModel.deleteMediaItem("audio_records", item.id) }) {
                                Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                            }
                        }
                        
                        // Player placeholder logic
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            IconButton(
                                onClick = { viewModel.loadAndPlayAudio(item.base64) },
                                modifier = Modifier.size(36.dp).background(Color(0xFF00E5FF), CircleShape)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Box(modifier = Modifier.weight(1f).height(4.dp).background(Color(0xFFE5E7EB), RoundedCornerShape(2.dp)))
                        }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun RemoteControlTab(viewModel: AdminViewModel) {
    val isStreamingActive by remember(viewModel) { viewModel.liveStreamState.map { it?.isActive == true }.distinctUntilChanged() }.collectAsState(initial = false)
    var controlContainerSize by remember { mutableStateOf(IntSize.Zero) }

    Column(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
            border = BorderStroke(1.dp, if(isStreamingActive) Color(0xFF2196F3) else Color(0xFFE5E7EB)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("التحكم التفاعلي باللمس", color = Color(0xFF1F2937), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(if (isStreamingActive) "مراقب" else "غير متصل", color = if (isStreamingActive) Color(0xFF39D353) else Color(0xFFFF4081), fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(450.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .border(1.dp, Color(0xFFE5E7EB))
                        .onSizeChanged { controlContainerSize = it }
                        .pointerInput(isStreamingActive) {
                            if (!isStreamingActive) return@pointerInput
                            detectTapGestures(
                                onTap = { offset ->
                                    if (controlContainerSize.width > 0 && controlContainerSize.height > 0) {
                                        val normalizedX = (offset.x / controlContainerSize.width) * 100f
                                        val normalizedY = (offset.y / controlContainerSize.height) * 100f
                                        viewModel.sendRemoteClick(normalizedX, normalizedY)
                                    }
                                }
                            )
                        }
                        .pointerInput(isStreamingActive) {
                            if (!isStreamingActive) return@pointerInput
                            var startPoint = Offset.Zero
                            var endPoint = Offset.Zero
                            detectDragGestures(
                                onDragStart = { startPoint = it },
                                onDragEnd = {
                                    if (controlContainerSize.width > 0 && controlContainerSize.height > 0) {
                                        val x1 = (startPoint.x / controlContainerSize.width) * 100f
                                        val y1 = (startPoint.y / controlContainerSize.height) * 100f
                                        val x2 = (endPoint.x / controlContainerSize.width) * 100f
                                        val y2 = (endPoint.y / controlContainerSize.height) * 100f
                                        
                                        // Only send if it's a real swipe (not just a jittery tap)
                                        val dist = kotlin.math.sqrt((startPoint.x - endPoint.x)*(startPoint.x - endPoint.x) + (startPoint.y - endPoint.y)*(startPoint.y - endPoint.y))
                                        if (dist > 30) {
                                            viewModel.sendRemoteSwipe(x1, y1, x2, y2)
                                        }
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    endPoint = change.position
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap by produceState<Bitmap?>(initialValue = null, viewModel) {
                        viewModel.liveStreamState.map { Pair(it?.image, it?.timestamp) }.distinctUntilChanged().conflate().collect { pair ->
                            val imgStr = pair.first
                            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { imgStr?.let { com.example.admin.LiveStreamState(image = it).toBitmap() } }
                        }
                    }
                    if (isStreamingActive && bitmap != null) {
                        Image(
                            bitmap!!.asImageBitmap(), 
                            null, 
                            modifier = Modifier.fillMaxSize(), 
                            contentScale = ContentScale.Fit
                        )
                    } else if (isStreamingActive) {
                        CircularProgressIndicator(color = Color(0xFF2196F3))
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SettingsRemote, null, tint = Color(0xFF6B7280), modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("البث والتحكم غير مفعل.", color = Color(0xFF6B7280), fontSize = 12.sp)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { viewModel.startLiveStream() }, 
                        enabled = !isStreamingActive, 
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)), 
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("بدء العرض")
                    }
                    Button(
                        onClick = { viewModel.stopLiveStream() }, 
                        enabled = isStreamingActive, 
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)), 
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("إيقاف")
                    }
                }
                
                if (isStreamingActive) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "استخدم اللمس للنقر على شاشة الطفل مباشرة. سيتم إرسال إحداثيات اللمس ونسبتها المئوية تلقائياً للتنفيذ.",
                        color = Color(0xFF6B7280),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun AudioControlTab(viewModel: AdminViewModel) {
    var volume by remember { mutableFloatStateOf(50f) }

    Column(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
            border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("التحكم في مستوى الصوت", color = Color(0xFF1F2937), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VolumeMute, "صامت", tint = Color(0xFF6B7280))
                    Slider(
                        value = volume,
                        onValueChange = { volume = it },
                        onValueChangeFinished = { viewModel.setRemoteVolume(volume.toInt()) },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFFA726),
                            activeTrackColor = Color(0xFFFFA726)
                        )
                    )
                    Icon(Icons.Default.VolumeUp, "مرتفع", tint = Color(0xFFFFA726))
                }
                Text("المستوى الحالي: ${volume.toInt()}%", color = Color(0xFF6B7280), fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
            border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("تشغيل أصوات تنبيهية", color = Color(0xFF1F2937), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { viewModel.playRemoteSound(1) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.NotificationsActive, null)
                            Text("صافرة إنذار", fontSize = 11.sp, maxLines = 1)
                        }
                    }

                    Button(
                        onClick = { viewModel.playRemoteSound(2) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA726)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.MusicNote, null)
                            Text("نغمة هادئة", fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
        
        Text(
            "هذه الميزة تسمح لك بلفت انتباه الطفل أو العثور على الجهاز المفقود عبر تشغيل أصوات مرتفعة عن بعد.",
            color = Color(0xFF6B7280),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
fun NotificationTab(viewModel: AdminViewModel) {
    var title by remember { mutableStateOf("تنبيه من الوالدين") }
    var message by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
            border = BorderStroke(1.dp, Color(0xFFE5E7EB))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("إرسال إشعار فوري", color = Color(0xFF1F2937), fontWeight = FontWeight.Bold)
                
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("العنوان") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1F2937),
                        unfocusedTextColor = Color(0xFF1F2937),
                        focusedBorderColor = Color(0xFF9155FF)
                    )
                )

                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("الرسالة") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1F2937),
                        unfocusedTextColor = Color(0xFF1F2937),
                        focusedBorderColor = Color(0xFF9155FF)
                    )
                )
                
                Button(
                    onClick = { 
                        if (message.isNotBlank()) {
                            viewModel.sendNotification(title, message)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                    enabled = message.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, null)
                    Spacer(Modifier.width(8.dp))
                    Text("إرسال الآن")
                }
            }
        }
        
        Text(
            "سيظهر هذا الإشعار فوراً في شريط التنبيهات على هاتف الطفل حتى لو كان التطبيق في الخلفية.",
            color = Color(0xFF6B7280),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun CameraLiveTab(viewModel: AdminViewModel) {
    val cameraStreamState by viewModel.cameraStreamState.collectAsState(initial = null)
    val isStreamingActive = cameraStreamState?.isActive == true
    val isLoading = cameraStreamState?.isLoading == true
    val error = cameraStreamState?.error
    val streamUrl = cameraStreamState?.streamUrl
    
    val bitmap by produceState<Bitmap?>(initialValue = null, viewModel) {
        viewModel.cameraStreamState.map { Pair(it?.image, it?.timestamp) }.distinctUntilChanged().conflate().collect { pair ->
            val imgStr = pair.first
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { 
                imgStr?.let { 
                    com.example.admin.CameraStreamState(image = it).toBitmap() 
                } 
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
            border = BorderStroke(1.dp, Color(0xFFE5E7EB))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "بث حي للكاميرا",
                            color = Color(0xFF1F2937),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "جاهز لفتح اتصال البث المباشر",
                            color = Color(0xFF6B7280),
                            fontSize = 12.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.startCameraStream(false) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9155FF))
                    ) {
                        Icon(Icons.Default.FlipCameraAndroid, null)
                        Spacer(Modifier.width(8.dp))
                        Text("كاميرا خلفية")
                    }
                    Button(
                        onClick = { viewModel.startCameraStream(true) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9155FF))
                    ) {
                        Icon(Icons.Default.CameraFront, null)
                        Spacer(Modifier.width(8.dp))
                        Text("كاميرا أمامية")
                    }
                }
                
                Text(
                    "ملاحظة: البث المباشر يستهلك بيانات الإنترنت والبطارية، سيتم إغلاق البث تلقائياً عند إغلاق النافذة.",
                    color = Color(0xFFFFA726),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (isStreamingActive || isLoading) {
        Dialog(onDismissRequest = { viewModel.stopCameraStream() }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 400.dp, max = 600.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                border = BorderStroke(1.dp, if(isStreamingActive) Color(0xFF39D353) else Color(0xFF9155FF)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isLoading) "جاري الاتصال بـ الكاميرا..." else "البث الحي مباشر",
                            color = if (isLoading) Color(0xFF9155FF) else Color(0xFF39D353),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        IconButton(onClick = { viewModel.stopCameraStream() }) {
                            Icon(Icons.Default.Close, null, tint = Color(0xFF1F2937))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFF9155FF))
                                Spacer(Modifier.height(16.dp))
                                Text("في انتظار استجابة هاتف الطفل...", color = Color(0xFF6B7280), fontSize = 14.sp)
                            }
                        } else if (isStreamingActive) {
                            val isWebRtcSignaling = !streamUrl.isNullOrBlank() && (streamUrl.contains("webrtc") || streamUrl.contains("signaling") || streamUrl.contains("render.com"))
                            if (!streamUrl.isNullOrBlank() && !isWebRtcSignaling) {
                                LiveStreamPlayer(
                                    streamUrl = streamUrl,
                                    deviceToken = viewModel.selectedDeviceToken.value,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (bitmap != null) {
                                Image(
                                    bitmap = bitmap!!.asImageBitmap(),
                                    contentDescription = "Camera Stream",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color(0xFF9155FF))
                                    Spacer(Modifier.height(8.dp))
                                    Text("جاري استقبال الإطارات...", color = Color(0xFF6B7280), fontSize = 12.sp)
                                }
                            }
                        }
                        
                        if (error != null) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth(),
                                color = Color.Black.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = error!!,
                                    color = Color.Red,
                                    modifier = Modifier.padding(8.dp),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    
                    Button(
                        onClick = { viewModel.stopCameraStream() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444))
                    ) {
                        Icon(Icons.Default.Stop, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if(isLoading) "إلغاء الطلب" else "إيقاف البث وإغلاق")
                    }
                }
            }
        }
    }
}

@Composable
fun LiveStreamRequirementsPage(viewModel: AdminViewModel) {
    val liveStreamState by viewModel.liveStreamState.collectAsState(initial = null)
    val isStreamingActive = liveStreamState?.isActive == true
    val isLoading = liveStreamState?.isLoading == true
    val error = liveStreamState?.error
    val streamUrl = liveStreamState?.streamUrl
    
    var showFullscreenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showFullscreenStream by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
            border = BorderStroke(1.dp, if(isStreamingActive) Color(0xFF39D353) else if (isLoading) Color(0xFF9155FF) else Color(0xFFE5E7EB)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("البث المباشر للشاشة", color = Color(0xFF1F2937), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (isLoading) "جاري التحميل..." else if (isStreamingActive) "نشط" else "متوقف", 
                        color = if (isLoading) Color(0xFF9155FF) else if (isStreamingActive) Color(0xFF39D353) else Color(0xFFFF4081), 
                        fontSize = 11.sp
                    )
                }
                
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.1f)),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("خطأ: $error", color = Color(0xFFEF4444), fontSize = 11.sp, modifier = Modifier.padding(8.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                Box(modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black).border(1.dp, Color(0xFFE5E7EB)), contentAlignment = Alignment.Center) {
                    val bitmap by produceState<Bitmap?>(initialValue = null, viewModel) {
                        viewModel.liveStreamState.map { Pair(it?.image, it?.timestamp) }.distinctUntilChanged().conflate().collect { pair ->
                            val imgStr = pair.first
                            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { imgStr?.let { com.example.admin.LiveStreamState(image = it).toBitmap() } }
                        }
                    }
                    if (isLoading) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF9155FF))
                            Spacer(Modifier.height(8.dp))
                            Text("في انتظار استجابة الطفل...", color = Color(0xFF6B7280), fontSize = 12.sp)
                        }
                    } else if (isStreamingActive) {
                        val isWebRtcSignaling = !streamUrl.isNullOrBlank() && (streamUrl.contains("webrtc") || streamUrl.contains("signaling") || streamUrl.contains("render.com"))
                        if (!streamUrl.isNullOrBlank() && !isWebRtcSignaling) {
                            LiveStreamPlayer(streamUrl = streamUrl, deviceToken = viewModel.selectedDeviceToken.value, modifier = Modifier.fillMaxSize())
                            IconButton(onClick = { showFullscreenStream = streamUrl }, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)) {
                                Icon(Icons.Default.Fullscreen, null, tint = Color.White)
                            }
                        } else if (bitmap != null) {
                            Image(bitmap!!.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            IconButton(onClick = { showFullscreenBitmap = bitmap }, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)) {
                                Icon(Icons.Default.Fullscreen, null, tint = Color.White)
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFF9155FF))
                                Spacer(Modifier.height(8.dp))
                                Text("جاري استقبال الإطارات...", color = Color(0xFF6B7280), fontSize = 11.sp)
                            }
                        }
                    } else {
                        Text("البث غير نشط.", color = Color(0xFF6B7280), fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { viewModel.startLiveStream() }, enabled = !isStreamingActive && !isLoading, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)), modifier = Modifier.weight(1f)) {
                        Text("بدء البث")
                    }
                    Button(onClick = { viewModel.stopLiveStream() }, enabled = isStreamingActive || isLoading, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)), modifier = Modifier.weight(1f)) {
                        Text(if(isLoading) "إلغاء الطلب" else "إيقاف")
                    }
                }
            }
        }
    }

    showFullscreenBitmap?.let { bmp ->
        Dialog(onDismissRequest = { showFullscreenBitmap = null }) {
            Box(modifier = Modifier.fillMaxSize().clickable { showFullscreenBitmap = null }.background(Color.Black), contentAlignment = Alignment.Center) {
                Image(bmp.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
    }

    showFullscreenStream?.let { url ->
        Dialog(onDismissRequest = { showFullscreenStream = null }) {
            Box(modifier = Modifier.fillMaxSize().clickable { showFullscreenStream = null }.background(Color.Black), contentAlignment = Alignment.Center) {
                val isWebRtcSignaling = !url.isNullOrBlank() && (url.contains("webrtc") || url.contains("signaling") || url.contains("render.com"))
                if (!isWebRtcSignaling) {
                    LiveStreamPlayer(streamUrl = url, deviceToken = viewModel.selectedDeviceToken.value, modifier = Modifier.fillMaxSize())
                } else {
                    Text("لا يمكن عرض البث عبر بروتوكول الإشارة", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun ChangeIconRequirementsPage(viewModel: AdminViewModel) {
    val context = LocalContext.current
    var selectedIconId by remember { mutableStateOf<String?>(null) }
    
    val iconOptions = listOf(
        Triple("icon1", "name1", "icon1"), 
        Triple("icon2", "name2", "icon2"),
        Triple("icon3", "name3", "icon3")
    )

    Column(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("استبدال أيقونة وتسمية تطبيق الطفل", color = Color(0xFF1F2937), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("اختر أيقونة ومسمى بديل لتغيير مظهر تطبيق الطفل بهدف إخفائه بشكل فعال.", color = Color(0xFF6B7280), fontSize = 12.sp)

        iconOptions.forEach { (iconId, label, filename) ->
            Card(
                onClick = { selectedIconId = iconId },
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedIconId == iconId) Color(0xFF9155FF).copy(alpha = 0.1f) else Color.White
                ),
                border = BorderStroke(1.dp, if (selectedIconId == iconId) Color(0xFF9155FF) else Color(0xFFE5E7EB)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    val bitmap = remember(filename) { 
                        var bmp: android.graphics.Bitmap? = null
                        val extensions = listOf(".png", ".jpg", ".jpeg", "")
                        for (ext in extensions) {
                            try {
                                val st = context.assets.open(filename + ext)
                                bmp = android.graphics.BitmapFactory.decodeStream(st)
                                if (bmp != null) break
                            } catch (e: Exception) {
                                // ignore
                            }
                        }
                        bmp?.asImageBitmap()
                    }
                    if (bitmap != null) {
                        Image(bitmap = bitmap, contentDescription = label, modifier = Modifier.size(40.dp))
                    } else {
                        Box(modifier = Modifier.size(40.dp).background(Color(0xFFE5E7EB), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Apps, null, tint = Color(0xFF6B7280))
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, color = Color(0xFF1F2937), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(iconId, color = Color(0xFF6B7280), fontSize = 11.sp)
                    }
                    if (selectedIconId == iconId) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF9155FF))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = {
                val opt = iconOptions.find { it.first == selectedIconId }
                if (opt != null) {
                    viewModel.runCommand("change_icon", mapOf("iconName" to opt.first, "appName" to opt.second))
                }
            },
            enabled = selectedIconId != null,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9155FF)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("استبدال الأيقونة", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InstalledAppsRequirementsPage(viewModel: AdminViewModel) {
    val installedApps by viewModel.installedApps.collectAsState()
    var query by remember { mutableStateOf("") }
    var filterSystem by remember { mutableStateOf<Boolean?>(false) }
    var selectedAppForDialog by remember { mutableStateOf<com.example.admin.InstalledApp?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { viewModel.requestAppsList() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081)), modifier = Modifier.fillMaxWidth()) {
            Text("جرد وتنزيل قائمة التطبيقات")
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("بحث عن تطبيق..", color = Color(0xFF6B7280)) },
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("المستخدم" to false, "النظام" to true, "الكل" to null).forEach { (lbl, valState) ->
                Button(
                    onClick = { filterSystem = valState },
                    colors = ButtonDefaults.buttonColors(containerColor = if(filterSystem == valState) Color(0xFF9155FF) else Color(0xFFFFFFFF)),
                    modifier = Modifier.weight(1f).height(34.dp)
                ) {
                    Text(lbl, fontSize = 11.sp)
                }
            }
        }

        val list = installedApps.filter {
            (query.isBlank() || it.name.contains(query, true)) && (filterSystem == null || it.isSystem == filterSystem)
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list) { app ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)), 
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {  },
                            onLongClick = { selectedAppForDialog = app }
                        )
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if(app.isSystem) Icons.Default.Settings else Icons.Default.PlayArrow, null, tint = if(app.isSystem) Color.Gray else Color.Green, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(app.name, color = Color(0xFF1F2937), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(app.packageName, color = Color(0xFF6B7280), fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }

    if (selectedAppForDialog != null) {
        AlertDialog(
            onDismissRequest = { selectedAppForDialog = null },
            title = { Text(selectedAppForDialog!!.name, color = Color(0xFF1F2937)) },
            text = { Text("اختر الإجراء المطلوب لهذا التطبيق:", color = Color(0xFF6B7280)) },
            containerColor = Color(0xFFFFFFFF),
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.runCommand("open_app", mapOf("packageName" to selectedAppForDialog!!.packageName))
                            selectedAppForDialog = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("فتح التطبيق")
                    }
                }
            }
        )
    }
}

// 3. TAB 1: DEVICE SUMMARY & GENERAL METRICS
@Composable
fun GeneralStatusTab(device: Device, viewModel: AdminViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Device name, Connection state indicator card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = device.name,
                        color = Color(0xFF1F2937),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Connection Status Lamp badge
                    val isOnline = device.isOnline
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isOnline) Color(0xFF238636).copy(alpha = 0.2f)
                                else Color(0xFF6B7280).copy(alpha = 0.2f)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) Color(0xFF238636) else Color(0xFF6B7280))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isOnline) "متصل الآن" else "غير متصل",
                            color = if (isOnline) Color(0xFF39D353) else Color(0xFF6B7280),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                val timeFormatted = remember(device.lastActive) {
                    if (device.lastActive == 0L) "متاح أبداً" 
                    else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(device.lastActive))
                }
                Text(
                    text = "آخر نشاط: $timeFormatted",
                    color = Color(0xFF6B7280),
                    fontSize = 12.sp
                )

                Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFE5E7EB))

                // Lock Device state toggling switch panel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (device.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (device.isLocked) Color(0xFFFF4081) else Color(0xFF238636),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (device.isLocked) "الجهاز مقفل حالياً" else "الجهاز حر ومفتوح",
                                color = Color(0xFF1F2937),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "يمكنك قفل الشاشة لمنع استخدامه",
                                color = Color(0xFF6B7280),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Dual fast lock/unlock buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.changeLockStatus(true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (device.isLocked) Color(0xFFFF4081).copy(alpha = 0.3f) else Color(0xFFFF4081),
                                contentColor = Color(0xFF1F2937)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("قفل", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.changeLockStatus(false) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!device.isLocked) Color(0xFF238636).copy(alpha = 0.3f) else Color(0xFF238636),
                                contentColor = Color(0xFF1F2937)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("إلغاء", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Diagnostic / Battery indicator Card
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = getBatteryIcon(device.battery),
                        contentDescription = null,
                        tint = getBatteryColor(device.battery),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("البطارية", color = Color(0xFF6B7280), fontSize = 12.sp)
                    Text(
                        text = "${device.battery}%",
                        color = Color(0xFF1F2937),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                val usagePercentage = if (device.storageTotal > 0) {
                    ((device.storageUsed.toDouble() / device.storageTotal.toDouble()) * 100).toInt()
                } else {
                    0
                }
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = Color(0xFF9155FF),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("مساحة التخزين", color = Color(0xFF6B7280), fontSize = 12.sp)
                    Text(
                        text = "$usagePercentage%",
                        color = Color(0xFF1F2937),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Detailed Storage Visual Progress bar Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "تفاصيل المساحة التخزينية لجهاز الطفل",
                    color = Color(0xFF1F2937),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val usedGb = if (device.storageUsed > 1_000_000)
                    String.format(Locale.ENGLISH, "%.2f", device.storageUsed.toDouble() / (1024.0 * 1024.0 * 1024.0))
                else
                    String.format(Locale.ENGLISH, "%.2f", device.storageUsed.toDouble())
                
                val totalGb = if (device.storageTotal > 1_000_000)
                    String.format(Locale.ENGLISH, "%.2f", device.storageTotal.toDouble() / (1024.0 * 1024.0 * 1024.0))
                else
                    String.format(Locale.ENGLISH, "%.2f", device.storageTotal.toDouble())
                val progressValue = if (device.storageTotal > 0) {
                    (device.storageUsed.toFloat() / device.storageTotal.toFloat())
                } else {
                    0f
                }

                LinearProgressIndicator(
                    progress = { progressValue },
                    color = Color(0xFF9155FF),
                    trackColor = Color(0xFFE5E7EB),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "المستخدم: $usedGb جيجا",
                        color = Color(0xFF6B7280),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "الإجمالي: $totalGb جيجا",
                        color = Color(0xFF6B7280),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Info disclaimer card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE5E7EB)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFFFF4081),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "هذا لوحة التحكم الخاصة بالمشرف ومزامنتها تامة مع برنامج الطفل لحمايته وتتبع نشاطاته بطريقة قانونية وعلم الوالدين.",
                    color = Color(0xFF374151),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// 4. TAB 2: REMOTE OPERATIONS (SCREENSHOTS, CAM, AUDIO, APPS)
@Composable
fun RemoteCommandCenterTab(viewModel: AdminViewModel) {
    val screenshots by viewModel.screenshots.collectAsState()
    val cameraPhotos by viewModel.cameraPhotos.collectAsState()
    val isStreamingActive by remember(viewModel) { viewModel.liveStreamState.map { it?.isActive == true }.distinctUntilChanged() }.collectAsState(initial = false)
    val isLoading by remember(viewModel) { viewModel.liveStreamState.map { it?.isLoading == true }.distinctUntilChanged() }.collectAsState(initial = false)
    val audioRecords by viewModel.audioRecords.collectAsState()
    val cameraVideos by viewModel.cameraVideos.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()

    val isAudioPlaying by viewModel.isAudioPlaying.collectAsState()
    val audioDuration by viewModel.audioDuration.collectAsState()
    val audioPosition by viewModel.audioPosition.collectAsState()

    var activeCommandSubIndex by remember { mutableIntStateOf(0) }
    var showFullscreenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var userAppsSearchQuery by remember { mutableStateOf("") }
    var userAppsFilterIsSystem by remember { mutableStateOf<Boolean?>(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Mode switch selectors
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .background(Color(0xFFFFFFFF), RoundedCornerShape(8.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val items = listOf("الكاميرا", "الصوت", "التطبيقات", "الفيديو")
            items.forEachIndexed { idx, title ->
                Button(
                    onClick = { activeCommandSubIndex = idx },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeCommandSubIndex == idx) Color(0xFF9155FF) else Color.Transparent,
                        contentColor = if (activeCommandSubIndex == idx) Color(0xFF1F2937) else Color(0xFF6B7280)
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp), color = Color(0xFFE5E7EB))

        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            when (activeCommandSubIndex) {
                0 -> {
                    // SCREENSHOT AND PHOTO DISPATCH PANEL
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 📺 Live Stream Card... (Keep as is since it's real-time)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, if (isStreamingActive) Color(0xFF39D353) else if(isLoading) Color(0xFF9155FF) else Color(0xFFE5E7EB)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Tv, null, tint = if (isStreamingActive) Color(0xFF39D353) else Color(0xFF6B7280), modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("بث الشاشة الحي", color = Color(0xFF1F2937), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            Text("مراقبة شاشة الهاتف لحظياً", color = Color(0xFF6B7280), fontSize = 11.sp)
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (isStreamingActive) Color(0xFF238636).copy(alpha = 0.2f) else if(isLoading) Color(0xFF9155FF).copy(alpha = 0.2f) else Color(0xFFFF4081).copy(alpha = 0.2f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (isStreamingActive) Color(0xFF39D353) else if(isLoading) Color(0xFF9155FF) else Color(0xFFFF4081)))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isLoading) "تحميل" else if (isStreamingActive) "نشط" else "متوقف", color = if (isStreamingActive) Color(0xFF39D353) else if(isLoading) Color(0xFF9155FF) else Color(0xFFFF4081), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFE5E7EB))
                                Box(modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF9FAFB)).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                    val bitmap by produceState<Bitmap?>(initialValue = null, viewModel) {
                                        viewModel.liveStreamState.map { Pair(it?.image, it?.timestamp) }.distinctUntilChanged().conflate().collect { pair ->
                                            val imgStr = pair.first
                                            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { imgStr?.let { com.example.admin.LiveStreamState(image = it).toBitmap() } }
                                        }
                                    }
                                    if (isLoading) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(color = Color(0xFF9155FF), modifier = Modifier.size(36.dp))
                                            Spacer(Modifier.height(8.dp))
                                            Text("جاري الاتصال بهاتف الطفل...", color = Color(0xFF6B7280), fontSize = 12.sp)
                                        }
                                    } else if (isStreamingActive && bitmap != null) {
                                        Image(bitmap!!.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                        Box(modifier = Modifier.align(Alignment.TopStart).padding(12.dp).background(Color(0xFFEF4444), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF1F2937)))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("مباشر", color = Color(0xFF1F2937), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        IconButton(onClick = { showFullscreenBitmap = bitmap }, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape)) {
                                            Icon(Icons.Default.Fullscreen, null, tint = Color(0xFF1F2937))
                                        }
                                    } else if (isStreamingActive) {
                                        CircularProgressIndicator(color = Color(0xFF9155FF), modifier = Modifier.size(36.dp))
                                    } else {
                                        Icon(Icons.Default.TvOff, null, tint = Color(0xFF6B7280).copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(onClick = { viewModel.startLiveStream() }, enabled = !isStreamingActive && !isLoading, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)), shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f).height(46.dp)) {
                                        Icon(Icons.Default.PlayArrow, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("بدء البث", fontSize = 12.sp)
                                    }
                                    Button(onClick = { viewModel.stopLiveStream() }, enabled = isStreamingActive || isLoading, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)), shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f).height(46.dp)) {
                                        Icon(if(isLoading) Icons.Default.Cancel else Icons.Default.Stop, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(if(isLoading) "إلغاء الطلب" else "إيقاف", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFE5E7EB))

                        // Controls
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.requestScreenshot() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9155FF)), modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp)) {
                                Icon(Icons.Default.Screenshot, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("لقطة شاشة", fontSize = 12.sp)
                            }
                            Button(onClick = { viewModel.requestPhoto(false) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFFFFF)), border = BorderStroke(1.dp, Color(0xFFE5E7EB)), modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp)) {
                                Icon(Icons.Default.Camera, null, modifier = Modifier.size(18.dp), tint = Color(0xFFFF4081))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("صورة خلفية", fontSize = 12.sp)
                            }
                        }
                        Button(onClick = { viewModel.requestPhoto(true) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFFFFF)), border = BorderStroke(1.dp, Color(0xFFE5E7EB)), modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(10.dp)) {
                            Icon(Icons.Default.CameraFront, null, modifier = Modifier.size(18.dp), tint = Color.Cyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("صورة أمامية", fontSize = 12.sp)
                        }

                        // Bento Grids
                        Text("لقطات الشاشة المُخزنة", color = Color(0xFF1F2937), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        BentoMediaGrid(
                            items = screenshots,
                            category = "screenshots",
                            onDelete = { viewModel.deleteMediaItem("screenshots", it.id) },
                            onExpand = { showFullscreenBitmap = it },
                            onSave = { bmp -> saveBitmapToGallery(context, bmp, "sc_${System.currentTimeMillis()}") }
                        )

                        HorizontalDivider(color = Color(0xFFE5E7EB))

                        Text("سجل صور الكاميرا", color = Color(0xFF1F2937), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        BentoMediaGrid(
                            items = cameraPhotos,
                            category = "camera_photos",
                            onDelete = { viewModel.deleteMediaItem("camera_photos", it.id) },
                            onExpand = { showFullscreenBitmap = it },
                            onSave = { bmp -> saveBitmapToGallery(context, bmp, "cam_${System.currentTimeMillis()}") }
                        )
                    }
                }
                1 -> {
                    // AMBIENT SOUND TAB...
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Mic, null, tint = Color(0xFF9155FF), modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text("التسجيل الصوتي المحيطي", color = Color(0xFF1F2937), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text("استماع للأصوات بجانب هاتف الطفل", color = Color(0xFF6B7280), fontSize = 11.sp)
                                    }
                                }
                                Button(onClick = { viewModel.requestAudioRecord() }, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9155FF)), shape = RoundedCornerShape(10.dp)) {
                                    Text("ابدأ تسجيل 30 ثانية الآن", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Text("التسجيلات المستلمة", color = Color(0xFF1F2937), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        if (audioRecords.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                Text("لا توجد تسجيلات صوتية", color = Color(0xFF6B7280), fontSize = 12.sp)
                            }
                        } else {
                            audioRecords.forEach { item ->
                                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)), border = BorderStroke(1.dp, Color(0xFFE5E7EB)), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { viewModel.loadAndPlayAudio(item.base64) }) {
                                            Icon(Icons.Default.PlayCircle, null, tint = Color.Green, modifier = Modifier.size(32.dp))
                                        }
                                        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                            Text("تسجيل صوتي", color = Color(0xFF1F2937), fontSize = 13.sp)
                                            Text(SimpleDateFormat("h:mm a, d/M", Locale.getDefault()).format(Date(item.timestamp)), color = Color(0xFF6B7280), fontSize = 10.sp)
                                        }
                                        IconButton(onClick = { viewModel.deleteMediaItem("audio_records", item.id) }) {
                                            Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // APPS TAB... (existing logic is fine, maybe add search/filter if needed)
                    Column(modifier = Modifier.fillMaxSize()) {
                        Button(onClick = { viewModel.requestAppsList() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081))) {
                            Text("تحديث قائمة التطبيقات")
                        }
                        Spacer(Modifier.height(8.dp))
                        // Simplified apps list
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(installedApps) { app ->
                                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Apps, null, tint = Color(0xFF1F2937))
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(app.name, color = Color(0xFF1F2937), fontWeight = FontWeight.Bold)
                                            Text(app.packageName, color = Color(0xFF6B7280), fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // VIDEO RECORDING PANEL
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                            border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Videocam, null, tint = Color(0xFFEF4444), modifier = Modifier.size(36.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("تسجيل فيديو عن بعد", color = Color(0xFF1F2937), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("سيتم تسجيل فيديو لمدة 10 ثوانٍ من كاميرا الطفل", color = Color(0xFF6B7280), fontSize = 11.sp)
                                Spacer(Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        onClick = { viewModel.requestVideo(false) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                        modifier = Modifier.weight(1f).height(46.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.CameraRear, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("خلفية", fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = { viewModel.requestVideo(true) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                        modifier = Modifier.weight(1f).height(46.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.CameraFront, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("أمامية", color = Color.Black, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VideoLibrary, null, tint = Color(0xFF6B7280), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("سجل الفيديوهات المستلمة", color = Color(0xFF1F2937), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text("${cameraVideos.size} فيديو", color = Color(0xFF6B7280), fontSize = 10.sp)
                            }
                            
                            if (cameraVideos.isEmpty()) {
                                EmptyHistoryPlaceholder("لم يتم استقبال أي فيديوهات بعد")
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    cameraVideos.forEach { video ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                                            border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.PlayCircle, null, tint = Color(0xFFEF4444), modifier = Modifier.size(32.dp))
                                                Spacer(Modifier.width(12.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text("فيديو مسجل", color = Color(0xFF1F2937), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Text(
                                                        SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault()).format(Date(video.timestamp)),
                                                        color = Color(0xFF6B7280),
                                                        fontSize = 11.sp
                                                    )
                                                }
                                                IconButton(onClick = { viewModel.deleteMediaItem("camera_videos", video.id) }) {
                                                    Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
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
        }
    }

    showFullscreenBitmap?.let { bmp ->
        Dialog(onDismissRequest = { showFullscreenBitmap = null }) {
            Box(modifier = Modifier.fillMaxSize().clickable { showFullscreenBitmap = null }.background(Color.Black.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                Image(bmp.asImageBitmap(), null, modifier = Modifier.fillMaxWidth(0.95f), contentScale = ContentScale.Fit)
            }
        }
    }
}

// 5. TAB 3: FILE EXPLORER PANEL
@Composable
fun RemoteFileExplorerTab(viewModel: AdminViewModel) {
    val fileItems by viewModel.fileItems.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Upper Navigation Breadcrumbs
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            border = BorderStroke(1.dp, Color(0xFFE5E7EB))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "مستكشف ملفات الطفل السحابي",
                    color = Color(0xFF6B7280),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Folder, null, tint = Color(0xFF9155FF), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = currentPath,
                        color = Color(0xFF1F2937),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }
        }

        // Control Breadcrumb Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val showUp = currentPath != "/storage/emulated/0" && currentPath != "/" && currentPath.isNotEmpty()
            
            Button(
                onClick = {
                    if (showUp) {
                        val parent = currentPath.substringBeforeLast("/")
                        viewModel.exploreDirectory(if (parent.isBlank()) "/" else parent)
                    } else {
                        viewModel.exploreDirectory("/storage/emulated/0")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5E7EB)),
                enabled = showUp,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("المجلد السابق", fontSize = 11.sp)
            }

            IconButton(
                onClick = { viewModel.exploreDirectory(currentPath) },
                modifier = Modifier.background(Color(0xFFE5E7EB), CircleShape).size(36.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF1F2937), modifier = Modifier.size(18.dp))
            }
        }

        // Folders and files list
        if (fileItems.isNotEmpty()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(fileItems) { item ->
                    Card(
                        onClick = {
                            if (item.isDir) {
                                viewModel.exploreDirectory(item.path)
                            }
                        },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (item.isDir) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = if (item.isDir) Color(0xFFD29922) else Color(0xFF6B7280),
                                modifier = Modifier.size(24.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    color = Color(0xFF1F2937),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = item.date,
                                        color = Color(0xFF6B7280),
                                        fontSize = 10.sp
                                    )
                                    if (!item.isDir) {
                                        val kbGbText = remember(item.size) { formatSize(item.size) }
                                        Text(
                                            text = "• $kbGbText",
                                            color = Color(0xFF6B7280),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }

                            if (item.isDir) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color(0xFFE5E7EB)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderZip, null, tint = Color(0xFFE5E7EB), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("المجلد فارغ أو لم يتم تلقي القائمة بعد.", color = Color(0xFF6B7280), fontSize = 12.sp)
                }
            }
        }
    }
}

// 6. TAB 4: LIVE RECEPTION - SMS & SECURITY ALERTS
@Composable
fun ContactsTab(viewModel: AdminViewModel) {
    val contacts by viewModel.contacts.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(
            onClick = { viewModel.requestContacts() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9155FF))
        ) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("تحديث جهات الاتصال")
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(contacts) { contact ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = Color(0xFF9155FF).copy(alpha = 0.2f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, null, tint = Color(0xFF9155FF), modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(contact.name, color = Color(0xFF1F2937), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(contact.number, color = Color(0xFF6B7280), fontSize = 12.sp)
                        }
                    }
                }
            }
            if (contacts.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text("لا توجد جهات اتصال متاحة، جرب التحديث.", color = Color(0xFF6B7280), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SmsAndSecurityAlertsTab(viewModel: AdminViewModel) {
    val smsLogs by viewModel.smsLogs.collectAsState()
    val securityAlerts by viewModel.securityAlerts.collectAsState()

    var activeSmsSubTab by remember { mutableIntStateOf(0) } // Tabs: 0Sms, 1SecurityAlerts Wall

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Toggle indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .background(Color(0xFFFFFFFF), RoundedCornerShape(8.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = { activeSmsSubTab = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeSmsSubTab == 0) Color(0xFF9155FF) else Color.Transparent,
                    contentColor = if (activeSmsSubTab == 0) Color(0xFF1F2937) else Color(0xFF6B7280)
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Text("أرشيف الرسائل SMS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { activeSmsSubTab = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeSmsSubTab == 1) Color(0xFFFF4081) else Color.Transparent,
                    contentColor = if (activeSmsSubTab == 1) Color(0xFF1F2937) else Color(0xFF6B7280)
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (securityAlerts.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text("جدار الإنذارات الأمنية", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (activeSmsSubTab == 0) {
                // SMS LOGS DASHBOARD
                if (smsLogs.isNotEmpty()) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(smsLogs) { sms ->
                            val isIncoming = sms.type == "incoming"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isIncoming) Arrangement.Start else Arrangement.End
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isIncoming) Color(0xFFFFFFFF) else Color(0xFFF5F3FF)
                                    ),
                                    shape = RoundedCornerShape(
                                        topStart = 12.dp,
                                        topEnd = 12.dp,
                                        bottomStart = if (isIncoming) 0.dp else 12.dp,
                                        bottomEnd = if (isIncoming) 12.dp else 0.dp
                                    ),
                                    border = BorderStroke(1.dp, if (isIncoming) Color(0xFFE5E7EB) else Color(0xFF9155FF).copy(alpha = 0.5f)),
                                    modifier = Modifier.widthIn(max = 280.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = sms.sender,
                                                color = if (isIncoming) Color(0xFF00E5FF) else Color(0xFFFF4081),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            
                                            // Direction Indicator Arrow label
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFFE5E7EB))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (isIncoming) "واردة" else "صادرة",
                                                    color = if (isIncoming) Color(0xFF39D353) else Color(0xFFFF4081),
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Text(
                                            text = sms.body,
                                            color = Color(0xFF1F2937),
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))
                                        val timeStr = remember(sms.timestamp) {
                                            SimpleDateFormat("yyyy-MM-dd h:mm a", Locale.getDefault()).format(Date(sms.timestamp))
                                        }
                                        Text(
                                            text = timeStr,
                                            color = Color(0xFF6B7280),
                                            fontSize = 9.sp,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Sms, null, tint = Color(0xFFE5E7EB), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("لا يتوفر رسائل SMS مؤرشفة في النظام حالياً.", color = Color(0xFF6B7280), fontSize = 12.sp)
                        }
                    }
                }
            } else {
                // REALTIME SECURITY ALERTS WALL
                Column(modifier = Modifier.fillMaxSize()) {
                    if (securityAlerts.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "مؤشر الإنذارات:  ${securityAlerts.size} تنبيهات",
                                color = Color(0xFF1F2937),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Button(
                                onClick = { viewModel.clearAlerts() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("مسح جدار الانذارات", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(securityAlerts) { alert ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFEF4444).copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (alert.title == "DEVICE_BOOTED") "إعادة التشغيل (BOOT)" 
                                                       else if (alert.title == "BATTERY_LOW") "بطارية حرجة منخفضة" 
                                                       else alert.title,
                                                color = Color(0xFF1F2937),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = alert.message,
                                                color = Color(0xFF374151),
                                                fontSize = 11.sp
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val timeStr = remember(alert.timestamp) {
                                                SimpleDateFormat("yyyy-MM-dd h:mm a", Locale.getDefault()).format(Date(alert.timestamp))
                                            }
                                            Text(
                                                text = timeStr,
                                                color = Color(0xFF6B7280),
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.HealthAndSafety, null, tint = Color(0xFF238636), modifier = Modifier.size(64.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("لا يوجد تجاوزات خطيرة. طفلكم بأمان!", color = Color(0xFF1F2937), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("أي تنبيهات مثل هبوط البطارية أو تشغيل الهاتف ستظهر هنا فورياً.", color = Color(0xFF6B7280), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// 7. PLACEHOLDER / PAIRING NO DEVICES DETECTED SCREEN
@Composable
fun EmptyDevicesScreen(viewModel: AdminViewModel) {
    var pairingToken by remember { mutableStateOf("") }
    var friendlyName by remember { mutableStateOf("") }
    val isProgressing by viewModel.isLoading.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.SettingsInputAntenna,
            contentDescription = null,
            tint = Color(0xFF9155FF),
            modifier = Modifier.size(72.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "توصيل هاتف طفل جديد",
            color = Color(0xFF1F2937),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "الرجاء كتابة رمز الاقتران الفريد (Pairing Token) الخاص بالطفل للربط الفوري ومزامنته",
            color = Color(0xFF6B7280),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Card Entry Inputs
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(0.5.dp, Color.White),
            modifier = Modifier.fillMaxWidth().shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = Color(0xFF9155FF).copy(alpha = 0.1f)
            )
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = pairingToken,
                    onValueChange = { 
                        pairingToken = it
                        friendlyName = it
                    },
                    label = { Text("رمز الاقتران والاسم (Token)", color = Color(0xFF6B7280)) },
                    placeholder = { Text("اكتب اسم هاتف الطفل وسيتم استخدامه كرمز إقتران أيضاً..", color = Color(0xFF6B7280)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1F2937),
                        unfocusedTextColor = Color(0xFF1F2937),
                        focusedBorderColor = Color(0xFF9155FF),
                        unfocusedBorderColor = Color(0xFFE5E7EB),
                        focusedContainerColor = Color(0xFFF3F4F6),
                        unfocusedContainerColor = Color(0xFFF3F4F6)
                    ),
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = friendlyName,
                    onValueChange = { 
                        friendlyName = it
                        pairingToken = it
                    },
                    label = { Text("اسم هاتف الطفل (مثال: هاتف أحمد)", color = Color(0xFF6B7280)) },
                    placeholder = { Text("يطابق رمز الاقتران تلقائياً..", color = Color(0xFF6B7280)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1F2937),
                        unfocusedTextColor = Color(0xFF1F2937),
                        focusedBorderColor = Color(0xFF9155FF),
                        unfocusedBorderColor = Color(0xFFE5E7EB),
                        focusedContainerColor = Color(0xFFF3F4F6),
                        unfocusedContainerColor = Color(0xFFF3F4F6)
                    ),
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        if (pairingToken.isNotBlank() && friendlyName.isNotBlank()) {
                            viewModel.registerDeviceManually(pairingToken, friendlyName)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color(0xFFE5E7EB)),
                    contentPadding = PaddingValues(),
                    enabled = !isProgressing && pairingToken.isNotBlank() && friendlyName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(listOf(Color(0xFF9155FF), Color(0xFF7C3AED))))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isProgressing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text("مزامنة وإقتران في قاعدة البيانات", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditDatabaseUrlDialog(
    currentUrl: String,
    onClose: () -> Unit,
    onSave: (String) -> Unit
) {
    var urlText by remember { mutableStateOf(currentUrl) }
    
    Dialog(onDismissRequest = onClose) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(0.5.dp, Color.White),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = Color.Black.copy(alpha = 0.1f)
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تعديل رابط قاعدة البيانات",
                        color = Color(0xFF1F2937),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "اغلاق", tint = Color(0xFF6B7280))
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFE5E7EB))
                
                Text(
                    text = "أدخل الرابط الكامل لقاعدة بيانات Firebase Realtime المخصصة لك:",
                    color = Color(0xFF6B7280),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF1F2937), fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    placeholder = { Text("https://example-default-rtdb.firebaseio.com", color = Color(0xFF6B7280)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF9155FF),
                        unfocusedBorderColor = Color(0xFFE5E7EB),
                        focusedLabelColor = Color(0xFF9155FF),
                        unfocusedLabelColor = Color(0xFF6B7280)
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "خوادم ومناطق مقترحة (اضغط للتحديد):",
                    color = Color(0xFF1F2937),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Suggestions List
                val suggestions = listOf(
                    "https://studio-3242759193-af8cb-default-rtdb.europe-west1.firebasedatabase.app" to "أوروبا (طبيعي)",
                    "https://studio-3242759193-af8cb-default-rtdb.firebaseio.com" to "الولايات المتحدة (رسمي)",
                    "https://studio-3242759193-af8cb-default-rtdb.asia-southeast1.firebasedatabase.app" to "آسيا (تجريبي)"
                )
                
                suggestions.forEach { (url, label) ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE5E7EB))
                            .clickable { urlText = url }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = label, color = Color(0xFF9155FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (url.length > 35) url.take(25) + "..." else url,
                                color = Color(0xFF6B7280),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onClose) {
                        Text("إلغاء", color = Color(0xFF6B7280))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (urlText.isNotBlank()) {
                                onSave(urlText)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9155FF))
                    ) {
                        Text("حفظ وتحديث", color = Color.White)
                    }
                }
            }
        }
    }
}

// 8. POPUP SELECTOR FOR MULTIPLE CHILD DEVICES
@Composable
fun DeviceSelectionDialog(
    devicesList: List<Device>,
    currentToken: String?,
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
    onAddDevice: (token: String, name: String) -> Unit
) {
    var showAddPane by remember { mutableStateOf(false) }
    var newToken by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onClose) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(0.5.dp, Color.White),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = Color.Black.copy(alpha = 0.15f)
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showAddPane) "إضافة جهاز طفل جديد" else "أجهزة الأطفال المتصلة",
                        color = Color(0xFF1F2937),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF6B7280))
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFE5E7EB))

                if (!showAddPane) {
                    // Devices selector list
                    if (devicesList.isNotEmpty()) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 240.dp)
                        ) {
                            items(devicesList) { dev ->
                                val selected = dev.id == currentToken
                                val online = dev.isOnline
                                Card(
                                    onClick = { onSelect(dev.id) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selected) Color(0xFFE5E7EB) else Color(0xFFF3F4F6)
                                    ),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (selected) Color(0xFF9155FF) else Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(if (online) Color(0xFF238636) else Color(0xFF6B7280))
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = dev.name,
                                                color = Color(0xFF1F2937),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "الرمز الفريد: ${dev.id}",
                                                color = Color(0xFF6B7280),
                                                fontSize = 11.sp
                                            )
                                        }

                                        if (selected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color(0xFF9155FF)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("لا يتوفر أجهزة مسجلة.", color = Color(0xFF6B7280), fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showAddPane = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9155FF)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اربط طفل طارئ جديد", fontSize = 12.sp)
                    }
                } else {
                    // Create token panel UI
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = newToken,
                            onValueChange = { 
                                newToken = it
                                newName = it
                            },
                            placeholder = { Text("رمز الاقتران والاسم.. (مثل: ahmad_phone)", color = Color(0xFF6B7280)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF1F2937),
                                unfocusedTextColor = Color(0xFF1F2937),
                                focusedBorderColor = Color(0xFF9155FF),
                                unfocusedBorderColor = Color(0xFFE5E7EB),
                                focusedContainerColor = Color(0xFFF3F4F6),
                                unfocusedContainerColor = Color(0xFFF3F4F6)
                            ),
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = newName,
                            onValueChange = { 
                                newName = it
                                newToken = it
                            },
                            placeholder = { Text("اسم وكنية هاتف الطفل..", color = Color(0xFF6B7280)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF1F2937),
                                unfocusedTextColor = Color(0xFF1F2937),
                                focusedBorderColor = Color(0xFF9155FF),
                                unfocusedBorderColor = Color(0xFFE5E7EB),
                                focusedContainerColor = Color(0xFFF3F4F6),
                                unfocusedContainerColor = Color(0xFFF3F4F6)
                            ),
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showAddPane = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5E7EB)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("إلغاء", fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    if (newToken.isNotBlank() && newName.isNotBlank()) {
                                        onAddDevice(newToken, newName)
                                        showAddPane = false
                                        onClose()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9155FF)),
                                enabled = newToken.isNotBlank() && newName.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("ربط الجهاز", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// FORMAT SIZES UTILITIES
fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.ENGLISH, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

// BATTERY AUXILIARY HELPERS
fun getBatteryIcon(level: Int): ImageVector {
    return when {
        level >= 90 -> Icons.Default.BatteryFull
        level >= 70 -> Icons.Default.Battery5Bar
        level >= 40 -> Icons.Default.Battery4Bar
        level >= 15 -> Icons.Default.Battery2Bar
        else -> Icons.Default.BatteryAlert
    }
}

fun getBatteryColor(level: Int): Color {
    return when {
        level >= 40 -> Color(0xFF39D353)
        level >= 15 -> Color(0xFFD29922)
        else -> Color(0xFFEF4444)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SwipeToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val state = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    
    // Smoothly animate appearance & dismissal with beautiful spring specs
    val triggerAlpha by animateFloatAsState(
        targetValue = if (isRefreshing || state.distanceFraction > 0.05f) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 250)
    )
    val triggerScale by animateFloatAsState(
        targetValue = if (isRefreshing || state.distanceFraction > 0.05f) 1f else 0.85f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        )
    )
    val triggerOffsetY by animateFloatAsState(
        targetValue = if (isRefreshing || state.distanceFraction > 0.05f) 16f else -30f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        )
    )

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier,
        indicator = {
            if (triggerAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            alpha = triggerAlpha
                            scaleX = triggerScale
                            scaleY = triggerScale
                            translationY = triggerOffsetY.dp.toPx()
                        }
                        .shadow(elevation = 12.dp, shape = RoundedCornerShape(24.dp), clip = false)
                        .background(Color.White, RoundedCornerShape(24.dp))
                        .border(1.dp, Color(0xFFF3F4F6), RoundedCornerShape(24.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isRefreshing) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFF9155FF),
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = "جاري تحديث البيانات الآن...",
                                color = Color(0xFF111827),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            val rotation = state.distanceFraction * 360f
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color(0xFF9155FF),
                                modifier = Modifier
                                    .size(16.dp)
                                    .graphicsLayer { rotationZ = rotation }
                            )
                            val pullPercent = (state.distanceFraction * 100).coerceAtMost(100f).toInt()
                            Text(
                                text = if (state.distanceFraction >= 1f) "اترك للتحديث فوراً" else "اسحب للتحديث ($pullPercent%)",
                                color = Color(0xFF4B5563),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    ) {
        content()
    }
}

@Composable
fun VideoPlayerDialog(
    videoBase64: String,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    val context = LocalContext.current
    var isPreparing by remember { mutableStateOf(true) }
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    LaunchedEffect(videoBase64) {
        withContext(Dispatchers.IO) {
            try {
                val cleanBase64 = if (videoBase64.contains(",")) videoBase64.substringAfter(",") else videoBase64
                val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                val tempFile = File(context.cacheDir, "temp_video_play_${System.currentTimeMillis()}.mp4")
                FileOutputStream(tempFile).use { out ->
                    out.write(bytes)
                }
                withContext(Dispatchers.Main) {
                    exoPlayer.setMediaItem(Media3MediaItem.fromUri(tempFile.absolutePath))
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    isPreparing = false
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "خطأ في تجهيز الفيديو: ${t.message}", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.8f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFE5E7EB))
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("مشغل الفيديو", color = Color(0xFF1F2937), fontWeight = FontWeight.Bold)
                    Row {
                        IconButton(onClick = onDownload, enabled = !isPreparing) {
                            Icon(Icons.Default.Download, "تنزيل", tint = if (isPreparing) Color.Gray else Color.White, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "إغلاق", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPreparing) {
                        CircularProgressIndicator(color = Color(0xFF39D353))
                    } else {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = true
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiveStreamPlayer(
    streamUrl: String,
    deviceToken: String? = null,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(modifier = modifier) {
        val exoPlayer = remember(streamUrl) {
            val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(500, 2000, 500, 500)
                .build()
            
            ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .build().apply {
                val mediaItem = Media3MediaItem.fromUri(streamUrl)
                if (streamUrl.startsWith("rtsp://", ignoreCase = true)) {
                    val mediaSource = RtspMediaSource.Factory()
                        .setForceUseRtpTcp(true)
                        .setTimeoutMs(3000)
                        .createMediaSource(mediaItem)
                    setMediaSource(mediaSource)
                } else {
                    setMediaItem(mediaItem)
                }
                playWhenReady = true
                prepare()
            }
        }

        DisposableEffect(exoPlayer) {
            onDispose {
                exoPlayer.release()
            }
        }

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.TopEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.7f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color(0xFF39D353), androidx.compose.foundation.shape.CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "بث مشغل الفيديو الآمن الفوري",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
