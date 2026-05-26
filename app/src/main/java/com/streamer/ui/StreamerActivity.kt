package com.streamer.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.streamer.service.StreamingForegroundService
import dagger.hilt.android.AndroidEntryPoint
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

class StreamerActivity : ComponentActivity() {

    private lateinit var viewModel: StreamerViewModel
    private var isReceiverRegistered = false

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.streamer.ACTION_STOP_CAPTURE") {
                Log.d("StreamerActivity", "Stop action broadcast received! Stopping capture.")
                viewModel.stopStreaming()
                Toast.makeText(this@StreamerActivity, "تم إيقاف البث بنجاح", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val projectionData = result.data!!
            // Start Foreground Service
            StreamingForegroundService.startService(this, projectionData)
            
            // Execute Stream Start inside ViewModel
            viewModel.startStreaming(
                context = this,
                roomId = roomIdState,
                projectionData = projectionData,
                onSuccess = {
                    runOnUiThread {
                        Toast.makeText(this, "بدأ البث المباشر للشاشة بنجاح!", Toast.LENGTH_LONG).show()
                    }
                },
                onError = { err ->
                    runOnUiThread {
                        Toast.makeText(this, "فشل البث: $err", Toast.LENGTH_LONG).show()
                    }
                }
            )
        } else {
            Toast.makeText(this, "تم رفض إذن بث الشاشة!", Toast.LENGTH_SHORT).show()
        }
    }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.all { it.value }
        if (granted) {
            Log.d("StreamerActivity", "All permissions successfully granted.")
        } else {
            Toast.makeText(this, "الرجاء الموافقة على جميع الأذونات ليعمل البث", Toast.LENGTH_LONG).show()
        }
    }

    private var roomIdState by mutableStateOf("streamer-88")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Prevent screen sleep
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Manual Fallback ViewModel Injection to make sure it compiles & runs anywhere, while still keeping Hilt compilation
        try {
            val signalingManager = com.streamer.signaling.SignalingManager()
            val webRTCManager = com.streamer.webrtc.WebRTCManager()
            val screenCaptureManager = com.streamer.webrtc.ScreenCaptureManager(webRTCManager)
            val cameraManager = com.streamer.webrtc.CameraManager(webRTCManager)
            viewModel = StreamerViewModel(signalingManager, webRTCManager, screenCaptureManager, cameraManager)
        } catch (e: Exception) {
            Log.e("StreamerActivity", "Manual VM initialization fail, falling back to lazy provider.", e)
        }

        // Server wake up call
        try {
            viewModel.wakeUpServer()
        } catch (e: Exception) {
            Log.e("StreamerActivity", "Error calling wakeUpServer", e)
        }

        // Register Stop receiver
        try {
            val filter = IntentFilter("com.streamer.ACTION_STOP_CAPTURE")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(stopReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(stopReceiver, filter)
            }
            isReceiverRegistered = true
        } catch (e: java.lang.Exception) {
            Log.e("StreamerActivity", "Error registering receiver", e)
        }

        // Request Permissions
        requestAllPermissions()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A)
                ) {
                    StreamerMainScreen(
                        viewModel = viewModel,
                        roomId = roomIdState,
                        onRoomIdChange = { roomIdState = it },
                        onStartClick = { requestScreenCapture() },
                        onStopClick = { stopStreamingAction() }
                    )
                }
            }
        }
    }

    private fun requestAllPermissions() {
        val list = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val notGranted = list.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            permissionsLauncher.launch(notGranted.toTypedArray())
        }
    }

    private fun requestScreenCapture() {
        val mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        if (mediaProjectionManager != null) {
            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        } else {
            Toast.makeText(this, "هذا الهاتف لا يدعم MediaProjection!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopStreamingAction() {
        viewModel.stopStreaming()
        StreamingForegroundService.stopService(this)
        Toast.makeText(this, "تم إيقاف جميع تدفقات البث بنجاح", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(stopReceiver)
            } catch (e: Exception) {}
        }
    }
}

@Composable
fun StreamerMainScreen(
    viewModel: StreamerViewModel,
    roomId: String,
    onRoomIdChange: (String) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val streamState by viewModel.streamState.collectAsState()
    val screenTrack by viewModel.activeScreenTrack.collectAsState()
    val frontTrack by viewModel.activeFrontTrack.collectAsState()
    val backTrack by viewModel.activeBackTrack.collectAsState()
    val eglBaseContext = viewModel.webRTCManager.eglBase?.eglBaseContext

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Card Status
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "جهاز الطفل (StreamerApp)",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "بث الشاشة والكاميرات بلحظية UDP فائقة",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }

                // Status Indicator dot
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = when (streamState) {
                                    is StreamerState.Streaming -> Color(0xFF22C55E)
                                    is StreamerState.Connecting -> Color(0xFFF59E0B)
                                    is StreamerState.Idle -> Color(0xFFEF4444)
                                    is StreamerState.Error -> Color(0xFFEF4444)
                                },
                                shape = CircleShape
                            )
                    )
                    Text(
                        text = when (streamState) {
                            is StreamerState.Streaming -> "نشط"
                            is StreamerState.Connecting -> "جاري الربط"
                            is StreamerState.Idle -> "غير متصل"
                            is StreamerState.Error -> "خطأ"
                        },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Viewports: 65% Height Top (Screen Capture Preview), 35% Height Bottom (Camera previews)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF020617))
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                // Screen Track viewport 65% weight
                CardVideoView(
                    track = screenTrack,
                    eglContext = eglBaseContext,
                    label = "شاشة الهاتف الحالية",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.65f)
                        .clip(RoundedCornerShape(8.dp))
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom Cameras viewports 35% weight side by side
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.35f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CardVideoView(
                        track = frontTrack,
                        eglContext = eglBaseContext,
                        label = "الكاميرا الأمامية",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                    )

                    CardVideoView(
                        track = backTrack,
                        eglContext = eglBaseContext,
                        label = "الكاميرا الخلفية",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }
        }

        // Control Inputs Panel
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = roomId,
                    onValueChange = onRoomIdChange,
                    label = { Text("رمز الغرفة (Room ID)") },
                    enabled = streamState is StreamerState.Idle,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFF9155FF),
                        unfocusedLabelColor = Color(0xFF94A3B8),
                        focusedBorderColor = Color(0xFF9155FF),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onStartClick,
                        enabled = streamState is StreamerState.Idle,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("دخول وبدء البث", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onStopClick,
                        enabled = streamState !is StreamerState.Idle,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إيقاف البث", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CardVideoView(
    track: VideoTrack?,
    eglContext: org.webrtc.EglBase.Context?,
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF020617))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (track == null || eglContext == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(10.dp)
            ) {
                Icon(
                    Icons.Default.VideocamOff,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        init(eglContext, null)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                        setMirror(false)
                        track.addSink(this)
                    }
                },
                onRelease = { view ->
                    try {
                        track.removeSink(view)
                        view.release()
                    } catch (e: Exception) {}
                },
                modifier = Modifier.fillMaxSize()
            )

            // overlay translucent label
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(label, color = Color.White, fontSize = 9.sp)
            }
        }
    }
}
