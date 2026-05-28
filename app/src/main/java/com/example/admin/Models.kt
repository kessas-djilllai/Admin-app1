package com.example.admin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

data class Device(
    val id: String,
    val name: String,
    val battery: Int,
    val lastActive: Long,
    val storageUsed: Long = 0L, // in Bytes
    val storageTotal: Long = 0L, // in Bytes
    val isLocked: Boolean = false,
    val networkType: String? = null,
    val carrierName: String? = null,
    val isCharging: Boolean = false,
    val isRealtimeUpdated: Boolean = false,
    val isOnlineOverride: Boolean? = null,
    val status: String? = null
) {
    val isOnline: Boolean
        get() = when {
            isOnlineOverride == false -> false
            isOnlineOverride == true -> true
            status == "disconnected" -> false
            status == "connected" -> true
            else -> lastActive == 0L || (System.currentTimeMillis() - lastActive) < 15 * 60 * 1000
        }
}

data class SmsLog(
    val id: String,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val type: String // "incoming" or "outgoing"
)

data class SecurityAlert(
    val id: String,
    val title: String, // "DEVICE_BOOTED", "BATTERY_LOW", etc.
    val message: String,
    val timestamp: Long
)

data class InstalledApp(
    val name: String,
    val packageName: String,
    val isSystem: Boolean
)

data class FileItem(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val date: String
)

data class MediaItem(
    val id: String,
    val base64: String = "",
    val url: String = "",
    val timestamp: Long,
    val type: String, // "screenshot", "camera_front", "camera_back", "audio", "video_front", "video_back"
    val cameraType: String? = null,
    val commandSource: String = ""
) {
    var lastDecodeError: String? = null

    fun toBitmap(): Bitmap? {
        if (base64.isBlank()) return null
        return try {
            val cleanBase64 = if (base64.contains(",")) base64.substringAfter(",") else base64
            val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (t: Throwable) {
            lastDecodeError = t.message ?: t.toString()
            null
        }
    }

    fun toImageBitmap(): ImageBitmap? {
        return toBitmap()?.asImageBitmap()
    }
}

data class LiveStreamState(
    val isActive: Boolean = false,
    val isLoading: Boolean = false,
    val image: String? = null,
    val streamUrl: String? = null,
    val timestamp: Long = 0L,
    val error: String? = null
) {
    fun toBitmap(): Bitmap? {
        val imgStr = image ?: return null
        if (imgStr.isBlank()) return null
        return try {
            val cleanBase64 = if (imgStr.contains(",")) imgStr.substringAfter(",") else imgStr
            val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size, options)
        } catch (t: Throwable) {
            null
        }
    }
}

data class CameraStreamState(
    val isActive: Boolean = false,
    val isLoading: Boolean = false,
    val image: String? = null,
    val streamUrl: String? = null,
    val cameraType: String = "back", // "front" or "back"
    val timestamp: Long = 0L,
    val error: String? = null
) {

    fun toBitmap(): Bitmap? {
        val imgStr = image ?: return null
        if (imgStr.isBlank()) return null
        return try {
            val cleanBase64 = if (imgStr.contains(",")) imgStr.substringAfter(",") else imgStr
            val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size, options)
        } catch (t: Throwable) {
            null
        }
    }
}

data class Contact(
    val id: String,
    val name: String,
    val number: String
)

