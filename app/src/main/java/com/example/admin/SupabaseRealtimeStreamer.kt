package com.example.admin

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SupabaseRealtimeStreamer(
    private val deviceToken: String?,
    private val onLiveStreamUpdate: ((deviceToken: String, LiveStreamState) -> Unit)? = null,
    private val onCameraStreamUpdate: ((deviceToken: String, CameraStreamState) -> Unit)? = null,
    private val onDeviceUpdate: ((Device) -> Unit)? = null,
    private val onStatusUpdate: ((Boolean) -> Unit)? = null,
    val onPresenceSync: ((Set<String>) -> Unit)? = null,
    val onPresenceJoin: ((String) -> Unit)? = null,
    val onPresenceLeave: ((String) -> Unit)? = null,
    val onHeartbeat: ((String) -> Unit)? = null,
    val onStatusReply: ((String) -> Unit)? = null,
    val onCommandReply: ((token: String, status: String, message: String, timestamp: Long) -> Unit)? = null
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS) // Indefinite read timeout for WebSocket
        .pingInterval(20, java.util.concurrent.TimeUnit.SECONDS) // Keepalive ping interval set to 20 seconds
        .build()
        
    private var webSocket: WebSocket? = null
    private val streamerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isClosed = false
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    
    fun connect(rootUrl: String, anonKey: String) {
        if (isClosed) return
        disconnect()
        
        val cleanedUrl = rootUrl.trim().removeSuffix("/")
        val wsBase = cleanedUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            
        val wsUrl = "$wsBase/realtime/v1/websocket?apikey=$anonKey&vsn=1.0.0"
        Log.d("SupabaseRealtimeStreamer", "Connecting to Supabase Realtime WebSocket: $wsUrl")
        
        val request = Request.Builder()
            .url(wsUrl)
            .build()
            
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("SupabaseRealtimeStreamer", "WebSocket opened. Joining public realtime channel...")
                onStatusUpdate?.invoke(true)
                // Join public schema channel for table changes
                joinRealtimeChannel(webSocket)
                startHeartbeats()
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleWebSocketMessage(text)
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("SupabaseRealtimeStreamer", "WebSocket closing: $code / $reason")
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("SupabaseRealtimeStreamer", "WebSocket closed: $code / $reason")
                onStatusUpdate?.invoke(false)
                reconnectIfNeed(rootUrl, anonKey)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("SupabaseRealtimeStreamer", "WebSocket failure: ${t.localizedMessage}", t)
                onStatusUpdate?.invoke(false)
                reconnectIfNeed(rootUrl, anonKey)
            }
        })
    }
    
    private fun joinRealtimeChannel(ws: WebSocket) {
        try {
            val configObject = JSONObject().apply {
                val postgresChangesArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("event", "*")
                        put("schema", "public")
                        put("table", "live_streams")
                    })
                    put(JSONObject().apply {
                        put("event", "*")
                        put("schema", "public")
                        put("table", "camera_stream")
                    })
                    put(JSONObject().apply {
                        put("event", "*")
                        put("schema", "public")
                        put("table", "device")
                    })
                    put(JSONObject().apply {
                        put("event", "*")
                        put("schema", "public")
                        put("table", "devices")
                    })
                }
                put("postgres_changes", postgresChangesArray)
            }
            
            val payload = JSONObject().apply {
                put("config", configObject)
            }
            
            val joinMsg = JSONObject().apply {
                put("topic", "realtime:public")
                put("event", "phx_join")
                put("payload", payload)
                put("ref", "join_ref_1")
            }
            
            ws.send(joinMsg.toString())
            Log.d("SupabaseRealtimeStreamer", "Join channel request sent: $joinMsg")

            // Join the specific broadcast channels for real-time device status/broadcast/presence events
            val topicName = "realtime:device_presence"
            val joinTopicMsg = JSONObject().apply {
                put("topic", topicName)
                put("event", "phx_join")
                put("payload", JSONObject().apply {
                    put("config", JSONObject().apply {
                        put("presence", JSONObject().apply {
                            put("key", "token")
                        })
                    })
                })
                put("ref", "join_ref_broadcast_device_presence")
            }
            ws.send(joinTopicMsg.toString())
            Log.d("SupabaseRealtimeStreamer", "Joined broadcast presence channel: $topicName with token key custom presence state config")
        } catch (e: Exception) {
            Log.e("SupabaseRealtimeStreamer", "Error constructing join message", e)
        }
    }
    
    private fun startHeartbeats() {
        heartbeatJob?.cancel()
        heartbeatJob = streamerScope.launch {
            var ref = 1
            while (isActive) {
                delay(20000) // Send heartbeat every 20 seconds to align with child device config
                try {
                    val heartbeat = JSONObject().apply {
                        put("topic", "phoenix")
                        put("event", "heartbeat")
                        put("payload", JSONObject())
                        put("ref", "hb_$ref")
                    }
                    webSocket?.send(heartbeat.toString())
                    ref++
                } catch (e: Exception) {
                    Log.e("SupabaseRealtimeStreamer", "Error sending heartbeat", e)
                }
            }
        }
    }
    
    private fun handleWebSocketMessage(text: String) {
        try {
            val json = JSONObject(text)
            val event = json.optString("event")
            val topic = json.optString("topic")
            
            // Standard Phoenix Presence Event Dispatching
            if (topic == "realtime:device_presence") {
                if (event == "presence_state") {
                    val payload = json.optJSONObject("payload")
                    if (payload != null) {
                        val activeTokens = mutableSetOf<String>()
                        val iterator = payload.keys()
                        while (iterator.hasNext()) {
                            val token = iterator.next()
                            if (token.isNotBlank() && token != "null") {
                                activeTokens.add(token)
                            }
                        }
                        Log.d("SupabaseRealtimeStreamer", "Presence State synced ($topic): $activeTokens")
                        onPresenceSync?.invoke(activeTokens)
                    }
                } else if (event == "presence_diff") {
                    val payload = json.optJSONObject("payload")
                    if (payload != null) {
                        val joins = payload.optJSONObject("joins")
                        if (joins != null) {
                            val iterator = joins.keys()
                            while (iterator.hasNext()) {
                                val token = iterator.next()
                                if (token.isNotBlank() && token != "null") {
                                    Log.d("SupabaseRealtimeStreamer", "Presence Join ($topic): $token")
                                    onPresenceJoin?.invoke(token)
                                }
                            }
                        }
                        val leaves = payload.optJSONObject("leaves")
                        if (leaves != null) {
                            val iterator = leaves.keys()
                            while (iterator.hasNext()) {
                                val token = iterator.next()
                                if (token.isNotBlank() && token != "null") {
                                    Log.d("SupabaseRealtimeStreamer", "Presence Leave ($topic): $token")
                                    onPresenceLeave?.invoke(token)
                                }
                            }
                        }
                    }
                }
            }

            // Check for broadcast 'heartbeat' event
            val topPayloadCheck = json.optJSONObject("payload")
            if (topPayloadCheck != null) {
                val pType = topPayloadCheck.optString("type")
                val pEvent = topPayloadCheck.optString("event")
                if ((pType == "broadcast" && pEvent == "heartbeat") || event == "heartbeat") {
                    val innerPayload = topPayloadCheck.optJSONObject("payload")
                    val tokenVal = innerPayload?.optString("token") ?: innerPayload?.optString("device_token") 
                        ?: topPayloadCheck.optString("token") ?: topPayloadCheck.optString("device_token")
                        ?: json.optString("ref")
                    if (!tokenVal.isNullOrBlank() && tokenVal != "null") {
                        Log.d("SupabaseRealtimeStreamer", "Broadcast heartbeat pulse detected from child ($topic): $tokenVal")
                        onHeartbeat?.invoke(tokenVal)
                    }
                }
                if (pType == "broadcast" && pEvent == "status_reply") {
                    val innerPayload = topPayloadCheck.optJSONObject("payload")
                    val tokenVal = innerPayload?.optString("token") ?: innerPayload?.optString("device_token")
                        ?: topPayloadCheck.optString("token") ?: topPayloadCheck.optString("device_token")
                    if (!tokenVal.isNullOrBlank() && tokenVal != "null") {
                        Log.d("SupabaseRealtimeStreamer", "Broadcast status_reply detected from child ($topic): $tokenVal")
                        onStatusReply?.invoke(tokenVal)
                    }
                }
                if (pType == "broadcast" && (pEvent == "command_reply" || pEvent == "command_response" || pEvent == "command_status" || pEvent == "response" || pEvent == "reply" || pEvent.contains("reply") || pEvent.contains("response"))) {
                    val innerPayload = topPayloadCheck.optJSONObject("payload")
                    val p = innerPayload ?: topPayloadCheck
                    val tokenVal = p.optString("token").takeIf { it.isNotBlank() && it != "null" }
                        ?: p.optString("device_token").takeIf { it.isNotBlank() && it != "null" }
                    val statusVal = p.optString("status").takeIf { it.isNotBlank() && it != "null" }
                        ?: p.optString("command_status").takeIf { it.isNotBlank() && it != "null" }
                    val messageVal = p.optString("message") ?: p.optString("result") ?: p.optString("response_message") ?: ""
                    val tsVal = p.optLong("timestamp", 0L).takeIf { it != 0L }
                        ?: p.optLong("command_timestamp", 0L).takeIf { it != 0L }
                        ?: p.optLong("cmd_timestamp", 0L)
                    
                    if (!tokenVal.isNullOrBlank() && statusVal != null) {
                        Log.d("SupabaseRealtimeStreamer", "Broadcast command reply processed from child ($tokenVal): status=$statusVal, ts=$tsVal")
                        onCommandReply?.invoke(tokenVal, statusVal, messageVal, tsVal)
                    }
                }
            }
            
            // Check if this topic or event corresponds to a broadcast from the targeted devices
            val isBroadcastTopic = topic == "realtime:device_presence"
            
            if ((isBroadcastTopic && event != "presence_state" && event != "presence_diff") || event == "device_update" || event == "device_presence") {
                val topPayload = json.optJSONObject("payload")
                var devicePayload: JSONObject? = null
                
                if (topPayload != null) {
                    val pType = topPayload.optString("type")
                    val pEvent = topPayload.optString("event")
                    
                    // Option A: If wrapped in standard phoenix/supabase broadcast envelope
                    if (pType == "broadcast" && (pEvent == "device_update" || pEvent == "device_presence")) {
                        devicePayload = topPayload.optJSONObject("payload")
                    }
                    
                    // Option B: If the top level payload is directly the device data
                    if (devicePayload == null) {
                        if (topPayload.has("token") || topPayload.has("device_name")) {
                            devicePayload = topPayload
                        }
                    }
                }
                
                if (devicePayload != null) {
                    val tokenVal = devicePayload.optString("token").takeIf { it.isNotBlank() && it != "null" }
                    if (tokenVal != null) {
                        val deviceNameVal = devicePayload.optString("device_name", "Unknown Device")
                        val batteryVal = devicePayload.optInt("battery", 0)
                        val netTypeVal = devicePayload.optString("net_type").takeIf { it.isNotBlank() && it != "null" }
                        val netNameVal = devicePayload.optString("net_name").takeIf { it.isNotBlank() && it != "null" }
                        val statusVal = devicePayload.optString("status")
                        val storageTotalVal = devicePayload.optLong("storage_total", 0L)
                        val storageUsedVal = devicePayload.optLong("storage_used", 0L)
                        val lastUpdatedStr = devicePayload.optString("last_updated")
                        
                        var lastActiveMs = System.currentTimeMillis()
                        if (!lastUpdatedStr.isNullOrBlank() && lastUpdatedStr != "null") {
                            try {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    val instant = java.time.Instant.parse(lastUpdatedStr.replace(" ", "T"))
                                    lastActiveMs = instant.toEpochMilli()
                                } else {
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    lastActiveMs = sdf.parse(lastUpdatedStr.replace("Z", ""))?.time ?: System.currentTimeMillis()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        
                        val parsedDevice = Device(
                            id = tokenVal,
                            name = deviceNameVal,
                            battery = batteryVal,
                            lastActive = lastActiveMs,
                            storageUsed = storageUsedVal,
                            storageTotal = storageTotalVal,
                            isLocked = false,
                            networkType = netTypeVal,
                            carrierName = netNameVal,
                            isCharging = statusVal == "charging",
                            isRealtimeUpdated = true, // Explicitly true indicating high-speed real-time websocket update receipt
                            status = statusVal
                        )
                        Log.d("SupabaseRealtimeStreamer", "Received broadcast device update on topic $topic for ${parsedDevice.name}")
                        onDeviceUpdate?.invoke(parsedDevice)
                        return
                    }
                }
            }
            
            // Standard Postgres real-time fallback listening
            if (topic == "realtime:public" && event == "postgres_changes") {
                val payload = json.optJSONObject("payload") ?: return
                val table = payload.optString("table").takeIf { it.isNotBlank() } ?: "live_streams"
                
                val dataObj = payload.optJSONObject("data")
                val record = dataObj?.optJSONObject("record") 
                    ?: dataObj?.optJSONObject("new") 
                    ?: payload.optJSONObject("record") 
                    ?: payload.optJSONObject("new") 
                    ?: payload
                
                if (table == "device" || table == "devices") {
                    val tokenVal = (if (record.has("token")) record.optString("token") else record.optString("device_token"))
                        .takeIf { !it.isNullOrBlank() && it != "null" } ?: return
                    val deviceNameVal = record.optString("device_name", "Unknown Device")
                    val batteryVal = record.optInt("battery", 0)
                    val netTypeVal = record.optString("net_type").takeIf { it.isNotBlank() && it != "null" }
                    val netNameVal = record.optString("net_name").takeIf { it.isNotBlank() && it != "null" }
                    val statusVal = record.optString("status")
                    val storageTotalVal = record.optLong("storage_total", 0L)
                    val storageUsedVal = record.optLong("storage_used", 0L)
                    val lastUpdatedStr = record.optString("last_updated")
                    
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
                    
                    val parsedDevice = Device(
                        id = tokenVal,
                        name = deviceNameVal,
                        battery = batteryVal,
                        lastActive = lastActiveMs,
                        storageUsed = storageUsedVal,
                        storageTotal = storageTotalVal,
                        isLocked = false,
                        networkType = netTypeVal,
                        carrierName = netNameVal,
                        isCharging = statusVal == "charging",
                        isRealtimeUpdated = true, // Also true here
                        status = statusVal
                    )
                    onDeviceUpdate?.invoke(parsedDevice)
                } else {
                    var recordDeviceToken = record.optString("device_token")
                    if (recordDeviceToken.isBlank()) {
                        recordDeviceToken = record.optString("deviceToken")
                    }
                    
                    if (deviceToken != null && recordDeviceToken != deviceToken) {
                        // Not for this device
                        return
                    }
                    
                    val isActiveVal = record.optBoolean("is_active", record.optBoolean("isActive", false))
                    val imageBase64 = record.optString("image").takeIf { it.isNotBlank() && it != "null" }
                    val streamUrlVal = record.optString("stream_url", record.optString("streamUrl", "")).takeIf { it.isNotBlank() && it != "null" }
                    val timestampVal = record.optLong("timestamp", 0L)
                    val errorVal = record.optString("error").takeIf { it.isNotBlank() && it != "null" }
                    
                    if (table == "live_streams") {
                        val state = LiveStreamState(
                            isActive = isActiveVal,
                            isLoading = false,
                            image = imageBase64,
                            streamUrl = streamUrlVal,
                            timestamp = timestampVal,
                            error = errorVal
                        )
                        onLiveStreamUpdate?.invoke(recordDeviceToken, state)
                    } else if (table == "camera_stream") {
                        val cameraTypeVal = record.optString("camera_type", "back")
                        
                        val actualStreamUrl = if (imageBase64?.startsWith("rtsp://") == true || imageBase64?.startsWith("http") == true) imageBase64 else streamUrlVal
                        val actualImage = if (actualStreamUrl != null && actualStreamUrl == imageBase64) null else imageBase64
                        
                        val state = CameraStreamState(
                            isActive = isActiveVal,
                            isLoading = false,
                            image = actualImage,
                            streamUrl = actualStreamUrl,
                            cameraType = cameraTypeVal,
                            timestamp = timestampVal,
                            error = errorVal
                        )
                        onCameraStreamUpdate?.invoke(recordDeviceToken, state)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseRealtimeStreamer", "Error parsing WebSocket message", e)
        }
    }
    
    private fun reconnectIfNeed(rootUrl: String, anonKey: String) {
        if (isClosed) return
        reconnectJob?.cancel()
        reconnectJob = streamerScope.launch {
            delay(5000) // Wait 5 seconds before reconnecting
            if (isActive && !isClosed) {
                Log.d("SupabaseRealtimeStreamer", "Attempting reconnection...")
                connect(rootUrl, anonKey)
            }
        }
    }
    
    fun disconnect() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        try {
            webSocket?.close(1000, "Normal closure")
        } catch (e: Exception) {
            // Ignore
        }
        webSocket = null
        onStatusUpdate?.invoke(false)
    }
    
    fun sendBroadcast(topic: String, eventName: String, payload: JSONObject) {
        try {
            val msg = JSONObject().apply {
                put("topic", topic)
                put("event", "broadcast")
                put("payload", JSONObject().apply {
                    put("type", "broadcast")
                    put("event", eventName)
                    put("payload", payload)
                })
                put("ref", "bc_${System.currentTimeMillis()}")
            }
            webSocket?.send(msg.toString())
            Log.d("SupabaseRealtimeStreamer", "Sent broadcast msg to $topic: $msg")
        } catch (e: Exception) {
            Log.e("SupabaseRealtimeStreamer", "Error sending broadcast", e)
        }
    }

    fun shutdown() {
        isClosed = true
        disconnect()
        streamerScope.cancel()
    }
}
