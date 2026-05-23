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
import androidx.compose.ui.window.Dialog
import com.example.admin.*
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*

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

@Composable
fun AppNavigation(viewModel: AdminViewModel) {
    val isUnlocked by viewModel.isUnlocked.collectAsState()

    AnimatedContent(
        targetState = isUnlocked,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "LockScreenTransition"
    ) { Unlocked ->
        if (!Unlocked) {
            PinLockScreen(
                onUnlockSuccess = { pin ->
                    viewModel.unlockWithPin(pin)
                }
            )
        } else {
            AdminDashboard(viewModel)
        }
    }
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
            .background(Color(0xFF0B0E14)) // Cosmic deep black
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "قفل المشرف",
            tint = Color(0xFF9155FF),
            modifier = Modifier
                .size(72.dp)
                .padding(bottom = 16.dp)
        )

        Text(
            text = "تأكيد هوية المشرف",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "الرجاء إدخال الرمز السري للدخول للوحة المراقبة",
            color = Color(0xFF8B949E),
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
                            if (errorAlert) Color(0xFFDA3633)
                            else if (filled) Color(0xFF9155FF)
                            else Color(0xFF21262D)
                        )
                        .border(
                            width = 2.dp,
                            color = if (errorAlert) Color(0xFFDA3633) else Color(0xFF30363D),
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
                                containerColor = if (key == "C" || key == "⌫") Color(0xFF21262D) else Color(0xFF161B22),
                                contentColor = if (key == "C" || key == "⌫") Color(0xFFFF4081) else Color.White
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
            color = Color(0xFF8B949E).copy(alpha = 0.5f),
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
                .background(Color(0xFF0B0E14))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "التحكم والمراقبة الأبوية",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "اختر جهاز طفل للملخصات والتحكم البعيد",
                        color = Color(0xFF8B949E),
                        fontSize = 11.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showAddDeviceDialog = true },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF9155FF))
                            .size(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "إضافة جهاز", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { viewModel.lockPIN() }) {
                        Icon(Icons.Default.Lock, contentDescription = "قفل التطبيق", tint = Color(0xFFFF4081))
                    }
                }
            }

            TabRow(
                selectedTabIndex = deviceListTabSelected,
                containerColor = Color(0xFF161B22),
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[deviceListTabSelected]),
                        color = Color(0xFF9155FF)
                    )
                }
            ) {
                Tab(
                    selected = deviceListTabSelected == 0,
                    onClick = { deviceListTabSelected = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhoneAndroid, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("جميع الأجهزة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = deviceListTabSelected == 1,
                    onClick = { deviceListTabSelected = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF39D353)))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("الأجهزة النشطة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = deviceListTabSelected == 2,
                    onClick = { deviceListTabSelected = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF8B949E)))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("الأجهزة غير النشطة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            val filteredDevices = when (deviceListTabSelected) {
                0 -> devices
                1 -> devices.filter { it.isOnline }
                else -> devices.filter { !it.isOnline }
            }.sortedByDescending { it.isOnline }

            if (filteredDevices.isEmpty()) {
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
                            tint = Color(0xFF8B949E).copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when (deviceListTabSelected) {
                                0 -> "لا توجد أجهزة متصلة بقاعدة البيانات حالياً"
                                1 -> "لا توجد أجهزة نشطة حالياً (نشط خلال 15 دقيقة)"
                                else -> "لا توجد أجهزة غير نشطة حالياً"
                            },
                            color = Color(0xFF8B949E),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // Diagnostics Panel
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                            border = BorderStroke(1.dp, Color(0xFF30363D)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Cloud,
                                        contentDescription = null,
                                        tint = Color(0xFF9155FF),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "معلومات الاتصال بالخادم والتشخيص",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                Text(
                                    text = "الرابط الحالي المستخدم لقاعدة البيانات:",
                                    color = Color(0xFF8B949E),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = currentDbUrl,
                                    color = Color(0xFF58A6FF),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                                
                                if (connectionError != null) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFFF3366).copy(alpha = 0.08f))
                                            .border(1.dp, Color(0xFFFF3366).copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = Color(0xFFFF3366),
                                            modifier = Modifier.size(14.dp).padding(top = 2.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = "حالة الاتصال: فشل الاتصال",
                                                color = Color(0xFFFF3366),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = connectionError ?: "",
                                                color = Color(0xFFCE93D8),
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF39D353)))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "الاتصال بالخادم نجح (أو بانتظار تحديث البيانات)",
                                            color = Color(0xFF39D353),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(14.dp))
                                
                                Button(
                                    onClick = { showEditDbDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    border = BorderStroke(1.dp, Color(0xFF30363D)),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "تغيير رابط قاعدة البيانات يدوياً",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredDevices) { dev ->
                        Card(
                            onClick = { viewModel.selectDevice(dev.id) },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                            border = BorderStroke(1.dp, Color(0xFF30363D)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF9155FF).copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.PhoneAndroid, null, tint = Color(0xFF9155FF), modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(dev.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        if (dev.isOnline) {
                                            Text("متصل الآن • رعاية نشطة", color = Color(0xFF39D353), fontSize = 11.sp)
                                        } else {
                                            Text(getRelativeTimeString(dev.lastActive), color = Color(0xFFFFD54F), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(getBatteryIcon(dev.battery), null, tint = getBatteryColor(dev.battery), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("${dev.battery}%", color = Color(0xFF8B949E), fontSize = 11.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(Icons.Default.ArrowForwardIos, null, tint = Color(0xFF8B949E), modifier = Modifier.size(12.dp))
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
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF161B22), contentColor = Color.White) {
                    NavigationBarItem(
                        selected = bottomNavSelectedTab == 0,
                        onClick = {
                            bottomNavSelectedTab = 0
                            openCommandDetails = null
                        },
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("الرئيسية", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color(0xFF9155FF),
                            indicatorColor = Color(0xFF9155FF),
                            unselectedIconColor = Color(0xFF8B949E),
                            unselectedTextColor = Color(0xFF8B949E)
                        )
                    )
                    NavigationBarItem(
                        selected = bottomNavSelectedTab == 1,
                        onClick = { bottomNavSelectedTab = 1 },
                        icon = { Icon(Icons.Default.List, null) },
                        label = { Text("الأوامر", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color(0xFF9155FF),
                            indicatorColor = Color(0xFF9155FF),
                            unselectedIconColor = Color(0xFF8B949E),
                            unselectedTextColor = Color(0xFF8B949E)
                        )
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0B0E14))
                    .padding(innerPadding)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF161B22))
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.selectDevice("") }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(activeDevice.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (activeDevice.isOnline) Color(0xFF39D353) else Color(0xFF8B949E)))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (activeDevice.isOnline) "متصل الآن" else "غير متصل", color = if (activeDevice.isOnline) Color(0xFF39D353) else Color(0xFF8B949E), fontSize = 11.sp)
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.lockPIN() }) {
                        Icon(Icons.Default.Lock, null, tint = Color(0xFFFF4081))
                    }
                }

                commandResponse?.let { resp ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (resp.first == "success") Color(0xFF1B4721) else Color(0xFF4C1C1B)),
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (resp.first == "success") Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("استجابة جهاز الطفل: ${resp.second}", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }

                if (bottomNavSelectedTab == 0) {
                    DeviceHomeTab(activeDevice, viewModel)
                } else {
                    DeviceCommandsTab(activeDevice, viewModel, openCommandDetails, onOpenCommand = { openCommandDetails = it })
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
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                border = BorderStroke(1.dp, Color(0xFF30363D))
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
                        color = Color.White,
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
                                        CommandStepStatus.FAILED -> Color(0xFFDA3633).copy(alpha = 0.2f)
                                        else -> Color(0xFF21262D)
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
                                    Icon(Icons.Default.Close, null, tint = Color(0xFFDA3633), modifier = Modifier.size(18.dp))
                                }
                                else -> {
                                    Text("١", color = Color(0xFF8B949E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "المرحلة الأولى: إرسال الأمر للجهاز",
                                color = Color.White,
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
                                color = Color(0xFF8B949E),
                                fontSize = 11.sp
                            )
                        }
                    }

                    progress.sendError?.let { err ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFDA3633).copy(alpha = 0.1f)),
                            border = BorderStroke(1.dp, Color(0xFFDA3633).copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 44.dp, bottom = 10.dp)
                        ) {
                            Text(
                                text = "خطأ بالتفصيل: $err",
                                color = Color(0xFFF85149),
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
                            .background(Color(0xFF30363D))
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
                                        CommandStepStatus.FAILED -> Color(0xFFDA3633).copy(alpha = 0.2f)
                                        else -> Color(0xFF21262D)
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
                                    Icon(Icons.Default.Close, null, tint = Color(0xFFDA3633), modifier = Modifier.size(18.dp))
                                }
                                else -> {
                                    Text("٢", color = Color(0xFF8B949E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "المرحلة الثانية: قبول وتنفيذ هاتف الطفل للأمر",
                                color = Color.White,
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
                                color = Color(0xFF8B949E),
                                fontSize = 11.sp
                            )
                        }
                    }

                    progress.executionError?.let { err ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFDA3633).copy(alpha = 0.1f)),
                            border = BorderStroke(1.dp, Color(0xFFDA3633).copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 44.dp, bottom = 10.dp)
                        ) {
                            Text(
                                text = "تفاصيل الخطأ: $err",
                                color = Color(0xFFF85149),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    progress.resultMessage?.let { msg ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F242C)),
                            border = BorderStroke(1.dp, Color(0xFF30363D)),
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
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Button(
                        onClick = { viewModel.clearActiveCommandProgress() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (progress.executionStatus == CommandStepStatus.RUNNING || progress.sendStatus == CommandStepStatus.RUNNING) Color(0xFF21262D) else Color(0xFF9155FF)
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
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                border = BorderStroke(1.dp, Color(0xFF30363D))
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
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewModel.clearDirectScreenshot() }) {
                            Icon(Icons.Default.Close, null, tint = Color.White)
                        }
                    }

                    Text(
                        text = "تم التقاطها: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp)),
                        color = Color(0xFF8B949E),
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
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
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                border = BorderStroke(1.dp, Color(0xFF30363D))
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
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewModel.clearDirectPhoto() }) {
                            Icon(Icons.Default.Close, null, tint = Color.White)
                        }
                    }

                    Text(
                        text = "الكاميرا المستعملة: " + (if (item.cameraType == "front" || item.type == "camera_front") "الأمامية" else "الخلفية") + " | " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp)),
                        color = Color(0xFF8B949E),
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF30363D)),
            modifier = Modifier.fillMaxWidth()
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
                        Text(if (device.isLocked) "الجهاز مقفل حالياً" else "الجهاز حر ومفتوح", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("قفل أو فك قفل شاشة الطفل لمنع استخدامه", color = Color(0xFF8B949E), fontSize = 10.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { viewModel.changeLockStatus(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Text("قفل الهاتف", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.changeLockStatus(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Text("فك القفل", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                border = BorderStroke(1.dp, Color(0xFF30363D)),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(getBatteryIcon(device.battery), null, tint = getBatteryColor(device.battery), modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("البطارية", color = Color(0xFF8B949E), fontSize = 11.sp)
                    Text("${device.battery}%", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                border = BorderStroke(1.dp, Color(0xFF30363D)),
                modifier = Modifier.weight(1f)
            ) {
                val usagePercentage = if (device.storageTotal > 0) ((device.storageUsed.toDouble() / device.storageTotal.toDouble()) * 100).toInt() else 0
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Storage, null, tint = Color(0xFF9155FF), modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("مساحة التخزين", color = Color(0xFF8B949E), fontSize = 11.sp)
                    Text("$usagePercentage%", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            border = BorderStroke(1.dp, Color(0xFF30363D)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("تفاصيل مساحة التخزين", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                val usedGb = String.format(Locale.ENGLISH, "%.2f", device.storageUsed.toDouble() / (1024.0 * 1024.0 * 1024.0))
                val totalGb = String.format(Locale.ENGLISH, "%.2f", device.storageTotal.toDouble() / (1024.0 * 1024.0 * 1024.0))
                val progress = if (device.storageTotal > 0) (device.storageUsed.toFloat() / device.storageTotal.toFloat()) else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    color = Color(0xFF9155FF),
                    trackColor = Color(0xFF21262D),
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("المستخدم: $usedGb جيجا", color = Color(0xFF8B949E), fontSize = 11.sp)
                    Text("الإجمالي: $totalGb جيجا", color = Color(0xFF8B949E), fontSize = 11.sp)
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
            Text("قائمة الأوامر الفورية", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            
            val cmdItems = listOf(
                CommandItemInfo("screenshot", "لقطة الشاشة والكاميرات", "التقاط لقطة شاشة هاتف الطفل أو صور حية بالكاميرا", Icons.Default.Screenshot, Color(0xFF9155FF)),
                CommandItemInfo("audio_record", "تسجيل الصوت المحيطي", "تسجيل مقطع صوتي محيطي بالوقت الحقيقي والاستماع إليه", Icons.Default.Mic, Color(0xFF00E5FF)),
                CommandItemInfo("live_stream", "البث المباشر للشاشة", "مراقبة شاشة الهاتف لحظياً وبطريقة آمنة بالكامل", Icons.Default.Tv, Color(0xFF39D353)),
                CommandItemInfo("file_explorer", "مستكشف ملفات الهاتف", "استكشاف وتنزيل ملفات جهاز الطفل بالكامل", Icons.Default.FolderOpen, Color(0xFFFFD54F)),
                CommandItemInfo("apps", "قائمة التطبيقات وحزمها", "الاطلاع وفلترة التطبيقات المنصبة على الهاتف للأمان", Icons.Default.Apps, Color(0xFFFF4081)),
                CommandItemInfo("sms", "الرسائل وتنبيهات الأمان", "مزامنة الرسائل النصية والتنبيهات المكتشفة بالهاتف", Icons.Default.Sms, Color(0xFFFF9100))
            )

            cmdItems.forEach { cmd ->
                Card(
                    onClick = { onOpenCommand(cmd.id) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                    border = BorderStroke(1.dp, Color(0xFF30363D)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
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
                            Text(cmd.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(cmd.description, color = Color(0xFF8B949E), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Default.ArrowForwardIos, null, tint = Color(0xFF8B949E), modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF21262D)).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onOpenCommand(null) }) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when (openCommandDetails) {
                        "screenshot" -> "متطلبات لقطة الشاشة والكاميرات"
                        "audio_record" -> "متطلبات تسجيل الصوت المحيطي"
                        "live_stream" -> "متطلبات البث المباشر للشاشة"
                        "file_explorer" -> "مستكشف الملفات البعيد للطفل"
                        "apps" -> "إدارة وجرد التطبيقات المثبتة"
                        "sms" -> "الأرشيف والرسائل وتنبيهات الأمان"
                        else -> "لوحة التحكم بالأمر"
                    },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (openCommandDetails) {
                    "screenshot" -> ScreenshotRequirementsPage(viewModel)
                    "audio_record" -> AudioRecordRequirementsPage(viewModel)
                    "live_stream" -> LiveStreamRequirementsPage(viewModel)
                    "file_explorer" -> RemoteFileExplorerTab(viewModel)
                    "apps" -> InstalledAppsRequirementsPage(viewModel)
                    "sms" -> SmsAndSecurityAlertsTab(viewModel)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScreenshotRequirementsPage(viewModel: AdminViewModel) {
    val screenshot by viewModel.screenshot.collectAsState()
    val cameraPhoto by viewModel.cameraPhoto.collectAsState()
    val context = LocalContext.current

    var fullscreenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var actionScreenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var actionCamBitmap by remember { mutableStateOf<Bitmap?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { viewModel.requestScreenshot() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9155FF)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(42.dp)
            ) {
                Icon(Icons.Default.Screenshot, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("خذ لقطة شاشة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { viewModel.requestPhoto(false) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B22)),
                border = BorderStroke(1.dp, Color(0xFF30363D)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(42.dp)
            ) {
                Icon(Icons.Default.Camera, null, tint = Color(0xFFFF4081), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("كاميرا خلفية", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Button(
            onClick = { viewModel.requestPhoto(true) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B22)),
            border = BorderStroke(1.dp, Color(0xFF30363D)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().height(42.dp)
        ) {
            Icon(Icons.Default.CameraFront, null, tint = Color.Cyan, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("التقاط صورة كاميرا أمامية", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Divider(color = Color(0xFF30363D))

        screenshot?.let { item ->
            val bitmap by produceState<Bitmap?>(initialValue = null, item.base64) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { item.toBitmap() }
            }
            Column(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF161B22), RoundedCornerShape(10.dp)).border(1.dp, Color(0xFF30363D), RoundedCornerShape(10.dp)).padding(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("آخر لقطة شاشة مستلمة", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(item.timestamp)), color = Color(0xFF8B949E), fontSize = 10.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (bitmap != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(6.dp)).combinedClickable(
                            onClick = { fullscreenBitmap = bitmap },
                            onLongClick = { actionScreenBitmap = bitmap }
                        )
                    ) {
                        Image(bitmap!!.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(4.dp)) {
                            Text("انقر للتكبير • مطولاً للخيارات", color = Color.White, fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        cameraPhoto?.let { item ->
            val bitmap by produceState<Bitmap?>(initialValue = null, item.base64) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { item.toBitmap() }
            }
            Column(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF161B22), RoundedCornerShape(10.dp)).border(1.dp, Color(0xFF30363D), RoundedCornerShape(10.dp)).padding(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("صورة كاميرا الطفل: " + (if(item.cameraType == "front") "الأمامية" else "الخلفية"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(item.timestamp)), color = Color(0xFF8B949E), fontSize = 10.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (bitmap != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(6.dp)).combinedClickable(
                            onClick = { fullscreenBitmap = bitmap },
                            onLongClick = { actionCamBitmap = bitmap }
                        )
                    ) {
                        Image(bitmap!!.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(4.dp)) {
                            Text("انقر للتكبير • مطولاً للخيارات", color = Color.White, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }

    fullscreenBitmap?.let { bmp ->
        Dialog(onDismissRequest = { fullscreenBitmap = null }) {
            Box(modifier = Modifier.fillMaxSize().clickable { fullscreenBitmap = null }.background(Color.Black.copy(alpha = 0.95f)), contentAlignment = Alignment.Center) {
                Image(bmp.asImageBitmap(), null, modifier = Modifier.fillMaxWidth(0.95f), contentScale = ContentScale.Fit)
            }
        }
    }

    actionScreenBitmap?.let { bmp ->
        Dialog(onDismissRequest = { actionScreenBitmap = null }) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFF30363D)), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("خيارات لقطة الشاشة", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Divider(color = Color(0xFF30363D))
                    Button(
                        onClick = {
                            val ok = saveBitmapToGallery(context, bmp, "screenshot_${System.currentTimeMillis()}")
                            Toast.makeText(context, if(ok) "تم الحفظ بنجاح!" else "فشل الحفظ", Toast.LENGTH_SHORT).show()
                            actionScreenBitmap = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("حفظ في معرض وسائط المشرف")
                    }
                    Button(
                        onClick = {
                            Toast.makeText(context, "تم الإخفاء", Toast.LENGTH_SHORT).show()
                            actionScreenBitmap = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDA3633)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("حذف الإطار من لوحة المعاينة")
                    }
                    OutlinedButton(onClick = { actionScreenBitmap = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("إلغاء", color = Color.White)
                    }
                }
            }
        }
    }

    actionCamBitmap?.let { bmp ->
        Dialog(onDismissRequest = { actionCamBitmap = null }) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFF30363D)), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("خيارات الكاميرا", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Divider(color = Color(0xFF30363D))
                    Button(
                        onClick = {
                            val ok = saveBitmapToGallery(context, bmp, "photo_${System.currentTimeMillis()}")
                            Toast.makeText(context, if(ok) "تم الحفظ بنجاح!" else "فشل الحفظ", Toast.LENGTH_SHORT).show()
                            actionCamBitmap = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("حفظ في معرض وسائط المشرف")
                    }
                    Button(
                        onClick = {
                            Toast.makeText(context, "تم الإخفاء", Toast.LENGTH_SHORT).show()
                            actionCamBitmap = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDA3633)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("حذف الإطار من لوحة المعاينة")
                    }
                    OutlinedButton(onClick = { actionCamBitmap = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("إلغاء", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun AudioRecordRequirementsPage(viewModel: AdminViewModel) {
    val audioRecord by viewModel.audioRecord.collectAsState()
    val isAudioPlaying by viewModel.isAudioPlaying.collectAsState()
    val audioDuration by viewModel.audioDuration.collectAsState()
    val audioPosition by viewModel.audioPosition.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { viewModel.requestAudioRecord() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
            modifier = Modifier.fillMaxWidth().height(42.dp)
        ) {
            Icon(Icons.Default.Mic, null, tint = Color.Black)
            Spacer(modifier = Modifier.width(6.dp))
            Text("طلب تسجيل صوت جديد الآن", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Divider(color = Color(0xFF30363D))

        audioRecord?.let { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                border = BorderStroke(1.dp, Color(0xFF30363D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("آخر تسجيل صوتي مستلم", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    val progressValue = if (audioDuration > 0) audioPosition.toFloat() / audioDuration.toFloat() else 0f
                    val formattedPosition = String.format(Locale.ENGLISH, "%02d:%02d", (audioPosition / 1000) / 60, (audioPosition / 1000) % 60)
                    val formattedDuration = String.format(Locale.ENGLISH, "%02d:%02d", (audioDuration / 1000) / 60, (audioDuration / 1000) % 60)

                    LinearProgressIndicator(progress = { progressValue }, color = Color(0xFF00E5FF), modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formattedPosition, color = Color(0xFF8B949E), fontSize = 11.sp)
                        Text(formattedDuration, color = Color(0xFF8B949E), fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = {
                                if (isAudioPlaying) {
                                    viewModel.stopAudio()
                                } else {
                                    viewModel.loadAndPlayAudio(item.base64)
                                }
                            },
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF00E5FF))
                        ) {
                            Icon(if (isAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black)
                        }
                    }
                }
            }
        } ?: Text("لا توجد تسجيلات صوتية.", color = Color(0xFF8B949E), fontSize = 11.sp)
    }
}

@Composable
fun LiveStreamRequirementsPage(viewModel: AdminViewModel) {
    val liveStreamState by viewModel.liveStreamState.collectAsState()
    val isStreamingActive = liveStreamState?.isActive == true
    var showFullscreenBitmap by remember { mutableStateOf<Bitmap?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            border = BorderStroke(1.dp, if(isStreamingActive) Color(0xFF39D353) else Color(0xFF30363D)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("البث المباشر للشاشة", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(if (isStreamingActive) "نشط" else "متوقف", color = if (isStreamingActive) Color(0xFF39D353) else Color(0xFFFF4081), fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Box(modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black).border(1.dp, Color(0xFF21262D)), contentAlignment = Alignment.Center) {
                    val bitmap by produceState<Bitmap?>(initialValue = null, liveStreamState?.image) {
                        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { liveStreamState?.toBitmap() }
                    }
                    if (isStreamingActive && bitmap != null) {
                        Image(bitmap!!.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        IconButton(onClick = { showFullscreenBitmap = bitmap }, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)) {
                            Icon(Icons.Default.Fullscreen, null, tint = Color.White)
                        }
                    } else if (isStreamingActive) {
                        CircularProgressIndicator(color = Color(0xFF9155FF))
                    } else {
                        Text("البث غير نشط.", color = Color(0xFF8B949E), fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { viewModel.startLiveStream() }, enabled = !isStreamingActive, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)), modifier = Modifier.weight(1f)) {
                        Text("بدء البث")
                    }
                    Button(onClick = { viewModel.stopLiveStream() }, enabled = isStreamingActive, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDA3633)), modifier = Modifier.weight(1f)) {
                        Text("إيقاف")
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
}

@Composable
fun InstalledAppsRequirementsPage(viewModel: AdminViewModel) {
    val installedApps by viewModel.installedApps.collectAsState()
    var query by remember { mutableStateOf("") }
    var filterSystem by remember { mutableStateOf<Boolean?>(false) }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { viewModel.requestAppsList() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081)), modifier = Modifier.fillMaxWidth()) {
            Text("جرد وتنزيل قائمة التطبيقات")
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("بحث عن تطبيق..", color = Color(0xFF8B949E)) },
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("المستخدم" to false, "النظام" to true, "الكل" to null).forEach { (lbl, valState) ->
                Button(
                    onClick = { filterSystem = valState },
                    colors = ButtonDefaults.buttonColors(containerColor = if(filterSystem == valState) Color(0xFF9155FF) else Color(0xFF161B22)),
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
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if(app.isSystem) Icons.Default.Settings else Icons.Default.PlayArrow, null, tint = if(app.isSystem) Color.Gray else Color.Green, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(app.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(app.packageName, color = Color(0xFF8B949E), fontSize = 10.sp)
                        }
                    }
                }
            }
        }
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
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
                        color = Color.White,
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
                                else Color(0xFF8B949E).copy(alpha = 0.2f)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) Color(0xFF238636) else Color(0xFF8B949E))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isOnline) "متصل الآن" else "غير متصل",
                            color = if (isOnline) Color(0xFF39D353) else Color(0xFF8B949E),
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
                    color = Color(0xFF8B949E),
                    fontSize = 12.sp
                )

                Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFF30363D))

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
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "يمكنك قفل الشاشة لمنع استخدامه",
                                color = Color(0xFF8B949E),
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
                                contentColor = Color.White
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
                                contentColor = Color.White
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
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
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
                    Text("البطارية", color = Color(0xFF8B949E), fontSize = 12.sp)
                    Text(
                        text = "${device.battery}%",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
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
                    Text("مساحة التخزين", color = Color(0xFF8B949E), fontSize = 12.sp)
                    Text(
                        text = "$usagePercentage%",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Detailed Storage Visual Progress bar Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "تفاصيل المساحة التخزينية لجهاز الطفل",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val usedGb = String.format(Locale.ENGLISH, "%.2f", device.storageUsed.toDouble() / (1024.0 * 1024.0 * 1024.0))
                val totalGb = String.format(Locale.ENGLISH, "%.2f", device.storageTotal.toDouble() / (1024.0 * 1024.0 * 1024.0))
                val progressValue = if (device.storageTotal > 0) {
                    (device.storageUsed.toFloat() / device.storageTotal.toFloat())
                } else {
                    0f
                }

                LinearProgressIndicator(
                    progress = { progressValue },
                    color = Color(0xFF9155FF),
                    trackColor = Color(0xFF21262D),
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
                        color = Color(0xFF8B949E),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "الإجمالي: $totalGb جيجا",
                        color = Color(0xFF8B949E),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Info disclaimer card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF21262D)),
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
                    color = Color(0xFFC9D1D9),
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
    val screenshot by viewModel.screenshot.collectAsState()
    val cameraPhoto by viewModel.cameraPhoto.collectAsState()
    val liveStreamState by viewModel.liveStreamState.collectAsState()
    val audioRecord by viewModel.audioRecord.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()

    val isAudioPlaying by viewModel.isAudioPlaying.collectAsState()
    val audioDuration by viewModel.audioDuration.collectAsState()
    val audioPosition by viewModel.audioPosition.collectAsState()

    var activeCommandSubIndex by remember { mutableIntStateOf(0) } // SubTabs: 0ScreenShoot/Cam, 1AmbientSound, 2AppsDiag
    var showFullscreenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var userAppsSearchQuery by remember { mutableStateOf("") }
    var userAppsFilterIsSystem by remember { mutableStateOf<Boolean?>(false) } // false: user apps, true: system apps, null: all
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Mode switch selectors inside control center
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .background(Color(0xFF161B22), RoundedCornerShape(8.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val items = listOf("الكاميرا والشاشة", "الصوت المحيطي", "تطبيقات الطفل")
            items.forEachIndexed { idx, title ->
                Button(
                    onClick = { activeCommandSubIndex = idx },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeCommandSubIndex == idx) Color(0xFF9155FF) else Color.Transparent,
                        contentColor = if (activeCommandSubIndex == idx) Color.White else Color(0xFF8B949E)
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                ) {
                    Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Divider(modifier = Modifier.padding(bottom = 12.dp), color = Color(0xFF30363D))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when (activeCommandSubIndex) {
                0 -> {
                    // SCREENSHOT AND PHOTO DISPATCH PANEL
                    val isStreamingActive = liveStreamState?.isActive == true
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 📺 بث الشاشة الحي بالوقت الحقيقي (Real-time Live Stream)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (liveStreamState?.isActive == true) Color(0xFF39D353) else Color(0xFF30363D)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Tv,
                                            contentDescription = null,
                                            tint = if (liveStreamState?.isActive == true) Color(0xFF39D353) else Color(0xFF8B949E),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "البث المباشر للشاشة (Live Stream)",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "مراقبة شاشة الهاتف لحظياً وبطريقة آمنة بالكامل",
                                                color = Color(0xFF8B949E),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    // Stream Status Indicator Badge
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isStreamingActive) Color(0xFF238636).copy(alpha = 0.2f)
                                                else Color(0xFFFF4081).copy(alpha = 0.2f)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (isStreamingActive) Color(0xFF39D353) else Color(0xFFFF4081))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isStreamingActive) "نشط" else "متوقف",
                                            color = if (isStreamingActive) Color(0xFF39D353) else Color(0xFFFF4081),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFF30363D))

                                // Streaming viewport / placeholder
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(260.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF0B0E14))
                                        .border(1.dp, Color(0xFF21262D), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val bitmap by produceState<Bitmap?>(initialValue = null, liveStreamState?.image) {
                                        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { liveStreamState?.toBitmap() }
                                    }
                                    val bmp = bitmap
                                    if (isStreamingActive && bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "بث شاشة الهاتف",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                        
                                        // Floating LIVE badge
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(12.dp)
                                                .background(Color(0xFFDA3633), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.White)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "مباشر LIVE",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        // Fullscreen overlay click action
                                        IconButton(
                                            onClick = { showFullscreenBitmap = bitmap },
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(8.dp)
                                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        ) {
                                            Icon(Icons.Default.Fullscreen, null, tint = Color.White)
                                        }
                                    } else if (isStreamingActive && bitmap == null) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(color = Color(0xFF9155FF), modifier = Modifier.size(36.dp))
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "جاري إنشاء اتصال آمن وتلقي إطارات البث...",
                                                color = Color(0xFF8B949E),
                                                fontSize = 11.sp,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(horizontal = 24.dp)
                                            )
                                        }
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(16.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.TvOff,
                                                contentDescription = null,
                                                tint = Color(0xFF8B949E).copy(alpha = 0.5f),
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "البث المباشر للشاشة غير نشط حالياً.",
                                                color = Color(0xFF8B949E),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "اضغط على زر التشغيل بالأسفل لبدء بث الشاشة فوراً بالوقت الحقيقي",
                                                color = Color(0xFF8B949E).copy(alpha = 0.7f),
                                                fontSize = 10.sp,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Action Buttons (Start & Stop Stream)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.startLiveStream() },
                                        enabled = !isStreamingActive,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF238636),
                                            disabledContainerColor = Color(0xFF238636).copy(alpha = 0.15f),
                                            contentColor = Color.White,
                                            disabledContentColor = Color(0xFF8B949E).copy(alpha = 0.4f)
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).height(46.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("بدء البث الحقيقي", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { viewModel.stopLiveStream() },
                                        enabled = isStreamingActive,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFDA3633),
                                            disabledContainerColor = Color(0xFFDA3633).copy(alpha = 0.15f),
                                            contentColor = Color.White,
                                            disabledContentColor = Color(0xFF8B949E).copy(alpha = 0.4f)
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).height(46.dp)
                                    ) {
                                        Icon(Icons.Default.Stop, null, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("إيقاف البث", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Informational message or error from database
                                liveStreamState?.error?.let { err ->
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF4C1C1B)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Error,
                                                contentDescription = null,
                                                tint = Color(0xFFFF7B72),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "خطأ جهاز الطفل: $err",
                                                color = Color(0xFFFFA198),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Divider to separate Live stream from diagnostic camera pulls
                        Divider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFF30363D))

                        // Quick Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.requestScreenshot() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9155FF)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Screenshot, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("طلب لقطة شاشة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { viewModel.requestPhoto(false) }, // Back camera by default
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B22)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF30363D))
                            ) {
                                Icon(Icons.Default.Camera, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFFF4081))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("صورة كاميرا (خلفي)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Additional camera trigger button for front camera
                        Button(
                            onClick = { viewModel.requestPhoto(true) }, // Front
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B22)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF30363D))
                        ) {
                            Icon(Icons.Default.CameraFront, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF00E5FF))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("التقاط صورة كاميرا أمامية", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Display screenshots results
                        screenshot?.let { item ->
                            val bitmap by produceState<Bitmap?>(initialValue = null, item.base64) {
                                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { item.toBitmap() }
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF161B22), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "آخر لقطة شاشة مستلمة",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(item.timestamp)),
                                        color = Color(0xFF8B949E),
                                        fontSize = 11.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                val bmp = bitmap
                                if (bmp != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { showFullscreenBitmap = bmp }
                                    ) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Screenshot preview",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(8.dp)
                                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                                .padding(6.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.ZoomIn, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("اضغط للتكبير لملء الشاشة", color = Color.White, fontSize = 9.sp)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = {
                                            val ok = saveBitmapToGallery(context, bmp, "child_screenshot_${item.timestamp}")
                                            if (ok) {
                                                Toast.makeText(context, "تم حفظ لقطة الشاشة في معرض الصور بنجاح!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "فشل حفظ الملف بالمعرض.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("حفظ في معرض وسائط المشرف", fontSize = 12.sp)
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .background(Color(0xFF21262D), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("عذراً، الصورة تالفة أو غير مكتملة الرفع.", color = Color(0xFF8B949E))
                                    }
                                }
                            }
                        }

                        // Display Camera photo results
                        cameraPhoto?.let { item ->
                            val bitmap by produceState<Bitmap?>(initialValue = null, item.base64) {
                                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { item.toBitmap() }
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF161B22), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val sideText = if (item.cameraType == "front") "الأمامية" else "الخلفية"
                                    Text(
                                        text = "صورة ملتقطة من كاميرا الطفل: $sideText",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(item.timestamp)),
                                        color = Color(0xFF8B949E),
                                        fontSize = 11.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                val bmp = bitmap
                                if (bmp != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { showFullscreenBitmap = bmp }
                                    ) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Camera photo preview",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(8.dp)
                                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                                .padding(6.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.ZoomIn, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("اضغط للتكبير لملء الشاشة", color = Color.White, fontSize = 9.sp)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = {
                                            val ok = saveBitmapToGallery(context, bmp, "child_camera_${item.timestamp}")
                                            if (ok) {
                                                Toast.makeText(context, "تم حفظ الصورة بالمعرض بنجاح!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "فشل الحفظ بالمعرض.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("حفظ في معرض وسائط المشرف", fontSize = 12.sp)
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .background(Color(0xFF21262D), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("جاري معالجة الصورة أو أنها غير جاهزة..", color = Color(0xFF8B949E))
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // AMBIENT AUDIOS CONTROL PANEL
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = { viewModel.requestAudioRecord() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9155FF)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تسجيل صوت محيطي طارئ (10 ثوان)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        // Playback Module
                        audioRecord?.let { item ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, Color(0xFF30363D))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "التسجيل الصوتي المحسوس المستلم",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "تاريخ الرفع: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp)),
                                        color = Color(0xFF8B949E),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )

                                    // Timeline progress indicator
                                    val positionSec = audioPosition / 1000
                                    val durationSec = audioDuration / 1000
                                    
                                    Slider(
                                        value = audioPosition.toFloat(),
                                        onValueChange = { viewModel.seekAudio(it.toInt()) },
                                        valueRange = 0f..(if (audioDuration > 0) audioDuration.toFloat() else 10000f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFFFF4081),
                                            activeTrackColor = Color(0xFF9155FF),
                                            inactiveTrackColor = Color(0xFF21262D)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = String.format(Locale.ENGLISH, "%02d:%02d", positionSec / 60, positionSec % 60),
                                            color = Color(0xFF8B949E),
                                            fontSize = 12.sp
                                        )
                                        val totalLength = if (durationSec > 0) durationSec else 10
                                        Text(
                                            text = String.format(Locale.ENGLISH, "%02d:%02d", totalLength / 60, totalLength % 60),
                                            color = Color(0xFF8B949E),
                                            fontSize = 12.sp
                                        )
                                    }

                                    // Control panel play buttons
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { viewModel.stopAudio() },
                                            modifier = Modifier
                                                .padding(end = 16.dp)
                                                .background(Color(0xFF21262D), CircleShape)
                                        ) {
                                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White)
                                        }

                                        IconButton(
                                            onClick = {
                                                if (isAudioPlaying) {
                                                    viewModel.pauseAudio()
                                                } else {
                                                    // if media player is loaded but paused, resume. Else load from scratch.
                                                    if (audioPosition > 0) {
                                                        viewModel.resumeAudio()
                                                    } else {
                                                        viewModel.loadAndPlayAudio(item.base64)
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .size(56.dp)
                                                .background(Color(0xFF9155FF), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = if (isAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = "Play/Pause",
                                                tint = Color.White,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } ?: Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(Color(0xFF161B22), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AudioFile, null, tint = Color(0xFF8B949E), modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("لا يوجد تسجيل صوتي متاح. اضغط أعلاه للطلب", color = Color(0xFF8B949E), fontSize = 12.sp)
                            }
                        }
                    }
                }

                2 -> {
                    // APPS DIAGNOSTICS CONTROL PANEL
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Button(
                            onClick = { viewModel.requestAppsList() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9155FF)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تحديث قائمة التطبيقات المثبتة بالطفل", fontSize = 12.sp)
                        }

                        // Search box
                        OutlinedTextField(
                            value = userAppsSearchQuery,
                            onValueChange = { userAppsSearchQuery = it },
                            placeholder = { Text("بحث باسم التطبيق..", color = Color(0xFF8B949E)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF9155FF),
                                unfocusedBorderColor = Color(0xFF30363D),
                                focusedContainerColor = Color(0xFF161B22),
                                unfocusedContainerColor = Color(0xFF161B22)
                            ),
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF8B949E)) },
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(8.dp)
                        )

                        // Segment filter
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val opts = listOf("الكل" to null, "تطبيقات المستخدم" to false, "تطبيقات النظام" to true)
                            opts.forEach { (label, valIsSys) ->
                                Button(
                                    onClick = { userAppsFilterIsSystem = valIsSys },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (userAppsFilterIsSystem == valIsSys) Color(0xFF21262D) else Color.Transparent,
                                        contentColor = if (userAppsFilterIsSystem == valIsSys) Color.White else Color(0xFF8B949E)
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, if (userAppsFilterIsSystem == valIsSys) Color(0xFF9155FF) else Color(0xFF30363D)),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(label, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }

                        // Filter items
                        val filteredApps = installedApps.filter { app ->
                            val matchSearch = app.name.contains(userAppsSearchQuery, ignoreCase = true) ||
                                              app.packageName.contains(userAppsSearchQuery, ignoreCase = true)
                            val matchSystem = userAppsFilterIsSystem == null || app.isSystem == userAppsFilterIsSystem
                            matchSearch && matchSystem
                        }

                        if (filteredApps.isNotEmpty()) {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredApps) { app ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
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
                                                    .size(40.dp)
                                                    .background(Color(0xFF21262D), RoundedCornerShape(6.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (app.isSystem) Icons.Default.SettingsSuggest else Icons.Default.Android,
                                                    contentDescription = null,
                                                    tint = if (app.isSystem) Color(0xFF00E5FF) else Color(0xFF39D353),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = app.name,
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = app.packageName,
                                                    color = Color(0xFF8B949E),
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            // App category flag
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (app.isSystem) Color(0xFF21262D) else Color(0xFF238636).copy(alpha = 0.2f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (app.isSystem) "نظامي" else "مثبت",
                                                    color = if (app.isSystem) Color(0xFF8B949E) else Color(0xFF39D353),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
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
                                Text("لا يوجد تطبيقات متطابقة أو القائمة فارغة.", color = Color(0xFF8B949E), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Full screen bitmap visual overlay zoomable Dialog
    showFullscreenBitmap?.let { bitmap ->
        Dialog(onDismissRequest = { showFullscreenBitmap = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(550.dp)
                    .background(Color.Black, RoundedCornerShape(16.dp))
                    .padding(8.dp)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Fullscreen",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                IconButton(
                    onClick = { showFullscreenBitmap = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            border = BorderStroke(1.dp, Color(0xFF30363D))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "مستكشف ملفات الطفل السحابي",
                    color = Color(0xFF8B949E),
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
                        color = Color.White,
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
                enabled = showUp,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("المجلد السابق", fontSize = 11.sp)
            }

            IconButton(
                onClick = { viewModel.exploreDirectory(currentPath) },
                modifier = Modifier.background(Color(0xFF21262D), CircleShape).size(36.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White, modifier = Modifier.size(18.dp))
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
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
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
                                tint = if (item.isDir) Color(0xFFD29922) else Color(0xFF8B949E),
                                modifier = Modifier.size(24.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = item.date,
                                        color = Color(0xFF8B949E),
                                        fontSize = 10.sp
                                    )
                                    if (!item.isDir) {
                                        val kbGbText = remember(item.size) { formatSize(item.size) }
                                        Text(
                                            text = "• $kbGbText",
                                            color = Color(0xFF8B949E),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }

                            if (item.isDir) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color(0xFF30363D)
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
                    Icon(Icons.Default.FolderZip, null, tint = Color(0xFF21262D), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("المجلد فارغ أو لم يتم تلقي القائمة بعد.", color = Color(0xFF8B949E), fontSize = 12.sp)
                }
            }
        }
    }
}

// 6. TAB 4: LIVE RECEPTION - SMS & SECURITY ALERTS
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
                .background(Color(0xFF161B22), RoundedCornerShape(8.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = { activeSmsSubTab = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeSmsSubTab == 0) Color(0xFF9155FF) else Color.Transparent,
                    contentColor = if (activeSmsSubTab == 0) Color.White else Color(0xFF8B949E)
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
                    contentColor = if (activeSmsSubTab == 1) Color.White else Color(0xFF8B949E)
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
                                .background(Color(0xFFDA3633))
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
                                        containerColor = if (isIncoming) Color(0xFF161B22) else Color(0xFF2A1C3F)
                                    ),
                                    shape = RoundedCornerShape(
                                        topStart = 12.dp,
                                        topEnd = 12.dp,
                                        bottomStart = if (isIncoming) 0.dp else 12.dp,
                                        bottomEnd = if (isIncoming) 12.dp else 0.dp
                                    ),
                                    border = BorderStroke(1.dp, if (isIncoming) Color(0xFF30363D) else Color(0xFF9155FF).copy(alpha = 0.5f)),
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
                                                    .background(Color(0xFF21262D))
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
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))
                                        val timeStr = remember(sms.timestamp) {
                                            SimpleDateFormat("yyyy-MM-dd h:mm a", Locale.getDefault()).format(Date(sms.timestamp))
                                        }
                                        Text(
                                            text = timeStr,
                                            color = Color(0xFF8B949E),
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
                            Icon(Icons.Default.Sms, null, tint = Color(0xFF21262D), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("لا يتوفر رسائل SMS مؤرشفة في النظام حالياً.", color = Color(0xFF8B949E), fontSize = 12.sp)
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
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Button(
                                onClick = { viewModel.clearAlerts() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDA3633)),
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
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1113)),
                                    border = BorderStroke(1.dp, Color(0xFFDA3633)),
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
                                                .background(Color(0xFFDA3633).copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = Color(0xFFDA3633),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (alert.title == "DEVICE_BOOTED") "إعادة التشغيل (BOOT)" 
                                                       else if (alert.title == "BATTERY_LOW") "بطارية حرجة منخفضة" 
                                                       else alert.title,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = alert.message,
                                                color = Color(0xFFC9D1D9),
                                                fontSize = 11.sp
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val timeStr = remember(alert.timestamp) {
                                                SimpleDateFormat("yyyy-MM-dd h:mm a", Locale.getDefault()).format(Date(alert.timestamp))
                                            }
                                            Text(
                                                text = timeStr,
                                                color = Color(0xFF8B949E),
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
                                Text("لا يوجد تجاوزات خطيرة. طفلكم بأمان!", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("أي تنبيهات مثل هبوط البطارية أو تشغيل الهاتف ستظهر هنا فورياً.", color = Color(0xFF8B949E), fontSize = 11.sp)
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
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "الرجاء كتابة رمز الاقتران الفريد (Pairing Token) الخاص بالطفل للربط الفوري ومزامنته",
            color = Color(0xFF8B949E),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Card Entry Inputs
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = pairingToken,
                    onValueChange = { pairingToken = it },
                    label = { Text("رمز الاقتران (Token)", color = Color(0xFF8B949E)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF9155FF),
                        unfocusedBorderColor = Color(0xFF30363D),
                        focusedContainerColor = Color(0xFF0D1117),
                        unfocusedContainerColor = Color(0xFF0D1117)
                    ),
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = friendlyName,
                    onValueChange = { friendlyName = it },
                    label = { Text("اسم هاتف الطفل (مثال: هاتف أحمد)", color = Color(0xFF8B949E)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF9155FF),
                        unfocusedBorderColor = Color(0xFF30363D),
                        focusedContainerColor = Color(0xFF0D1117),
                        unfocusedContainerColor = Color(0xFF0D1117)
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9155FF)),
                    enabled = !isProgressing && pairingToken.isNotBlank() && friendlyName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isProgressing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("مزامنة وإقتران في قاعدة البيانات", fontWeight = FontWeight.Bold)
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            border = BorderStroke(1.dp, Color(0xFF30363D))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تعديل رابط قاعدة البيانات",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "اغلاق", tint = Color(0xFF8B949E))
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF30363D))
                
                Text(
                    text = "أدخل الرابط الكامل لقاعدة بيانات Firebase Realtime المخصصة لك:",
                    color = Color(0xFF8B949E),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    placeholder = { Text("https://example-default-rtdb.firebaseio.com", color = Color(0xFF8B949E)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF9155FF),
                        unfocusedBorderColor = Color(0xFF30363D),
                        focusedLabelColor = Color(0xFF9155FF),
                        unfocusedLabelColor = Color(0xFF8B949E)
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "خوادم ومناطق مقترحة (اضغط للتحديد):",
                    color = Color.White,
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
                            .background(Color(0xFF21262D))
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
                                color = Color(0xFF8B949E),
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
                        Text("إلغاء", color = Color(0xFF8B949E))
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Color(0xFF30363D))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showAddPane) "إضافة جهاز طفل جديد" else "أجهزة الأطفال المتصلة",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8B949E))
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF30363D))

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
                                        containerColor = if (selected) Color(0xFF21262D) else Color(0xFF0D1117)
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
                                                .background(if (online) Color(0xFF238636) else Color(0xFF8B949E))
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = dev.name,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "الرمز الفريد: ${dev.id}",
                                                color = Color(0xFF8B949E),
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
                            Text("لا يتوفر أجهزة مسجلة.", color = Color(0xFF8B949E), fontSize = 12.sp)
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
                            onValueChange = { newToken = it },
                            placeholder = { Text("اكتب token فريد هنا.. (مثل: ahmad_phone)", color = Color(0xFF8B949E)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF9155FF),
                                unfocusedBorderColor = Color(0xFF30363D),
                                focusedContainerColor = Color(0xFF0D1117),
                                unfocusedContainerColor = Color(0xFF0D1117)
                            ),
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            placeholder = { Text("اسم وكنية هاتف الطفل..", color = Color(0xFF8B949E)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF9155FF),
                                unfocusedBorderColor = Color(0xFF30363D),
                                focusedContainerColor = Color(0xFF0D1117),
                                unfocusedContainerColor = Color(0xFF0D1117)
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
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
        else -> Color(0xFFDA3633)
    }
}
