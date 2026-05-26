package com.example.webrtc

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import org.webrtc.*
import java.util.concurrent.TimeUnit

class WebRtcClient(
    private val context: Context,
    private val signalingUrl: String,
    private val roomId: String,
    private val onConnectionStateChange: (PeerConnection.PeerConnectionState) -> Unit = {}
) {
    private val eglBase = EglBase.create()
    val eglBaseContext: EglBase.Context = eglBase.eglBaseContext

    var remoteVideoTrack by mutableStateOf<VideoTrack?>(null)
    var isConnected by mutableStateOf(false)
    var connectionError by mutableStateOf<String?>(null)

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var webSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    init {
        initWebRTC()
        connectSignaling()
    }

    private fun initWebRTC() {
        try {
            val options = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(options)

            val builder = PeerConnectionFactory.builder()
                .setOptions(PeerConnectionFactory.Options())
                
            builder.setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBaseContext))
            builder.setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBaseContext, true, true))
            
            peerConnectionFactory = builder.createPeerConnectionFactory()
            
            val rtcConfig = PeerConnection.RTCConfiguration(getIceServers()).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            }

            peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.d("WebRtcClient", "IceConnectionChange: $state")
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) {}

                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                    Log.d("WebRtcClient", "IceGatheringChange: $state")
                }

                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate?.let {
                        sendIceCandidate(it)
                    }
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

                override fun onAddStream(stream: MediaStream?) {
                    Log.d("WebRtcClient", "onAddStream: ${stream?.id}")
                    if (stream != null && stream.videoTracks.isNotEmpty()) {
                        remoteVideoTrack = stream.videoTracks[0]
                    }
                }

                override fun onRemoveStream(stream: MediaStream?) {}

                override fun onDataChannel(channel: DataChannel?) {}

                override fun onRenegotiationNeeded() {}

                override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                    Log.d("WebRtcClient", "onAddTrack")
                    val track = receiver?.track()
                    if (track is VideoTrack) {
                        remoteVideoTrack = track
                    }
                }

                override fun onTrack(transceiver: RtpTransceiver?) {
                    super.onTrack(transceiver)
                    val track = transceiver?.receiver?.track()
                    if (track is VideoTrack) {
                        remoteVideoTrack = track
                    }
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    Log.d("WebRtcClient", "onConnectionChange: $newState")
                    newState?.let {
                        onConnectionStateChange(it)
                        if (it == PeerConnection.PeerConnectionState.CONNECTED) {
                            isConnected = true
                            connectionError = null
                        } else if (it == PeerConnection.PeerConnectionState.FAILED || it == PeerConnection.PeerConnectionState.DISCONNECTED) {
                            isConnected = false
                        }
                    }
                }
            })
            
            peerConnection?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))
            peerConnection?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))

        } catch (e: Exception) {
            Log.e("WebRtcClient", "Error initializing WebRTC", e)
            connectionError = "خطأ في تهيئة WebRTC: $e"
        }
    }

    private fun connectSignaling() {
        // Replace HTTP/HTTPS with WS/WSS
        val wsUrl = signalingUrl.replace("https://", "wss://").replace("http://", "ws://")
        val request = Request.Builder().url(wsUrl).build()
        
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebRtcClient", "Signaling WebSocket connected")
                joinRoom()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebRtcClient", "Received signaling message: $text")
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type").takeIf { it.isNotBlank() } ?: json.optString("event")
                    
                    val data = json.optJSONObject("data") ?: json

                    when (type) {
                        "offer" -> {
                            val sdpStr = data.optString("sdp")
                            handleOffer(sdpStr)
                        }
                        "candidate" -> {
                            val candidateStr = data.optString("candidate")
                            val sdpMid = data.optString("sdpMid")
                            val sdpMLineIndex = data.optInt("sdpMLineIndex", 0)
                            handleRemoteCandidate(candidateStr, sdpMid, sdpMLineIndex)
                        }
                        "answer" -> {
                            val sdpStr = data.optString("sdp")
                            handleAnswer(sdpStr)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WebRtcClient", "Error parsing signaling message", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebRtcClient", "Signaling connection failed", t)
                connectionError = "فشل في الاتصال بخادم الإشارات: ${t.localizedMessage}. جاري إعادة الاتصال تلقائياً..."
                
                CoroutineScope(Dispatchers.IO).launch {
                    delay(5000)
                    if (this@WebRtcClient.webSocket == null || connectionError != null) {
                        connectSignaling()
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebRtcClient", "Signaling connection closing: $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebRtcClient", "Signaling connection closed: $reason")
            }
        })
    }

    private fun handleOffer(sdpStr: String) {
        val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, sdpStr)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d("WebRtcClient", "Set remote description (OFFER) success")
                createAnswer()
            }
            override fun onCreateFailure(reason: String?) {
                Log.e("WebRtcClient", "Set remote description failure: $reason")
            }
            override fun onSetFailure(reason: String?) {
                Log.e("WebRtcClient", "Set remote description failure: $reason")
            }
        }, sessionDescription)
    }

    private fun createAnswer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let { answerDesc ->
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d("WebRtcClient", "Set local description (ANSWER) success")
                            sendAnswer(answerDesc.description)
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(reason: String?) {
                            Log.e("WebRtcClient", "Set local description failure: $reason")
                        }
                    }, answerDesc)
                }
            }
            override fun onCreateFailure(reason: String?) {
                Log.e("WebRtcClient", "Create Answer failure: $reason")
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(reason: String?) {}
        }, constraints)
    }

    private fun handleAnswer(sdpStr: String) {
        val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, sdpStr)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d("WebRtcClient", "Set remote description (ANSWER) success")
            }
            override fun onCreateFailure(reason: String?) {}
            override fun onSetFailure(reason: String?) {}
        }, sessionDescription)
    }

    private fun handleRemoteCandidate(candidateStr: String, sdpMid: String, sdpMLineIndex: Int) {
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, candidateStr)
        peerConnection?.addIceCandidate(candidate)
        Log.d("WebRtcClient", "Added remote ICE candidate")
    }

    private fun joinRoom() {
        try {
            // General structure 1
            val msg1 = JSONObject().apply {
                put("type", "join")
                put("roomId", roomId)
                put("room", roomId)
                put("role", "parent")
            }
            webSocket?.send(msg1.toString())

            // General structure 2 (events-based commonly used in Socket.io-like websockets)
            val msg2 = JSONObject().apply {
                put("event", "join")
                put("data", JSONObject().apply {
                    put("roomId", roomId)
                    put("room", roomId)
                    put("role", "parent")
                })
            }
            webSocket?.send(msg2.toString())
            
            // General structure 3
            val msg3 = JSONObject().apply {
                put("action", "join")
                put("roomId", roomId)
            }
            webSocket?.send(msg3.toString())

            Log.d("WebRtcClient", "Join room request sent for room: $roomId")
        } catch (e: Exception) {
            Log.e("WebRtcClient", "Error sending join room message", e)
        }
    }

    private fun sendAnswer(sdpStr: String) {
        try {
            val msg = JSONObject().apply {
                put("type", "answer")
                put("sdp", sdpStr)
                put("roomId", roomId)
                put("room", roomId)
            }
            webSocket?.send(msg.toString())

            val msgEvent = JSONObject().apply {
                put("event", "answer")
                put("data", JSONObject().apply {
                    put("sdp", sdpStr)
                    put("roomId", roomId)
                })
            }
            webSocket?.send(msgEvent.toString())
        } catch (e: Exception) {
            Log.e("WebRtcClient", "Error sending answer", e)
        }
    }

    private fun sendIceCandidate(candidate: IceCandidate) {
        try {
            val msg = JSONObject().apply {
                put("type", "candidate")
                put("candidate", candidate.sdp)
                put("sdpMid", candidate.sdpMid)
                put("sdpMLineIndex", candidate.sdpMLineIndex)
                put("roomId", roomId)
                put("room", roomId)
            }
            webSocket?.send(msg.toString())

            val msgEvent = JSONObject().apply {
                put("event", "candidate")
                put("data", JSONObject().apply {
                    put("candidate", candidate.sdp)
                    put("sdpMid", candidate.sdpMid)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                })
            }
            webSocket?.send(msgEvent.toString())
        } catch (e: Exception) {
            Log.e("WebRtcClient", "Error sending ICE candidate", e)
        }
    }

    private fun getIceServers(): List<PeerConnection.IceServer> {
        return listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
        )
    }

    fun release() {
        try {
            webSocket?.close(1000, "Closed by player release")
            webSocket = null
            
            peerConnection?.dispose()
            peerConnection = null
            
            peerConnectionFactory?.dispose()
            peerConnectionFactory = null
            
            eglBase.release()
        } catch (e: Exception) {
            Log.e("WebRtcClient", "Error releasing WebRTC client", e)
        }
    }
}

@Composable
fun WebRtcLiveStreamPlayer(
    signalingUrl: String,
    roomId: String,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val rtcClient = remember(signalingUrl, roomId) {
        WebRtcClient(context, signalingUrl, roomId)
    }

    DisposableEffect(rtcClient) {
        onDispose {
            rtcClient.release()
        }
    }

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val videoTrack = rtcClient.remoteVideoTrack
        val isConnected = rtcClient.isConnected
        val errorMsg = rtcClient.connectionError

        if (videoTrack != null) {
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        init(rtcClient.eglBaseContext, null)
                        setEnableHardwareScaler(true)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                        videoTrack.addSink(this)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (errorMsg != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = errorMsg,
                    color = Color.Red,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF9155FF)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isConnected) "جاري استقبال بث WebRTC السريع..." else "جاري الاتصال بخادم البث الفوري (50ms)...",
                    color = Color.White,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
