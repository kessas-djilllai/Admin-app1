package com.streamer.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*
import javax.inject.Inject
import javax.inject.Singleton

class CameraManager(
    private val webRTCManager: WebRTCManager
) {
    private var frontCapturer: CameraVideoCapturer? = null
    private var backCapturer: CameraVideoCapturer? = null
    
    private var frontHelper: SurfaceTextureHelper? = null
    private var backHelper: SurfaceTextureHelper? = null
    
    private var frontSource: VideoSource? = null
    private var backSource: VideoSource? = null
    
    private var frontTrack: VideoTrack? = null
    private var backTrack: VideoTrack? = null

    fun startFrontCamera(context: Context): VideoTrack? {
        if (frontTrack != null) return frontTrack
        Log.d("CameraManager", "Starting front camera (1280x720 @ 60fps)")
        try {
            webRTCManager.initialize(context)
            val eglContext = webRTCManager.eglBase?.eglBaseContext ?: return null
            
            val enumerator = Camera2Enumerator(context)
            val deviceNames = enumerator.deviceNames
            
            var frontDevice: String? = null
            for (device in deviceNames) {
                if (enumerator.isFrontFacing(device)) {
                    frontDevice = device
                    break
                }
            }
            
            if (frontDevice == null) {
                Log.e("CameraManager", "Front camera not found on device!")
                return null
            }
            
            frontCapturer = enumerator.createCapturer(frontDevice, null)
            frontHelper = SurfaceTextureHelper.create("CameraFrontThread", eglContext)
            frontSource = webRTCManager.createVideoSource(false)
            
            frontCapturer?.initialize(frontHelper, context, frontSource?.capturerObserver)
            frontCapturer?.startCapture(1280, 720, 60)
            
            frontTrack = webRTCManager.createVideoTrack("front_camera_track", frontSource!!)
            Log.d("CameraManager", "Front camera started successfully.")
            return frontTrack
        } catch (e: Exception) {
            Log.e("CameraManager", "Error starting front camera: ${e.message}", e)
            return null
        }
    }

    fun startBackCamera(context: Context): VideoTrack? {
        if (backTrack != null) return backTrack
        Log.d("CameraManager", "Starting back camera (1280x720 @ 60fps)")
        try {
            webRTCManager.initialize(context)
            val eglContext = webRTCManager.eglBase?.eglBaseContext ?: return null
            
            val enumerator = Camera2Enumerator(context)
            val deviceNames = enumerator.deviceNames
            
            var backDevice: String? = null
            for (device in deviceNames) {
                if (enumerator.isBackFacing(device)) {
                    backDevice = device
                    break
                }
            }
            
            if (backDevice == null) {
                Log.e("CameraManager", "Back camera not found on device!")
                return null
            }
            
            backCapturer = enumerator.createCapturer(backDevice, null)
            backHelper = SurfaceTextureHelper.create("CameraBackThread", eglContext)
            backSource = webRTCManager.createVideoSource(false)
            
            backCapturer?.initialize(backHelper, context, backSource?.capturerObserver)
            backCapturer?.startCapture(1280, 720, 60)
            
            backTrack = webRTCManager.createVideoTrack("back_camera_track", backSource!!)
            Log.d("CameraManager", "Back camera started successfully.")
            return backTrack
        } catch (e: Exception) {
            Log.e("CameraManager", "Error starting back camera: ${e.message}", e)
            return null
        }
    }

    fun stopAll() {
        Log.d("CameraManager", "Stopping front and back camera capturers...")
        try {
            // Front Camera
            frontCapturer?.stopCapture()
            frontCapturer?.dispose()
            frontCapturer = null
            
            frontHelper?.dispose()
            frontHelper = null
            
            frontSource?.dispose()
            frontSource = null
            
            frontTrack = null

            // Back Camera
            backCapturer?.stopCapture()
            backCapturer?.dispose()
            backCapturer = null
            
            backHelper?.dispose()
            backHelper = null
            
            backSource?.dispose()
            backSource = null
            
            backTrack = null
            
            Log.d("CameraManager", "All camera resources stopped successfully.")
        } catch (e: Exception) {
            Log.e("CameraManager", "Error during stopAll: ${e.message}", e)
        }
    }
}
