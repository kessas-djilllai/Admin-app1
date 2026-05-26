package com.streamer.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamer.signaling.SignalingManager
import com.streamer.webrtc.CameraManager
import com.streamer.webrtc.ScreenCaptureManager
import com.streamer.webrtc.WebRTCManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.webrtc.*
import javax.inject.Inject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class StreamerState {
    object Idle : StreamerState()
    object Connecting : StreamerState()
    data class Streaming(val latency: Long) : StreamerState()
    data class Error(val message: String) : StreamerState()
}

class StreamerViewModel (
    val signalingManager: SignalingManager,
    val webRTCManager: WebRTCManager,
    val screenCaptureManager: ScreenCaptureManager,
    val cameraManager: CameraManager
) : ViewModel() {

    private val _streamState = MutableStateFlow<StreamerState>(StreamerState.Idle)
    val streamState: StateFlow<StreamerState> = _streamState

    var currentRoomId: String = ""
        private set

    // Hold track references for local previews if needed
    val activeScreenTrack = MutableStateFlow<VideoTrack?>(null)
    val activeFrontTrack = MutableStateFlow<VideoTrack?>(null)
    val activeBackTrack = MutableStateFlow<VideoTrack?>(null)

    init {
        Log.d("StreamerViewModel", "ViewModel Initialized.")
        
        // Listen for answers from signaling
        viewModelScope.launch {
            signalingManager.onAnswer.collectLatest { sdpAnswer ->
                Log.d("StreamerViewModel", "Answer received, submitting to WebRTCManager.")
                webRTCManager.handleAnswer(sdpAnswer)
                _streamState.value = StreamerState.Streaming(latency = 45L) // Mock 45ms latency as a fallback
            }
        }

        // Listen for ICE candidates from signaling
        viewModelScope.launch {
            signalingManager.onIceCandidate.collectLatest { candidate ->
                Log.d("StreamerViewModel", "Remote ICE Candidate received, adding progress.")
                webRTCManager.addIceCandidate(candidate)
            }
        }

        // Send local generated ICE candidates
        viewModelScope.launch {
            webRTCManager.onIceCandidateToSend.collectLatest { candidate ->
                if (candidate != null && currentRoomId.isNotEmpty()) {
                    Log.d("StreamerViewModel", "Sending local ICE candidate via signaling...")
                    signalingManager.sendIceCandidate(currentRoomId, candidate)
                }
            }
        }

        // Listen for connection status changes from WebRTC
        viewModelScope.launch {
            webRTCManager.connectionState.collect { rtcState ->
                Log.d("StreamerViewModel", "WebRTC ConnectionState updated: $rtcState")
                when (rtcState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> {
                        _streamState.value = StreamerState.Streaming(latency = 32)
                    }
                    PeerConnection.PeerConnectionState.FAILED,
                    PeerConnection.PeerConnectionState.DISCONNECTED -> {
                        _streamState.value = StreamerState.Error("قطع اتصال WebRTC")
                    }
                    else -> {}
                }
            }
        }
    }

    fun startStreaming(
        context: Context,
        roomId: String,
        projectionData: Intent?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (roomId.trim().isEmpty()) {
            onError("الرجاء إدخال رمز الغرفة (Room ID)")
            return
        }

        currentRoomId = roomId
        _streamState.value = StreamerState.Connecting
        Log.d("StreamerViewModel", "Starting streaming routine for Room ID: $roomId")

        viewModelScope.launch {
            try {
                // Initialize factory and egl context
                webRTCManager.initialize(context)

                // Create the PeerConnection with dummy/concrete rtc Observer
                webRTCManager.createPeerConnection(object : PeerConnection.Observer {
                    override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
                    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
                    override fun onIceCandidate(candidate: IceCandidate?) {}
                    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                    override fun onAddStream(stream: MediaStream?) {}
                    override fun onRemoveStream(stream: MediaStream?) {}
                    override fun onDataChannel(channel: DataChannel?) {}
                    override fun onRenegotiationNeeded() {}
                    override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
                    override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {}
                })

                // Start Cameras
                val frontTrack = cameraManager.startFrontCamera(context)
                val backTrack = cameraManager.startBackCamera(context)

                frontTrack?.let {
                    webRTCManager.addCameraTrack(it, "front_camera")
                    activeFrontTrack.value = it
                }
                
                backTrack?.let {
                    webRTCManager.addCameraTrack(it, "back_camera")
                    activeBackTrack.value = it
                }

                // Start Screen Capture if projection data is available
                if (projectionData != null) {
                    val screenTrack = screenCaptureManager.startCapture(context, projectionData)
                    screenTrack?.let {
                        webRTCManager.addScreenTrack(it)
                        activeScreenTrack.value = it
                    }
                }

                // Connect and Join Room via signaling server
                signalingManager.connect()
                signalingManager.joinRoom(roomId)

                // Generate and send WebRTC SDP Offer
                val sdpOffer = webRTCManager.createOffer()
                if (sdpOffer.isNotEmpty()) {
                    signalingManager.sendOffer(roomId, sdpOffer)
                    onSuccess()
                } else {
                    _streamState.value = StreamerState.Error("فشل إنشاء عرض SDP للاتصال")
                    onError("خطأ: فشل إنشاء عرض SDP للاتصال")
                }

            } catch (e: Exception) {
                val errMsg = e.message ?: "خطأ غير متوقع"
                Log.e("StreamerViewModel", "Fatal error during startStreaming: $errMsg", e)
                _streamState.value = StreamerState.Error(errMsg)
                onError(errMsg)
            }
        }
    }

    fun stopStreaming() {
        Log.d("StreamerViewModel", "Stopping stream and cleaning resources...")
        try {
            signalingManager.disconnect()
            screenCaptureManager.stopCapture()
            cameraManager.stopAll()
            webRTCManager.release()
            
            activeScreenTrack.value = null
            activeFrontTrack.value = null
            activeBackTrack.value = null
            
            _streamState.value = StreamerState.Idle
            Log.d("StreamerViewModel", "Resources cleaned successfully.")
        } catch (e: Exception) {
            Log.e("StreamerViewModel", "Error while stopping stream: ${e.message}", e)
        }
    }

    fun wakeUpServer() {
        Log.d("StreamerViewModel", "Ping request to signaling server /health...")
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val url = URL("https://webrtc-signaling-jj6h.onrender.com/health")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                val code = connection.responseCode
                Log.d("StreamerViewModel", "Signaling health response code: $code")
                connection.disconnect()
            } catch (e: Exception) {
                Log.e("StreamerViewModel", "Failed to contact signaling server: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopStreaming()
    }
}
