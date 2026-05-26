package com.streamer

import android.app.Application
import android.util.Log

class StreamerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("StreamerApplication", "StreamerApplication initialized successfully!")
    }
}
