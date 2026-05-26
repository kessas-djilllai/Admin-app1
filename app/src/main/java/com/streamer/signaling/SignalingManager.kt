package com.streamer.signaling

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.IceCandidate
import javax.inject.Inject
import javax.inject.Singleton

class SignalingManager {
    private var socket: Socket? = null
    
    private val _onAnswer = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val onAnswer: SharedFlow<String> = _onAnswer
    
    private val _onIceCandidate = MutableSharedFlow<IceCandidate>(extraBufferCapacity = 64)
    val onIceCandidate: SharedFlow<IceCandidate> = _onIceCandidate
    
    private val _onPeerJoined = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val onPeerJoined: SharedFlow<String> = _onPeerJoined

    private val scope = CoroutineScope(Dispatchers.IO)

    fun connect() {
        if (socket != null && socket!!.connected()) {
            Log.d("SignalingManager", "Socket already connected")
            return
        }
        try {
            Log.d("SignalingManager", "Connecting to socket...")
            val options = IO.Options().apply {
                forceNew = true
                reconnection = true
                reconnectionDelay = 1000
                reconnectionDelayMax = 5000
                reconnectionAttempts = Int.MAX_VALUE
                timeout = 20000
            }
            socket = IO.socket("https://webrtc-signaling-jj6h.onrender.com", options)
            
            socket?.on(Socket.EVENT_CONNECT, Emitter.Listener {
                Log.d("SignalingManager", "Socket Connected successfully!")
            })
            
            socket?.on(Socket.EVENT_DISCONNECT, Emitter.Listener {
                Log.d("SignalingManager", "Socket Disconnected, retrying...")
            })

            socket?.on("answer", Emitter.Listener { args ->
                val data = args[0] as? JSONObject ?: return@Listener
                Log.d("SignalingManager", "Received SDP Answer: $data")
                val sdp = data.optString("sdp") ?: ""
                scope.launch {
                    _onAnswer.emit(sdp)
                }
            })

            socket?.on("iceCandidate", Emitter.Listener { args ->
                val data = args[0] as? JSONObject ?: return@Listener
                Log.d("SignalingManager", "Received IceCandidate: $data")
                val sdpMid = data.optString("sdpMid") ?: ""
                val sdpMLineIndex = data.optInt("sdpMLineIndex", 0)
                val candidateSdp = data.optString("candidate") ?: ""
                scope.launch {
                    _onIceCandidate.emit(IceCandidate(sdpMid, sdpMLineIndex, candidateSdp))
                }
            })

            socket?.on("peerJoined", Emitter.Listener { args ->
                val peerId = args.getOrNull(0)?.toString() ?: "unknown"
                Log.d("SignalingManager", "New Peer Joined room: $peerId")
                scope.launch {
                    _onPeerJoined.emit(peerId)
                }
            })

            socket?.connect()
        } catch (e: Exception) {
            Log.e("SignalingManager", "Error connecting signaling socket: ${e.message}", e)
        }
    }

    fun joinRoom(roomId: String) {
        Log.d("SignalingManager", "Joining room: $roomId")
        try {
            val json = JSONObject().put("roomId", roomId)
            socket?.emit("joinRoom", json)
        } catch (e: Exception) {
            Log.e("SignalingManager", "Error joining room: ${e.message}", e)
        }
    }

    fun sendOffer(roomId: String, sdp: String) {
        Log.d("SignalingManager", "Sending SDP Offer for room: $roomId")
        try {
            val json = JSONObject()
                .put("roomId", roomId)
                .put("sdp", sdp)
            socket?.emit("offer", json)
        } catch (e: Exception) {
            Log.e("SignalingManager", "Error sending offer: ${e.message}", e)
        }
    }

    fun sendIceCandidate(roomId: String, candidate: IceCandidate) {
        Log.d("SignalingManager", "Sending IceCandidate for room: $roomId")
        try {
            val json = JSONObject()
                .put("roomId", roomId)
                .put("sdpMid", candidate.sdpMid)
                .put("sdpMLineIndex", candidate.sdpMLineIndex)
                .put("candidate", candidate.sdp)
            socket?.emit("iceCandidate", json)
        } catch (e: Exception) {
            Log.e("SignalingManager", "Error sending ice candidate: ${e.message}", e)
        }
    }

    fun disconnect() {
        try {
            socket?.disconnect()
            socket?.off()
            socket = null
            Log.d("SignalingManager", "Disconnected signaling socket and cleaned listeners.")
        } catch (e: Exception) {
            Log.e("SignalingManager", "Error disconnect socket: ${e.message}", e)
        }
    }
}
