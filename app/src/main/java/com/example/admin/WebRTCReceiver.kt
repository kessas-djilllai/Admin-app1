package com.example.admin

import android.content.Context
import android.util.Log
import org.webrtc.*
import java.util.UUID

class WebRTCReceiver(
    private val context: Context,
    private val onScreenTrack: (VideoTrack) -> Unit,
    private val onFrontCameraTrack: (VideoTrack) -> Unit,
    private val onBackCameraTrack: (VideoTrack) -> Unit,
    private val onIceCandidateReady: (IceCandidate) -> Unit,
    private val onConnectionStateChange: (PeerConnection.PeerConnectionState) -> Unit
) {
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var eglBase: EglBase? = null

    init {
        initializeWebRTC()
    }

    fun getEglContext(): EglBase.Context? {
        return eglBase?.eglBaseContext
    }

    private fun initializeWebRTC() {
        try {
            eglBase = EglBase.create()
            
            val options = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(options)

            val encoderFactory = DefaultVideoEncoderFactory(eglBase?.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(eglBase?.eglBaseContext)

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()
                
            Log.d("WebRTCReceiver", "WebRTC initialized successfully.")
        } catch (e: Exception) {
            Log.e("WebRTCReceiver", "Failed to initialize WebRTC", e)
        }
    }

    fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        ).apply {
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED // Disable TCP for ultra-low UDP delay
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d("WebRTCReceiver", "Signaling state changed: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d("WebRTCReceiver", "ICE connection state changed: $state")
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                Log.d("WebRTCReceiver", "ICE receiving change: $receiving")
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d("WebRTCReceiver", "ICE gathering state: $state")
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    Log.d("WebRTCReceiver", "New local ICE candidate: ${candidate.sdp}")
                    onIceCandidateReady(candidate)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {
                stream?.videoTracks?.forEach { track ->
                    routeVideoTrack(track)
                }
            }

            override fun onRemoveStream(stream: MediaStream?) {}

            override fun onDataChannel(channel: DataChannel?) {}

            override fun onRenegotiationNeeded() {}

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is VideoTrack) {
                    Log.d("WebRTCReceiver", "New Video Track added with ID: ${track.id()}")
                    routeVideoTrack(track)
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                Log.d("WebRTCReceiver", "Connection state changed: $newState")
                if (newState != null) {
                    onConnectionStateChange(newState)
                }
            }
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)
        Log.d("WebRTCReceiver", "PeerConnection created.")
    }

    private fun routeVideoTrack(track: VideoTrack) {
        val id = track.id()
        Log.d("WebRTCReceiver", "Routing track: $id")
        when {
            id.contains("screen", ignoreCase = true) || id.contains("screen_track", ignoreCase = true) -> {
                Log.d("WebRTCReceiver", "Routing to screen renderer")
                onScreenTrack(track)
            }
            id.contains("front", ignoreCase = true) || id.contains("camera_front", ignoreCase = true) -> {
                Log.d("WebRTCReceiver", "Routing to front camera renderer")
                onFrontCameraTrack(track)
            }
            id.contains("back", ignoreCase = true) || id.contains("camera_back", ignoreCase = true)  -> {
                Log.d("WebRTCReceiver", "Routing to back camera renderer")
                onBackCameraTrack(track)
            }
            else -> {
                // Dual/multi stream fallback if IDs are custom or indexed
                Log.d("WebRTCReceiver", "Routing track by default to front/back based on index")
                if (id.contains("1")) {
                    onFrontCameraTrack(track)
                } else if (id.contains("2")) {
                    onBackCameraTrack(track)
                } else {
                    onScreenTrack(track)
                }
            }
        }
    }

    fun handleOffer(sdpMarkup: String, onAnswerCreated: (String) -> Unit) {
        val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, sdpMarkup)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d("WebRTCReceiver", "Remote Offer description set successfully. Creating Answer...")
                createAnswer(onAnswerCreated)
            }
            override fun onCreateFailure(error: String?) {
                Log.e("WebRTCReceiver", "Failed to create/set remote offer: $error")
            }
            override fun onSetFailure(error: String?) {
                Log.e("WebRTCReceiver", "Failed to set remote offer: $error")
            }
        }, sessionDescription)
    }

    private fun createAnswer(onAnswerCreated: (String) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(d: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d("WebRTCReceiver", "Local Answer description set. Sending sdp answer...")
                            onAnswerCreated(desc.description)
                        }
                        override fun onCreateFailure(e: String?) {}
                        override fun onSetFailure(e: String?) {}
                    }, desc)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
        Log.d("WebRTCReceiver", "Added remote ICE candidate: ${candidate.sdp}")
    }

    fun close() {
        try {
            peerConnection?.close()
            peerConnection = null
            peerConnectionFactory?.dispose()
            peerConnectionFactory = null
            eglBase?.release()
            eglBase = null
            Log.d("WebRTCReceiver", "WebRTC receiver resources released.")
        } catch (e: Exception) {
            Log.e("WebRTCReceiver", "Error closing WebRTC receiver", e)
        }
    }
}
