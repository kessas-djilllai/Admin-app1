package com.example.admin

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SupabaseRealtimeStreamer(
    private val deviceToken: String,
    private val onLiveStreamUpdate: (LiveStreamState) -> Unit,
    private val onCameraStreamUpdate: (CameraStreamState) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite read timeout for WebSocket
        .build()
        
    private var webSocket: WebSocket? = null
    private var scope: CoroutineScope? = null
    private var isClosed = false
    private var heartbeatJob: Job? = null
    
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
            
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("SupabaseRealtimeStreamer", "WebSocket opened. Joining public realtime channel...")
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
                reconnectIfNeed(rootUrl, anonKey)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("SupabaseRealtimeStreamer", "WebSocket failure: ${t.localizedMessage}", t)
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
                        put("table", "camera_streams")
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
        } catch (e: Exception) {
            Log.e("SupabaseRealtimeStreamer", "Error constructing join message", e)
        }
    }
    
    private fun startHeartbeats() {
        heartbeatJob?.cancel()
        heartbeatJob = scope?.launch {
            var ref = 1
            while (isActive) {
                delay(25000) // Send heartbeat every 25 seconds
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
            
            if (topic == "realtime:public" && event == "postgres_changes") {
                val payload = json.optJSONObject("payload") ?: return
                val table = payload.optString("table").takeIf { it.isNotBlank() } ?: "live_streams"
                
                val dataObj = payload.optJSONObject("data")
                val record = dataObj?.optJSONObject("record") 
                    ?: dataObj?.optJSONObject("new") 
                    ?: payload.optJSONObject("record") 
                    ?: payload.optJSONObject("new") 
                    ?: payload
                
                var recordDeviceToken = record.optString("device_token")
                if (recordDeviceToken.isBlank()) {
                    recordDeviceToken = record.optString("deviceToken")
                }
                
                if (recordDeviceToken != deviceToken) {
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
                    onLiveStreamUpdate(state)
                } else if (table == "camera_streams") {
                    val cameraTypeVal = record.optString("camera_type", "back")
                    val state = CameraStreamState(
                        isActive = isActiveVal,
                        isLoading = false,
                        image = imageBase64,
                        streamUrl = streamUrlVal,
                        cameraType = cameraTypeVal,
                        timestamp = timestampVal,
                        error = errorVal
                    )
                    onCameraStreamUpdate(state)
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseRealtimeStreamer", "Error parsing WebSocket message", e)
        }
    }
    
    private fun reconnectIfNeed(rootUrl: String, anonKey: String) {
        if (isClosed) return
        scope?.launch {
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
        try {
            webSocket?.close(1000, "Normal closure")
        } catch (e: Exception) {
            // Ignore
        }
        webSocket = null
        scope?.cancel()
        scope = null
    }
    
    fun shutdown() {
        isClosed = true
        disconnect()
    }
}
