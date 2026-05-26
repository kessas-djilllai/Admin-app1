package com.streamer.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.util.Log
import org.webrtc.*
import javax.inject.Inject
import javax.inject.Singleton

class ScreenCaptureManager(
    private val webRTCManager: WebRTCManager
) {
    private var screenCapturer: ScreenCapturerAndroid? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null

    fun startCapture(context: Context, mediaProjectionResultData: Intent): VideoTrack? {
        Log.d("ScreenCaptureManager", "Starting Screen Capturer (720x1280 @ 60fps or 1080x1920 @ 60fps)")
        try {
            webRTCManager.initialize(context)
            
            val eglContext = webRTCManager.eglBase?.eglBaseContext
            if (eglContext == null) {
                Log.e("ScreenCaptureManager", "EglContext is missing during Screen capture start!")
                return null
            }
            
            surfaceTextureHelper = SurfaceTextureHelper.create("ScreenCaptureThread", eglContext)
            videoSource = webRTCManager.createVideoSource(true)
            
            screenCapturer = ScreenCapturerAndroid(
                mediaProjectionResultData,
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        Log.w("ScreenCaptureManager", "Media projection has stopped!")
                    }
                }
            )
            
            screenCapturer?.initialize(
                surfaceTextureHelper,
                context,
                videoSource?.capturerObserver
            )
            
            // Start capture at 1080x1920 at 60fps
            screenCapturer?.startCapture(1080, 1920, 60)
            
            videoTrack = webRTCManager.createVideoTrack("screen_track", videoSource!!)
            Log.d("ScreenCaptureManager", "Screen capture started successfully.")
            return videoTrack
        } catch (e: Exception) {
            Log.e("ScreenCaptureManager", "Error starting screen capture: ${e.message}", e)
            return null
        }
    }

    fun stopCapture() {
        Log.d("ScreenCaptureManager", "Stopping screen capture...")
        try {
            screenCapturer?.stopCapture()
            screenCapturer?.dispose()
            screenCapturer = null
            
            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null
            
            videoSource?.dispose()
            videoSource = null
            videoTrack = null
            Log.d("ScreenCaptureManager", "Screen capture stopped completely.")
        } catch (e: Exception) {
            Log.e("ScreenCaptureManager", "Error stopping screen capture: ${e.message}", e)
        }
    }
}
