package com.example.admin

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.IceCandidate
import java.io.IOException

class SignalingManager(
    private val databaseUrl: String
) {
    private val client = OkHttpClient()
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private fun getRoomUrl(roomId: String): String {
        val sanitizedBase = databaseUrl.trim().removeSuffix("/")
        return "$sanitizedBase/rooms/$roomId"
    }

    suspend fun clearRoom(roomId: String) = withContext(Dispatchers.IO) {
        val url = "${getRoomUrl(roomId)}.json"
        val request = Request.Builder()
            .url(url)
            .delete()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                Log.d("SignalingManager", "Room $roomId cleared: ${response.isSuccessful}")
            }
        } catch (e: Exception) {
            Log.e("SignalingManager", "Error clearing room $roomId", e)
        }
    }

    suspend fun sendAnswer(roomId: String, sdp: String) = withContext(Dispatchers.IO) {
        val url = "${getRoomUrl(roomId)}/answer.json"
        val json = JSONObject().apply {
            put("type", "answer")
            put("sdp", sdp)
            put("timestamp", System.currentTimeMillis())
        }
        val request = Request.Builder()
            .url(url)
            .put(json.toString().toRequestBody(mediaType))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                Log.d("SignalingManager", "Sent external Answer: ${response.isSuccessful}")
            }
        } catch (e: Exception) {
            Log.e("SignalingManager", "Error sending answer", e)
        }
    }

    suspend fun sendIceCandidate(roomId: String, candidate: IceCandidate, isLocalAdmin: Boolean) = withContext(Dispatchers.IO) {
        val url = "${getRoomUrl(roomId)}/candidates/admin.json"
        val json = JSONObject().apply {
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
            put("sdp", candidate.sdp)
        }
        val request = Request.Builder()
            .url(url)
            .put(json.toString().toRequestBody(mediaType))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                Log.d("SignalingManager", "Sent Admin ICE Candidate to child: ${response.isSuccessful}")
            }
        } catch (e: Exception) {
            Log.e("SignalingManager", "Error sending ice candidate", e)
        }
    }

    fun listenForOffer(roomId: String): Flow<String?> = flow {
        val url = "${getRoomUrl(roomId)}/offer.json"
        var lastOffer: String? = null
        while (currentCoroutineContext().isActive) {
            val request = Request.Builder().url(url).get().build()
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank() && body != "null") {
                            val json = JSONObject(body)
                            val sdp = json.optString("sdp")
                            if (sdp != lastOffer) {
                                lastOffer = sdp
                                emit(sdp)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SignalingManager", "Error listening for Offer", e)
            }
            delay(1000) // Poll for offer every 1 second
        }
    }.flowOn(Dispatchers.IO)

    fun listenForChildIceCandidates(roomId: String): Flow<IceCandidate> = flow {
        val url = "${getRoomUrl(roomId)}/candidates/child.json"
        var knownSdp: String? = null
        while (currentCoroutineContext().isActive) {
            val request = Request.Builder().url(url).get().build()
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank() && body != "null") {
                            val json = JSONObject(body)
                            val sdp = json.optString("sdp")
                            val sdpMid = json.optString("sdpMid")
                            val sdpMLineIndex = json.optInt("sdpMLineIndex", 0)
                            
                            if (sdp.isNotEmpty() && sdp != knownSdp) {
                                knownSdp = sdp
                                emit(IceCandidate(sdpMid, sdpMLineIndex, sdp))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SignalingManager", "Error listening for child ICE candidate", e)
            }
            delay(1000) // Poll for children candidates every 1 second
        }
    }.flowOn(Dispatchers.IO)
}
