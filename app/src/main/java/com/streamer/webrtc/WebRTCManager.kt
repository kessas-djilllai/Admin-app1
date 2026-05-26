package com.streamer.webrtc

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.webrtc.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class WebRTCManager {
    private var factory: PeerConnectionFactory? = null
    var eglBase: EglBase? = null
        private set
        
    private var peerConnection: PeerConnection? = null
    
    private val _connectionState = MutableStateFlow(PeerConnection.PeerConnectionState.NEW)
    val connectionState: StateFlow<PeerConnection.PeerConnectionState> = _connectionState
    
    private val _onIceCandidateToSend = MutableStateFlow<IceCandidate?>(null)
    val onIceCandidateToSend: StateFlow<IceCandidate?> = _onIceCandidateToSend

    fun initialize(context: Context) {
        if (factory != null) return
        Log.d("WebRTCManager", "Initializing PeerConnectionFactory...")
        
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )
        
        eglBase = EglBase.create()
        val eglContext = eglBase!!.eglBaseContext
        
        // Hardware encoder factory
        val encoderFactory = DefaultVideoEncoderFactory(eglContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglContext)
        
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
            
        Log.d("WebRTCManager", "PeerConnectionFactory initialized cleanly.")
    }

    fun createPeerConnection(rtcEventsHandler: PeerConnection.Observer) {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        
        peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d("WebRTCManager", "onSignalingChange: $state")
                rtcEventsHandler.onSignalingChange(state)
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d("WebRTCManager", "onIceConnectionChange: $state")
                rtcEventsHandler.onIceConnectionChange(state)
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                rtcEventsHandler.onIceConnectionReceivingChange(receiving)
            }
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d("WebRTCManager", "onIceGatheringChange: $state")
                rtcEventsHandler.onIceGatheringChange(state)
            }
            override fun onIceCandidate(candidate: IceCandidate?) {
                Log.d("WebRTCManager", "New ice candidate generated: $candidate")
                candidate?.let {
                    _onIceCandidateToSend.value = it
                    rtcEventsHandler.onIceCandidate(it)
                }
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
                rtcEventsHandler.onIceCandidatesRemoved(candidates)
            }
            override fun onAddStream(stream: MediaStream?) {
                rtcEventsHandler.onAddStream(stream)
            }
            override fun onRemoveStream(stream: MediaStream?) {
                rtcEventsHandler.onRemoveStream(stream)
            }
            override fun onDataChannel(channel: DataChannel?) {
                rtcEventsHandler.onDataChannel(channel)
            }
            override fun onRenegotiationNeeded() {
                rtcEventsHandler.onRenegotiationNeeded()
            }
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                rtcEventsHandler.onAddTrack(receiver, streams)
            }
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {
                Log.d("WebRTCManager", "onConnectionChange: $state")
                state?.let { _connectionState.value = it }
                rtcEventsHandler.onConnectionChange(state)
            }
        })
        
        Log.d("WebRTCManager", "PeerConnection created successfully.")
    }

    fun addScreenTrack(videoTrack: VideoTrack) {
        Log.d("WebRTCManager", "Adding Screen track to PeerConnection...")
        try {
            val streamId = "screen_stream"
            peerConnection?.addTrack(videoTrack, listOf(streamId))
        } catch (e: Exception) {
            Log.e("WebRTCManager", "Error adding screen track: ${e.message}", e)
        }
    }

    fun addCameraTrack(videoTrack: VideoTrack, label: String) {
        Log.d("WebRTCManager", "Adding Camera track ($label) to PeerConnection...")
        try {
            val streamId = "${label}_stream"
            peerConnection?.addTrack(videoTrack, listOf(streamId))
        } catch (e: Exception) {
            Log.e("WebRTCManager", "Error adding camera track $label: ${e.message}", e)
        }
    }

    fun createVideoSource(isScreencast: Boolean): VideoSource? {
        return factory?.createVideoSource(isScreencast)
    }

    fun createVideoTrack(id: String, source: VideoSource): VideoTrack? {
        return factory?.createVideoTrack(id, source)
    }

    fun setBitrate() {
        Log.d("WebRTCManager", "Setting target bitrates: min=1Mbps, max=8Mbps")
        try {
            val senders = peerConnection?.senders ?: return
            for (sender in senders) {
                val parameters = sender.parameters ?: continue
                if (parameters.encodings.isNotEmpty()) {
                    for (encoding in parameters.encodings) {
                        encoding.minBitrateBps = 1_000_000 // 1Mbps
                        encoding.maxBitrateBps = 8_000_000 // 8Mbps
                        encoding.maxFramerate = 60
                    }
                    sender.parameters = parameters
                }
            }
        } catch (e: Exception) {
            Log.e("WebRTCManager", "Error setting bitrate: ${e.message}", e)
        }
    }

    suspend fun createOffer(): String = suspendCancellableCoroutine { cont ->
        Log.d("WebRTCManager", "Creating Local SDP Offer...")
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d("WebRTCManager", "Local SDP set successfully.")
                            cont.resume(it.description)
                        }
                        override fun onCreateFailure(error: String?) {
                            Log.e("WebRTCManager", "onSetLocalDescription Failure: $error")
                            cont.resume("")
                        }
                        override fun onSetFailure(error: String?) {
                            Log.e("WebRTCManager", "onSetLocalDescription Failure: $error")
                            cont.resume("")
                        }
                    }, it)
                } ?: cont.resume("")
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e("WebRTCManager", "onCreateOffer Failure: $error")
                cont.resume("")
            }
            override fun onSetFailure(error: String?) {
                Log.e("WebRTCManager", "onCreateOffer Failure: $error")
                cont.resume("")
            }
        }, MediaConstraints())
    }

    fun handleAnswer(sdp: String) {
        Log.d("WebRTCManager", "Handling Answer SDP...")
        try {
            val sessionDesc = SessionDescription(SessionDescription.Type.ANSWER, sdp)
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {}
                override fun onSetSuccess() {
                    Log.d("WebRTCManager", "Remote SDP Answer set successfully.")
                    setBitrate()
                }
                override fun onCreateFailure(error: String?) {}
                override fun onSetFailure(error: String?) {
                    Log.e("WebRTCManager", "Remote SDP Answer set failure: $error")
                }
            }, sessionDesc)
        } catch (e: Exception) {
            Log.e("WebRTCManager", "Error setting remote Description answer: ${e.message}", e)
        }
    }

    fun addIceCandidate(candidate: IceCandidate) {
        Log.d("WebRTCManager", "Adding Remote IceCandidate...")
        try {
            peerConnection?.addIceCandidate(candidate)
        } catch (e: Exception) {
            Log.e("WebRTCManager", "Error adding ice candidate: ${e.message}", e)
        }
    }

    fun release() {
        Log.d("WebRTCManager", "Releasing all WebRTC resources...")
        try {
            peerConnection?.close()
            peerConnection = null
            eglBase?.release()
            eglBase = null
            factory = null
            Log.d("WebRTCManager", "WebRTC release completed.")
        } catch (e: Exception) {
            Log.e("WebRTCManager", "Error releasing WebRTC resources: ${e.message}", e)
        }
    }
}
